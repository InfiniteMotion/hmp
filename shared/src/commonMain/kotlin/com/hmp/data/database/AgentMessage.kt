package com.hmp.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Agent 会话消息（设计总纲 7.3 agent_message）。
 * 只存 transcript 文本（音频不落库）；render_hint 驱动 UI 渲染厚度
 * （text / song / songlist / explain / confirm）。
 *
 * data_json：结构化 payload（v7.1 M6-T1 新增）——
 *   - songlist: {"songs":[{"id":123,"title":"...","why":"因为你最近常听"}],"summary":"今夜电台 · 12 首备选"}
 *   - confirm: 确认请求列表 JSON
 *   - 其余 render_hint 可为 null
 */
@Entity(tableName = "agent_message")
data class AgentMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    /** user / agent / system */
    val role: String,
    val content: String? = null,
    @ColumnInfo(name = "render_hint")
    val renderHint: String? = null,
    /** 结构化 payload（songlist/confirm 等需要额外数据时） */
    @ColumnInfo(name = "data_json")
    val dataJson: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

@Dao
interface AgentMessageDao {
    @Insert
    suspend fun insert(message: AgentMessage): Long

    @Query("SELECT * FROM agent_message WHERE session_id = :sessionId ORDER BY id ASC")
    suspend fun getBySession(sessionId: String): List<AgentMessage>

    @Query("SELECT session_id FROM agent_message GROUP BY session_id ORDER BY MAX(id) DESC")
    suspend fun getSessions(): List<String>

    @Query("DELETE FROM agent_message WHERE created_at < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}