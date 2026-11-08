package com.gamso.wakely.presentation.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamso.wakely.R
import com.gamso.wakely.domain.model.LicenseId
import com.gamso.wakely.presentation.common.BackButton

private fun LicenseId.label() = when (this) {
    LicenseId.MIT -> "Licence MIT"
    LicenseId.APACHE_2 -> "Licence Apache 2.0"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    state: AboutUiState,
    onShowLicense: (LicenseId) -> Unit,
    onCloseLicense: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("À propos") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "app") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(88.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_alarm),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    Text(
                        "Wakely",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    state.appInfo?.let {
                        Text("Version ${it.versionName}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Le réveil qui s'adapte à votre agenda : jours fériés, congés et événements.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Développé par gamso",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text("© 2026 gamso · ${LicenseId.MIT.label()}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onShowLicense(LicenseId.MIT) }) { Text("Voir la licence de Wakely") }
                }
            }

            item(key = "credits-title") {
                Text(
                    "Composants tiers",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Wakely intègre les éléments suivants, utilisés conformément à leur licence.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(state.credits, key = { it.name }) { credit ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(credit.name, style = MaterialTheme.typography.titleSmall)
                        Text("© ${credit.author}", style = MaterialTheme.typography.bodySmall)
                        Text(credit.usage, style = MaterialTheme.typography.bodyMedium)
                        Text("Source : ${credit.source}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onShowLicense(credit.license) }, contentPadding = PaddingValues(0.dp)) {
                            Text(credit.license.label())
                        }
                    }
                }
            }
        }
    }

    state.openLicense?.let { (id, text) ->
        AlertDialog(
            onDismissRequest = onCloseLicense,
            confirmButton = { TextButton(onClick = onCloseLicense) { Text("Fermer") } },
            title = { Text(id.label()) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
            },
        )
    }
}
