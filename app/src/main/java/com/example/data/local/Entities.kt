package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val backendLabel: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["sessionId"])],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val modelName: String,
    val latencyMs: Long = 0L,
    val wordCount: Int = 0,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "knowledge_notes")
data class KnowledgeNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String,
    val tags: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val topic: String,
    val status: String = "PENDING", // "PENDING" | "SYNCING" | "FAILED"
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "agent_profiles")
data class AgentProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val role: String,
    val systemPrompt: String,
    val temperature: Float = 0.5f,
    val topK: Int = 30,
    val focusTags: String = "",
    val createdByAi: Boolean = false,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "self_improvement_logs")
data class SelfImprovementLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val agentName: String,
    val targetPrompt: String,
    val critiqueSummary: String,
    val improvedResponse: String,
    val upgradedInstruction: String,
    val timestamp: Long = System.currentTimeMillis(),
)
