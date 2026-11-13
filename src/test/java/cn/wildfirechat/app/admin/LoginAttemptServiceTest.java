package cn.wildfirechat.app.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoginAttemptServiceTest {
    @Test
    public void blocksAfterTenFailuresAndClearsOnSuccess() {
        LoginAttemptService service = new LoginAttemptService();
        String ip = "127.0.0.1";

        for (int i = 0; i < 9; i++) {
            service.onFailure(ip);
        }
        assertFalse(service.isBlocked(ip));

        service.onFailure(ip);
        assertTrue(service.isBlocked(ip));

        service.onSuccess(ip);
        assertFalse(service.isBlocked(ip));
    }

    @Test
    public void otherIpsAreNotAffected() {
        LoginAttemptService service = new LoginAttemptService();
        for (int i = 0; i < 10; i++) {
            service.onFailure("10.0.0.1");
        }
        assertTrue(service.isBlocked("10.0.0.1"));
        assertFalse(service.isBlocked("10.0.0.2"));
        assertFalse(service.isBlocked(null));
    }
}
