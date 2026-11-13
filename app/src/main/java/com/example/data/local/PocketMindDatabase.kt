package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        KnowledgeNoteEntity::class,
        SyncQueueEntity::class,
        AgentProfileEntity::class,
        SelfImprovementLogEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class PocketMindDatabase : RoomDatabase() {
    abstract fun pocketMindDao(): PocketMindDao

    companion object {
        @Volatile
        private var instance: PocketMindDatabase? = null

        fun getInstance(context: Context): PocketMindDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PocketMindDatabase::class.java,
                    "pocketmind_offline.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
