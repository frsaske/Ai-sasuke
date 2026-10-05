package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity
import com.example.data.model.MemoryEntity
import com.example.data.model.FileIndexEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, MemoryEntity::class, FileIndexEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SasukeXDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun fileIndexDao(): FileIndexDao

    companion object {
        private const val DB_NAME = "sasukex_chat_database.db"

        @Volatile
        private var INSTANCE: SasukeXDatabase? = null

        fun getInstance(context: Context): SasukeXDatabase {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                try {
                    val instance = buildDatabase(appCtx)
                    // Proactively trigger SQLite open to validate schema and migrations immediately
                    instance.openHelper.writableDatabase
                    INSTANCE = instance
                    instance
                } catch (e: Exception) {
                    // Safety fallback: if database file was corrupted or schema integrity failed on device, reset cleanly
                    try {
                        appCtx.deleteDatabase(DB_NAME)
                    } catch (_: Exception) {}
                    val fallback = buildDatabase(appCtx)
                    try {
                        fallback.openHelper.writableDatabase
                    } catch (_: Exception) {}
                    INSTANCE = fallback
                    fallback
                }
            }
        }

        private fun buildDatabase(context: Context): SasukeXDatabase {
            return Room.databaseBuilder(
                context,
                SasukeXDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigration(true)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
        }
    }
}
