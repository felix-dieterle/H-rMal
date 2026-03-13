package com.felix.hormal

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Checks the latest GitHub Release for this app and offers to download + install it
 * when a newer build is available.
 *
 * A release is considered an auto-update candidate when its body contains "[auto_update]".
 * The build number is the numeric suffix of the release tag (e.g. "v1.0-42" → 42).
 */
object UpdateChecker {

    private const val RELEASES_API_URL =
        "https://api.github.com/repos/felix-dieterle/H-rMal/releases/latest"

    /** Call this once from MainActivity.onCreate to check for updates in the background. */
    fun checkForUpdate(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { fetchUpdateInfo() } ?: return@launch
            if (!activity.isFinishing) {
                showUpdateDialog(activity, result.first, result.second)
            }
        }
    }

    /** Returns (releaseName, apkUrl) if a newer build is available, null otherwise. */
    private fun fetchUpdateInfo(): Pair<String, String>? {
        return runCatching {
            val json = URL(RELEASES_API_URL).readText()
            val release = JSONObject(json)

            val body = release.optString("body", "")
            if (!body.contains("[auto_update]")) return null

            val tagName = release.getString("tag_name") // e.g. "v1.0-42"
            val remoteBuild = tagName.substringAfterLast("-").toIntOrNull() ?: return null
            if (remoteBuild <= BuildConfig.VERSION_CODE) return null

            val assets = release.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            apkUrl ?: return null

            Pair(release.optString("name", tagName), apkUrl)
        }.getOrNull()
    }

    private fun showUpdateDialog(activity: AppCompatActivity, releaseName: String, apkUrl: String) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_available_title))
            .setMessage(activity.getString(R.string.update_available_message, releaseName))
            .setPositiveButton(activity.getString(R.string.update_download)) { _, _ ->
                activity.lifecycleScope.launch {
                    downloadAndInstall(activity, apkUrl)
                }
            }
            .setNegativeButton(activity.getString(R.string.update_later), null)
            .show()
    }

    @Suppress("DEPRECATION")
    private suspend fun downloadAndInstall(activity: AppCompatActivity, apkUrl: String) {
        val apkDir = File(activity.cacheDir, "apk_downloads")
        apkDir.mkdirs()
        val apkFile = File(apkDir, "update.apk")

        val progress = ProgressDialog(activity).apply {
            setMessage(activity.getString(R.string.update_downloading))
            isIndeterminate = true
            setCancelable(false)
            show()
        }

        val success = withContext(Dispatchers.IO) {
            runCatching {
                URL(apkUrl).openStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }.getOrDefault(false)
        }

        progress.dismiss()

        if (success && !activity.isFinishing) {
            installApk(activity, apkFile)
        }
    }

    private fun installApk(activity: AppCompatActivity, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
