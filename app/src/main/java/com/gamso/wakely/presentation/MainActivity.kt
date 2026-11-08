package com.gamso.wakely.presentation

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamso.wakely.R
import com.gamso.wakely.presentation.about.AboutScreen
import com.gamso.wakely.presentation.about.AboutViewModel
import com.gamso.wakely.presentation.alarms.AlarmListScreen
import com.gamso.wakely.presentation.alarms.AlarmListViewModel
import com.gamso.wakely.presentation.alarms.SystemWarnings
import com.gamso.wakely.presentation.common.appViewModelFactory
import com.gamso.wakely.presentation.edit.AlarmEditScreen
import com.gamso.wakely.presentation.edit.AlarmEditViewModel
import com.gamso.wakely.presentation.events.EventAlarmEditScreen
import com.gamso.wakely.presentation.events.EventAlarmEditViewModel
import com.gamso.wakely.presentation.settings.SettingsScreen
import com.gamso.wakely.presentation.settings.SettingsViewModel
import com.gamso.wakely.presentation.theme.WakelyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WakelyTheme { WakelyNavigation() } }
    }
}

private object Routes {
    const val LIST = "list"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    /** The unique suffix guarantees a fresh ViewModel each time an editor opens. */
    fun new() = "edit/new/${System.nanoTime()}"
    fun edit(id: Long) = "edit/$id/${System.nanoTime()}"
    fun newEvent() = "event/new/${System.nanoTime()}"
    fun editEvent(id: Long) = "event/$id/${System.nanoTime()}"
}

@Composable
private fun WakelyNavigation() {
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf(Routes.LIST) }
    BackHandler(enabled = route != Routes.LIST) {
        route = if (route == Routes.ABOUT) Routes.SETTINGS else Routes.LIST
    }

    // Read through a State so the pre-filled keyword follows the language of the phone.
    val holidayKeyword = rememberUpdatedState(stringResource(R.string.holiday_keyword))
    val listVm: AlarmListViewModel = viewModel(
        factory = appViewModelFactory {
            AlarmListViewModel(
                it.alarmRepository, it.eventAlarmRepository, it.settingsRepository, it.calendarRepository,
                it.alarmScheduler, it.getDaysOff, it.getRuleEvents, it.computeNextRing, it.setAlarmEnabled,
                it.setEventAlarmEnabled, it.rescheduleAlarms, it.timeProvider,
            )
        },
    )
    var warnings by rememberSaveable(stateSaver = WarningsSaver) { mutableStateOf(SystemWarnings()) }
    var permissionsTick by rememberSaveable { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        warnings = systemWarnings(context)
        permissionsTick++
        listVm.refresh()
    }
    val requestCalendar = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR)) }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    // First launch: request all needed permissions at once.
    LaunchedEffect(Unit) {
        val missing = buildList {
            if (!context.isGranted(Manifest.permission.READ_CALENDAR)) add(Manifest.permission.READ_CALENDAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !context.isGranted(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        warnings = systemWarnings(context)
        listVm.refresh()
    }

    when {
        route == Routes.LIST -> {
            val state by listVm.uiState.collectAsStateWithLifecycle()
            AlarmListScreen(
                state = state,
                warnings = warnings,
                onAdd = { route = Routes.new() },
                onAddEventAlarm = { route = Routes.newEvent() },
                onEdit = { route = Routes.edit(it) },
                onEditEventAlarm = { route = Routes.editEvent(it) },
                onToggle = { item, enabled -> listVm.onToggle(item.alarm, enabled) },
                onToggleEventAlarm = { item, enabled -> listVm.onToggle(item.rule, enabled) },
                onOpenCalendars = { route = Routes.SETTINGS },
                onRequestCalendarPermission = requestCalendar,
                onRequestNotifications = requestNotifications,
                onOpenExactAlarmSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    }
                },
                onOpenFullScreenSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        context.openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    }
                },
            )
        }

        route == Routes.ABOUT -> {
            val vm: AboutViewModel = viewModel(factory = appViewModelFactory { AboutViewModel(it.appInfoRepository) })
            val state by vm.uiState.collectAsStateWithLifecycle()
            AboutScreen(
                state = state,
                onShowLicense = { vm.showLicense(it) },
                onCloseLicense = vm::closeLicense,
                onBack = { route = Routes.SETTINGS },
            )
        }

        route == Routes.SETTINGS -> {
            val vm: SettingsViewModel = viewModel(
                key = "settings-$permissionsTick",
                factory = appViewModelFactory {
                    SettingsViewModel(
                        it.calendarRepository,
                        it.settingsRepository,
                        it.updateCalendarFilters,
                        it.updateSummarySettings,
                        it.applicationScope,
                        holidayKeyword = { holidayKeyword.value },
                    )
                },
            )
            LaunchedEffect(Unit) { vm.reload() }
            val state by vm.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                viewModel = vm,
                onBack = { route = Routes.LIST },
                onRequestPermission = requestCalendar,
                onOpenAbout = { route = Routes.ABOUT },
            )
        }

        route.startsWith("event/") -> {
            val ruleId = route.split('/').getOrNull(1)?.toLongOrNull()
            val vm: EventAlarmEditViewModel = viewModel(
                key = route,
                factory = appViewModelFactory {
                    EventAlarmEditViewModel(
                        ruleId,
                        it.eventAlarmRepository,
                        it.calendarRepository,
                        it.settingsRepository,
                        it.saveEventAlarm,
                        it.deleteEventAlarm,
                        it.timeProvider,
                        it.soundRepository.defaultSound(),
                    )
                },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            EventAlarmEditScreen(vm, state, onClose = { route = Routes.LIST })
        }

        else -> {
            val alarmId = route.split('/').getOrNull(1)?.toLongOrNull()
            val vm: AlarmEditViewModel = viewModel(
                key = route,
                factory = appViewModelFactory {
                    AlarmEditViewModel(
                        alarmId,
                        it.alarmRepository,
                        it.saveAlarm,
                        it.deleteAlarm,
                        it.soundRepository.defaultSound(),
                    )
                },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            AlarmEditScreen(vm, state, onClose = { route = Routes.LIST })
        }
    }
}

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openSettings(action: String) {
    runCatching {
        startActivity(Intent(action, Uri.parse("package:$packageName")))
    }
}

private fun systemWarnings(context: Context): SystemWarnings {
    val nm = context.getSystemService(NotificationManager::class.java)
    return SystemWarnings(
        notificationsDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.isGranted(Manifest.permission.POST_NOTIFICATIONS),
        fullScreenDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !nm.canUseFullScreenIntent(),
    )
}

private val WarningsSaver = androidx.compose.runtime.saveable.Saver<SystemWarnings, List<Boolean>>(
    save = { listOf(it.notificationsDenied, it.fullScreenDenied) },
    restore = { SystemWarnings(it[0], it[1]) },
)
