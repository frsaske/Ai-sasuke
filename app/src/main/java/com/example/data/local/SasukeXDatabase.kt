package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SasukeXDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        private const val DB_NAME = "sasukex_chat_database.db"

        @Volatile
        private var INSTANCE: SasukeXDatabase? = null

        fun getInstance(context: Context): SasukeXDatabase {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                try {
                    val instance = buildDatabase(appCtx)
                    INSTANCE = instance
                    instance
                } catch (e: Exception) {
                    // Safety fallback: if database file was corrupted or schema integrity failed on device, reset cleanly
                    try {
                        appCtx.deleteDatabase(DB_NAME)
                    } catch (_: Exception) {}
                    val fallback = buildDatabase(appCtx)
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
