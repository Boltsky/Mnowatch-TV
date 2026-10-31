package com.example.domain.model

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0",
    val currentVersionCode: Int = 1,
    val currentVersionName: String = "1.0",
    val title: String = "",
    val changelog: String = "",
    val apkUrl: String = "",
    val releaseDate: String = "",
    val isForceUpdate: Boolean = false
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFileUri: android.net.Uri, val apkFile: java.io.File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}
