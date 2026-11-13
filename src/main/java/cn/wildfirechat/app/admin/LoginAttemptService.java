package cn.wildfirechat.app.admin;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的登录失败次数限制：同一 IP 连续失败 10 次后锁定 10 分钟。
 * 仅保存在内存中，重启后重置。
 */
@Component
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final long BLOCK_MS = 10 * 60 * 1000L;
    private static final int MAX_TRACKED_IPS = 10000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        if (key == null) {
            return false;
        }
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (attempt.blockedUntil > now) {
            return true;
        }
        if (now - attempt.firstAttemptAt > WINDOW_MS) {
            attempts.remove(key, attempt);
        }
        return false;
    }

    public void onFailure(String key) {
        if (key == null) {
            return;
        }
        if (attempts.size() > MAX_TRACKED_IPS) {
            attempts.clear();
        }
        long now = System.currentTimeMillis();
        attempts.compute(key, (k, old) -> {
            if (old == null || now - old.firstAttemptAt > WINDOW_MS) {
                return new Attempt(now);
            }
            old.count++;
            if (old.count >= MAX_ATTEMPTS) {
                old.blockedUntil = now + BLOCK_MS;
            }
            return old;
        });
    }

    public void onSuccess(String key) {
        if (key != null) {
            attempts.remove(key);
        }
    }

    private static class Attempt {
        private final long firstAttemptAt;
        private int count = 1;
        private long blockedUntil;

        Attempt(long firstAttemptAt) {
            this.firstAttemptAt = firstAttemptAt;
        }
    }
}
