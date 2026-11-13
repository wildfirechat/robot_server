package cn.wildfirechat.app.stats;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理后台统计服务。
 */
@Service
public class StatsService {
    private static final int PREVIEW_LENGTH = 80;
    private static final long CACHE_TTL_MS = 3000;

    private final StatsRepository repository;
    private final Object cacheLock = new Object();
    private volatile DashboardStats cachedStats;
    private volatile long cachedAt;

    public StatsService(StatsRepository repository) {
        this.repository = repository;
    }

    /**
     * 概览统计涉及多条聚合查询，做 3 秒缓存，避免后台频繁刷新时反复扫表。
     */
    public DashboardStats load() {
        long now = System.currentTimeMillis();
        DashboardStats snapshot = cachedStats;
        if (snapshot != null && now - cachedAt < CACHE_TTL_MS) {
            return snapshot;
        }
        synchronized (cacheLock) {
            now = System.currentTimeMillis();
            if (cachedStats != null && now - cachedAt < CACHE_TTL_MS) {
                return cachedStats;
            }
            DashboardStats stats = buildStats();
            cachedStats = stats;
            cachedAt = System.currentTimeMillis();
            return stats;
        }
    }

    private DashboardStats buildStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();

        DashboardStats stats = new DashboardStats();
        stats.setTotalMessages(repository.countAllMessages());
        stats.setTodayUserMessages(repository.countMessagesByRole("user", todayStart));
        stats.setTodayAssistantMessages(repository.countMessagesByRole("assistant", todayStart));
        stats.setTodayLlmCalls(repository.countLlmCallsSince(todayStart));
        stats.setTodayLlmFailures(repository.countLlmFailuresSince(todayStart));
        stats.setTotalConversations(repository.countConversations());
        stats.setTotalLlmCalls(repository.countLlmCalls());
        stats.setTotalLlmFailures(repository.countLlmFailures());
        stats.setAvgLlmDurationMs(Math.round(repository.avgLlmDurationMs()));
        long totalCalls = stats.getTotalLlmCalls();
        stats.setLlmSuccessRate(totalCalls == 0 ? 0
                : (int) Math.round((totalCalls - stats.getTotalLlmFailures()) * 100.0 / totalCalls));

        Map<LocalDate, long[]> messages = repository.dailyMessages(weekStart);
        Map<LocalDate, long[]> llmCalls = repository.dailyLlmCalls(weekStart);
        List<DailyStat> days = new ArrayList<>(7);
        long max = 0;
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long[] messageCounts = messages.getOrDefault(date, new long[2]);
            long[] llmCounts = llmCalls.getOrDefault(date, new long[2]);
            DailyStat day = new DailyStat(date, messageCounts[0], messageCounts[1], llmCounts[0], llmCounts[1]);
            days.add(day);
            max = Math.max(max, Math.max(day.getTotalMessages(), day.getLlmCalls()));
        }
        for (DailyStat day : days) {
            day.setMessagePercent(percent(day.getTotalMessages(), max));
            day.setLlmPercent(percent(day.getLlmCalls(), max));
        }
        stats.setDailyStats(days);

        stats.setRecentConversations(repository.recentConversations(10));
        List<RecentMessage> recentMessages = new ArrayList<>();
        for (RecentMessage message : repository.recentMessages(20)) {
            recentMessages.add(new RecentMessage(message.getConvType(), message.getConvTarget(),
                    message.getRole(), preview(message.getPreview()), message.getTime()));
        }
        stats.setRecentMessages(recentMessages);
        return stats;
    }

    private static int percent(long value, long max) {
        if (max <= 0 || value <= 0) {
            return 0;
        }
        return Math.max(2, (int) Math.round(value * 100.0 / max));
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        text = text.replaceAll(" {2,}", " ");
        if (text.length() > PREVIEW_LENGTH) {
            return text.substring(0, PREVIEW_LENGTH) + "…";
        }
        return text;
    }
}
