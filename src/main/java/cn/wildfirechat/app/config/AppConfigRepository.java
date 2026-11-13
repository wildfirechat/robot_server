package cn.wildfirechat.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应用配置的持久化存储（key-value 形式），兼容 H2 和 MySQL。
 */
@Repository
public class AppConfigRepository {
    private static final Logger LOG = LoggerFactory.getLogger(AppConfigRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean mysql;

    public AppConfigRepository(JdbcTemplate jdbcTemplate) {
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
        String contentColumn = mysql ? "LONGTEXT" : "CLOB";
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_config ("
                + "config_key VARCHAR(128) PRIMARY KEY,"
                + "config_value " + contentColumn + ","
                + "updated_time TIMESTAMP NOT NULL)");
    }

    public Map<String, String> findAll() {
        Map<String, String> values = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT config_key, config_value FROM app_config",
                (RowCallbackHandler) rs -> values.put(rs.getString("config_key"), rs.getString("config_value")));
        return values;
    }

    public void saveAll(Map<String, String> values) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            int updated = jdbcTemplate.update(
                    "UPDATE app_config SET config_value = ?, updated_time = ? WHERE config_key = ?",
                    entry.getValue(), now, entry.getKey());
            if (updated == 0) {
                jdbcTemplate.update(
                        "INSERT INTO app_config (config_key, config_value, updated_time) VALUES (?, ?, ?)",
                        entry.getKey(), entry.getValue(), now);
            }
        }
    }
}
