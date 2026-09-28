package io.github.ccbili30.stash.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

class EnumConverters {
    @TypeConverter
    fun entryTypeToString(v: EntryType): String = v.name

    @TypeConverter
    fun stringToEntryType(v: String): EntryType = EntryType.valueOf(v)

    @TypeConverter
    fun originToString(v: EntryOrigin): String = v.name

    @TypeConverter
    fun stringToOrigin(v: String): EntryOrigin = EntryOrigin.valueOf(v)
}

@Dao
interface EntryDao {
    @Transaction
    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EntryWithTags>>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: Long): Flow<EntryWithTags?>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): Entry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: Entry): Long

    @Query("UPDATE entries SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE entries SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag)

    @Query("DELETE FROM entry_tag WHERE entryId = :entryId")
    suspend fun clearTagsFor(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntryTag(cross: EntryTag)

    @Query("SELECT name FROM tags ORDER BY name")
    fun observeTags(): Flow<List<String>>

    @Query("DELETE FROM tags WHERE name NOT IN (SELECT DISTINCT tagName FROM entry_tag)")
    suspend fun pruneTags()

    @Query("DELETE FROM entries WHERE url = :url AND createdAt > :since")
    suspend fun deleteRecentDuplicateUrl(url: String, since: Long): Int
}

@Database(
    entities = [Entry::class, Tag::class, EntryTag::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(EnumConverters::class)
abstract class StashDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        @Volatile
        private var instance: StashDatabase? = null

        fun get(context: Context): StashDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StashDatabase::class.java,
                    "stash.db",
                ).build().also { instance = it }
            }
    }
}
