package org.totschnig.myexpenses.util.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.totschnig.myexpenses.BuildConfig
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val downloadUrl: String,
    val apkName: String,
    val htmlUrl: String,
    val size: Long
)

object AppUpdateManager {

    private const val PREF_KEY_LAST_UPDATE_CHECK = "last_github_update_check_millis"
    private const val UPDATE_CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L // 24 hours

    private val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Compares remote release tag with current app version.
     * Returns true if remote version is strictly newer.
     */
    fun isNewerVersion(remoteTag: String, currentVersionName: String): Boolean {
        try {
            val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V").removePrefix("r")
            val cleanCurrent = currentVersionName.trim().removePrefix("v").removePrefix("V").removePrefix("r")

            if (cleanRemote == cleanCurrent) return false

            val remoteParts = cleanRemote.split("-", limit = 2)
            val currentParts = cleanCurrent.split("-", limit = 2)

            val remoteSemVer = remoteParts[0].split(".").mapNotNull { it.toIntOrNull() }
            val currentSemVer = currentParts[0].split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteSemVer.size, currentSemVer.size)
            for (i in 0 until maxLen) {
                val r = remoteSemVer.getOrElse(i) { 0 }
                val c = currentSemVer.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }

            // Numeric components are identical. Check suffix (e.g. "4.1.3" vs "4.1.3-pre")
            val remoteHasPre = remoteParts.size > 1
            val currentHasPre = currentParts.size > 1

            // A release without pre-release suffix is newer than one with pre-release suffix
            if (!remoteHasPre && currentHasPre) return true
            if (remoteHasPre && !currentHasPre) return false

            return false
        } catch (e: Exception) {
            Timber.w(e, "Failed to compare versions: remoteTag=$remoteTag, current=$currentVersionName")
            return false
        }
    }

    /**
     * Checks if at least 24 hours have elapsed since the last update check.
     */
    fun shouldCheckAutomatically(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(PREF_KEY_LAST_UPDATE_CHECK, 0L)
        return (System.currentTimeMillis() - lastCheck) >= UPDATE_CHECK_INTERVAL_MILLIS
    }

    /**
     * Records the timestamp of an update check.
     */
    fun recordCheckTime(context: Context) {
        val prefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(PREF_KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
    }

    /**
     * Queries GitHub Releases for the latest release.
     * Returns ReleaseInfo if a newer release exists with an APK asset, null if up to date.
     */
    suspend fun checkUpdate(
        context: Context,
        client: OkHttpClient = defaultHttpClient
    ): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val repo = BuildConfig.GITHUB_REPO
            val url = "https://api.github.com/repos/$repo/releases/latest"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MyExpenses-Android-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("GitHub API returned HTTP ${response.code}")
                )
            }

            val bodyString = response.body.string()
            if (bodyString.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Empty response from GitHub API"))
            }

            val json = JSONObject(bodyString)
            val tagName = json.optString("tag_name")
            val releaseName = json.optString("name", tagName)
            val releaseBody = json.optString("body", "")
            val htmlUrl = json.optString("html_url", "https://github.com/$repo/releases")

            val assetsArray = json.optJSONArray("assets")
            var apkDownloadUrl: String? = null
            var apkName: String? = null
            var apkSize: Long = 0L

            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url")
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkDownloadUrl.isNullOrEmpty() || apkName.isNullOrEmpty()) {
                Timber.d("No APK asset found for release $tagName")
                return@withContext Result.success(null)
            }

            val isNewer = isNewerVersion(tagName, BuildConfig.VERSION_NAME)
            if (isNewer) {
                val releaseInfo = ReleaseInfo(
                    tagName = tagName,
                    name = releaseName,
                    body = releaseBody,
                    downloadUrl = apkDownloadUrl,
                    apkName = apkName,
                    htmlUrl = htmlUrl,
                    size = apkSize
                )
                Result.success(releaseInfo)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking GitHub release updates")
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file to the app's cache directory while reporting progress.
     */
    suspend fun downloadApk(
        context: Context,
        releaseInfo: ReleaseInfo,
        client: OkHttpClient = defaultHttpClient,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            val targetFile = File(updatesDir, releaseInfo.apkName)
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val request = Request.Builder()
                .url(releaseInfo.downloadUrl)
                .header("User-Agent", "MyExpenses-Android-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to download APK: HTTP ${response.code}")
                )
            }

            val responseBody = response.body

            val totalBytes = if (releaseInfo.size > 0) releaseInfo.size else responseBody.contentLength()
            var downloadedBytes = 0L

            responseBody.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            onProgress(100)
            Result.success(targetFile)
        } catch (e: Exception) {
            Timber.e(e, "Error downloading update APK")
            Result.failure(e)
        }
    }

    /**
     * Checks if the app has permission to request package installs on Android 8.0+.
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Returns an intent directing the user to enable "Install unknown apps" for MyExpenses.
     */
    fun createManageUnknownAppSourcesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Launches the system package installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}
