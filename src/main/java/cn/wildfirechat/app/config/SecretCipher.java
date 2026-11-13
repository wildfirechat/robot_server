package cn.wildfirechat.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置敏感项（机器人 Secret、大模型 API Key）的静态加密。
 * 使用 AES-256-GCM，密钥优先取环境变量 ROBOT_CONFIG_KEY（Base64 的 32 字节），
 * 否则从 data/config.key 读取；文件不存在时生成并设置为 600 权限。
 */
@Component
public class SecretCipher {
    private static final Logger LOG = LoggerFactory.getLogger(SecretCipher.class);

    static final String ENV_KEY = "ROBOT_CONFIG_KEY";
    static final String ENV_KEY_FILE = "ROBOT_CONFIG_KEY_FILE";
    static final String DEFAULT_KEY_FILE = "data/config.key";

    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(Environment environment) {
        this.key = new SecretKeySpec(loadKey(environment), "AES");
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
            throw new IllegalStateException("配置解密失败，请检查 ROBOT_CONFIG_KEY 或 data/config.key 是否与数据库匹配", e);
        }
    }

    private static byte[] loadKey(Environment environment) {
        String envKey = environment.getProperty(ENV_KEY);
        if (StringUtils.hasText(envKey)) {
            byte[] bytes = decodeKey(envKey.trim(), ENV_KEY);
            LOG.info("配置加密使用环境变量 {}", ENV_KEY);
            return bytes;
        }

        String configuredPath = environment.getProperty(ENV_KEY_FILE, DEFAULT_KEY_FILE);
        Path path = Paths.get(configuredPath).toAbsolutePath().normalize();
        if (Files.exists(path)) {
            byte[] bytes = readKeyFile(path);
            LOG.info("配置加密使用密钥文件 {}", path);
            return bytes;
        }

        byte[] generated = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(generated);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, Base64.getEncoder().encode(generated),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows 等不支持 POSIX 权限时忽略
            }
            LOG.warn("已生成配置加密密钥文件 {}，请妥善备份；密钥丢失将无法解密已保存的 Secret/API Key", path);
            return generated;
        } catch (FileAlreadyExistsException e) {
            return readKeyFile(path);
        } catch (IOException e) {
            throw new IllegalStateException("生成配置加密密钥失败: " + path, e);
        }
    }

    private static byte[] readKeyFile(Path path) {
        try {
            String value = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
            return decodeKey(value, path.toString());
        } catch (IOException e) {
            throw new IllegalStateException("读取配置加密密钥失败: " + path, e);
        }
    }

    private static byte[] decodeKey(String base64, String source) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length != KEY_BYTES) {
                throw new IllegalStateException("配置加密密钥必须是 Base64 编码的 32 字节，来源: " + source);
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("配置加密密钥不是合法的 Base64，来源: " + source, e);
        }
    }
}
