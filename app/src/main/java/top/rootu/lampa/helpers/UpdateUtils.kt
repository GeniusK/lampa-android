package top.rootu.lampa.helpers

import top.rootu.lampa.models.Asset

/**
 * Pure helpers for the self-updater, kept free of Android/framework dependencies
 * so they can be unit-tested on the JVM.
 */
object UpdateUtils {

    /**
     * Chooses the APK download URL from a release's assets.
     *
     * Prefers an `.apk` whose file name contains [flavor] (e.g. `app-lite-release.apk`),
     * then falls back to the first `.apk` asset. Returns `null` when there is no APK
     * (the previous implementation blindly took the *last* asset of any type).
     */
    fun pickApkUrl(assets: List<Asset>, flavor: String): String? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        return apks.firstOrNull { it.name.contains(flavor, ignoreCase = true) }?.browser_download_url
            ?: apks.firstOrNull()?.browser_download_url
    }
}
