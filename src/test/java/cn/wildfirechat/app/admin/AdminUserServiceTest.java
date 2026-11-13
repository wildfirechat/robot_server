package cn.wildfirechat.app.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdminUserServiceTest {
    private AdminUserRepository repository;
    private PasswordEncoder encoder;
    private AdminUserService service;

    @BeforeEach
    public void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:admin_user_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        repository = new AdminUserRepository(new JdbcTemplate(dataSource));
        encoder = new BCryptPasswordEncoder(4);
        service = new AdminUserService(repository, encoder);
        repository.insert("admin", encoder.encode("OldPass12345"), System.currentTimeMillis(), true);
    }

    @Test
    public void changePasswordStoresNewHash() {
        service.changePassword("admin", "OldPass12345", "NewPass67890", "NewPass67890");

        AdminUser user = repository.findByUsername("admin").orElseThrow(IllegalStateException::new);
        assertTrue(encoder.matches("NewPass67890", user.getPasswordHash()));
        assertTrue(!encoder.matches("OldPass12345", user.getPasswordHash()));
        assertTrue(!user.isMustChangePassword());
    }

    @Test
    public void rejectsWrongCurrentPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "WrongPass12345", "NewPass67890", "NewPass67890"));
    }

    @Test
    public void rejectsWeakNewPasswordAndMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "OldPass12345", "short", "short"));
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "OldPass12345", "NewPass67890", "Different67890"));
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "OldPass12345", "OldPass12345", "OldPass12345"));
    }

    @Test
    public void passwordPolicyEnforcesLengthAndCharacterClasses() {
        assertTrue(PasswordPolicy.isStrong("Abcdef12345"));
        assertTrue(!PasswordPolicy.isStrong("1234567890"));
        assertTrue(!PasswordPolicy.isStrong("abcdefghij"));
        assertTrue(!PasswordPolicy.isStrong("Abc123"));
        assertEquals(10, PasswordPolicy.MIN_LENGTH);
    }
}
