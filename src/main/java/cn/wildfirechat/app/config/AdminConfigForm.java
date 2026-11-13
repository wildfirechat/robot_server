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

    public static AdminConfigForm from(RobotConfig config) {
        AdminConfigForm form = new AdminConfigForm();
        form.setImId(config.getIm_id());
        form.setImName(config.getIm_name());
        form.setImUrl(config.getIm_url());
        form.setImSecret("");
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
}
