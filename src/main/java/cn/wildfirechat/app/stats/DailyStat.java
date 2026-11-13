package cn.wildfirechat.app.stats;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 单日统计。
 */
public class DailyStat {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    private final LocalDate date;
    private final long userMessages;
    private final long assistantMessages;
    private final long llmCalls;
    private final long llmFailures;
    private int messagePercent;
    private int llmPercent;

    public DailyStat(LocalDate date, long userMessages, long assistantMessages, long llmCalls, long llmFailures) {
        this.date = date;
        this.userMessages = userMessages;
        this.assistantMessages = assistantMessages;
        this.llmCalls = llmCalls;
        this.llmFailures = llmFailures;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDateText() {
        return date.format(DATE_FORMAT);
    }

    public long getUserMessages() {
        return userMessages;
    }

    public long getAssistantMessages() {
        return assistantMessages;
    }

    public long getTotalMessages() {
        return userMessages + assistantMessages;
    }

    public long getLlmCalls() {
        return llmCalls;
    }

    public long getLlmFailures() {
        return llmFailures;
    }

    public int getMessagePercent() {
        return messagePercent;
    }

    public void setMessagePercent(int messagePercent) {
        this.messagePercent = messagePercent;
    }

    public int getLlmPercent() {
        return llmPercent;
    }

    public void setLlmPercent(int llmPercent) {
        this.llmPercent = llmPercent;
    }
}
