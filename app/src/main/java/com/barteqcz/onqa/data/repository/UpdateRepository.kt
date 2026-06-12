package com.barteqcz.onqa.data.repository

import com.barteqcz.onqa.BuildConfig
import com.barteqcz.onqa.data.model.UpdateInfo
import com.barteqcz.onqa.data.remote.GitHubApiService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val apiService: GitHubApiService
) {
    suspend fun checkForUpdates(): UpdateInfo {
        return try {
            val latestRelease = apiService.getLatestRelease()
            val currentVersion = BuildConfig.VERSION_NAME
            val latestVersion = latestRelease.tagName.removePrefix("v")
            
            val isUpdateAvailable = isVersionNewer(currentVersion, latestVersion)
            
            val apkAsset = latestRelease.assets.find { it.name.endsWith(".apk") }
            
            UpdateInfo(
                latestVersion = latestVersion,
                downloadUrl = apkAsset?.browserDownloadUrl ?: latestRelease.htmlUrl,
                releaseNotes = latestRelease.body,
                isUpdateAvailable = isUpdateAvailable
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates")
            UpdateInfo("", "", isUpdateAvailable = false)
        }
    }

    internal fun isVersionNewer(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        val size = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until size) {
            val curr = currentParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > curr) return true
            if (lat < curr) return false
        }
        return false
    }
}
