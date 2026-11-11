package cn.wildfirechat.app.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天记录仓储。使用 JdbcTemplate 实现，兼容 H2（默认）与 MySQL。
 */
@Repository
public class ChatMessageRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ChatMessageRepository.class);

    private static final String SELECT_COLUMNS =
            "SELECT id, conv_type, conv_target, sender, role, content, send_time FROM chat_message";

    private static final RowMapper<ChatMessage> ROW_MAPPER = new RowMapper<ChatMessage>() {
        @Override
        public ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChatMessage message = new ChatMessage();
            message.setId(rs.getLong("id"));
            message.setConvType(rs.getInt("conv_type"));
            message.setConvTarget(rs.getString("conv_target"));
            message.setSender(rs.getString("sender"));
            message.setRole(rs.getString("role"));
            message.setContent(rs.getString("content"));
            message.setSendTime(rs.getTimestamp("send_time").getTime());
            return message;
        }
    };

    private final JdbcTemplate jdbcTemplate;
    private final boolean mysql;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.mysql = detectMysql(jdbcTemplate);
        initSchema();
    }

    private static boolean detectMysql(JdbcTemplate jdbcTemplate) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("mysql");
        } catch (Exception e) {
            LOG.warn("无法识别数据库类型，将按 H2 处理", e);
            return false;
        }
    }

    /**
     * 建表。应用启动时自动执行，表已存在时不会重复创建。
     */
    private void initSchema() {
        String contentColumn = mysql ? "LONGTEXT" : "CLOB";
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chat_message ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "conv_type INT NOT NULL,"
                + "conv_target VARCHAR(128) NOT NULL,"
                + "sender VARCHAR(128),"
                + "role VARCHAR(16) NOT NULL,"
                + "content " + contentColumn + ","
                + "send_time TIMESTAMP NOT NULL)");
        try {
            if (mysql) {
                // MySQL 不支持 CREATE INDEX IF NOT EXISTS，重复创建时忽略异常
                jdbcTemplate.execute("CREATE INDEX idx_chat_message_conv ON chat_message (conv_type, conv_target, id)");
            } else {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_message_conv ON chat_message (conv_type, conv_target, id)");
            }
        } catch (Exception e) {
            LOG.debug("聊天记录索引已存在: {}", e.getMessage());
        }
    }

    /**
     * 保存一条聊天记录。
     *
     * @return 保存成功返回 true；失败仅记录日志，避免影响机器人回复
     */
    public boolean insert(ChatMessage message) {
        try {
            jdbcTemplate.update("INSERT INTO chat_message (conv_type, conv_target, sender, role, content, send_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    message.getConvType(),
                    message.getConvTarget(),
                    message.getSender(),
                    message.getRole(),
                    message.getContent(),
                    new java.sql.Timestamp(message.getSendTime()));
            return true;
        } catch (Exception e) {
            LOG.error("保存聊天记录失败, convType:{}, convTarget:{}, role:{}",
                    message.getConvType(), message.getConvTarget(), message.getRole(), e);
            return false;
        }
    }

    /**
     * 查询某个会话最近的若干条记录，返回值按时间正序排列（最早的在前）。
     */
    public List<ChatMessage> findRecent(int convType, String convTarget, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<ChatMessage> messages = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE conv_type = ? AND conv_target = ? ORDER BY id DESC LIMIT ?",
                new Object[]{convType, convTarget, limit},
                ROW_MAPPER);
        List<ChatMessage> result = new ArrayList<>(messages);
        Collections.reverse(result);
        return result;
    }
}
