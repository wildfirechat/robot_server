package cn.wildfirechat.app.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应用配置的持久化存储（key-value 形式），使用 PostgreSQL。
 */
@Repository
public class AppConfigRepository {
    private final JdbcTemplate jdbcTemplate;

    public AppConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initSchema();
    }

    private void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_config ("
                + "config_key VARCHAR(128) PRIMARY KEY,"
                + "config_value TEXT,"
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
