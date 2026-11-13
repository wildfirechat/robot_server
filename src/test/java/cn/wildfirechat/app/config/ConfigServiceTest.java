package cn.wildfirechat.app.config;

import cn.wildfirechat.app.RobotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigServiceTest {
    private RobotConfig robotConfig;
    private AppConfigRepository repository;
    private SecretCipher secretCipher;
    private ConfigService configService;

    @BeforeEach
    public void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:config_service_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        robotConfig = new RobotConfig();
        repository = new AppConfigRepository(jdbcTemplate);
        secretCipher = new SecretCipher(jdbcTemplate);
        configService = new ConfigService(repository, robotConfig, secretCipher);
        configService.init();
    }

    @Test
    public void defaultsArePersistedAndApplied() {
        Map<String, String> values = repository.findAll();
        assertFalse(values.isEmpty());
        assertEquals("FireRobot", values.get(ConfigService.IM_ID));
        assertEquals("deepseek-chat", values.get(ConfigService.LLM_MODEL));
        assertEquals("FireRobot", robotConfig.getIm_id());
        assertEquals("deepseek-chat", robotConfig.getLlm().getModel());
        assertFalse(robotConfig.getLlm().isEnabled());
    }

    @Test
    public void saveAppliesAndPersistsConfig() {
        AdminConfigForm form = validForm();
        form.setLlmEnabled(true);
        form.setLlmBaseUrl("http://127.0.0.1:8000/v1");
        form.setLlmApiKey("secret-key");
        form.setLlmModel("my-model");
        form.setLlmHistoryLimit(12);
        form.setLlmTemperature(0.3);

        configService.save(form);

        assertEquals("secret", robotConfig.getIm_secret());
        assertEquals("my-model", robotConfig.getLlm().getModel());
        assertTrue(robotConfig.getLlm().isEnabled());
        assertEquals(12, robotConfig.getLlm().getHistoryLimit());
        assertEquals(0.3, robotConfig.getLlm().getTemperature(), 0.0001);

        // 重新从数据库加载，确认已持久化
        Map<String, String> stored = repository.findAll();
        assertTrue(stored.get(ConfigService.LLM_API_KEY).startsWith("enc:v1:"));
        assertTrue(!stored.get(ConfigService.LLM_API_KEY).contains("secret-key"));
        assertTrue(stored.get(ConfigService.IM_SECRET).startsWith("enc:v1:"));

        RobotConfig reloaded = new RobotConfig();
        ConfigService reloadedService = new ConfigService(repository, reloaded, secretCipher);
        reloadedService.init();
        assertEquals("my-model", reloaded.getLlm().getModel());
        assertEquals("secret-key", reloaded.getLlm().getApiKey());
        assertEquals(12, reloaded.getLlm().getHistoryLimit());
        assertEquals("secret", reloaded.getIm_secret());
    }

    @Test
    public void blankApiKeyKeepsOldValueAndClearApiKeyRemovesIt() {
        AdminConfigForm form = validForm();
        form.setLlmApiKey("first-key");
        configService.save(form);
        assertEquals("first-key", robotConfig.getLlm().getApiKey());

        AdminConfigForm keepForm = AdminConfigForm.from(robotConfig);
        keepForm.setImSecret("secret");
        configService.save(keepForm);
        assertEquals("first-key", robotConfig.getLlm().getApiKey());

        keepForm.setClearApiKey(true);
        configService.save(keepForm);
        assertEquals("", robotConfig.getLlm().getApiKey());
    }

    @Test
    public void invalidConfigIsRejected() {
        AdminConfigForm form = validForm();
        form.setLlmTemperature(5);
        assertThrows(IllegalArgumentException.class, () -> configService.save(form));

        AdminConfigForm badUrl = validForm();
        badUrl.setImUrl("ftp://example.com");
        assertThrows(IllegalArgumentException.class, () -> configService.save(badUrl));

        AdminConfigForm noSecret = validForm();
        noSecret.setImSecret("");
        robotConfig.setIm_secret("");
        assertThrows(IllegalArgumentException.class, () -> configService.save(noSecret));

        assertEquals(0.7, robotConfig.getLlm().getTemperature(), 0.0001);
    }

    private AdminConfigForm validForm() {
        AdminConfigForm form = AdminConfigForm.from(robotConfig);
        form.setImId("FireRobot");
        form.setImName("小火");
        form.setImUrl("http://127.0.0.1");
        form.setImSecret("secret");
        form.setLlmEnabled(false);
        form.setLlmBaseUrl("https://api.deepseek.com");
        form.setLlmModel("deepseek-chat");
        form.setLlmSystemPrompt("你是助手");
        form.setLlmTemperature(0.7);
        form.setLlmMaxTokens(2048);
        form.setLlmTimeoutSeconds(120);
        form.setLlmHistoryLimit(30);
        form.setLlmStream(true);
        form.setLlmStreamIntervalMs(500);
        return form;
    }
}
