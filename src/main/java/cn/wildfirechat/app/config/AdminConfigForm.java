package cn.wildfirechat.app.config;

import cn.wildfirechat.app.RobotConfig;

/**
 * 管理后台配置表单。密钥类字段不回显，留空表示保持原值。
 */
public class AdminConfigForm {
    private String imId;
    private String imName;
    private String imUrl;
    private String imSecret;
    private int retentionDays;

    private boolean llmEnabled;
    private String llmBaseUrl;
    private String llmApiKey;
    private boolean clearApiKey;
    private String llmModel;
    private String llmSystemPrompt;
    private double llmTemperature;
    private int llmMaxTokens;
    private int llmTimeoutSeconds;
    private int llmHistoryLimit;
    private boolean llmStream;
    private long llmStreamIntervalMs;

    private boolean ragEnabled;
    private String ragEmbeddingBaseUrl;
    private String ragEmbeddingApiKey;
    private String ragEmbeddingModel = "text-embedding-3-small";
    private int ragTopK = 5;
    private double ragScoreThreshold = 0.25;
    private int ragMaxContextChars = 6000;
    private int ragChunkSize = 600;
    private int ragChunkOverlap = 100;

    private int crawlMaxPages = 1000;
    private int crawlMaxDepth = 5;
    private int crawlMaxPageBytes = 5 * 1024 * 1024;
    private long crawlMaxTotalBytes = 200L * 1024 * 1024;
    private int crawlMaxDurationSeconds = 600;
    private int crawlIntervalMs = 300;
    private boolean crawlAllowPrivateHosts;
    private boolean crawlRespectRobots = true;
    private String crawlUserAgent = "WildfireRAG/1.0";

    public static AdminConfigForm from(RobotConfig config) {
        AdminConfigForm form = new AdminConfigForm();
        form.setImId(config.getIm_id());
        form.setImName(config.getIm_name());
        form.setImUrl(config.getIm_url());
        form.setImSecret("");
        form.setRetentionDays(config.getRetentionDays());
        RobotConfig.Llm llm = config.getLlm();
        form.setLlmEnabled(llm.isEnabled());
        form.setLlmBaseUrl(llm.getBaseUrl());
        form.setLlmApiKey("");
        form.setClearApiKey(false);
        form.setLlmModel(llm.getModel());
        form.setLlmSystemPrompt(llm.getSystemPrompt());
        form.setLlmTemperature(llm.getTemperature());
        form.setLlmMaxTokens(llm.getMaxTokens());
        form.setLlmTimeoutSeconds(llm.getTimeoutSeconds());
        form.setLlmHistoryLimit(llm.getHistoryLimit());
        form.setLlmStream(llm.isStream());
        form.setLlmStreamIntervalMs(llm.getStreamIntervalMs());

        RobotConfig.Rag rag = config.getRag();
        form.setRagEnabled(rag.isEnabled());
        form.setRagEmbeddingBaseUrl(rag.getEmbeddingBaseUrl());
        form.setRagEmbeddingApiKey("");
        form.setRagEmbeddingModel(rag.getEmbeddingModel());
        form.setRagTopK(rag.getTopK());
        form.setRagScoreThreshold(rag.getScoreThreshold());
        form.setRagMaxContextChars(rag.getMaxContextChars());
        form.setRagChunkSize(rag.getChunkSize());
        form.setRagChunkOverlap(rag.getChunkOverlap());
        form.setCrawlMaxPages(rag.getCrawlMaxPages());
        form.setCrawlMaxDepth(rag.getCrawlMaxDepth());
        form.setCrawlMaxPageBytes(rag.getCrawlMaxPageBytes());
        form.setCrawlMaxTotalBytes(rag.getCrawlMaxTotalBytes());
        form.setCrawlMaxDurationSeconds(rag.getCrawlMaxDurationSeconds());
        form.setCrawlIntervalMs(rag.getCrawlIntervalMs());
        form.setCrawlAllowPrivateHosts(rag.isCrawlAllowPrivateHosts());
        form.setCrawlRespectRobots(rag.isCrawlRespectRobots());
        form.setCrawlUserAgent(rag.getCrawlUserAgent());
        return form;
    }

    public String getImId() {
        return imId;
    }

    public void setImId(String imId) {
        this.imId = imId;
    }

