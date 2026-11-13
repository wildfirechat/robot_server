package cn.wildfirechat.app.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecretCipherTest {
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:secret_cipher_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    public void encryptAndDecryptRoundTrip() {
        SecretCipher cipher = new SecretCipher(jdbcTemplate);

        String encrypted = cipher.encrypt("my-secret");
        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals("my-secret", encrypted);
        assertNotEquals(encrypted, cipher.encrypt("my-secret"));
        assertEquals("my-secret", cipher.decrypt(encrypted));
        assertEquals("", cipher.decrypt(""));
        assertEquals("plain", cipher.decrypt("plain"));
    }

    @Test
    public void keyIsGeneratedAndReusedFromDatabase() {
        SecretCipher first = new SecretCipher(jdbcTemplate);
        String encrypted = first.encrypt("persisted-secret");

        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_secret", Long.class);
        assertEquals(1, count);

        SecretCipher second = new SecretCipher(jdbcTemplate);
        assertEquals("persisted-secret", second.decrypt(encrypted));
    }

    @Test
    public void decryptWithDifferentDatabaseKeyFails() {
        SecretCipher cipher = new SecretCipher(jdbcTemplate);
        String encrypted = cipher.encrypt("my-secret");

        DriverManagerDataSource otherSource = new DriverManagerDataSource();
        otherSource.setDriverClassName("org.h2.Driver");
        otherSource.setUrl("jdbc:h2:mem:secret_cipher_other_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        otherSource.setUsername("sa");
        otherSource.setPassword("");
        SecretCipher other = new SecretCipher(new JdbcTemplate(otherSource));

        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    }
}
