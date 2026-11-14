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
    private final Rag rag = new Rag();

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

    public Rag getRag() {
        return rag;
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

    public static class Rag {
        /** 是否启用知识库检索 */
        private volatile boolean enabled = false;
        /** Embedding 接口地址，留空复用大模型地址 */
        private volatile String embeddingBaseUrl = "";
        /** Embedding API Key，留空复用大模型 Key */
        private volatile String embeddingApiKey = "";
        /** Embedding 模型名称 */
        private volatile String embeddingModel = "text-embedding-3-small";
        /** 检索返回的片段数 */
        private volatile int topK = 5;
        /** 最低余弦相似度 */
        private volatile double scoreThreshold = 0.25;
        /** 拼进 prompt 的参考资料最大字符数 */
        private volatile int maxContextChars = 6000;
        /** 文档分块大小（字符） */
        private volatile int chunkSize = 600;
        /** 相邻块重叠字符数 */
        private volatile int chunkOverlap = 100;

        /** 整站抓取最大页数 */
        private volatile int crawlMaxPages = 1000;
        /** 整站抓取最大深度 */
        private volatile int crawlMaxDepth = 5;
        /** 单页最大响应字节数 */
        private volatile int crawlMaxPageBytes = 5 * 1024 * 1024;
        /** 整站最大抓取总字节数 */
        private volatile long crawlMaxTotalBytes = 200L * 1024 * 1024;
        /** 整站抓取最大时长（秒） */
        private volatile int crawlMaxDurationSeconds = 600;
        /** 两次请求的最小间隔（毫秒） */
        private volatile int crawlIntervalMs = 300;
        /** 是否允许抓取内网/私有地址 */
        private volatile boolean crawlAllowPrivateHosts = false;
        /** 是否遵守 robots.txt */
        private volatile boolean crawlRespectRobots = true;
        /** 抓取 User-Agent */
        private volatile String crawlUserAgent = "WildfireRAG/1.0";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEmbeddingBaseUrl() {
            return embeddingBaseUrl;
        }

        public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
            this.embeddingBaseUrl = embeddingBaseUrl;
        }

        public String getEmbeddingApiKey() {
            return embeddingApiKey;
        }

        public void setEmbeddingApiKey(String embeddingApiKey) {
            this.embeddingApiKey = embeddingApiKey;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getScoreThreshold() {
            return scoreThreshold;
        }

        public void setScoreThreshold(double scoreThreshold) {
            this.scoreThreshold = scoreThreshold;
        }

        public int getMaxContextChars() {
            return maxContextChars;
        }

        public void setMaxContextChars(int maxContextChars) {
            this.maxContextChars = maxContextChars;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }

        public int getCrawlMaxPages() {
            return crawlMaxPages;
        }

        public void setCrawlMaxPages(int crawlMaxPages) {
            this.crawlMaxPages = crawlMaxPages;
        }

        public int getCrawlMaxDepth() {
            return crawlMaxDepth;
        }

        public void setCrawlMaxDepth(int crawlMaxDepth) {
            this.crawlMaxDepth = crawlMaxDepth;
        }

        public int getCrawlMaxPageBytes() {
            return crawlMaxPageBytes;
        }

        public void setCrawlMaxPageBytes(int crawlMaxPageBytes) {
            this.crawlMaxPageBytes = crawlMaxPageBytes;
        }

        public long getCrawlMaxTotalBytes() {
            return crawlMaxTotalBytes;
        }

        public void setCrawlMaxTotalBytes(long crawlMaxTotalBytes) {
            this.crawlMaxTotalBytes = crawlMaxTotalBytes;
        }

        public int getCrawlMaxDurationSeconds() {
            return crawlMaxDurationSeconds;
        }

        public void setCrawlMaxDurationSeconds(int crawlMaxDurationSeconds) {
            this.crawlMaxDurationSeconds = crawlMaxDurationSeconds;
        }

        public int getCrawlIntervalMs() {
            return crawlIntervalMs;
        }

        public void setCrawlIntervalMs(int crawlIntervalMs) {
            this.crawlIntervalMs = crawlIntervalMs;
        }

        public boolean isCrawlAllowPrivateHosts() {
            return crawlAllowPrivateHosts;
        }

        public void setCrawlAllowPrivateHosts(boolean crawlAllowPrivateHosts) {
            this.crawlAllowPrivateHosts = crawlAllowPrivateHosts;
        }

        public boolean isCrawlRespectRobots() {
            return crawlRespectRobots;
        }

        public void setCrawlRespectRobots(boolean crawlRespectRobots) {
            this.crawlRespectRobots = crawlRespectRobots;
        }

        public String getCrawlUserAgent() {
            return crawlUserAgent;
        }

        public void setCrawlUserAgent(String crawlUserAgent) {
            this.crawlUserAgent = crawlUserAgent;
        }
    }
}
