package cn.wildfirechat.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecretCipherTest {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    public void encryptAndDecryptRoundTrip() {
        SecretCipher cipher = new SecretCipher(new MockEnvironment().withProperty(SecretCipher.ENV_KEY, KEY));

        String encrypted = cipher.encrypt("my-secret");
        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals("my-secret", encrypted);
        assertNotEquals(encrypted, cipher.encrypt("my-secret"));
        assertEquals("my-secret", cipher.decrypt(encrypted));
        assertEquals("", cipher.decrypt(""));
        assertEquals("plain", cipher.decrypt("plain"));
    }

    @Test
    public void decryptWithDifferentKeyFails() {
        SecretCipher cipher = new SecretCipher(new MockEnvironment().withProperty(SecretCipher.ENV_KEY, KEY));
        String encrypted = cipher.encrypt("my-secret");

        String otherKey = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU=";
        SecretCipher other = new SecretCipher(new MockEnvironment().withProperty(SecretCipher.ENV_KEY, otherKey));
        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    }

    @Test
    public void generatesKeyFileWhenNoEnvKey(@TempDir Path tempDir) throws Exception {
        Path keyFile = tempDir.resolve("config.key");
        MockEnvironment environment = new MockEnvironment()
                .withProperty(SecretCipher.ENV_KEY_FILE, keyFile.toString());

        SecretCipher cipher = new SecretCipher(environment);
        String encrypted = cipher.encrypt("secret-value");

        assertTrue(Files.exists(keyFile));
        assertTrue(Files.size(keyFile) > 0);

        SecretCipher reloaded = new SecretCipher(environment);
        assertEquals("secret-value", reloaded.decrypt(encrypted));
    }
}
