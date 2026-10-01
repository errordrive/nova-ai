package com.nova.ai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nova.ai.data.local.dao.AgentProfileDao
import com.nova.ai.data.local.dao.AgentSessionDao
import com.nova.ai.data.local.dao.AuditDao
import com.nova.ai.data.local.dao.ConversationDao
import com.nova.ai.data.local.dao.MessageDao
import com.nova.ai.data.local.dao.ModelCacheDao
import com.nova.ai.data.local.dao.PermissionRuleDao
import com.nova.ai.data.local.dao.ProviderDao
import com.nova.ai.data.local.dao.ToolCallDao
import com.nova.ai.data.local.dao.WorkspaceDao
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

/**
 * Room database for Nova. Holds providers, conversations, messages, workspaces,
 * permission rules, agent sessions, tool calls, audit records, and agent profiles.
 *
 * Secrets (API keys) are intentionally excluded; they live in
 * [com.nova.ai.data.security.SecureCredentialStore].
 */
@Database(
    entities = [
        ProviderEntity::class,
        ModelCacheEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        WorkspaceEntity::class,
        PermissionRuleEntity::class,
        AgentSessionEntity::class,
        ToolCallEntity::class,
        AuditRecordEntity::class,
        AgentProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /** Data access for providers. */
    abstract fun providerDao(): ProviderDao

    /** Data access for cached model listings. */
    abstract fun modelCacheDao(): ModelCacheDao

    /** Data access for conversations. */
    abstract fun conversationDao(): ConversationDao

    /** Data access for messages. */
    abstract fun messageDao(): MessageDao

    /** Data access for workspaces. */
    abstract fun workspaceDao(): WorkspaceDao

    /** Data access for permission rules. */
    abstract fun permissionRuleDao(): PermissionRuleDao

    /** Data access for agent sessions. */
    abstract fun agentSessionDao(): AgentSessionDao

    /** Data access for tool calls. */
    abstract fun toolCallDao(): ToolCallDao

    /** Data access for audit records. */
    abstract fun auditDao(): AuditDao

    /** Data access for agent profiles. */
    abstract fun agentProfileDao(): AgentProfileDao
}
