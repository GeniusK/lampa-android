package top.rootu.lampa.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.Spanned
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.models.Release
import top.rootu.lampa.models.Releases
import top.rootu.lampa.net.TlsSocketFactory
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.GeneralSecurityException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory


object Updater {
    private const val TAG = "Updater"

    // Self-hosted update manifest for the (sole) "lite" flavor.
    // Manifest mimics the GitHub releases JSON and adds a numeric "version_code".
    private val RELEASE_LINK =
        "https://lampa.gk.cloud-ip.cc/media/downloads/update-${BuildConfig.FLAVOR}.json"
    private var releases: Releases? = null
    private var newVersion: Release? = null

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                // Enable TLSv1.2/1.3 via Conscrypt on Android < 10. Certificates are
                // validated against the system CA store (no trust-all).
                val socketFactory: SSLSocketFactory = TlsSocketFactory()
                HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory)
            } catch (_: GeneralSecurityException) {
            }
        }
    }

    fun check(): Boolean {
        try {
            val url = URL(RELEASE_LINK)
            val connection = if (RELEASE_LINK.startsWith("https"))
                url.openConnection() as HttpsURLConnection? // NetCipher.getHttpsURLConnection(url)
            else
                url.openConnection() as HttpURLConnection? // NetCipher.getHttpURLConnection(url)
            connection?.connect()
            val body = connection?.inputStream?.use {
                it.bufferedReader(Charset.defaultCharset()).readText()
            } ?: return false
            releases = getJson(body, Releases::class.java)
            releases?.forEach { rel ->
                if (rel.version_code > BuildConfig.VERSION_CODE) {
                    newVersion = rel
                    connection.disconnect()
                    return true
                }
            }
            connection.disconnect()
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getVersion(): String {
        if (newVersion == null)
            CoroutineScope(Dispatchers.IO).launch {
                check()
            }
        return newVersion?.tag_name?.replace("v", "") ?: ""
    }

    fun getOverview(): Spanned {
        var ret = ""

        releases?.forEach { rel ->
            if (rel.version_code > BuildConfig.VERSION_CODE) {
                ret += "<font color='white'><b>${rel.tag_name}</b></font> <br>"
                ret += "<i>${rel.body.replace("\r\n", "<br/>")}</i><br/><br/>"
            } else {
                ret += "${rel.tag_name}<br>"
                ret += "<i>${rel.body.replace("\r\n", "<br/>")}</i><br/><br/>"
            }
        }
        return HtmlCompat.fromHtml(ret.trim(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private val download = Any()

    private fun downloadApk(file: File, onProgress: ((prc: Int) -> Unit)?) {
        synchronized(download) {
            newVersion?.let { rel ->
                if (file.exists())
                    file.delete()
                // Pick the APK asset for this flavor; fall back to the first .apk.
                // (Previously the loop kept only the LAST asset regardless of type.)
                val link = UpdateUtils.pickApkUrl(rel.assets, BuildConfig.FLAVOR) ?: ""
                if (link.isNotEmpty()) {
                    try {
                        val url = URL(link)
                        val connection = if (link.startsWith("https"))
                            url.openConnection() as HttpsURLConnection? // NetCipher.getHttpsURLConnection(url)
                        else
                            url.openConnection() as HttpURLConnection? // NetCipher.getHttpURLConnection(url)
                        connection?.connect()
                        connection?.inputStream.use { input ->
                            FileOutputStream(file).use { fileOut ->
                                val contentLength = connection?.contentLength ?: 0
                                if (onProgress == null)
                                    input?.copyTo(fileOut)
                                else {
                                    val buffer = ByteArray(65535)
                                    val length = contentLength + 1
                                    var offset: Long = 0
                                    while (true) {
                                        val read = input?.read(buffer) ?: 0
                                        offset += read
                                        val prc = (offset * 100 / length).toInt()
                                        onProgress(prc)
                                        if (read <= 0)
                                            break
                                        fileOut.write(buffer, 0, read)
                                    }
                                    fileOut.flush()
                                }
                                fileOut.flush()
                                fileOut.close()
                            }
                        }
                        connection?.disconnect()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun installNewVersion(onProgress: ((prc: Int) -> Unit)?) {
        val ctx = App.context
        if (newVersion == null && !check())
            return

        newVersion?.let {
            // NB: mkdirs() must run on the parent dir, not on the .apk file itself
            // (the old code created a *directory* named LAMPA.apk).
            val destination = File(ctx.getExternalFilesDir(null), "LAMPA.apk")
            destination.parentFile?.mkdirs()
            if (destination.exists()) destination.delete()

            downloadApk(destination, onProgress)

            if (!destination.exists() || destination.length() == 0L) {
                Log.e(TAG, "APK download failed or empty")
                App.toast(R.string.download_failed_message)
                return
            }

            // Refuse to install an APK that is not signed by this app's own key.
            // Defeats a MITM-swapped update: the attacker cannot forge our signature,
            // and Android would reject a differently-signed update anyway.
            if (!isApkSignedBySameCert(ctx, destination)) {
                Log.e(TAG, "Downloaded APK signature mismatch — aborting install")
                destination.delete()
                App.toast(R.string.update_signature_mismatch)
                return
            }

            // Launch the installer directly and catch absence, rather than gating on
            // resolveActivity() — under package visibility (targetSdk 30+) the latter
            // can return null even when startActivity() would succeed.
            val install = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setDataAndType(
                        Uri.fromFile(destination),
                        "application/vnd.android.package-archive"
                    )
                }
            } else {
                val fileUri = FileProvider.getUriForFile(
                    ctx,
                    BuildConfig.APPLICATION_ID + ".update_provider",
                    destination
                )
                Intent(Intent.ACTION_VIEW, fileUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            try {
                ctx.startActivity(install)
            } catch (_: ActivityNotFoundException) {
                App.toast(R.string.error_app_not_found)
            }
        }
    }

    /**
     * True when [apk]'s signing certificate set exactly matches the currently
     * installed app's. Uses GET_SIGNATURES (available on all supported API levels).
     */
    @Suppress("DEPRECATION", "PackageManagerGetSignatures")
    private fun isApkSignedBySameCert(ctx: Context, apk: File): Boolean {
        return try {
            val pm = ctx.packageManager
            val archive = pm.getPackageArchiveInfo(
                apk.absolutePath, PackageManager.GET_SIGNATURES
            ) ?: return false
            val installed = pm.getPackageInfo(
                ctx.packageName, PackageManager.GET_SIGNATURES
            )
            val archiveSigs = archive.signatures?.mapNotNull { it?.toByteArray()?.toHex() }
                ?.toSet().orEmpty()
            val installedSigs = installed.signatures?.mapNotNull { it?.toByteArray()?.toHex() }
                ?.toSet().orEmpty()
            archiveSigs.isNotEmpty() && archiveSigs == installedSigs
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed", e)
            false
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}