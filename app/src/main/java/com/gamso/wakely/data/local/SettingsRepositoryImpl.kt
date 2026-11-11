package com.gamso.wakely.data.local

import android.content.Context
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.HolidaySettings
import com.gamso.wakely.domain.model.PatternHash
import com.gamso.wakely.domain.model.SummarySettings
import com.gamso.wakely.domain.repository.SettingsRepository
import java.time.LocalDate
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
    private val holiday = MutableStateFlow(loadHoliday())
    private val pause =
        MutableStateFlow(prefs.getString(KEY_PAUSE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    private val pauseButton = MutableStateFlow(prefs.getBoolean(KEY_PAUSE_BUTTON, true))
    private val skipButtons = MutableStateFlow(prefs.getBoolean(KEY_SKIP_BUTTONS, true))
    private val unlockPattern = MutableStateFlow(
        prefs.getString(KEY_PATTERN_SALT, null)?.let { salt ->
            prefs.getString(KEY_PATTERN_HASH, null)?.let { PatternHash(salt, it) }
        },
    )

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

    override fun observePauseButton(): Flow<Boolean> = pauseButton.asStateFlow()

    override suspend fun setPauseButton(enabled: Boolean) {
        pauseButton.value = enabled
        withContext(Dispatchers.IO) { prefs.edit().putBoolean(KEY_PAUSE_BUTTON, enabled).commit() }
    }

    override fun observeUnlockPattern(): Flow<PatternHash?> = unlockPattern.asStateFlow()

    override suspend fun getUnlockPattern(): PatternHash? = unlockPattern.value

    override suspend fun setUnlockPattern(pattern: PatternHash?) {
        unlockPattern.value = pattern
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_PATTERN_SALT, pattern?.salt).putString(KEY_PATTERN_HASH, pattern?.hash).commit()
        }
    }

    override fun observeSkipButtons(): Flow<Boolean> = skipButtons.asStateFlow()

    override suspend fun getSkipButtons(): Boolean = skipButtons.value

    override suspend fun setSkipButtons(enabled: Boolean) {
        skipButtons.value = enabled
        withContext(Dispatchers.IO) { prefs.edit().putBoolean(KEY_SKIP_BUTTONS, enabled).commit() }
    }

    override fun observePause(): Flow<LocalDate?> = pause.asStateFlow()

    override suspend fun getPause(): LocalDate? = pause.value

    override suspend fun setPause(until: LocalDate?) {
        pause.value = until
        withContext(Dispatchers.IO) {
            prefs.edit().apply {
                if (until !=
                    null
                ) {
                    putString(KEY_PAUSE, until.toString())
                } else {
                    remove(KEY_PAUSE)
                }
            }.commit()
        }
    }

    override fun observeHolidaySettings(): Flow<HolidaySettings> = holiday.asStateFlow()

    override suspend fun getHolidaySettings(): HolidaySettings = holiday.value

    override suspend fun setHolidaySettings(settings: HolidaySettings) {
        holiday.value = settings
        withContext(Dispatchers.IO) {
            prefs.edit().apply {
                // Absent = never chosen (follow the device); empty = chosen "none".
                if (settings.countryChosen) {
                    putString(
                        KEY_HOLIDAY_COUNTRY,
                        settings.country.orEmpty(),
                    )
                } else {
                    remove(KEY_HOLIDAY_COUNTRY)
                }
                val region = settings.region
                if (region != null) {
                    putString(KEY_HOLIDAY_REGION, region)
                } else {
                    remove(KEY_HOLIDAY_REGION)
                }
                putStringSet(KEY_HOLIDAY_EXCLUDED, settings.excludedIds.toSet())
            }.commit()
        }
    }

    private fun loadHoliday(): HolidaySettings {
        val stored = prefs.getString(KEY_HOLIDAY_COUNTRY, null)
        return HolidaySettings(
            country = stored?.takeIf { it.isNotEmpty() },
            countryChosen = stored != null,
            region = prefs.getString(KEY_HOLIDAY_REGION, null)?.takeIf { it.isNotEmpty() },
            excludedIds = prefs.getStringSet(KEY_HOLIDAY_EXCLUDED, emptySet()).orEmpty().toSet(),
        )
    }

    /**
     * Public holidays no longer come from calendars: filters saved with the former "holidays" category (or in the
     * format of version 1, which only had that one) are dropped.
     */
    private fun loadFilters(): List<CalendarFilter> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_FILTERS, "[]"))
        (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            val category = if (o.has("source")) {
                runCatching { CalendarCategory.valueOf(o.getString("category")) }.getOrNull()
            } else {
                null
            }
            category?.let { c -> CalendarFilter(o.getString("source"), c, o.optString("keywords")) }
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
        const val KEY_SKIP_BUTTONS = "skip_buttons"
        const val KEY_PATTERN_SALT = "unlock_pattern_salt"
        const val KEY_PATTERN_HASH = "unlock_pattern_hash"
        const val KEY_PAUSE = "pause_until"
        const val KEY_PAUSE_BUTTON = "pause_button"
        const val KEY_SUMMARY_ENABLED = "summary_enabled"
        const val KEY_SUMMARY_TIME = "summary_time"
        const val KEY_HOLIDAY_COUNTRY = "holiday_country"
        const val KEY_HOLIDAY_REGION = "holiday_region"
        const val KEY_HOLIDAY_EXCLUDED = "holiday_excluded_ids"
    }
}
