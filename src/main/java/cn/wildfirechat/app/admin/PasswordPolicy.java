package cn.wildfirechat.app.admin;

/**
 * 管理员密码强度策略。
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        if (password == null) {
            return false;
        }
        int length = password.length();
        return length >= MIN_LENGTH && length <= MAX_LENGTH
                && password.matches(".*[A-Za-z].*")
                && password.matches(".*\\d.*");
    }

    public static void validate(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("密码长度至少 " + MIN_LENGTH + " 位");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("密码长度不能超过 " + MAX_LENGTH + " 位");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须同时包含字母和数字");
        }
    }
}
