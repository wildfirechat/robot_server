package cn.wildfirechat.app.llm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试用的 OpenAI 兼容接口桩服务，记录请求内容并返回预设响应。
 */
class TestLlmServer implements Closeable {
    private final HttpServer server;

    final List<String> requestBodies = new CopyOnWriteArrayList<>();
    final List<String> requestPaths = new CopyOnWriteArrayList<>();
    final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();

    volatile String responseBody = "";
    volatile String contentType = "text/event-stream; charset=utf-8";
    volatile int status = 200;

    TestLlmServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestBodies.add(readAll(exchange.getRequestBody()));
                requestPaths.add(exchange.getRequestURI().getPath());
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                authorizationHeaders.add(authorization == null ? "" : authorization);

                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(bytes);
                }
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private static String readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
