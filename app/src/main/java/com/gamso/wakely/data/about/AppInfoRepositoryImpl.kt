package com.gamso.wakely.data.about

import android.content.Context
import com.gamso.wakely.domain.model.AppInfo
import com.gamso.wakely.domain.model.LicenseId
import com.gamso.wakely.domain.repository.AppInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Version from the installed package; license texts from assets/licenses. */
class AppInfoRepositoryImpl(private val context: Context) : AppInfoRepository {

    override fun getAppInfo(): AppInfo {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return AppInfo(versionName = info.versionName.orEmpty())
    }

    override suspend fun getLicenseText(id: LicenseId): String = withContext(Dispatchers.IO) {
        val file = when (id) {
            LicenseId.MIT -> "licenses/mit.txt"
            LicenseId.APACHE_2 -> "licenses/apache-2.0.txt"
        }
        runCatching { context.assets.open(file).bufferedReader().use { it.readText() } }.getOrDefault("")
    }
}
