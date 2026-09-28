package io.github.ccbili30.stash.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/** 链接标题抓取：og:title 优先，<title> 兜底；失败返回 null（列表降级显示域名）。 */
class TitleFetcher {

    suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36",
            )
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val bytes = readHead(conn.inputStream, 256 * 1024)
                val cs = detectCharset(bytes) ?: Charsets.UTF_8
                val html = String(bytes, cs)
                extractTitle(html)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun extractTitle(html: String): String? {
        val og = OG_TITLE.find(html)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
        val titleTag = TITLE.find(html)?.groupValues?.getOrNull(1)
        return (og ?: titleTag)
            ?.let(::decodeEntities)
            ?.trim()
            ?.take(200)
            ?.ifBlank { null }
    }

    private fun readHead(input: java.io.InputStream, limit: Int): ByteArray {
        return input.use { stream ->
            val buf = ByteArray(limit)
            var read = 0
            while (read < limit) {
                val r = stream.read(buf, read, limit - read)
                if (r < 0) break
                read += r
            }
            buf.copyOf(read)
        }
    }

    private fun detectCharset(bytes: ByteArray): Charset? {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val name = CHARSET.find(head)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    private fun decodeEntities(s: String): String {
        var out = s
        for ((k, v) in ENTITIES) out = out.replace(k, v)
        out = NUM_ENTITY.replace(out) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        out = HEX_ENTITY.replace(out) { it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: "" }
        return out
    }

    companion object {
        private val OG_TITLE = Regex(
            """<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']*)["']|<meta[^>]+content=["']([^"']*)["'][^>]+property=["']og:title["']""",
            RegexOption.IGNORE_CASE,
        )
        private val TITLE = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)
        private val CHARSET = Regex("""charset=["']?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)
        private val NUM_ENTITY = Regex("""&#(\d+);""")
        private val HEX_ENTITY = Regex("""&#x([0-9A-Fa-f]+);""")
        private val ENTITIES = mapOf(
            "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"",
            "&apos;" to "'", "&nbsp;" to " ", "&#39;" to "'", "&hellip;" to "…",
        )
    }
}
