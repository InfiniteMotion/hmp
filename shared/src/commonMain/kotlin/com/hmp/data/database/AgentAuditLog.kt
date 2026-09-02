package com.hmp.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Agent 审计日志（设计总纲 7.3 agent_audit_log）。
 * 伙伴的一切自主动作留痕：工具调用/许可裁决/云端修正。args_hash + created_at 满足「审计四问」
 * （来源/何时/被确证过吗/被谁修正）的可追溯要求；label_correction 事件 args 含旧值快照，
 * 使被覆盖的旧认识进历史不消失。
 */
@Entity(tableName = "agent_audit_log")
data class AgentAuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "task_id")
    val taskId: Long? = null,
    /** 工具名或动作类型（tool / label_correction / cloud_arbitration ...） */
    val tool: String,
    /** 参数哈希（防篡改 + 可复现该次调用） */
    @ColumnInfo(name = "args_hash")
    val argsHash: String? = null,
    /** 结果：success / failed / rejected / revoked / superseded */
    val outcome: String,
    /** 理由（含 T0 行为证据的一句解释；label_correction 含旧值快照） */
    val reason: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

@Dao
interface AgentAuditLogDao {
    @Insert
    suspend fun insert(log: AgentAuditLog): Long

    @Query("SELECT * FROM agent_audit_log ORDER BY id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<AgentAuditLog>

    @Query("SELECT * FROM agent_audit_log WHERE task_id = :taskId ORDER BY id ASC")
    suspend fun getByTaskId(taskId: Long): List<AgentAuditLog>

    /** 审计页用：全量读取，最新在前 */
    @Query("SELECT * FROM agent_audit_log ORDER BY id DESC")
    suspend fun getAll(): List<AgentAuditLog>

    /** 审计页筛选按 tool（动作类型） */
    @Query("SELECT * FROM agent_audit_log WHERE tool = :tool ORDER BY id DESC")
    suspend fun queryByTool(tool: String): List<AgentAuditLog>

    /** 撤销支持：按 id 查单条 */
    @Query("SELECT * FROM agent_audit_log WHERE id = :id")
    suspend fun getById(id: Long): AgentAuditLog?

    @Query("DELETE FROM agent_audit_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM agent_audit_log")
    suspend fun deleteAll()

    /** 审计页展示的总数 */
    @Query("SELECT COUNT(*) FROM agent_audit_log")
    suspend fun count(): Long
}