package com.prabhix.operator.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val queue: String,
    val status: String,
    val priority: String,
    val subject: String?,
    val visitorName: String?,
    val visitorEmail: String?,
    val assignedAgentId: String?,
    val unreadAgentCount: Int,
    val lastMessageAt: String?,
    val lastMessagePreview: String?,
    val cachedAt: Long,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderType: String,
    val body: String,
    val occurredAt: String,
)

@Entity(tableName = "mail_threads")
data class MailThreadEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val status: String,
    val snippet: String?,
    val unreadCount: Int,
    val lastMessageAt: String?,
    val cachedAt: Long,
)

@Entity(tableName = "outbound_queue")
data class OutboundMessageEntity(
    @PrimaryKey val clientId: String,
    val conversationId: String,
    val body: String,
    val isNote: Boolean,
    val createdAt: Long,
    val attempts: Int = 0,
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE queue = :queue ORDER BY lastMessageAt DESC")
    fun observeByQueue(queue: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE queue = :queue")
    suspend fun clearQueue(queue: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY occurredAt ASC")
    fun observe(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MessageEntity>)
}

@Dao
interface MailThreadDao {
    @Query("SELECT * FROM mail_threads ORDER BY lastMessageAt DESC")
    fun observeAll(): Flow<List<MailThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MailThreadEntity>)
}

@Dao
interface OutboundQueueDao {
    @Query("SELECT * FROM outbound_queue ORDER BY createdAt ASC")
    suspend fun pending(): List<OutboundMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: OutboundMessageEntity)

    @Query("DELETE FROM outbound_queue WHERE clientId = :clientId")
    suspend fun remove(clientId: String)
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MailThreadEntity::class,
        OutboundMessageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PrabhixDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun mailThreadDao(): MailThreadDao
    abstract fun outboundQueueDao(): OutboundQueueDao
}
