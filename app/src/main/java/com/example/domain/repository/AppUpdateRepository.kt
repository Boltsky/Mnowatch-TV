package com.example.domain.repository

import com.example.domain.model.AppUpdateInfo
import com.example.domain.model.UpdateDownloadState
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface AppUpdateRepository {
    val downloadState: StateFlow<UpdateDownloadState>
    suspend fun checkForUpdate(force: Boolean = false): Result<AppUpdateInfo>
    suspend fun downloadApk(apkUrl: String): Result<File>
    fun installApk(apkFile: File): Result<Unit>
    fun dismissCurrentUpdate(versionCode: Int)
    fun isUpdateDismissed(versionCode: Int): Boolean
    fun resetDownloadState()
}
