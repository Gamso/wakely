package com.gamso.wakely.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.repository.CalendarRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads calendars synced on the device through CalendarContract: every Android account
 * (Google, Outlook/Exchange, Samsung...) is available without network access at alarm time.
 */
class CalendarRepositoryImpl(private val context: Context) : CalendarRepository {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun getCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_COLOR,
        )
        buildList {
            context.contentResolver.query(
                Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${Calendars.ACCOUNT_NAME} ASC, ${Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(Calendars._ID)
                val name = c.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME)
                val account = c.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME)
                val color = c.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR)
                while (c.moveToNext()) {
                    add(
                        DeviceCalendar(
                            c.getLong(id),
                            c.getString(name).orEmpty(),
                            c.getString(account).orEmpty(),
                            c.getInt(color),
                        ),
                    )
                }
            }
        }
    }

    override suspend fun getEvents(sourceKeys: Set<String>, from: LocalDate, to: LocalDate): List<CalendarEvent> =
        deviceEvents(sourceKeys, from, to)

    private suspend fun deviceEvents(sourceKeys: Set<String>, from: LocalDate, to: LocalDate): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            val ids = sourceKeys.filter { it.startsWith(PREFIX) }.mapNotNull { it.removePrefix(PREFIX).toLongOrNull() }
            if (ids.isEmpty() || !hasPermission()) return@withContext emptyList()
            val zone = ZoneId.systemDefault()
            // One-day margin: all-day events are stored in UTC.
            val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
            val uri = Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, begin)
                ContentUris.appendId(it, end)
            }.build()
            val selection = "${Instances.CALENDAR_ID} IN (${ids.joinToString { "?" }}) AND " +
                "(${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Events.STATUS_CANCELED})"
            val projection = arrayOf(
                Instances.CALENDAR_ID,
                Instances.TITLE,
                Instances.DESCRIPTION,
                Instances.BEGIN,
                Instances.END,
                Instances.ALL_DAY,
            )
            val events = mutableListOf<CalendarEvent>()
            context.contentResolver.query(
                uri,
                projection,
                selection,
                ids.map { it.toString() }.toTypedArray(),
                "${Instances.BEGIN} ASC",
            )?.use { c ->
                val calendarIdCol = c.getColumnIndexOrThrow(Instances.CALENDAR_ID)
                val titleCol = c.getColumnIndexOrThrow(Instances.TITLE)
                val descriptionCol = c.getColumnIndexOrThrow(Instances.DESCRIPTION)
                val beginCol = c.getColumnIndexOrThrow(Instances.BEGIN)
                val endCol = c.getColumnIndexOrThrow(Instances.END)
                val allDayCol = c.getColumnIndexOrThrow(Instances.ALL_DAY)
                while (c.moveToNext()) {
                    val allDay = c.getInt(allDayCol) == 1
                    // All-day instants are UTC midnights; timed ones are converted to local time.
                    val eventZone = if (allDay) ZoneOffset.UTC else zone
                    events += CalendarEvent(
                        sourceKey = DeviceCalendar.keyOf(c.getLong(calendarIdCol)),
                        title = c.getString(titleCol).orEmpty(),
                        description = c.getString(descriptionCol).orEmpty(),
                        start = Instant.ofEpochMilli(c.getLong(beginCol)).atZone(eventZone).toLocalDateTime(),
                        end = Instant.ofEpochMilli(c.getLong(endCol)).atZone(eventZone).toLocalDateTime(),
                        allDay = allDay,
                    )
                }
            }
            events
        }

    private companion object {
        const val PREFIX = "device:"
    }
}
