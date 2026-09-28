package io.github.ccbili30.stash.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class EntryType { IMAGE, LINK }
enum class EntryOrigin { SHARE, TILE, AUTO }

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EntryType,
    val createdAt: Long,
    /** 图片在 app 私有目录下的文件名（IMAGE） */
    val fileName: String? = null,
    /** 链接地址（LINK） */
    val url: String? = null,
    /** 链接标题（后台抓取回填，抓不到时为 null 显示域名） */
    val title: String? = null,
    /** 分享来源 app 包名 */
    val sourceApp: String? = null,
    /** 用户备注 */
    val note: String? = null,
    /** 图片像素宽高（瀑布流卡片按宽高比布局；0 表示未知） */
    val width: Int = 0,
    val height: Int = 0,
    val origin: EntryOrigin = EntryOrigin.SHARE,
)

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey val name: String,
)

@Entity(tableName = "entry_tag", primaryKeys = ["entryId", "tagName"])
data class EntryTag(
    val entryId: Long,
    val tagName: String,
)

data class EntryWithTags(
    @Embedded val entry: Entry,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val tags: List<EntryTag>,
) {
    val tagNames: List<String> get() = tags.map { it.tagName }.sorted()
}
