package cn.wildfirechat.app.chat;

import java.util.Collections;
import java.util.List;

/**
 * 管理后台消息分页结果。
 */
public class ChatMessagePage {
    private final List<ChatMessage> messages;
    private final int page;
    private final int pageSize;
    private final long total;

    public ChatMessagePage(List<ChatMessage> messages, int page, int pageSize, long total) {
        this.messages = messages == null ? Collections.emptyList() : messages;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotal() {
        return total;
    }

    public int getTotalPages() {
        if (total <= 0) {
            return 1;
        }
        long pages = (total + pageSize - 1) / pageSize;
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }

    public boolean isHasPrev() {
        return page > 1;
    }

    public boolean isHasNext() {
        return page < getTotalPages();
    }
}
