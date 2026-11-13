package cn.wildfirechat.app.im;

import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.sdk.RobotService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 按当前配置提供 RobotService。管理后台修改 IM 配置后会重建连接。
 */
@Component
public class RobotServiceProvider {
    private static final Logger LOG = LoggerFactory.getLogger(RobotServiceProvider.class);

    private final RobotConfig config;
    private volatile RobotService service;
    private volatile String fingerprint;

    public RobotServiceProvider(RobotConfig config) {
        this.config = config;
    }

    public RobotService get() {
        String current = fingerprintOfCurrentConfig();
        RobotService currentService = service;
        if (currentService != null && current.equals(fingerprint)) {
            return currentService;
        }
        synchronized (this) {
            if (service != null && current.equals(fingerprint)) {
                return service;
            }
            if (!StringUtils.hasText(config.getIm_url())
                    || !StringUtils.hasText(config.getIm_id())
                    || !StringUtils.hasText(config.getIm_secret())) {
                throw new IllegalStateException("机器人 IM 配置不完整，请先在管理后台配置 im_url / im_id / im_secret");
            }
            closeQuietly(service);
            service = new RobotService(config.getIm_url().trim(), config.getIm_id().trim(), config.getIm_secret());
            fingerprint = current;
            LOG.info("已按最新配置初始化机器人服务, imUrl={}, imId={}", config.getIm_url(), config.getIm_id());
            return service;
        }
    }

    private String fingerprintOfCurrentConfig() {
        return config.getIm_url() + "|" + config.getIm_id() + "|" + config.getIm_secret();
    }

    @PreDestroy
    public void destroy() {
        closeQuietly(service);
        service = null;
    }

    private static void closeQuietly(RobotService service) {
        if (service == null) {
            return;
        }
        try {
            service.close();
        } catch (Exception e) {
            LOG.warn("关闭机器人服务失败: {}", e.getMessage());
        }
    }
}
