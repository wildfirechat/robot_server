package cn.wildfirechat.app.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

/**
 * 首次启动时初始化管理员账号。
 * 密码优先从环境变量 ROBOT_ADMIN_PASSWORD 读取；未提供或强度不足时随机生成并打印到启动日志。
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminBootstrap(AdminUserRepository repository, PasswordEncoder passwordEncoder, Environment environment) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            LOG.info("已存在管理员账号，跳过管理员初始化。如需重置管理员密码：停止服务后执行 "
                    + "DELETE FROM admin_user WHERE username = 'admin';（使用自定义用户名时替换为实际用户名），"
                    + "然后设置环境变量 ROBOT_ADMIN_PASSWORD 并重启服务；未设置或强度不足时会生成随机密码并打印在启动日志中。");
            return;
        }
        String username = environment.getProperty("ROBOT_ADMIN_USERNAME", "admin").trim();
        if (!username.matches("[A-Za-z0-9_]{3,32}")) {
            LOG.warn("ROBOT_ADMIN_USERNAME 不合法，使用默认用户名 admin");
            username = "admin";
        }

        String password = environment.getProperty("ROBOT_ADMIN_PASSWORD");
        boolean generated = false;
        if (!PasswordPolicy.isStrong(password)) {
            if (StringUtils.hasText(password)) {
                LOG.warn("ROBOT_ADMIN_PASSWORD 不满足强度要求（至少10位且包含字母和数字），将生成随机密码");
            }
            do {
                password = generatePassword();
            } while (!PasswordPolicy.isStrong(password));
            generated = true;
        }

        repository.insert(username, passwordEncoder.encode(password), System.currentTimeMillis(), generated);
        if (generated) {
            LOG.warn("首次启动已生成管理员账号 [{}]，初始密码: {} ，请登录管理后台后立即修改！", username, password);
        } else {
            LOG.info("已根据 ROBOT_ADMIN_PASSWORD 创建管理员账号 [{}]", username);
        }
    }

    private static String generatePassword() {
        StringBuilder builder = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
