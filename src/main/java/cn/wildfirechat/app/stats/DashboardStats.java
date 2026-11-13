package cn.wildfirechat.app.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台概览数据。
 */
public class DashboardStats {
    private long totalMessages;
    private long todayUserMessages;
    private long todayAssistantMessages;
    private long todayLlmCalls;
    private long todayLlmFailures;
    private long totalConversations;
    private long totalLlmCalls;
    private long totalLlmFailures;
    private long avgLlmDurationMs;
    private int llmSuccessRate;

    private List<DailyStat> dailyStats = new ArrayList<>();
    private List<ConversationSummary> recentConversations = new ArrayList<>();
    private List<RecentMessage> recentMessages = new ArrayList<>();

    public long getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(long totalMessages) {
        this.totalMessages = totalMessages;
    }

    public long getTodayUserMessages() {
        return todayUserMessages;
    }

    public void setTodayUserMessages(long todayUserMessages) {
        this.todayUserMessages = todayUserMessages;
    }

    public long getTodayAssistantMessages() {
        return todayAssistantMessages;
    }

    public void setTodayAssistantMessages(long todayAssistantMessages) {
        this.todayAssistantMessages = todayAssistantMessages;
    }

    public long getTodayMessages() {
        return todayUserMessages + todayAssistantMessages;
    }

    public long getTodayLlmCalls() {
        return todayLlmCalls;
    }

    public void setTodayLlmCalls(long todayLlmCalls) {
        this.todayLlmCalls = todayLlmCalls;
    }

    public long getTodayLlmFailures() {
        return todayLlmFailures;
    }

    public void setTodayLlmFailures(long todayLlmFailures) {
        this.todayLlmFailures = todayLlmFailures;
    }

    public long getTotalConversations() {
        return totalConversations;
    }

    public void setTotalConversations(long totalConversations) {
        this.totalConversations = totalConversations;
    }

    public long getTotalLlmCalls() {
        return totalLlmCalls;
    }

    public void setTotalLlmCalls(long totalLlmCalls) {
        this.totalLlmCalls = totalLlmCalls;
    }

    public long getTotalLlmFailures() {
        return totalLlmFailures;
    }

    public void setTotalLlmFailures(long totalLlmFailures) {
        this.totalLlmFailures = totalLlmFailures;
    }

    public long getAvgLlmDurationMs() {
        return avgLlmDurationMs;
    }

    public void setAvgLlmDurationMs(long avgLlmDurationMs) {
        this.avgLlmDurationMs = avgLlmDurationMs;
    }

    public int getLlmSuccessRate() {
        return llmSuccessRate;
    }

    public void setLlmSuccessRate(int llmSuccessRate) {
        this.llmSuccessRate = llmSuccessRate;
    }

    public List<DailyStat> getDailyStats() {
        return dailyStats;
    }

    public void setDailyStats(List<DailyStat> dailyStats) {
        this.dailyStats = dailyStats;
    }

    public List<ConversationSummary> getRecentConversations() {
        return recentConversations;
    }

    public void setRecentConversations(List<ConversationSummary> recentConversations) {
        this.recentConversations = recentConversations;
    }

    public List<RecentMessage> getRecentMessages() {
        return recentMessages;
    }

    public void setRecentMessages(List<RecentMessage> recentMessages) {
        this.recentMessages = recentMessages;
    }
}
