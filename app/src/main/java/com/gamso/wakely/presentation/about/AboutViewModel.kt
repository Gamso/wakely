package com.gamso.wakely.presentation.about

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.wakely.R
import com.gamso.wakely.domain.model.AppInfo
import com.gamso.wakely.domain.model.LicenseId
import com.gamso.wakely.domain.repository.AppInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A third-party component shipped in the app. */
data class Credit(
    @StringRes val name: Int,
    @StringRes val author: Int,
    @StringRes val usage: Int,
    val license: LicenseId,
    val source: String,
)

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
        name = R.string.credit_aosp_name,
        author = R.string.author_aosp,
        usage = R.string.credit_aosp_usage,
        license = LicenseId.APACHE_2,
        source = "android.googlesource.com/platform/frameworks/base/+/main/data/sounds",
    ),
    Credit(
        name = R.string.credit_icons_name,
        author = R.string.author_google,
        usage = R.string.credit_icons_usage,
        license = LicenseId.APACHE_2,
        source = "github.com/google/material-design-icons",
    ),
    Credit(
        name = R.string.credit_androidx_name,
        author = R.string.author_aosp,
        usage = R.string.credit_androidx_usage,
        license = LicenseId.APACHE_2,
        source = "developer.android.com/jetpack/androidx",
    ),
    Credit(
        name = R.string.credit_kotlin_name,
        author = R.string.author_jetbrains,
        usage = R.string.credit_kotlin_usage,
        license = LicenseId.APACHE_2,
        source = "kotlinlang.org",
    ),
    Credit(
        name = R.string.credit_jollyday_name,
        author = R.string.author_jollyday,
        usage = R.string.credit_jollyday_usage,
        license = LicenseId.APACHE_2,
        source = "github.com/focus-shift/jollyday",
    ),
)
