package cn.wildfirechat.app.rag;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;

/**
 * 整站抓取范围：完全相同的主机名（不含子域名）+ 相同端口。
 */
class CrawlScope {
    private final String host;
    private final int port;

    private CrawlScope(String host, int port) {
        this.host = host;
        this.port = port;
    }

    static CrawlScope of(URI uri) {
        if (uri == null || uri.getHost() == null) {
            throw new IllegalArgumentException("地址中没有有效的主机名");
        }
        return new CrawlScope(normalizeHost(uri.getHost()), effectivePort(uri));
    }

    boolean matches(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return false;
        }
        if (uri.getUserInfo() != null) {
            return false;
        }
        if (!host.equals(normalizeHost(uri.getHost()))) {
            return false;
        }
        int targetPort = effectivePort(uri);
        return port == targetPort || (isStandardPort(port) && isStandardPort(targetPort));
    }

    private static boolean isStandardPort(int port) {
        return port == 80 || port == 443;
    }

    static String normalizeHost(String host) {
        String value = host.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.contains(":")) {
            return value.toLowerCase(Locale.ROOT);
        }
        value = IDN.toASCII(value).toLowerCase(Locale.ROOT);
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    static int effectivePort(URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    String getHost() {
        return host;
    }

    int getPort() {
        return port;
    }
}
