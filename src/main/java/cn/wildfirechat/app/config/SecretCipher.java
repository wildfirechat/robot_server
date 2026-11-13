package cn.wildfirechat.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;

/**
 * 配置敏感项（机器人 Secret、大模型 API Key）的静态加密。
 * 使用 AES-256-GCM；加密密钥保存在数据库 app_secret 表中，首次启动自动生成。
 * 多实例共享同一数据库时会使用同一把密钥。
 */
@Component
public class SecretCipher {
    private static final Logger LOG = LoggerFactory.getLogger(SecretCipher.class);

    static final String KEY_NAME = "config.encryption.key";
    private static final String TABLE_NAME = "app_secret";
    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final JdbcTemplate jdbcTemplate;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initSchema();
        this.key = new SecretKeySpec(loadOrCreateKey(), "AES");
    }

    /**
     * 加密配置值。空值保持为空，便于区分「未配置」。
     */
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText == null ? "" : plainText;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv).put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("配置加密失败", e);
        }
    }

    /**
     * 解密配置值。非加密格式（空值或历史明文）原样返回。
     */
    public String decrypt(String storedValue) {
        if (!StringUtils.hasText(storedValue) || !storedValue.startsWith(PREFIX)) {
            return storedValue;
        }
        try {
            byte[] data = Base64.getDecoder().decode(storedValue.substring(PREFIX.length()));
            if (data.length <= IV_BYTES) {
                throw new IllegalArgumentException("密文长度不合法");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[data.length - IV_BYTES];
            System.arraycopy(data, 0, iv, 0, IV_BYTES);
            System.arraycopy(data, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("配置解密失败，请检查数据库 app_secret 表中的加密密钥是否与密文匹配", e);
        }
    }

    private void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + "secret_name VARCHAR(64) PRIMARY KEY,"
                + "secret_value VARCHAR(256) NOT NULL,"
                + "created_time TIMESTAMP NOT NULL)");
    }

    private byte[] loadOrCreateKey() {
        String stored = findKey();
        if (stored != null) {
            return decodeKey(stored);
        }

        byte[] generated = new byte[KEY_BYTES];
        random.nextBytes(generated);
        String encoded = Base64.getEncoder().encodeToString(generated);
        try {
            jdbcTemplate.update("INSERT INTO " + TABLE_NAME
                            + " (secret_name, secret_value, created_time) VALUES (?, ?, ?)",
                    KEY_NAME, encoded, new Timestamp(System.currentTimeMillis()));
            LOG.warn("已在数据库 {} 表中生成配置加密密钥，请做好数据库备份", TABLE_NAME);
            return generated;
        } catch (DuplicateKeyException e) {
            // 多实例同时首次启动时，另一个实例已经写入密钥
            String existing = findKey();
            if (existing == null) {
                throw new IllegalStateException("读取配置加密密钥失败（并发初始化冲突）", e);
            }
            return decodeKey(existing);
        }
    }

    private String findKey() {
        List<String> values = jdbcTemplate.query(
                "SELECT secret_value FROM " + TABLE_NAME + " WHERE secret_name = ?",
                (rs, rowNum) -> rs.getString(1),
                KEY_NAME);
        return values.isEmpty() ? null : values.get(0);
    }

    private static byte[] decodeKey(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length != KEY_BYTES) {
                throw new IllegalStateException("配置加密密钥必须是 Base64 编码的 32 字节");
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("配置加密密钥不是合法的 Base64", e);
        }
    }
}
