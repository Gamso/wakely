package com.gamso.wakely.presentation.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gamso.wakely.R
import com.gamso.wakely.domain.model.PatternHash
import com.gamso.wakely.domain.usecase.PatternLock

/** Choosing the pattern: draw it, then draw it again to confirm. [onSave] gets the dots once both drawings agree. */
@Composable
fun PatternSetupDialog(onSave: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<List<Int>?>(null) }
    var problem by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(if (first == null) R.string.pattern_draw else R.string.pattern_confirm)) },
        text = {
            key(first) {
                PatternBox(problem, problem != null) { dots ->
                    val chosen = first
                    when {
                        chosen == null && !PatternLock.isValid(dots) -> problem = R.string.pattern_too_short

                        chosen == null -> {
                            first = dots
                            problem = null
                        }

                        chosen == dots -> onSave(dots)

                        else -> {
                            first = null
                            problem = R.string.pattern_mismatch
                        }
                    }
                }
            }
        },
    )
}

/** Asks for the pattern to stop an alarm: [onRight] runs when [stored] is drawn, a wrong drawing is just refused. */
@Composable
fun PatternChallengeDialog(stored: PatternHash, onRight: () -> Unit, onDismiss: () -> Unit) {
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.pattern_ring_title)) },
        text = {
            PatternBox(if (wrong) R.string.pattern_wrong else null, wrong) { dots ->
                if (PatternLock.matches(dots, stored)) onRight() else wrong = true
            }
        },
    )
}

@Composable
private fun PatternBox(@StringRes message: Int?, error: Boolean, onPattern: (List<Int>) -> Unit) {
    Column {
        Text(
            if (message != null) stringResource(message) else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        PatternPad(onPattern, Modifier.fillMaxWidth(), error)
    }
}
