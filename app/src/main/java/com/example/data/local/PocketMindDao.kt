package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PocketMindDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt, backendLabel = :backendLabel WHERE id = :sessionId")
    suspend fun touchSession(sessionId: Long, title: String, backendLabel: String, updatedAt: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSessionMessages(sessionId: Long)

    @Query("SELECT * FROM knowledge_notes ORDER BY updatedAt DESC")
    fun observeNotes(): Flow<List<KnowledgeNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: KnowledgeNoteEntity): Long

    @Query("SELECT COUNT(*) FROM knowledge_notes WHERE LOWER(title) = LOWER(:title)")
    suspend fun countNotesByTitle(title: String): Int

    @Query("DELETE FROM knowledge_notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Long)

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    fun observeSyncQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getPendingSyncQueue(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: SyncQueueEntity): Long

    @Query("UPDATE sync_queue SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateQueueItemStatus(id: Long, status: String, errorMessage: String = "")

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteQueueItem(id: Long)

    @Query("SELECT * FROM agent_profiles ORDER BY isActive DESC, createdAt DESC")
    fun observeAgents(): Flow<List<AgentProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: AgentProfileEntity): Long

    @Query("UPDATE agent_profiles SET isActive = (id = :agentId)")
    suspend fun setActiveAgent(agentId: Long)

    @Query("UPDATE agent_profiles SET isActive = 0")
    suspend fun deactivateAllAgents()

    @Query("DELETE FROM agent_profiles WHERE id = :agentId")
    suspend fun deleteAgent(agentId: Long)

    @Query("SELECT * FROM self_improvement_logs ORDER BY timestamp DESC")
    fun observeImprovementLogs(): Flow<List<SelfImprovementLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImprovementLog(log: SelfImprovementLogEntity): Long
}
