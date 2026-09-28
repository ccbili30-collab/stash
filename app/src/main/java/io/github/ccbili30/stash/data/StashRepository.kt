package io.github.ccbili30.stash.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import io.github.ccbili30.stash.net.TitleFetcher
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 唯一数据入口：Room + 私有媒体文件。
 * 文件即真相：图片全部复制进 filesDir/media，外部 Uri 只在入库瞬间用一次。
 */
class StashRepository private constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val dao = StashDatabase.get(context).entryDao()
    private val titleFetcher = TitleFetcher()

    val entries: Flow<List<EntryWithTags>> = dao.observeAll()
    val tags: Flow<List<String>> = dao.observeTags()

    fun observeEntry(id: Long): Flow<EntryWithTags?> = dao.observeById(id)

    // ---- 图片 ----

    /** 把分享来的图片 Uri 复制进私有目录并入库，返回是否成功 */
    suspend fun addImageFromUri(uri: Uri, sourceApp: String?, origin: EntryOrigin): Boolean {
        val name = copyImageToPrivate(uri) ?: return false
        val (w, h) = probeSize(imageFile(name))
        dao.insertEntry(
            Entry(
                type = EntryType.IMAGE,
                createdAt = System.currentTimeMillis(),
                fileName = name,
                sourceApp = sourceApp,
                origin = origin,
                width = w,
                height = h,
            ),
        )
        return true
    }

    /** 磁贴/无障碍截屏：Bitmap 直接落盘入库 */
    suspend fun addBitmap(bitmap: Bitmap) {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val file = File(dir, "shot-$stamp.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dao.insertEntry(
            Entry(
                type = EntryType.IMAGE,
                createdAt = System.currentTimeMillis(),
                fileName = file.name,
                origin = EntryOrigin.TILE,
                width = bitmap.width,
                height = bitmap.height,
            ),
        )
    }

    private fun copyImageToPrivate(uri: Uri): String? = runCatching {
        val mime = context.contentResolver.getType(uri) ?: "image/png"
        val ext = when {
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("webp") -> "webp"
            else -> "png"
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(dir, "img-$stamp.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file.name
    }.getOrNull()

    fun imageFile(fileName: String): File = File(File(context.filesDir, "media"), fileName)

    /** 把私有目录里的图片复制回相册（Pictures/Stash），相册 app 立即可见；无需权限 */
    suspend fun saveToGallery(entry: Entry): Boolean = withContext(Dispatchers.IO) {
        val name = entry.fileName ?: return@withContext false
        val file = imageFile(name)
        if (!file.exists()) return@withContext false
        runCatching {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, mimeOf(file.name))
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/Stash",
                )
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return@runCatching false
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        }.getOrDefault(false)
    }

    private fun mimeOf(fileName: String): String = when {
        fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
        fileName.endsWith(".webp", true) -> "image/webp"
        else -> "image/png"
    }

    /** 只读边界拿像素尺寸，不解码全图 */
    private fun probeSize(file: File): Pair<Int, Int> = runCatching {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
        opts.outWidth to opts.outHeight
    }.getOrDefault(0 to 0)

    // ---- 链接 ----

    suspend fun addLink(url: String, sourceApp: String?): Long {
        // 10 秒内同 URL 重复分享不重复入库
        val dup = dao.deleteRecentDuplicateUrl(url, System.currentTimeMillis() - 10_000)
        val id = dao.insertEntry(
            Entry(
                type = EntryType.LINK,
                createdAt = System.currentTimeMillis(),
                url = url,
                sourceApp = sourceApp,
            ),
        )
        // 后台抓标题，抓到自动回填
        scope.launch {
            val title = titleFetcher.fetch(url)
            if (title != null) dao.updateTitle(id, title)
        }
        return id
    }

    // ---- 编辑 ----

    suspend fun updateNote(id: Long, note: String) {
        dao.updateNote(id, note.ifBlank { null })
    }

    suspend fun setTags(id: Long, names: List<String>) {
        dao.clearTagsFor(id)
        names.filter { it.isNotBlank() }.distinct().forEach { name ->
            dao.insertTag(Tag(name.trim()))
            dao.insertEntryTag(EntryTag(id, name.trim()))
        }
        dao.pruneTags()
    }

    suspend fun delete(entry: Entry) {
        dao.deleteById(entry.id)
        // 时间戳命名不会撞名，直接删文件
        entry.fileName?.let { name -> imageFile(name).delete() }
    }

    companion object {
        @Volatile
        private var instance: StashRepository? = null

        fun get(context: Context): StashRepository =
            instance ?: synchronized(this) {
                instance ?: StashRepository(
                    context.applicationContext,
                    CoroutineScope(SupervisorJob() + Dispatchers.IO),
                ).also { instance = it }
            }
    }
}
