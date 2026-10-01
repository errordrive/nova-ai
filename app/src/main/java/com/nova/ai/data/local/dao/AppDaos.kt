package com.nova.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.nova.ai.data.local.entity.AgentProfileEntity
import com.nova.ai.data.local.entity.AgentSessionEntity
import com.nova.ai.data.local.entity.AuditRecordEntity
import com.nova.ai.data.local.entity.ConversationEntity
import com.nova.ai.data.local.entity.MessageEntity
import com.nova.ai.data.local.entity.ModelCacheEntity
import com.nova.ai.data.local.entity.PermissionRuleEntity
import com.nova.ai.data.local.entity.ProviderEntity
import com.nova.ai.data.local.entity.ToolCallEntity
import com.nova.ai.data.local.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

/** Data access for [ProviderEntity] rows. */
@Dao
interface ProviderDao {
    /** All providers, default first, then alphabetical. */
    @Query("SELECT * FROM providers ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getById(id: String): ProviderEntity?

    @Query("SELECT * FROM providers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ProviderEntity?

    @Upsert
    suspend fun upsert(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Clears the default flag on every provider. */
    @Query("UPDATE providers SET isDefault = 0")
    suspend fun clearDefault()

    /** Marks the given provider as the default. */
    @Query("UPDATE providers SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: String)
}

/** Data access for [ModelCacheEntity] rows. */
@Dao
interface ModelCacheDao {
    @Query("SELECT * FROM models_cache WHERE providerId = :providerId")
    suspend fun getByProvider(providerId: String): List<ModelCacheEntity>

    @Upsert
    suspend fun upsertAll(models: List<ModelCacheEntity>)

    @Query("DELETE FROM models_cache WHERE providerId = :providerId")
    suspend fun clearByProvider(providerId: String)
}

/** Data access for [ConversationEntity] rows. */
@Dao
interface ConversationDao {
    /** All conversations, most recently updated first. */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}

/** Data access for [MessageEntity] rows. */
@Dao
interface MessageDao {
    /** Messages of one conversation in chronological order. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeByConversation(conversationId: String): Flow<List<MessageEntity>>

    /** One-shot read of a conversation's messages in chronological order. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getByConversation(conversationId: String): List<MessageEntity>

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    /** Deletes a single message by id. */
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

/** Data access for [WorkspaceEntity] rows. */
@Dao
interface WorkspaceDao {
    /** All workspaces, default first, then alphabetical. */
    @Query("SELECT * FROM workspaces ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun getById(id: String): WorkspaceEntity?

    @Upsert
    suspend fun upsert(workspace: WorkspaceEntity)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Clears the default flag on every workspace. */
    @Query("UPDATE workspaces SET isDefault = 0")
    suspend fun clearDefault()

    /** Marks the given workspace as the default. */
    @Query("UPDATE workspaces SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: String)
}

/** Data access for [PermissionRuleEntity] rows. */
@Dao
interface PermissionRuleDao {
    @Query("SELECT * FROM permission_rules ORDER BY category ASC")
    fun observeAll(): Flow<List<PermissionRuleEntity>>

    @Query("SELECT * FROM permission_rules WHERE category = :category LIMIT 1")
    suspend fun getByCategory(category: String): PermissionRuleEntity?

    @Upsert
    suspend fun upsert(rule: PermissionRuleEntity)
}

/** Data access for [AgentSessionEntity] rows. */
@Dao
interface AgentSessionDao {
    /** All sessions, newest first. */
    @Query("SELECT * FROM agent_sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AgentSessionEntity>>

    @Query("SELECT * FROM agent_sessions WHERE id = :id")
    suspend fun getById(id: String): AgentSessionEntity?

    @Upsert
    suspend fun upsert(session: AgentSessionEntity)
}

/** Data access for [ToolCallEntity] rows. */
@Dao
interface ToolCallDao {
    /** Tool calls of one session in execution order. */
    @Query("SELECT * FROM tool_calls WHERE sessionId = :sessionId ORDER BY startedAt ASC")
    fun observeBySession(sessionId: String): Flow<List<ToolCallEntity>>

    @Upsert
    suspend fun upsert(toolCall: ToolCallEntity)
}

/** Data access for [AuditRecordEntity] rows. */
@Dao
interface AuditDao {
    /** Most recent audit records, newest first, capped at [limit]. */
    @Query("SELECT * FROM audit_records ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditRecordEntity>>

    @Insert
    suspend fun insert(record: AuditRecordEntity)

    @Query("DELETE FROM audit_records")
    suspend fun clear()
}

/** Data access for [AgentProfileEntity] rows. */
@Dao
interface AgentProfileDao {
    /** All profiles, alphabetical. */
    @Query("SELECT * FROM agent_profiles ORDER BY name ASC")
    fun observeAll(): Flow<List<AgentProfileEntity>>

    @Query("SELECT * FROM agent_profiles WHERE id = :id")
    suspend fun getById(id: String): AgentProfileEntity?

    @Upsert
    suspend fun upsert(profile: AgentProfileEntity)

    @Query("DELETE FROM agent_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM agent_profiles")
    suspend fun count(): Int
}
