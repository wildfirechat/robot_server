package cn.wildfirechat.app.config;

import cn.wildfirechat.app.RobotConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据库配置中心：启动时从 app_config 表加载配置到 RobotConfig，
 * 管理后台保存后立即写库并热更新 RobotConfig，无需重启。
 */
@Service
public class ConfigService {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigService.class);

    static final String IM_ID = "robot.im_id";
    static final String IM_NAME = "robot.im_name";
    static final String IM_URL = "robot.im_url";
    static final String IM_SECRET = "robot.im_secret";
    static final String RETENTION_DAYS = "robot.data.retention_days";

    static final String LLM_ENABLED = "robot.llm.enabled";
    static final String LLM_BASE_URL = "robot.llm.base_url";
    static final String LLM_API_KEY = "robot.llm.api_key";
    static final String LLM_MODEL = "robot.llm.model";
    static final String LLM_SYSTEM_PROMPT = "robot.llm.system_prompt";
    static final String LLM_TEMPERATURE = "robot.llm.temperature";
    static final String LLM_MAX_TOKENS = "robot.llm.max_tokens";
    static final String LLM_TIMEOUT_SECONDS = "robot.llm.timeout_seconds";
    static final String LLM_HISTORY_LIMIT = "robot.llm.history_limit";
    static final String LLM_STREAM = "robot.llm.stream";
    static final String LLM_STREAM_INTERVAL_MS = "robot.llm.stream_interval_ms";

    static final String RAG_ENABLED = "robot.rag.enabled";
    static final String RAG_EMBEDDING_BASE_URL = "robot.rag.embedding_base_url";
    static final String RAG_EMBEDDING_API_KEY = "robot.rag.embedding_api_key";
    static final String RAG_EMBEDDING_MODEL = "robot.rag.embedding_model";
    static final String RAG_TOP_K = "robot.rag.top_k";
    static final String RAG_SCORE_THRESHOLD = "robot.rag.score_threshold";
    static final String RAG_MAX_CONTEXT_CHARS = "robot.rag.max_context_chars";
    static final String RAG_CHUNK_SIZE = "robot.rag.chunk_size";
    static final String RAG_CHUNK_OVERLAP = "robot.rag.chunk_overlap";

    private final AppConfigRepository repository;
    private final RobotConfig robotConfig;
    private final SecretCipher secretCipher;
    private final AtomicLong version = new AtomicLong();

    public ConfigService(AppConfigRepository repository, RobotConfig robotConfig, SecretCipher secretCipher) {
        this.repository = repository;
        this.robotConfig = robotConfig;
        this.secretCipher = secretCipher;
    }

    @PostConstruct
    public void init() {
        Map<String, String> defaults = defaults();
        Map<String, String> stored = repository.findAll();
        Map<String, String> merged = new LinkedHashMap<>(defaults);
        merged.putAll(stored);
        // 配置加密密钥不匹配时直接启动失败，避免使用空 Secret 静默运行
        apply(merged);
        // 首次启动时把默认配置落库，后续都以数据库为准
        repository.saveAll(merged);
        version.incrementAndGet();
        LOG.info("配置已从数据库加载，共 {} 项", merged.size());
    }

    public long getVersion() {
        return version.get();
    }

    /**
     * 保存配置：先校验，再写库，最后热更新内存中的 RobotConfig。
     */
    @Transactional
    public void save(AdminConfigForm form) {
        validate(form);

        String apiKey;
        if (form.isClearApiKey()) {
            apiKey = "";
        } else if (StringUtils.hasText(form.getLlmApiKey())) {
            apiKey = form.getLlmApiKey().trim();
        } else {
            apiKey = robotConfig.getLlm().getApiKey();
        }
        String imSecret = StringUtils.hasText(form.getImSecret())
                ? form.getImSecret().trim() : robotConfig.getIm_secret();

        Map<String, String> values = new LinkedHashMap<>();
        values.put(IM_ID, form.getImId().trim());
        values.put(IM_NAME, trim(form.getImName()));
        values.put(IM_URL, form.getImUrl().trim());
        values.put(IM_SECRET, secretCipher.encrypt(imSecret));
        values.put(RETENTION_DAYS, String.valueOf(form.getRetentionDays()));
        values.put(LLM_ENABLED, String.valueOf(form.isLlmEnabled()));
        values.put(LLM_BASE_URL, trim(form.getLlmBaseUrl()));
        values.put(LLM_API_KEY, secretCipher.encrypt(apiKey));
        values.put(LLM_MODEL, trim(form.getLlmModel()));
        values.put(LLM_SYSTEM_PROMPT, form.getLlmSystemPrompt() == null ? "" : form.getLlmSystemPrompt());
        values.put(LLM_TEMPERATURE, String.valueOf(form.getLlmTemperature()));
        values.put(LLM_MAX_TOKENS, String.valueOf(form.getLlmMaxTokens()));
        values.put(LLM_TIMEOUT_SECONDS, String.valueOf(form.getLlmTimeoutSeconds()));
        values.put(LLM_HISTORY_LIMIT, String.valueOf(form.getLlmHistoryLimit()));
        values.put(LLM_STREAM, String.valueOf(form.isLlmStream()));
        values.put(LLM_STREAM_INTERVAL_MS, String.valueOf(form.getLlmStreamIntervalMs()));

        String ragApiKey = StringUtils.hasText(form.getRagEmbeddingApiKey())
                ? form.getRagEmbeddingApiKey().trim() : robotConfig.getRag().getEmbeddingApiKey();
        values.put(RAG_ENABLED, String.valueOf(form.isRagEnabled()));
        values.put(RAG_EMBEDDING_BASE_URL, trim(form.getRagEmbeddingBaseUrl()));
        values.put(RAG_EMBEDDING_API_KEY, secretCipher.encrypt(ragApiKey));
        values.put(RAG_EMBEDDING_MODEL, trim(form.getRagEmbeddingModel()));
        values.put(RAG_TOP_K, String.valueOf(form.getRagTopK()));
        values.put(RAG_SCORE_THRESHOLD, String.valueOf(form.getRagScoreThreshold()));
        values.put(RAG_MAX_CONTEXT_CHARS, String.valueOf(form.getRagMaxContextChars()));
        values.put(RAG_CHUNK_SIZE, String.valueOf(form.getRagChunkSize()));
        values.put(RAG_CHUNK_OVERLAP, String.valueOf(form.getRagChunkOverlap()));

        repository.saveAll(values);
        apply(values);
        version.incrementAndGet();
        LOG.info("管理员已更新配置，配置项: {}", values.keySet());
    }

    private void validate(AdminConfigForm form) {
        String imId = trim(form.getImId());
        require(imId, "机器人ID");
        if (imId.length() > 64) {
            throw new IllegalArgumentException("机器人ID不能超过64个字符");
        }
        if (trim(form.getImName()).length() > 64) {
            throw new IllegalArgumentException("机器人名称不能超过64个字符");
        }
        checkHttpUrl(trim(form.getImUrl()), "IM 服务地址");
        String secret = StringUtils.hasText(form.getImSecret())
                ? form.getImSecret().trim() : robotConfig.getIm_secret();
        require(secret, "机器人 Secret");
        if (secret.length() > 128) {
            throw new IllegalArgumentException("机器人 Secret 不能超过128个字符");
        }

        String baseUrl = trim(form.getLlmBaseUrl());
        if (form.isLlmEnabled()) {
            checkHttpUrl(baseUrl, "大模型接口地址");
            require(trim(form.getLlmModel()), "模型名称");
        } else if (!baseUrl.isEmpty()) {
            checkHttpUrl(baseUrl, "大模型接口地址");
        }
        if (trim(form.getLlmModel()).length() > 128) {
            throw new IllegalArgumentException("模型名称不能超过128个字符");
        }
        if (form.getLlmSystemPrompt() != null && form.getLlmSystemPrompt().length() > 20000) {
            throw new IllegalArgumentException("系统提示词不能超过20000个字符");
        }
        if (form.getLlmApiKey() != null && form.getLlmApiKey().length() > 500) {
            throw new IllegalArgumentException("API Key 不能超过500个字符");
        }
        if (!Double.isFinite(form.getLlmTemperature()) || form.getLlmTemperature() < 0 || form.getLlmTemperature() > 2) {
            throw new IllegalArgumentException("温度必须在 0~2 之间");
        }
        checkRange(form.getRetentionDays(), 0, 3650, "数据保留天数");
        checkRange(form.getLlmMaxTokens(), 1, 16384, "最大 token 数");
        checkRange(form.getLlmTimeoutSeconds(), 1, 600, "超时时间");
        checkRange(form.getLlmHistoryLimit(), 1, 500, "历史消息条数");
        if (form.getLlmStreamIntervalMs() < 0 || form.getLlmStreamIntervalMs() > 60000) {
            throw new IllegalArgumentException("流式消息间隔必须在 0~60000 毫秒之间");
        }

        if (form.isRagEnabled()) {
            require(trim(form.getRagEmbeddingModel()), "Embedding 模型");
        }
        String embeddingBaseUrl = trim(form.getRagEmbeddingBaseUrl());
        if (!embeddingBaseUrl.isEmpty()) {
            checkHttpUrl(embeddingBaseUrl, "Embedding 接口地址");
        }
        if (form.getRagEmbeddingApiKey() != null && form.getRagEmbeddingApiKey().length() > 500) {
            throw new IllegalArgumentException("Embedding API Key 不能超过500个字符");
        }
        checkRange(form.getRagTopK(), 1, 20, "知识库 TopK");
        if (!Double.isFinite(form.getRagScoreThreshold())
                || form.getRagScoreThreshold() < 0 || form.getRagScoreThreshold() > 1) {
            throw new IllegalArgumentException("知识库相似度阈值必须在 0~1 之间");
        }
        checkRange(form.getRagMaxContextChars(), 500, 12000, "知识库上下文长度");
        checkRange(form.getRagChunkSize(), 200, 2000, "知识库分块大小");
        if (form.getRagChunkOverlap() < 0 || form.getRagChunkOverlap() > form.getRagChunkSize() / 2) {
            throw new IllegalArgumentException("知识库分块重叠不能超过分块大小的一半");
        }
    }

    private static void checkRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + "必须在 " + min + "~" + max + " 之间");
        }
    }

    private static void require(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }

    private static void checkHttpUrl(String value, String field) {
        require(value, field);
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException(field + "必须是合法的 http/https 地址");
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains(field)) {
                throw e;
            }
            throw new IllegalArgumentException(field + "必须是合法的 http/https 地址");
        }
    }

    private void apply(Map<String, String> values) {
        robotConfig.im_id = value(values, IM_ID, robotConfig.im_id);
        robotConfig.im_name = value(values, IM_NAME, robotConfig.im_name);
        robotConfig.im_url = value(values, IM_URL, robotConfig.im_url);
        robotConfig.im_secret = secretCipher.decrypt(value(values, IM_SECRET, robotConfig.im_secret));
        robotConfig.retentionDays = intValue(values, RETENTION_DAYS, robotConfig.retentionDays);

        RobotConfig.Llm llm = robotConfig.getLlm();
        llm.setEnabled(boolValue(values, LLM_ENABLED, llm.isEnabled()));
        llm.setBaseUrl(value(values, LLM_BASE_URL, llm.getBaseUrl()));
        llm.setApiKey(secretCipher.decrypt(value(values, LLM_API_KEY, llm.getApiKey())));
        llm.setModel(value(values, LLM_MODEL, llm.getModel()));
        llm.setSystemPrompt(value(values, LLM_SYSTEM_PROMPT, llm.getSystemPrompt()));
        llm.setTemperature(doubleValue(values, LLM_TEMPERATURE, llm.getTemperature()));
        llm.setMaxTokens(intValue(values, LLM_MAX_TOKENS, llm.getMaxTokens()));
        llm.setTimeoutSeconds(intValue(values, LLM_TIMEOUT_SECONDS, llm.getTimeoutSeconds()));
        llm.setHistoryLimit(intValue(values, LLM_HISTORY_LIMIT, llm.getHistoryLimit()));
        llm.setStream(boolValue(values, LLM_STREAM, llm.isStream()));
        llm.setStreamIntervalMs(longValue(values, LLM_STREAM_INTERVAL_MS, llm.getStreamIntervalMs()));

        RobotConfig.Rag rag = robotConfig.getRag();
        rag.setEnabled(boolValue(values, RAG_ENABLED, rag.isEnabled()));
        rag.setEmbeddingBaseUrl(value(values, RAG_EMBEDDING_BASE_URL, rag.getEmbeddingBaseUrl()));
        rag.setEmbeddingApiKey(secretCipher.decrypt(value(values, RAG_EMBEDDING_API_KEY, rag.getEmbeddingApiKey())));
        rag.setEmbeddingModel(value(values, RAG_EMBEDDING_MODEL, rag.getEmbeddingModel()));
        rag.setTopK(intValue(values, RAG_TOP_K, rag.getTopK()));
        rag.setScoreThreshold(doubleValue(values, RAG_SCORE_THRESHOLD, rag.getScoreThreshold()));
        rag.setMaxContextChars(intValue(values, RAG_MAX_CONTEXT_CHARS, rag.getMaxContextChars()));
        rag.setChunkSize(intValue(values, RAG_CHUNK_SIZE, rag.getChunkSize()));
        rag.setChunkOverlap(intValue(values, RAG_CHUNK_OVERLAP, rag.getChunkOverlap()));
    }

    private Map<String, String> defaults() {
        RobotConfig defaults = new RobotConfig();
        RobotConfig.Llm llm = defaults.getLlm();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(IM_ID, defaults.im_id);
        values.put(IM_NAME, defaults.im_name);
        values.put(IM_URL, defaults.im_url);
        values.put(IM_SECRET, defaults.im_secret);
        values.put(RETENTION_DAYS, String.valueOf(defaults.retentionDays));
        values.put(LLM_ENABLED, String.valueOf(llm.isEnabled()));
        values.put(LLM_BASE_URL, llm.getBaseUrl());
        values.put(LLM_API_KEY, llm.getApiKey());
        values.put(LLM_MODEL, llm.getModel());
        values.put(LLM_SYSTEM_PROMPT, llm.getSystemPrompt());
        values.put(LLM_TEMPERATURE, String.valueOf(llm.getTemperature()));
        values.put(LLM_MAX_TOKENS, String.valueOf(llm.getMaxTokens()));
        values.put(LLM_TIMEOUT_SECONDS, String.valueOf(llm.getTimeoutSeconds()));
        values.put(LLM_HISTORY_LIMIT, String.valueOf(llm.getHistoryLimit()));
        values.put(LLM_STREAM, String.valueOf(llm.isStream()));
        values.put(LLM_STREAM_INTERVAL_MS, String.valueOf(llm.getStreamIntervalMs()));
        RobotConfig.Rag rag = defaults.getRag();
        values.put(RAG_ENABLED, String.valueOf(rag.isEnabled()));
        values.put(RAG_EMBEDDING_BASE_URL, rag.getEmbeddingBaseUrl());
        values.put(RAG_EMBEDDING_API_KEY, rag.getEmbeddingApiKey());
        values.put(RAG_EMBEDDING_MODEL, rag.getEmbeddingModel());
        values.put(RAG_TOP_K, String.valueOf(rag.getTopK()));
        values.put(RAG_SCORE_THRESHOLD, String.valueOf(rag.getScoreThreshold()));
        values.put(RAG_MAX_CONTEXT_CHARS, String.valueOf(rag.getMaxContextChars()));
        values.put(RAG_CHUNK_SIZE, String.valueOf(rag.getChunkSize()));
        values.put(RAG_CHUNK_OVERLAP, String.valueOf(rag.getChunkOverlap()));
        return values;
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value.trim();
    }

    private static boolean boolValue(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int intValue(Map<String, String> values, String key, int fallback) {
        try {
            String value = values.get(key);
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long longValue(Map<String, String> values, String key, long fallback) {
        try {
            String value = values.get(key);
            return value == null ? fallback : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleValue(Map<String, String> values, String key, double fallback) {
        try {
            String value = values.get(key);
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
