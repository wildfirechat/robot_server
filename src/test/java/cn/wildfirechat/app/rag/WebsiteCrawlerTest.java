package cn.wildfirechat.app.rag;

import cn.wildfirechat.app.RobotConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebsiteCrawlerTest {
    private HttpServer server;
    private int port;
    private RobotConfig config;
    private WebCrawler webCrawler;
    private WebsiteCrawler websiteCrawler;

    @BeforeEach
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/robots.txt".equals(path)) {
                send(exchange, 200, "text/plain", "User-agent: *\nDisallow: /private\n");
            } else if ("/".equals(path)) {
                send(exchange, 200, "text/html", "<html><head><title>首页</title></head><body>"
                        + "<a href='/a'>a</a>"
                        + "<a href='/b'>b</a>"
                        + "<a href='/depth1'>depth1</a>"
                        + "<a href='/private'>private</a>"
                        + "<a href='/redirect'>redirect</a>"
                        + "<a href='http://example.com/external'>external</a>"
                        + "<a href='http://localhost:" + port + "/a'>other-host</a>"
                        + "</body></html>");
            } else if ("/a".equals(path)) {
                send(exchange, 200, "text/html", "<html><head><title>A</title></head><body>"
                        + "<a href='/a1'>a1</a></body></html>");
            } else if ("/a1".equals(path)) {
                send(exchange, 200, "text/html", "<html><body>a1 content</body></html>");
            } else if ("/b".equals(path)) {
                send(exchange, 200, "text/html", "<html><body>b content</body></html>");
            } else if ("/depth1".equals(path)) {
                send(exchange, 200, "text/html", "<html><body><a href='/depth2'>d2</a></body></html>");
            } else if ("/depth2".equals(path)) {
                send(exchange, 200, "text/html", "<html><body><a href='/depth3'>d3</a></body></html>");
            } else if ("/depth3".equals(path)) {
                send(exchange, 200, "text/html", "<html><body>too deep</body></html>");
            } else if ("/private".equals(path)) {
                send(exchange, 200, "text/html", "<html><body>private</body></html>");
            } else if ("/redirect".equals(path)) {
                exchange.getResponseHeaders().add("Location", "/a");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                send(exchange, 404, "text/plain", "not found");
            }
        });
        server.start();

        config = new RobotConfig();
        RobotConfig.Rag rag = config.getRag();
        rag.setCrawlAllowPrivateHosts(true);
        rag.setCrawlRespectRobots(true);
        rag.setCrawlMaxDepth(2);
        rag.setCrawlMaxPages(20);
        rag.setCrawlMaxPageBytes(1024 * 1024);
        rag.setCrawlMaxTotalBytes(10 * 1024 * 1024);
        rag.setCrawlMaxDurationSeconds(60);
        rag.setCrawlIntervalMs(0);
        webCrawler = new WebCrawler(config, new DocumentParser());
        webCrawler.init();
        websiteCrawler = new WebsiteCrawler(webCrawler);
    }

    @AfterEach
    public void tearDown() throws Exception {
        server.stop(0);
        webCrawler.destroy();
    }

    @Test
    public void crawlsOnlySameHostAndNoSubdomains() throws Exception {
        TestHandler handler = new TestHandler();
        WebsiteCrawler.CrawlStats stats = websiteCrawler.crawl(root(), config.getRag(), handler);

        assertTrue(stats.getPages() >= 5, "should crawl root and same-host links");
        assertTrue(handler.urls.stream().anyMatch(u -> u.endsWith("/a")));
        assertTrue(handler.urls.stream().anyMatch(u -> u.endsWith("/a1")));
        assertTrue(handler.urls.stream().anyMatch(u -> u.endsWith("/b")));
        assertFalse(handler.urls.stream().anyMatch(u -> u.contains("example.com")));
        assertFalse(handler.urls.stream().anyMatch(u -> u.contains("localhost")));
        assertFalse(handler.urls.stream().anyMatch(u -> u.endsWith("/private")), "robots.txt should block");
        assertFalse(handler.urls.stream().anyMatch(u -> u.endsWith("/depth3")), "depth must be limited");
    }

    @Test
    public void maxPagesStopsCrawl() throws Exception {
        config.getRag().setCrawlMaxPages(2);
        TestHandler handler = new TestHandler();
        WebsiteCrawler.CrawlStats stats = websiteCrawler.crawl(root(), config.getRag(), handler);

        assertEquals(2, stats.getPages());
        assertTrue(stats.getStopReason() != null && stats.getStopReason().contains("最大页数"));
    }

    @Test
    public void privateHostIsBlockedByDefault() throws Exception {
        config.getRag().setCrawlAllowPrivateHosts(false);
        TestHandler handler = new TestHandler();
        WebsiteCrawler.CrawlStats stats = websiteCrawler.crawl(root(), config.getRag(), handler);

        assertEquals(0, stats.getPages());
        assertTrue(handler.urls.isEmpty());
    }

    private String root() {
        return "http://127.0.0.1:" + port + "/";
    }

    private static void send(com.sun.net.httpserver.HttpExchange exchange, int status,
                             String contentType, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static class TestHandler implements WebsiteCrawler.PageHandler {
        private final List<String> urls = new ArrayList<>();

        @Override
        public void handle(CrawlPage page) {
            urls.add(page.getUrl());
        }
    }
}
