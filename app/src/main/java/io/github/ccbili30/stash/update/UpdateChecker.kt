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
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"

    data class Release(
        val tag: String,
        val name: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
    )

    /**
     * 检查最新 Release；比 currentVersion 新返回 Release，没有新版返回 null。
     * 网络失败抛异常——调用方必须区分"没新版"和"检查失败"，不能混为一谈。
     *
     * 数据源两层：api.github.com 接口（带更新说明，但未认证限流 60次/小时/IP，
     * 共享出口 IP 常见 403）→ 降级用 releases/latest 网页 302 跳转解析版本号
     * （与浏览器同待遇，无限流）。
     */
    suspend fun check(currentVersion: String): Release? = withContext(Dispatchers.IO) {
        val viaApi = runCatching { fetchLatestApi() }.getOrNull()
        val rel = viaApi ?: fetchLatestByRedirect()
        rel?.takeIf { newer(it.tag, currentVersion) }
    }

    private fun fetchLatestApi(): Release? {
        val conn = URL(API_LATEST).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val code = conn.responseCode
            if (code == 403 || code == 429) {
                throw IllegalStateException("接口限流（$code，共享 IP 配额用尽）")
            }
            if (code != 200) throw IllegalStateException("GitHub 返回 $code")
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
            return Release(
                tag = json.optString("tag_name", ""),
                name = json.optString("name", ""),
                notes = json.optString("body", ""),
                apkUrl = apkUrl,
                apkSize = apkSize,
            )
        } finally {
            conn.disconnect()
        }
    }

    /** 网页通道：releases/latest 会 302 到 releases/tag/<tag>，读跳转头即得版本号 */
    private fun fetchLatestByRedirect(): Release {
        val conn = URL("https://github.com/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 12_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", BROWSER_UA)
        try {
            val code = conn.responseCode
            if (code != 301 && code != 302) {
                throw IllegalStateException("GitHub 返回 $code（网络不通或被拦截）")
            }
            val location = conn.getHeaderField("Location") ?: throw IllegalStateException("拿不到跳转地址")
            val tag = location.substringAfterLast('/').substringBefore('#')
            if (!tag.startsWith("v")) throw IllegalStateException("版本号解析失败：$location")
            // 命名约定：Release 资产固定叫 Stash-<tag>.apk
            val apkUrl = "https://github.com/$REPO/releases/download/$tag/Stash-$tag.apk"
            return Release(
                tag = tag,
                name = "Stash $tag",
                notes = "（接口限流，降级通道：详细说明见 GitHub Release 页）",
                apkUrl = apkUrl,
                apkSize = 0L,
            )
        } finally {
            conn.disconnect()
        }
    }

    /** 下载 APK：断流自动重试，完成校验大小（半截包直接判废），返回 APK 文件 */
    suspend fun download(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, "stash-${release.tag}.apk")
        var lastError: Exception = IllegalStateException("未知下载错误")
        repeat(3) { attempt ->
            try {
                onProgress(0f)
                downloadOnce(release, target, onProgress)
                // 完整性校验：文件大小必须与 Release 声明一致（拿不到声明时退化为非空检查）
                val expected = release.apkSize
                val actual = target.length()
                val ok = if (expected > 0) actual == expected else actual > 1_000_000
                if (ok) return@withContext target
                target.delete()
                lastError = IllegalStateException("下载不完整（${actual / 1024}KB / 预期 ${expected / 1024}KB）")
            } catch (e: Exception) {
                lastError = e
                target.delete()
            }
            if (attempt < 2) kotlinx.coroutines.delay(1500L)
        }
        throw lastError
    }

    private fun downloadOnce(
        release: Release,
        target: File,
        onProgress: (Float) -> Unit,
    ) {
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", BROWSER_UA)
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
            runCatching { context.startActivity(intent) }
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android-package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
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
