package io.github.ccbili30.stash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.ccbili30.stash.data.EntryOrigin
import io.github.ccbili30.stash.data.StashRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 系统分享目标：透明 Activity，收完即走。
 * 图片（单张/多张）复制进私有目录；文字提取第一个 http(s) 链接入库。
 */
class ShareIngestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val sourceApp = referrer?.host

        when (intent?.action) {
            Intent.ACTION_SEND -> when {
                intent.type?.startsWith("image/") == true ->
                    ingestImages(listOfNotNull(intent.getUriExtra(Intent.EXTRA_STREAM)))
                intent.type == "text/plain" && !sharedText.isNullOrBlank() ->
                    ingestText(sharedText, sourceApp)
                else -> finish()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getUriArrayListExtra(Intent.EXTRA_STREAM).orEmpty()
                if (uris.isNotEmpty()) ingestImages(uris) else finish()
            }
            else -> finish()
        }
    }

    private fun ingestImages(uris: List<Uri>) {
        val repo = StashRepository.get(this)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                uris.map { repo.addImageFromUri(it, null, EntryOrigin.SHARE) }.all { it }
            }
            Toast.makeText(
                this@ShareIngestActivity,
                if (ok) R.string.saved_toast else R.string.save_failed_toast,
                Toast.LENGTH_SHORT,
            ).show()
            finish()
        }
    }

    private fun ingestText(text: String, sourceApp: String?) {
        val url = URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', '，', '。', '"', '\'')
        if (url == null) {
            Toast.makeText(this, R.string.no_link_toast, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val repo = StashRepository.get(this)
        lifecycleScope.launch {
            repo.addLink(url, sourceApp)
            Toast.makeText(this@ShareIngestActivity, R.string.saved_toast, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    }
}

// ---- Parcelable Extra 的 API 版本兼容 ----
// 实测 API 35 上 am/部分来源的 Uri extra 经 getParcelableExtra(name, Uri::class.java)
// 会取 null（Bundle 里明明有 StringUri），故直接从 Bundle 取值并兼容 String 形式。

private fun Intent.getUriExtra(name: String): Uri? = when (val v = extras?.get(name)) {
    is Uri -> v
    is String -> runCatching { Uri.parse(v) }.getOrNull()
    is android.os.Parcelable ->
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(name, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            v as? Uri
        }
    else -> null
}

private fun Intent.getUriArrayListExtra(name: String): ArrayList<Uri>? {
    val v = extras?.get(name) ?: return null
    if (v is ArrayList<*>) {
        @Suppress("UNCHECKED_CAST")
        return v as? ArrayList<Uri>
    }
    return if (android.os.Build.VERSION.SDK_INT >= 33) {
        getParcelableArrayListExtra(name, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(name)
    }
}
