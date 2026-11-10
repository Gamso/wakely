package com.gamso.wakely.presentation.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamso.wakely.R
import com.gamso.wakely.domain.model.LicenseId
import com.gamso.wakely.presentation.common.BackButton

@Composable
private fun LicenseId.label() = stringResource(
    when (this) {
        LicenseId.MIT -> R.string.license_mit
        LicenseId.APACHE_2 -> R.string.license_apache
    },
)

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
                title = { Text(stringResource(R.string.about_title)) },
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
                    // The launcher icon on a white disc: its artwork is drawn for a 72 dp window inside 108 dp.
                    Box(
                        Modifier.size(88.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.requiredSize(132.dp),
                        )
                    }
                    Text(
                        "Wakely",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    state.appInfo?.let {
                        Text(
                            stringResource(R.string.app_version, it.versionName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        stringResource(R.string.developed_by),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        stringResource(R.string.fmt_copyright, LicenseId.MIT.label()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        onShowLicense(LicenseId.MIT)
                    }) { Text(stringResource(R.string.view_license)) }
                }
            }

            item(key = "credits-title") {
                Text(
                    stringResource(R.string.third_party),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.third_party_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(state.credits, key = { it.name }) { credit ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(credit.name), style = MaterialTheme.typography.titleSmall)
                        Text("© ${stringResource(credit.author)}", style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(credit.usage), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.source_label, credit.source),
                            style = MaterialTheme.typography.bodySmall,
                        )
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
            confirmButton = { TextButton(onClick = onCloseLicense) { Text(stringResource(R.string.action_close)) } },
            title = { Text(id.label()) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
            },
        )
    }
}