    public String getImName() {
        return imName;
    }

    public void setImName(String imName) {
        this.imName = imName;
    }

    public String getImUrl() {
        return imUrl;
    }

    public void setImUrl(String imUrl) {
        this.imUrl = imUrl;
    }

    public String getImSecret() {
        return imSecret;
    }

    public void setImSecret(String imSecret) {
        this.imSecret = imSecret;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public String getLlmBaseUrl() {
        return llmBaseUrl;
    }

    public void setLlmBaseUrl(String llmBaseUrl) {
        this.llmBaseUrl = llmBaseUrl;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public void setLlmApiKey(String llmApiKey) {
        this.llmApiKey = llmApiKey;
    }

    public boolean isClearApiKey() {
        return clearApiKey;
    }

    public void setClearApiKey(boolean clearApiKey) {
        this.clearApiKey = clearApiKey;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getLlmSystemPrompt() {
        return llmSystemPrompt;
    }

    public void setLlmSystemPrompt(String llmSystemPrompt) {
        this.llmSystemPrompt = llmSystemPrompt;
    }

    public double getLlmTemperature() {
        return llmTemperature;
    }

    public void setLlmTemperature(double llmTemperature) {
        this.llmTemperature = llmTemperature;
    }

    public int getLlmMaxTokens() {
        return llmMaxTokens;
    }

    public void setLlmMaxTokens(int llmMaxTokens) {
        this.llmMaxTokens = llmMaxTokens;
    }

    public int getLlmTimeoutSeconds() {
        return llmTimeoutSeconds;
    }

    public void setLlmTimeoutSeconds(int llmTimeoutSeconds) {
        this.llmTimeoutSeconds = llmTimeoutSeconds;
    }

    public int getLlmHistoryLimit() {
        return llmHistoryLimit;
    }

    public void setLlmHistoryLimit(int llmHistoryLimit) {
        this.llmHistoryLimit = llmHistoryLimit;
    }

    public boolean isLlmStream() {
        return llmStream;
    }

    public void setLlmStream(boolean llmStream) {
        this.llmStream = llmStream;
    }

    public long getLlmStreamIntervalMs() {
        return llmStreamIntervalMs;
    }

    public void setLlmStreamIntervalMs(long llmStreamIntervalMs) {
        this.llmStreamIntervalMs = llmStreamIntervalMs;
    }

    public boolean isRagEnabled() {
        return ragEnabled;
    }

    public void setRagEnabled(boolean ragEnabled) {
        this.ragEnabled = ragEnabled;
    }

    public String getRagEmbeddingBaseUrl() {
        return ragEmbeddingBaseUrl;
    }

    public void setRagEmbeddingBaseUrl(String ragEmbeddingBaseUrl) {
        this.ragEmbeddingBaseUrl = ragEmbeddingBaseUrl;
    }

    public String getRagEmbeddingApiKey() {
        return ragEmbeddingApiKey;
    }

    public void setRagEmbeddingApiKey(String ragEmbeddingApiKey) {
        this.ragEmbeddingApiKey = ragEmbeddingApiKey;
    }

    public String getRagEmbeddingModel() {
        return ragEmbeddingModel;
    }

    public void setRagEmbeddingModel(String ragEmbeddingModel) {
        this.ragEmbeddingModel = ragEmbeddingModel;
    }

    public int getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(int ragTopK) {
        this.ragTopK = ragTopK;
    }

    public double getRagScoreThreshold() {
        return ragScoreThreshold;
    }

    public void setRagScoreThreshold(double ragScoreThreshold) {
        this.ragScoreThreshold = ragScoreThreshold;
    }

    public int getRagMaxContextChars() {
        return ragMaxContextChars;
    }

    public void setRagMaxContextChars(int ragMaxContextChars) {
        this.ragMaxContextChars = ragMaxContextChars;
    }

    public int getRagChunkSize() {
        return ragChunkSize;
    }

    public void setRagChunkSize(int ragChunkSize) {
        this.ragChunkSize = ragChunkSize;
    }

    public int getRagChunkOverlap() {
        return ragChunkOverlap;
    }

    public void setRagChunkOverlap(int ragChunkOverlap) {
        this.ragChunkOverlap = ragChunkOverlap;
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
