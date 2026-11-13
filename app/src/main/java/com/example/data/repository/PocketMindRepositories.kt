package com.example.data.repository

import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.KnowledgeNoteEntity
import com.example.data.local.PocketMindDao
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: PocketMindDao) {
    val sessions: Flow<List<ChatSessionEntity>> = dao.observeSessions()

    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>> =
        dao.observeMessages(sessionId)

    suspend fun createSession(
        title: String = "New Offline Chat",
        backendLabel: String = "On-Device",
    ): Long = dao.insertSession(
        ChatSessionEntity(
            title = title,
            backendLabel = backendLabel,
        )
    )

    suspend fun updateSessionMeta(sessionId: Long, title: String, backendLabel: String) {
        dao.touchSession(
            sessionId = sessionId,
            title = title,
            backendLabel = backendLabel,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)

    suspend fun saveMessage(message: ChatMessageEntity): Long = dao.insertMessage(message)

    suspend fun clearMessages(sessionId: Long) = dao.clearSessionMessages(sessionId)
}

class NotesRepository(private val dao: PocketMindDao) {
    val notes: Flow<List<KnowledgeNoteEntity>> = dao.observeNotes()

    suspend fun saveNote(title: String, content: String, tags: String = ""): Long =
        dao.insertNote(
            KnowledgeNoteEntity(
                title = title.trim(),
                content = content.trim(),
                tags = tags.trim(),
            )
        )

    suspend fun deleteNote(noteId: Long) = dao.deleteNote(noteId)
}
