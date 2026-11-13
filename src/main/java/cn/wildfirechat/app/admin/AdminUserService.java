package cn.wildfirechat.app.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理员账号业务：修改密码等。
 */
@Service
public class AdminUserService {
    private static final Logger LOG = LoggerFactory.getLogger(AdminUserService.class);

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(AdminUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public void changePassword(String username, String currentPassword, String newPassword, String confirmPassword) {
        if (!StringUtils.hasText(currentPassword)) {
            throw new IllegalArgumentException("请输入当前密码");
        }
        AdminUser user = repository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("管理员账号不存在"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        PasswordPolicy.validate(newPassword);
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("两次输入的新密码不一致");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        repository.updatePassword(username, passwordEncoder.encode(newPassword), System.currentTimeMillis());
        LOG.info("管理员 [{}] 修改了登录密码", username);
    }
}
