package io.github.ccbili30.stash.service

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import io.github.ccbili30.stash.data.EntryOrigin
import io.github.ccbili30.stash.data.StashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 自动收截屏（设置开关控制）：监测相册截屏目录的新图，复制进 Stash。
 * 只读 Pictures/Screenshots 与 DCIM/Screenshots，不碰其他照片。
 */
object MediaScreenshotWatcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null
    private var resolver: android.content.ContentResolver? = null
    private val seen = HashSet<String>()

    fun hasPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    @Synchronized
    fun startIfPermitted(context: Context) {
        val app = context.applicationContext
        if (observer != null) return
        if (!hasPermission(app)) return
        val resolver = app.contentResolver
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri == null || seen.contains(uri.toString())) return
                seen.add(uri.toString())
                if (seen.size > 500) seen.clear()
                scope.launch { ingest(app, uri) }
            }
        }
        resolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            obs,
        )
        this.resolver = resolver
        observer = obs
    }

    @Synchronized
    fun stop() {
        observer?.let { obs -> runCatching { resolver?.unregisterContentObserver(obs) } }
        observer = null
        resolver = null
    }

    private suspend fun ingest(context: Context, uri: Uri) {
        runCatching {
            val resolver = context.contentResolver
            val projection = arrayOf(
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.IS_PENDING,
            )
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                val pendingCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_PENDING)
                val path = cursor.getString(pathCol) ?: return
                if (cursor.getInt(pendingCol) != 0) return // 还在写入，跳过
                if (!path.contains("Screenshots", ignoreCase = true)) return
                StashRepository.get(context).addImageFromUri(uri, null, EntryOrigin.AUTO)
            }
        }
    }
}
