package cn.wildfirechat.app.admin;

import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.app.im.RobotServiceProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ImNameServiceTest {
    private HttpServer server;
    private RobotConfig config;
    private RobotServiceProvider provider;
    private ImNameService nameService;

    @BeforeEach
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            if (path.contains("user_info")) {
                body = "{\"code\":0,\"msg\":\"ok\",\"result\":{\"userId\":\"user1\","
                        + "\"displayName\":\"小明\"}}";
            } else {
                body = "{\"code\":0,\"msg\":\"ok\",\"result\":{\"target_id\":\"group1\","
                        + "\"name\":\"测试群\"}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        config = new RobotConfig();
        config.setIm_id("FireRobot");
        config.setIm_name("小火");
        config.setIm_url("http://127.0.0.1:" + server.getAddress().getPort());
        config.setIm_secret("secret");
        provider = new RobotServiceProvider(config);
        nameService = new ImNameService(provider, config);
    }

    @AfterEach
    public void tearDown() {
        provider.destroy();
        server.stop(0);
    }

    @Test
    public void resolvesUserAndGroupDisplayNames() {
        assertEquals("小明", nameService.userName("user1"));
        assertEquals("测试群", nameService.groupName("group1"));
    }
}
