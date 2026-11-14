package cn.wildfirechat.app.stats;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计查询，SQL 兼容 PostgreSQL。
 */
@Repository
public class StatsRepository {
    private final JdbcTemplate jdbcTemplate;

    public StatsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countAllMessages() {
        return count("SELECT COUNT(*) FROM chat_message");
    }

    public long countMessagesSince(LocalDateTime since) {
        return count("SELECT COUNT(*) FROM chat_message WHERE send_time >= ?", Timestamp.valueOf(since));
    }

    public long countMessagesByRole(String role, LocalDateTime since) {
        return count("SELECT COUNT(*) FROM chat_message WHERE msg_role = ? AND send_time >= ?",
                role, Timestamp.valueOf(since));
    }

    public long countConversations() {
        return count("SELECT COUNT(*) FROM (SELECT conv_type, conv_target FROM chat_message "
                + "GROUP BY conv_type, conv_target) t");
    }

    public long countLlmCalls() {
        return count("SELECT COUNT(*) FROM llm_call");
    }

    public long countLlmCallsSince(LocalDateTime since) {
        return count("SELECT COUNT(*) FROM llm_call WHERE start_time >= ?", Timestamp.valueOf(since));
    }

    public long countLlmFailures() {
        return count("SELECT COUNT(*) FROM llm_call WHERE success = ?", Boolean.FALSE);
    }

    public long countLlmFailuresSince(LocalDateTime since) {
        return count("SELECT COUNT(*) FROM llm_call WHERE success = ? AND start_time >= ?",
                Boolean.FALSE, Timestamp.valueOf(since));
    }

    public double avgLlmDurationMs() {
        Double value = jdbcTemplate.queryForObject("SELECT AVG(duration_ms) FROM llm_call", Double.class);
        return value == null ? 0 : value;
    }

    /**
     * 按天统计用户/机器人消息数，返回 [用户消息数, 机器人消息数]。
     */
    public Map<LocalDate, long[]> dailyMessages(LocalDateTime since) {
        Map<LocalDate, long[]> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT CAST(send_time AS DATE) AS d, msg_role, COUNT(*) AS c FROM chat_message "
                        + "WHERE send_time >= ? GROUP BY CAST(send_time AS DATE), msg_role",
                rs -> {
                    LocalDate date = rs.getDate("d").toLocalDate();
                    long count = rs.getLong("c");
                    long[] values = result.computeIfAbsent(date, key -> new long[2]);
                    if ("user".equals(rs.getString("msg_role"))) {
                        values[0] += count;
                    } else if ("assistant".equals(rs.getString("msg_role"))) {
                        values[1] += count;
                    }
                },
                Timestamp.valueOf(since));
        return result;
    }

    /**
     * 按天统计大模型调用次数和失败次数，返回 [调用次数, 失败次数]。
     */
    public Map<LocalDate, long[]> dailyLlmCalls(LocalDateTime since) {
        Map<LocalDate, long[]> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT CAST(start_time AS DATE) AS d, COUNT(*) AS c, "
                        + "SUM(CASE WHEN success THEN 0 ELSE 1 END) AS f FROM llm_call "
                        + "WHERE start_time >= ? GROUP BY CAST(start_time AS DATE)",
                rs -> {
                    LocalDate date = rs.getDate("d").toLocalDate();
                    result.put(date, new long[]{rs.getLong("c"), rs.getLong("f")});
                },
                Timestamp.valueOf(since));
        return result;
    }

    public List<ConversationSummary> recentConversations(int limit) {
        return jdbcTemplate.query(
                "SELECT conv_type, conv_target, COUNT(*) AS c, MAX(send_time) AS t FROM chat_message "
                        + "GROUP BY conv_type, conv_target ORDER BY t DESC LIMIT ?",
                (rs, rowNum) -> new ConversationSummary(
                        rs.getInt("conv_type"),
                        rs.getString("conv_target"),
                        rs.getLong("c"),
                        rs.getTimestamp("t").toLocalDateTime()),
                limit);
    }

    public List<RecentMessage> recentMessages(int limit) {
        return jdbcTemplate.query(
                "SELECT conv_type, conv_target, msg_role, SUBSTRING(content, 1, 200) AS content, send_time "
                        + "FROM chat_message "
                        + "ORDER BY id DESC LIMIT ?",
                (rs, rowNum) -> new RecentMessage(
                        rs.getInt("conv_type"),
                        rs.getString("conv_target"),
                        rs.getString("msg_role"),
                        rs.getString("content"),
                        rs.getTimestamp("send_time").toLocalDateTime()),
                limit);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
