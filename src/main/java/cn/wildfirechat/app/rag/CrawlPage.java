package cn.wildfirechat.app.rag;

import java.util.Collections;
import java.util.List;

/**
 * 抓取到的一页内容。
 */
public class CrawlPage {
    private final String url;
    private final String title;
    private final String text;
    private final List<String> links;
    private final long bytes;
    private final String contentType;

    public CrawlPage(String url, String title, String text, List<String> links,
                     long bytes, String contentType) {
        this.url = url;
        this.title = title == null ? "" : title;
        this.text = text == null ? "" : text;
        this.links = links == null ? Collections.emptyList() : links;
        this.bytes = bytes;
        this.contentType = contentType == null ? "" : contentType;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public List<String> getLinks() {
        return links;
    }

    public long getBytes() {
        return bytes;
    }

    public String getContentType() {
        return contentType;
    }
}
