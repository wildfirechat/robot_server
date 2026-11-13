package cn.wildfirechat.app.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.Timestamp;

/**
 * 大模型调用记录，用于统计成功率、耗时和调用量。
 */
@Repository
public class LlmCallRepository {
    private static final Logger LOG = LoggerFactory.getLogger(LlmCallRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean mysql;

    public LlmCallRepository(JdbcTemplate jdbcTemplate) {
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

    private void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS llm_call ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "conv_type INT NOT NULL,"
                + "conv_target VARCHAR(128) NOT NULL,"
                + "sender VARCHAR(128),"
                + "model VARCHAR(128),"
                + "start_time TIMESTAMP NOT NULL,"
                + "duration_ms BIGINT NOT NULL,"
                + "success BOOLEAN NOT NULL,"
                + "error_message VARCHAR(512),"
                + "prompt_chars INT NOT NULL,"
                + "completion_chars INT NOT NULL)");
        try {
            if (mysql) {
                jdbcTemplate.execute("CREATE INDEX idx_llm_call_time ON llm_call (start_time)");
            } else {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_llm_call_time ON llm_call (start_time)");
            }
        } catch (Exception e) {
            LOG.debug("大模型调用记录索引已存在: {}", e.getMessage());
        }
    }

    public long count() {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM llm_call", Long.class);
        return value == null ? 0 : value;
    }

    public void insert(int convType, String convTarget, String sender, String model,
                       long startTime, long durationMs, boolean success,
                       String errorMessage, int promptChars, int completionChars) {
        try {
            jdbcTemplate.update("INSERT INTO llm_call (conv_type, conv_target, sender, model, start_time, "
                            + "duration_ms, success, error_message, prompt_chars, completion_chars) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    convType,
                    convTarget,
                    truncate(sender, 128),
                    truncate(model, 128),
                    new Timestamp(startTime),
                    durationMs,
                    success,
                    truncate(errorMessage, 512),
                    promptChars,
                    completionChars);
        } catch (Exception e) {
            LOG.error("记录大模型调用失败", e);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
