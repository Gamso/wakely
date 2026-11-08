package com.gamso.wakely.data.local

import android.content.Context
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.SummarySettings
import com.gamso.wakely.domain.repository.SettingsRepository
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val filters = MutableStateFlow(loadFilters())
    private val summary = MutableStateFlow(loadSummary())

    override fun observeCalendarFilters(): Flow<List<CalendarFilter>> = filters.asStateFlow()

    override suspend fun getCalendarFilters(): List<CalendarFilter> = filters.value

    override suspend fun setCalendarFilters(filters: List<CalendarFilter>) {
        this.filters.value = filters
        val json = JSONArray().also { arr ->
            filters.forEach {
                arr.put(
                    JSONObject()
                        .put("source", it.sourceKey)
                        .put("category", it.category.name)
                        .put("keywords", it.keywords),
                )
            }
        }
        withContext(Dispatchers.IO) { prefs.edit().putString(KEY_FILTERS, json.toString()).commit() }
    }

    override fun observeSummarySettings(): Flow<SummarySettings> = summary.asStateFlow()

    override suspend fun getSummarySettings(): SummarySettings = summary.value

    override suspend fun setSummarySettings(settings: SummarySettings) {
        summary.value = settings
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putBoolean(KEY_SUMMARY_ENABLED, settings.enabled)
                .putString(KEY_SUMMARY_TIME, settings.time.toString())
                .commit()
        }
    }

    private fun loadFilters(): List<CalendarFilter> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_FILTERS, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            if (o.has("source")) {
                CalendarFilter(
                    sourceKey = o.getString("source"),
                    category = runCatching { CalendarCategory.valueOf(o.getString("category")) }
                        .getOrDefault(CalendarCategory.HOLIDAYS),
                    keywords = o.optString("keywords"),
                )
            } else {
                // Format of version 1: device calendar id only, used for days off.
                CalendarFilter(
                    DeviceCalendar.keyOf(o.getLong("id")),
                    CalendarCategory.HOLIDAYS,
                    o.optString("keywords"),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun loadSummary() = SummarySettings(
        enabled = prefs.getBoolean(KEY_SUMMARY_ENABLED, true),
        time = runCatching { LocalTime.parse(prefs.getString(KEY_SUMMARY_TIME, null)) }
            .getOrDefault(SummarySettings().time),
    )

    private companion object {
        const val PREFS = "settings"
        const val KEY_FILTERS = "calendar_filters"
        const val KEY_SUMMARY_ENABLED = "summary_enabled"
        const val KEY_SUMMARY_TIME = "summary_time"
    }
}
