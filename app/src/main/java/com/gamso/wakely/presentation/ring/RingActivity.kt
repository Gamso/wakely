package com.gamso.wakely.presentation.ring

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamso.wakely.framework.AlarmService
import com.gamso.wakely.framework.RingingAlarm
import com.gamso.wakely.framework.RingingStateHolder
import com.gamso.wakely.presentation.common.Format
import com.gamso.wakely.presentation.common.appViewModelFactory
import com.gamso.wakely.presentation.theme.WakelyTheme
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

class RingViewModel(ringingState: RingingStateHolder) : ViewModel() {
    val ringing: StateFlow<RingingAlarm?> = ringingState.ringing
}

/** Full-screen UI shown (even when locked) while the alarm rings. */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            WakelyTheme {
                val vm: RingViewModel = viewModel(factory = appViewModelFactory { RingViewModel(it.ringingState) })
                val ringing by vm.ringing.collectAsStateWithLifecycle()
                LaunchedEffect(ringing) { if (ringing == null) finish() }
                RingScreen(
                    label = ringing?.label.orEmpty(),
                    snoozeMinutes = ringing?.snoozeMinutes ?: 0,
                    onStop = {
                        AlarmService.stop(this)
                        finish()
                    },
                    onSnooze = {
                        AlarmService.snooze(this)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun RingScreen(label: String, snoozeMinutes: Int, onStop: () -> Unit, onSnooze: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(Format.time(now), style = MaterialTheme.typography.displayLarge)
            Text(label.ifBlank { "Réveil" }, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(64.dp))
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Arrêter", style = MaterialTheme.typography.titleLarge)
            }
            if (snoozeMinutes > 0) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Répéter dans $snoozeMinutes min")
                }
            }
        }
    }
}
