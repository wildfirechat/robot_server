package cn.wildfirechat.app.rag;

import cn.wildfirechat.app.RobotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 整站抓取：只抓输入地址的完全相同主机、相同端口（允许 80/443 互跳），不抓子域名和外部链接。
 * BFS 深度优先受限，默认最大 5 层、1000 页。
 */
@Component
public class WebsiteCrawler {
    private static final Logger LOG = LoggerFactory.getLogger(WebsiteCrawler.class);
    private static final Set<String> SKIP_EXTENSIONS = new HashSet<>();

    static {
        for (String ext : new String[]{"jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "css", "js",
                "zip", "rar", "7z", "gz", "tar", "mp3", "mp4", "avi", "mov", "wmv", "flv",
                "exe", "dmg", "iso", "woff", "woff2", "ttf", "eot"}) {
            SKIP_EXTENSIONS.add(ext);
        }
    }

    private final WebCrawler webCrawler;

    public WebsiteCrawler(WebCrawler webCrawler) {
        this.webCrawler = webCrawler;
    }

    public URI normalizeRoot(String url) {
        return webCrawler.normalizeRoot(url);
    }

    public CrawlStats crawl(String rootUrl, RobotConfig.Rag rag, PageHandler handler) throws Exception {
        URI root = webCrawler.normalizeRoot(rootUrl);
        CrawlScope scope = webCrawler.scopeOf(root);
        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + Math.max(60, rag.getCrawlMaxDurationSeconds()) * 1000L;

        Deque<UrlDepth> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new UrlDepth(root, 0));
        visited.add(key(root));

        int requests = 0;
        int pages = 0;
        long totalBytes = 0;
        String stopReason = null;

        while (!queue.isEmpty()) {
            if (requests >= rag.getCrawlMaxPages()) {
                stopReason = "达到最大页数 " + rag.getCrawlMaxPages();
                break;
            }
            if (System.currentTimeMillis() > deadline) {
                stopReason = "达到最大抓取时长";
                break;
            }
            UrlDepth current = queue.poll();
            if (current == null) {
                break;
            }
            if (requests > 0 && rag.getCrawlIntervalMs() > 0) {
                try {
                    Thread.sleep(rag.getCrawlIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stopReason = "抓取被中断";
                    break;
                }
            }
            requests++;
            if (!webCrawler.isAllowed(current.uri.toString(), scope, rag)) {
                LOG.debug("robots.txt 禁止抓取: {}", current.uri);
                continue;
            }

            CrawlPage page;
            try {
                page = webCrawler.fetch(current.uri.toString(), scope, rag);
            } catch (WebCrawler.UnsupportedContentException e) {
                LOG.debug("跳过不支持的内容: {} ({})", current.uri, e.getMessage());
                continue;
            } catch (Exception e) {
                LOG.warn("抓取失败, url:{}, depth:{}, error:{}", current.uri, current.depth, e.getMessage());
                continue;
            }
            pages++;
            totalBytes += page.getBytes();
            handler.handle(page);

            if (totalBytes > rag.getCrawlMaxTotalBytes()) {
                stopReason = "达到整站最大字节数";
                break;
            }
            if (current.depth >= rag.getCrawlMaxDepth()) {
                continue;
            }
            int added = 0;
            for (String link : page.getLinks()) {
                if (added >= 500) {
                    break;
                }
                URI next;
                try {
                    next = normalizeLink(current.uri, link);
                } catch (Exception e) {
                    continue;
                }
                if (next == null || !scope.matches(next) || isSkipped(next)) {
                    continue;
                }
                String key = key(next);
                if (visited.add(key)) {
                    queue.add(new UrlDepth(next, current.depth + 1));
                    added++;
                }
            }
        }

        CrawlStats stats = new CrawlStats(pages, requests, totalBytes, stopReason);
        LOG.info("整站抓取结束, root:{}, pages:{}, requests:{}, bytes:{}, stop:{}",
                root, pages, requests, totalBytes, stopReason);
        return stats;
    }

    private static URI normalizeLink(URI base, String href) throws Exception {
        String value = href.trim();
        if (value.isEmpty() || value.startsWith("#") || value.startsWith("javascript:")
                || value.startsWith("mailto:") || value.startsWith("tel:")) {
            return null;
        }
        URI resolved = base.resolve(value);
        String scheme = resolved.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || resolved.getHost() == null) {
            return null;
        }
        return new URI(resolved.getScheme(), null, resolved.getHost(), resolved.getPort(),
                resolved.getPath(), resolved.getQuery(), null).normalize();
    }

    private static boolean isSkipped(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return false;
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot < path.lastIndexOf('/')) {
            return false;
        }
        return SKIP_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String key(URI uri) {
        return uri.toString();
    }

    public interface PageHandler {
        void handle(CrawlPage page) throws Exception;
    }

    public static class CrawlStats {
        private final int pages;
        private final int requests;
        private final long bytes;
        private final String stopReason;

        public CrawlStats(int pages, int requests, long bytes, String stopReason) {
            this.pages = pages;
            this.requests = requests;
            this.bytes = bytes;
            this.stopReason = stopReason;
        }

        public int getPages() {
            return pages;
        }

        public int getRequests() {
            return requests;
        }

        public long getBytes() {
            return bytes;
        }

        public String getStopReason() {
            return stopReason;
        }
    }

    private static class UrlDepth {
        private final URI uri;
        private final int depth;

        private UrlDepth(URI uri, int depth) {
            this.uri = uri;
            this.depth = depth;
        }
    }
}
