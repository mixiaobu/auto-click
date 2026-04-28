package org.xiaobu.autoclick.data.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class InstalledAppInfo(
    val packageName: String,
    val appLabel: String,
    val isSystemApp: Boolean
)

fun Context.getInstalledAppInfo(): List<InstalledAppInfo> {
    val applications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
    }

    return applications
        .mapNotNull { info ->
            val packageName = info.packageName.orEmpty()
            if (packageName.isBlank()) return@mapNotNull null
            InstalledAppInfo(
                packageName = packageName,
                appLabel = packageManager.getApplicationLabel(info).toString().ifBlank { packageName },
                isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<InstalledAppInfo> { it.appLabel.lowercase() }.thenBy { it.packageName })
}

fun Context.resolveAppLabel(packageName: String): String {
    if (packageName.isBlank()) return ""
    return runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}

fun Context.getAppIcon(packageName: String): Drawable? {
    if (packageName.isBlank()) return null
    return runCatching {
        packageManager.getApplicationIcon(packageName)
    }.getOrNull()
}
