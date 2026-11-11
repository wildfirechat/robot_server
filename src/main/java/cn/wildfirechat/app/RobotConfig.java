package cn.wildfirechat.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ConfigurationProperties(prefix="robot")
@PropertySource(value = "file:config/robot.properties", encoding = "UTF-8")
public class RobotConfig {
    public String im_url;
    public String im_secret;
    public String im_id;
    public String im_name;

    public String public_addr;

    /**
     * OpenAI 兼容的大模型配置，对应 robot.properties 中的 robot.llm.* 配置项。
     */
    private Llm llm = new Llm();

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

    public String getPublic_addr() {
        return public_addr;
    }

    public void setPublic_addr(String public_addr) {
        this.public_addr = public_addr;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public static class Llm {
        /** 是否启用大模型回复 */
        private boolean enabled = true;
        /** OpenAI 兼容接口地址，默认 DeepSeek，一般到 /v1 结尾 */
        private String baseUrl = "https://api.deepseek.com";
        /** API Key，本地部署的 vLLM/Ollama 等可留空 */
        private String apiKey = "";
        /** 模型名称，默认 deepseek-chat，也可用 deepseek-reasoner 等 */
        private String model = "deepseek-chat";
        /** 系统提示词 */
        private String systemPrompt = "你是一个智能助手，请使用简洁的中文回答用户的问题。";
        /** 温度，0~2 */
        private double temperature = 0.7;
        /** 单次回复最大 token 数，<=0 表示不传该参数 */
        private int maxTokens = 2048;
        /** 请求超时时间（秒） */
        private int timeoutSeconds = 120;
        /** 每次请求携带的历史消息条数（包含当前用户消息） */
        private int historyLimit = 30;
        /** 流式回复时，两次推送消息的最小间隔（毫秒） */
        private long streamIntervalMs = 500;
        /** 是否使用流式接口 */
        private boolean stream = true;

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
