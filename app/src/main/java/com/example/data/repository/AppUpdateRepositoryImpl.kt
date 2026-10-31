package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.domain.model.AppUpdateInfo
import com.example.domain.model.UpdateDownloadState
import com.example.domain.repository.AppUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class AppUpdateRepositoryImpl(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val sharedPreferences: SharedPreferences? = null
) : AppUpdateRepository {

    private val prefs: SharedPreferences by lazy {
        sharedPreferences ?: context.getSharedPreferences("app_updates_pref", Context.MODE_PRIVATE)
    }

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    override val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val currentVersionCode: Int by lazy {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (_: Exception) {
            1
        }
    }

    private val currentVersionName: String by lazy {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    override suspend fun checkForUpdate(force: Boolean): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            // 1. Primary: raw.githubusercontent.com version.json (zero API rate limits)
            val rawUrl = "https://raw.githubusercontent.com/Boltsky/Mnowatch-TV/main/version.json"
            val rawRequest = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "MunowatchTV-Updater")
                .header("Cache-Control", "no-cache")
                .build()

            val rawResult = try {
                okHttpClient.newCall(rawRequest).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val body = response.body!!.string()
                        parseVersionJson(body)
                    } else null
                }
            } catch (_: Exception) {
                null
            }

            if (rawResult != null) {
                return@withContext Result.success(rawResult)
            }

            // 2. Secondary fallback: GitHub Releases API
            val releaseUrl = "https://api.github.com/repos/Boltsky/Mnowatch-TV/releases/latest"
            val releaseRequest = Request.Builder()
                .url(releaseUrl)
                .header("User-Agent", "MunowatchTV-Updater")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val releaseResult = try {
                okHttpClient.newCall(releaseRequest).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val body = response.body!!.string()
                        parseGitHubReleaseJson(body)
                    } else null
                }
            } catch (_: Exception) {
                null
            }

            if (releaseResult != null) {
                return@withContext Result.success(releaseResult)
            }

            // Return current version with no update if both endpoints unreachable
            Result.success(
                AppUpdateInfo(
                    isUpdateAvailable = false,
                    latestVersionCode = currentVersionCode,
                    latestVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseVersionJson(jsonStr: String): AppUpdateInfo {
        val json = JSONObject(jsonStr)
        val latestCode = json.optInt("versionCode", 1)
        val latestName = json.optString("versionName", "1.0")
        val title = json.optString("title", "Munowatch TV v$latestName")
        val changelog = json.optString("changelog", "Performance improvements and bug fixes.")
        val apkUrl = json.optString("apkUrl", "https://raw.githubusercontent.com/Boltsky/Mnowatch-TV/main/apk/Munowatch-TV.apk")
        val releaseDate = json.optString("releaseDate", "")
        val forceUpdate = json.optBoolean("forceUpdate", false)

        val isAvailable = latestCode > currentVersionCode

        return AppUpdateInfo(
            isUpdateAvailable = isAvailable,
            latestVersionCode = latestCode,
            latestVersionName = latestName,
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
            title = title,
            changelog = changelog,
            apkUrl = apkUrl,
            releaseDate = releaseDate,
            isForceUpdate = forceUpdate
        )
    }

    fun parseGitHubReleaseJson(jsonStr: String): AppUpdateInfo {
        val json = JSONObject(jsonStr)
        val tagName = json.optString("tag_name", "v1.0").removePrefix("v")
        val title = json.optString("name", "Munowatch TV v$tagName")
        val changelog = json.optString("body", "Performance improvements and bug fixes.")
        val publishedAt = json.optString("published_at", "")

        // Find APK asset in release
        var apkUrl = "https://raw.githubusercontent.com/Boltsky/Mnowatch-TV/main/apk/Munowatch-TV.apk"
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", apkUrl)
                    break
                }
            }
        }

        // Try extracting numeric version code from tag name (e.g. "1.1" -> 11 or parsed integer)
        val codeMatch = Regex("""\b(\d+)\b""").find(tagName)?.value?.toIntOrNull() ?: 1
        val isAvailable = codeMatch > currentVersionCode || (tagName != currentVersionName && tagName > currentVersionName)

        return AppUpdateInfo(
            isUpdateAvailable = isAvailable,
            latestVersionCode = maxOf(codeMatch, currentVersionCode + if (isAvailable) 1 else 0),
            latestVersionName = tagName,
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
            title = title,
            changelog = changelog,
            apkUrl = apkUrl,
            releaseDate = publishedAt.take(10),
            isForceUpdate = false
        )
    }

    override suspend fun downloadApk(apkUrl: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = UpdateDownloadState.Downloading(0f, 0L, 0L)

            val targetDir = File(context.cacheDir, "updates")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, "Munowatch-TV.apk")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "MunowatchTV-Updater")
                .header("Accept", "*/*")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    val errMsg = "Failed to download update: HTTP ${response.code}"
                    _downloadState.value = UpdateDownloadState.Error(errMsg)
                    return@withContext Result.failure(Exception(errMsg))
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    val progress = if (contentLength > 0) {
                        (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                    } else {
                        -1f
                    }
                    _downloadState.value = UpdateDownloadState.Downloading(progress, totalRead, contentLength)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile
                )
                _downloadState.value = UpdateDownloadState.ReadyToInstall(uri, targetFile)
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            _downloadState.value = UpdateDownloadState.Error(e.message ?: "Download failed")
            Result.failure(e)
        }
    }

    override fun installApk(apkFile: File): Result<Unit> {
        return try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun dismissCurrentUpdate(versionCode: Int) {
        prefs.edit().putInt("dismissed_version_code", versionCode).apply()
    }

    override fun isUpdateDismissed(versionCode: Int): Boolean {
        return prefs.getInt("dismissed_version_code", 0) >= versionCode
    }

    override fun resetDownloadState() {
        _downloadState.value = UpdateDownloadState.Idle
    }
}
