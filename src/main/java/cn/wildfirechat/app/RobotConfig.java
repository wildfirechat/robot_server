package cn.wildfirechat.app;

import org.springframework.stereotype.Component;

/**
 * 机器人及大模型配置的运行期快照。
 * 配置值持久化在数据库 app_config 表中，由 ConfigService 在启动时加载、在管理后台保存后热更新。
 */
@Component
public class RobotConfig {
    public volatile String im_url = "http://localhost";
    public volatile String im_secret = "";
    public volatile String im_id = "FireRobot";
    public volatile String im_name = "小火";
    /** 聊天记录/大模型调用记录保留天数，0 表示不自动清理 */
    public volatile int retentionDays = 0;

    private final Llm llm = new Llm();

    public String getIm_name() {
        return im_name;
    }

    public void setIm_name(String im_name) {
        this.im_name = im_name;
    }

    public String getIm_id() {
        return im_id;
    }

    public void setIm_id(String im_id) {
        this.im_id = im_id;
    }

    public String getIm_url() {
        return im_url;
    }

    public void setIm_url(String im_url) {
        this.im_url = im_url;
    }

    public String getIm_secret() {
        return im_secret;
    }

    public void setIm_secret(String im_secret) {
        this.im_secret = im_secret;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Llm getLlm() {
        return llm;
    }

    public static class Llm {
        /** 是否启用大模型回复 */
        private volatile boolean enabled = false;
        /** OpenAI 兼容接口地址，默认 DeepSeek */
        private volatile String baseUrl = "https://api.deepseek.com";
        /** API Key，本地部署的 vLLM/Ollama 等可留空 */
        private volatile String apiKey = "";
        /** 模型名称，默认 deepseek-chat，也可用 deepseek-reasoner 等 */
        private volatile String model = "deepseek-chat";
        /** 系统提示词 */
        private volatile String systemPrompt = "你是一个智能助手，请使用简洁的中文回答用户的问题。";
        /** 温度，0~2 */
        private volatile double temperature = 0.7;
        /** 单次回复最大 token 数，<=0 表示不传该参数 */
        private volatile int maxTokens = 2048;
        /** 请求超时时间（秒） */
        private volatile int timeoutSeconds = 120;
        /** 每次请求携带的历史消息条数（包含当前用户消息） */
        private volatile int historyLimit = 30;
        /** 流式回复时，两次推送消息的最小间隔（毫秒） */
        private volatile long streamIntervalMs = 500;
        /** 是否使用流式接口 */
        private volatile boolean stream = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getHistoryLimit() {
            return historyLimit;
        }

        public void setHistoryLimit(int historyLimit) {
            this.historyLimit = historyLimit;
        }

        public long getStreamIntervalMs() {
            return streamIntervalMs;
        }

        public void setStreamIntervalMs(long streamIntervalMs) {
            this.streamIntervalMs = streamIntervalMs;
        }

        public boolean isStream() {
            return stream;
        }

        public void setStream(boolean stream) {
            this.stream = stream;
        }
    }
}
