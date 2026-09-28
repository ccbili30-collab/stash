package io.github.ccbili30.stash.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 更新流程状态（顶层类型，UI 直接引用） */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Latest(val currentVersion: String) : UpdateState
    data class Available(val release: UpdateChecker.Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class ReadyToInstall(val apk: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/** 应用内更新：GitHub Releases 检查 → APK 下载 → 系统安装。 */
object UpdateChecker {

    private const val REPO = "ccbili30-collab/stash"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val UA = "Stash-Updater/1.0"

    data class Release(
        val tag: String,
        val name: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
    )

    /** 检查最新 Release；比 currentVersion 新才返回，否则 null */
    suspend fun check(currentVersion: String): Release? = withContext(Dispatchers.IO) {
        fetchLatest()?.takeIf { newer(it.tag, currentVersion) }
    }

    private fun fetchLatest(): Release? = runCatching {
        val conn = URL(API_LATEST).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    apkUrl = a.getString("browser_download_url")
                    apkSize = a.optLong("size")
                    break
                }
            }
            apkUrl ?: return null
            Release(
                tag = json.optString("tag_name", ""),
                name = json.optString("name", ""),
                notes = json.optString("body", ""),
                apkUrl = apkUrl,
                apkSize = apkSize,
            )
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** 下载 APK 到 cacheDir/update/，onProgress 回调 0..1，返回 APK 文件 */
    suspend fun download(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, "stash-${release.tag}.apk")
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", UA)
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var done = 0L
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        output.write(buf, 0, r)
                        done += r
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            target
        } finally {
            conn.disconnect()
        }
    }

    /** 拉起系统安装器；未授权"安装未知应用"时先跳授权页（返回 false 表示去了授权页） */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android-package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    /** v1.2.0 > 1.0.9 这类 semver 比较 */
    fun newer(remoteTag: String, currentVersion: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .map { it.toIntOrNull() ?: 0 }
        val r = parts(remoteTag)
        val c = parts(currentVersion)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrNull(i) ?: 0
            val b = c.getOrNull(i) ?: 0
            if (a != b) return a > b
        }
        return false
    }
}
