package com.hmp.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Agent 任务记录（设计总纲 7.3 agent_task）。
 * 一个任务 = 一次目标导向的循环（触发器/预算/结果），是 agent 的执行单位。
 */
@Entity(tableName = "agent_task")
data class AgentTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 触发器类型：召唤(manual)/事件(event)/定时(scheduled) */
    @ColumnInfo(name = "trigger_type")
    val triggerType: String,
    /** 任务执行时的人格快照（人格可事后变更，任务留当时快照供审计） */
    @ColumnInfo(name = "persona_snapshot")
    val personaSnapshot: String? = null,
    /** 状态：pending / running / completed / failed / budget_exhausted / cancelled */
    val status: String,
    /** 实际消耗的步数预算 */
    @ColumnInfo(name = "budget_used")
    val budgetUsed: Int? = null,
    /** 任务结果（结构化 JSON 或摘要文本） */
    val result: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

@Dao
interface AgentTaskDao {
    @Insert
    suspend fun insert(task: AgentTask): Long

    @Query("SELECT * FROM agent_task WHERE id = :id")
    suspend fun getById(id: Long): AgentTask?

    @Query("SELECT * FROM agent_task ORDER BY id DESC")
    suspend fun getAll(): List<AgentTask>

    @Query("UPDATE agent_task SET status = :status, budget_used = :budgetUsed, result = :result WHERE id = :id")
    suspend fun updateResult(id: Long, status: String, budgetUsed: Int?, result: String?)
}