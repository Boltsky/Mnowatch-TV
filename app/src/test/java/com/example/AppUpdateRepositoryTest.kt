package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AppUpdateRepositoryImpl
import com.example.domain.model.UpdateDownloadState
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdateRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: AppUpdateRepositoryImpl
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sharedPreferences = context.getSharedPreferences("test_updates_pref", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        repository = AppUpdateRepositoryImpl(
            context = context,
            okHttpClient = OkHttpClient(),
            sharedPreferences = sharedPreferences
        )
    }

    @Test
    fun parseVersionJson_detectsNewUpdateAvailable() {
        val json = """
            {
              "versionCode": 999,
              "versionName": "9.9",
              "title": "Munowatch TV v9.9 Update",
              "changelog": "• Better video stream quality\n• In-app updates",
              "apkUrl": "https://raw.githubusercontent.com/Boltsky/Mnowatch-TV/main/apk/Munowatch-TV.apk",
              "releaseDate": "2026-10-31",
              "forceUpdate": false
            }
        """.trimIndent()

        val info = repository.parseVersionJson(json)
        assertTrue(info.isUpdateAvailable)
        assertEquals(999, info.latestVersionCode)
        assertEquals("9.9", info.latestVersionName)
        assertEquals("Munowatch TV v9.9 Update", info.title)
        assertTrue(info.changelog.contains("In-app updates"))
        assertFalse(info.isForceUpdate)
    }

    @Test
    fun parseVersionJson_detectsNoUpdateWhenCurrentOrOlder() {
        val json = """
            {
              "versionCode": 1,
              "versionName": "1.0",
              "title": "Munowatch TV v1.0",
              "changelog": "Initial Release",
              "apkUrl": "https://raw.githubusercontent.com/Boltsky/Mnowatch-TV/main/apk/Munowatch-TV.apk",
              "releaseDate": "2026-10-31",
              "forceUpdate": false
            }
        """.trimIndent()

        val info = repository.parseVersionJson(json)
        assertFalse(info.isUpdateAvailable)
        assertEquals(1, info.latestVersionCode)
        assertEquals("1.0", info.latestVersionName)
    }

    @Test
    fun parseGitHubReleaseJson_extractsApkAssetAndReleaseDetails() {
        val json = """
            {
              "tag_name": "v2.0",
              "name": "Munowatch TV 2.0 Super Release",
              "body": "Major video quality and TV experience upgrades",
              "published_at": "2026-10-31T12:00:00Z",
              "assets": [
                {
                  "name": "Munowatch-TV.apk",
                  "browser_download_url": "https://github.com/Boltsky/Mnowatch-TV/releases/download/v2.0/Munowatch-TV.apk"
                }
              ]
            }
        """.trimIndent()

        val info = repository.parseGitHubReleaseJson(json)
        assertTrue(info.isUpdateAvailable)
        assertEquals("2.0", info.latestVersionName)
        assertEquals("https://github.com/Boltsky/Mnowatch-TV/releases/download/v2.0/Munowatch-TV.apk", info.apkUrl)
        assertEquals("2026-10-31", info.releaseDate)
    }

    @Test
    fun dismissCurrentUpdate_persistsAndFiltersDismissedVersion() {
        assertFalse(repository.isUpdateDismissed(2))

        repository.dismissCurrentUpdate(2)
        assertTrue(repository.isUpdateDismissed(2))
        assertFalse(repository.isUpdateDismissed(3))
    }

    @Test
    fun resetDownloadState_restoresStateToIdle() {
        repository.resetDownloadState()
        assertEquals(UpdateDownloadState.Idle, repository.downloadState.value)
    }
}
