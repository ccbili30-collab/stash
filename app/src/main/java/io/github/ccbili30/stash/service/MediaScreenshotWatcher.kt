package io.github.ccbili30.stash.service

import android.Manifest
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
import io.github.ccbili30.stash.data.SettingsStore
import io.github.ccbili30.stash.data.StashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 系统截屏自动收集（主推路径）：用任意系统方式截图 → ContentObserver 实时收、
 * 打开 app 补扫漏收（进程被杀也不丢）。只读 Screenshots 目录。
 * 「清理相册原件」开启时，收进的截屏放入 pendingClean，主界面提示条引导
 * 走系统 createDeleteRequest 确认后删除相册原件。
 */
object MediaScreenshotWatcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ingestLock = Mutex()

    private var observer: ContentObserver? = null
    private var resolver: android.content.ContentResolver? = null

    private val _pendingClean = MutableStateFlow<List<Uri>>(emptyList())
    val pendingClean: StateFlow<List<Uri>> = _pendingClean

    /**
     * 用户主动熄灭收集的时刻（秒）。>0 表示存在"熄灭期"：
     * 点亮/补扫时把游标直接推到当下，熄灭期间的截图视为垃圾不补收；
     * 进程意外死亡不会有这个标记，恢复时照常全量补扫。
     */
    private var stoppedAtSec = 0L

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
        val res = app.contentResolver
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri == null) return
                scope.launch { ingest(context.applicationContext, listOf(uri)) }
            }
        }
        runCatching {
            res.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs)
        }.onFailure { return }
        this.resolver = res
        observer = obs
        backfillOnceIfEnabled(app)
    }

    @Synchronized
    fun stop(context: Context) {
        observer?.let { obs -> runCatching { resolver?.unregisterContentObserver(obs) } }
        observer = null
        resolver = null
        stoppedAtSec = System.currentTimeMillis() / 1000
    }

    /** 打开 app / 授予权限后调用：把上次处理之后的新截屏补收进来（幂等） */
    fun backfillOnceIfEnabled(context: Context) {
        val app = context.applicationContext
        scope.launch {
            if (!hasPermission(app)) return@launch
            if (!SettingsStore(app).autoScreenshotFlow.first()) return@launch
            val settings = SettingsStore(app)
            var since = settings.lastScreenshotSeenFlow.first()
            val now = System.currentTimeMillis() / 1000
            if (since == 0L) {
                // 首次：从现在开始，不回扫历史
                settings.setLastScreenshotSeen(now)
                return@launch
            }
            if (stoppedAtSec > 0) {
                // 存在熄灭期：丢弃熄灭期间的截图，游标跳到当下
                settings.setLastScreenshotSeen(maxOf(now, stoppedAtSec))
                stoppedAtSec = 0
                return@launch
            }
            val fresh = queryScreenshotsSince(app, since)
            if (fresh.isNotEmpty()) ingest(app, fresh.map { it.first })
        }
    }

    /** @return (uri, dateAdded秒) 列表，按时间升序 */
    private fun queryScreenshotsSince(
        context: Context,
        fromSecondExclusive: Long,
    ): List<Pair<Uri, Long>> = runCatching {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
            ),
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.Images.Media.IS_PENDING} = 0 AND " +
                "${MediaStore.Images.Media.DATE_ADDED} > ?",
            arrayOf("%Screenshots%", fromSecondExclusive.toString()),
            "${MediaStore.Images.Media.DATE_ADDED} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val list = ArrayList<Pair<Uri, Long>>()
            while (cursor.moveToNext()) {
                list.add(
                    Uri.withAppendedPath(collection, cursor.getLong(idCol).toString()) to
                        cursor.getLong(addedCol),
                )
            }
            list
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private suspend fun ingest(context: Context, uris: List<Uri>) = ingestLock.withLock {
        val app = context.applicationContext
        val repo = StashRepository.get(app)
        val settings = SettingsStore(app)
        val wantClean = settings.cleanAfterCollectFlow.first()
        var lastSeen = settings.lastScreenshotSeenFlow.first()
        val collected = ArrayList<Uri>()

        for (uri in uris) {
            val added = runCatching {
                app.contentResolver.query(
                    uri,
                    arrayOf(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        MediaStore.Images.Media.IS_PENDING,
                        MediaStore.Images.Media.DATE_ADDED,
                    ),
                    null, null, null,
                )?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    val path = c.getString(0) ?: return@use null
                    if (c.getInt(1) != 0) return@use null
                    if (!path.contains("Screenshots", ignoreCase = true)) return@use null
                    c.getLong(2)
                }
            }.getOrNull() ?: continue

            repo.addImageFromUri(uri, null, EntryOrigin.AUTO)
            lastSeen = maxOf(lastSeen, added)
            if (wantClean) collected.add(uri)
        }

        settings.setLastScreenshotSeen(lastSeen)
        if (collected.isNotEmpty()) {
            _pendingClean.value = _pendingClean.value + collected
        }
        Unit
    }

    /** 用户处理完（确认或拒绝）后清空待清理列表 */
    fun consumePendingClean() {
        _pendingClean.value = emptyList()
    }
}
