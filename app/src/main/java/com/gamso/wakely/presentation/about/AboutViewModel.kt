package com.gamso.wakely.presentation.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.wakely.domain.model.AppInfo
import com.gamso.wakely.domain.model.LicenseId
import com.gamso.wakely.domain.repository.AppInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A third-party component shipped in the app. */
data class Credit(val name: String, val author: String, val usage: String, val license: LicenseId, val source: String)

data class AboutUiState(
    val appInfo: AppInfo? = null,
    val credits: List<Credit> = CREDITS,
    /** License currently displayed in full, with its text. */
    val openLicense: Pair<LicenseId, String>? = null,
)

class AboutViewModel(private val repository: AppInfoRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AboutUiState(appInfo = runCatching { repository.getAppInfo() }.getOrNull()))
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    fun showLicense(id: LicenseId) = viewModelScope.launch {
        val text = repository.getLicenseText(id)
        _uiState.update { it.copy(openLicense = id to text) }
    }

    fun closeLicense() = _uiState.update { it.copy(openLicense = null) }
}

private val CREDITS = listOf(
    Credit(
        name = "Sonneries d'alarme Android",
        author = "The Android Open Source Project",
        usage = "Les 30 sonneries « Sonneries Android (AOSP) », intégrées sans modification (fichiers renommés).",
        license = LicenseId.APACHE_2,
        source = "android.googlesource.com/platform/frameworks/base/+/main/data/sounds",
    ),
    Credit(
        name = "Icônes Material Design",
        author = "Google",
        usage = "Icônes « Réglages » (roue dentée) et « Retour » (flèche).",
        license = LicenseId.APACHE_2,
        source = "github.com/google/material-design-icons",
    ),
    Credit(
        name = "AndroidX et Jetpack Compose",
        author = "The Android Open Source Project",
        usage = "Interface (Compose, Material 3), activités, cycle de vie et ViewModel.",
        license = LicenseId.APACHE_2,
        source = "developer.android.com/jetpack/androidx",
    ),
    Credit(
        name = "Kotlin et kotlinx.coroutines",
        author = "JetBrains s.r.o. et contributeurs",
        usage = "Langage et bibliothèque standard, programmation asynchrone.",
        license = LicenseId.APACHE_2,
        source = "kotlinlang.org",
    ),
)
