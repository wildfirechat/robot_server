package cn.wildfirechat.app.rag;

import cn.wildfirechat.app.RobotConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单页抓取器：限制同主机、同端口、不自动跳转、防 SSRF、限制响应大小。
 */
@Component
public class WebCrawler {
    private static final Logger LOG = LoggerFactory.getLogger(WebCrawler.class);
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_ROBOTS_BYTES = 512 * 1024;

    private final RobotConfig robotConfig;
    private final DocumentParser documentParser;
    private final Map<String, RobotsRules> robotsCache = new ConcurrentHashMap<>();

    private CloseableHttpClient httpClient;

    public WebCrawler(RobotConfig robotConfig, DocumentParser documentParser) {
        this.robotConfig = robotConfig;
        this.documentParser = documentParser;
    }

    @PostConstruct
    public void init() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(READ_TIMEOUT_MS)
                .build();
        httpClient = HttpClients.custom()
                .disableRedirectHandling()
                .setDnsResolver(this::resolveAddresses)
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(10)
                .setMaxConnPerRoute(2)
                .build();
    }

    @PreDestroy
    public void destroy() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    /**
     * 校验并规范化根地址。
     */
    public URI normalizeRoot(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("地址不能为空");
        }
        URI uri = normalize(URI.create(url.trim()));
        validateBasic(uri);
        return uri;
    }

    public CrawlScope scopeOf(URI root) {
        return CrawlScope.of(root);
    }

    public CrawlPage fetch(String url, CrawlScope scope, RobotConfig.Rag rag) throws Exception {
        RawPage raw = fetchRaw(normalize(URI.create(url)), scope, rag);
        return parse(raw, rag);
    }

    public boolean isAllowed(String url, CrawlScope scope, RobotConfig.Rag rag) {
        if (!rag.isCrawlRespectRobots()) {
            return true;
        }
        try {
            URI uri = normalize(URI.create(url));
            if (!scope.matches(uri)) {
                return false;
            }
            String key = scope.getHost() + ":" + scope.getPort();
            RobotsRules rules = robotsCache.computeIfAbsent(key, ignored -> loadRobots(uri, scope, rag));
            return rules.isAllowed(uri.getPath() == null ? "/" : uri.getPath());
        } catch (Exception e) {
            LOG.debug("robots.txt 检查失败，默认允许: {}", e.getMessage());
            return true;
        }
    }

    private RobotsRules loadRobots(URI anyUri, CrawlScope scope, RobotConfig.Rag rag) {
        try {
            URI robotsUri = normalize(anyUri.resolve("/robots.txt"));
            RawPage raw = fetchRaw(robotsUri, scope, rag);
            return RobotsRules.parse(new String(raw.bytes, StandardCharsets.UTF_8),
                    rag.getCrawlUserAgent());
        } catch (Exception e) {
            LOG.debug("读取 robots.txt 失败，默认允许: {}", e.getMessage());
            return RobotsRules.allowAll();
        }
    }

    private RawPage fetchRaw(URI start, CrawlScope scope, RobotConfig.Rag rag) throws Exception {
        URI current = normalize(start);
        for (int redirects = 0; ; redirects++) {
            validateForRequest(current, scope, rag);
            HttpGet get = new HttpGet(current);
            get.setHeader("User-Agent", rag.getCrawlUserAgent());
            get.setHeader("Accept", "text/html,application/xhtml+xml,text/plain,application/pdf,"
                    + "application/vnd.openxmlformats-officedocument.wordprocessingml.document;q=0.9,*/*;q=0.1");
            get.setHeader("Accept-Encoding", "identity");

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                if (isRedirect(status)) {
                    String location = response.getFirstHeader("Location") == null
                            ? null : response.getFirstHeader("Location").getValue();
                    if (location == null || location.trim().isEmpty()) {
                        throw new IOException("重定向缺少 Location");
                    }
                    if (redirects >= MAX_REDIRECTS) {
                        throw new IOException("重定向次数超过 " + MAX_REDIRECTS);
                    }
                    URI next = normalize(current.resolve(location.trim()));
                    validateForRequest(next, scope, rag);
                    current = next;
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new IOException("HTTP " + status);
                }
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new IOException("响应内容为空");
                }
                long contentLength = entity.getContentLength();
                if (contentLength > rag.getCrawlMaxPageBytes()) {
                    throw new IOException("页面超过单页大小限制");
                }
                byte[] bytes;
                try (InputStream input = entity.getContent()) {
                    bytes = readLimited(input, rag.getCrawlMaxPageBytes());
                }
                String contentType = entity.getContentType() == null
                        ? "" : entity.getContentType().getValue().toLowerCase(Locale.ROOT);
                return new RawPage(current, bytes, contentType);
            }
        }
    }

    private CrawlPage parse(RawPage raw, RobotConfig.Rag rag) throws Exception {
        String contentType = raw.contentType;
        String path = raw.uri.getPath() == null ? "" : raw.uri.getPath().toLowerCase(Locale.ROOT);
        String title = filename(raw.uri);

        if (contentType.contains("text/html") || contentType.contains("application/xhtml")
                || path.endsWith(".html") || path.endsWith(".htm") || path.endsWith("/")) {
            Document document = Jsoup.parse(new ByteArrayInputStream(raw.bytes), null, raw.uri.toString());
            document.select("script,style,noscript,iframe,svg,header,footer,nav,aside,form").remove();
            String pageTitle = document.title();
            if (pageTitle != null && !pageTitle.trim().isEmpty()) {
                title = pageTitle.trim();
            }
            String text = document.body() == null ? "" : document.body().text();
            List<String> links = new ArrayList<>();
            for (Element element : document.select("a[href]")) {
                String href = element.absUrl("href");
                if (href == null || href.isEmpty()) {
                    href = element.attr("href");
                }
                if (href != null && !href.trim().isEmpty()) {
                    links.add(href.trim());
                }
            }
            return new CrawlPage(raw.uri.toString(), title, text, links, raw.bytes.length, raw.contentType);
        }

        if (contentType.contains("text/plain") || contentType.contains("application/json")
                || contentType.contains("text/markdown") || contentType.contains("text/csv")) {
            String text = new String(raw.bytes, StandardCharsets.UTF_8);
            return new CrawlPage(raw.uri.toString(), title, text, Collections.emptyList(),
                    raw.bytes.length, raw.contentType);
        }

        if (contentType.contains("application/pdf") || path.endsWith(".pdf")
                || contentType.contains("wordprocessingml") || path.endsWith(".docx")) {
            DocumentParser.ParsedDocument parsed = documentParser.parse(filename(raw.uri), raw.bytes);
            return new CrawlPage(raw.uri.toString(), title, parsed.getText(), Collections.emptyList(),
                    raw.bytes.length, raw.contentType);
        }

        throw new UnsupportedContentException("不支持的响应类型: " + contentType);
    }

    private void validateForRequest(URI uri, CrawlScope scope, RobotConfig.Rag rag) throws Exception {
        validateBasic(uri);
        if (!scope.matches(uri)) {
            throw new IOException("地址不在允许的站内范围: " + uri);
        }
        if (!rag.isCrawlAllowPrivateHosts()) {
            checkPublicAddresses(uri.getHost());
        }
    }

    private static void validateBasic(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("地址不合法");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("只允许 http/https 地址");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("地址中不允许包含用户名密码");
        }
    }

    private void checkPublicAddresses(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(stripBrackets(host));
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new UnknownHostException("目标地址属于内网/保留地址: " + address.getHostAddress());
            }
        }
    }

    private InetAddress[] resolveAddresses(String host) throws UnknownHostException {
        String value = stripBrackets(host);
        InetAddress[] addresses = InetAddress.getAllByName(value);
        if (!robotConfig.getRag().isCrawlAllowPrivateHosts()) {
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    throw new UnknownHostException("目标地址属于内网/保留地址: " + address.getHostAddress());
                }
            }
        }
        return addresses;
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isBlockedIpv4(bytes, 0);
        }
        if (bytes.length == 16) {
            if ((bytes[0] & 0xfe) == 0xfc) {
                return true;
            }
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff) {
                return isBlockedIpv4(bytes, 12);
            }
            return false;
        }
        return true;
    }

    private static boolean isBlockedIpv4(byte[] bytes, int offset) {
        int first = bytes[offset] & 0xff;
        int second = bytes[offset + 1] & 0xff;
        if (first == 0 || first == 10 || first == 127) {
            return true;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        if (first == 169 && second == 254) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        if (first == 192 && second == 168) {
            return true;
        }
        return first >= 224;
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("响应超过单页大小限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static URI normalize(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), uri.getQuery(), null).normalize();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("地址格式不正确", e);
        }
    }

    private static String filename(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty() || path.endsWith("/")) {
            return "index.html";
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isEmpty() ? "index.html" : name;
    }

    private static String stripBrackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    public static class UnsupportedContentException extends Exception {
        public UnsupportedContentException(String message) {
            super(message);
        }
    }

    private static class RawPage {
        private final URI uri;
        private final byte[] bytes;
        private final String contentType;

        private RawPage(URI uri, byte[] bytes, String contentType) {
            this.uri = uri;
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }

    private static class RobotsRules {
        private final List<String> disallow;

        private RobotsRules(List<String> disallow) {
            this.disallow = disallow;
        }

        static RobotsRules allowAll() {
            return new RobotsRules(Collections.emptyList());
        }

        static RobotsRules parse(String content, String userAgent) {
            List<String> disallow = new ArrayList<>();
            boolean applies = false;
            for (String raw : content.split("\\r?\\n")) {
                String line = raw;
                int comment = line.indexOf('#');
                if (comment >= 0) {
                    line = line.substring(0, comment);
                }
                line = line.trim();
                if (line.isEmpty() || !line.contains(":")) {
                    continue;
                }
                int colon = line.indexOf(':');
                String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if ("user-agent".equals(key)) {
                    String agent = value.toLowerCase(Locale.ROOT);
                    applies = "*".equals(agent) || userAgent.toLowerCase(Locale.ROOT).contains(agent);
                } else if (applies && "disallow".equals(key) && !value.isEmpty()) {
                    disallow.add(value);
                }
            }
            return new RobotsRules(disallow);
        }

        boolean isAllowed(String path) {
            String value = path == null || path.isEmpty() ? "/" : path;
            for (String rule : disallow) {
                if (value.startsWith(rule)) {
                    return false;
                }
            }
            return true;
        }
    }
}
