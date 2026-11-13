package cn.wildfirechat.app.admin;

import cn.wildfirechat.app.RobotConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_security;DB_CLOSE_DELAY=-1",
        "ROBOT_ADMIN_PASSWORD=AdminPass12345",
        "ROBOT_CONFIG_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
public class AdminSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RobotConfig robotConfig;

    @Test
    public void unauthenticatedRequestsAreRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login"));
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("机器人管理后台")));
    }

    @Test
    public void loginAndRenderAdminPages() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("运行概览")));
        mockMvc.perform(get("/admin/config").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("配置管理")));
        mockMvc.perform(get("/admin/password").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("修改密码")));
    }

    @Test
    public void saveConfigUpdatesRuntimeConfig() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/admin/config").session(session).with(csrf())
                        .param("imId", "FireRobot")
                        .param("imName", "小火")
                        .param("imUrl", "http://127.0.0.1")
                        .param("imSecret", "robot-secret")
                        .param("llmEnabled", "true")
                        .param("llmBaseUrl", "http://127.0.0.1:8000/v1")
                        .param("llmApiKey", "api-key")
                        .param("llmModel", "test-model")
                        .param("llmSystemPrompt", "你是助手")
                        .param("llmTemperature", "0.5")
                        .param("llmMaxTokens", "1024")
                        .param("llmTimeoutSeconds", "30")
                        .param("llmHistoryLimit", "12")
                        .param("llmStream", "true")
                        .param("llmStreamIntervalMs", "300"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/config"));

        assertEquals("test-model", robotConfig.getLlm().getModel());
        assertEquals("robot-secret", robotConfig.getIm_secret());
        assertEquals(12, robotConfig.getLlm().getHistoryLimit());
        assertTrue(robotConfig.getLlm().isEnabled());
    }

    @Test
    public void configPostWithoutCsrfIsRejected() throws Exception {
        MockHttpSession session = login();
        mockMvc.perform(post("/admin/config").session(session)
                        .param("imId", "FireRobot")
                        .param("imUrl", "http://127.0.0.1")
                        .param("imSecret", "robot-secret")
                        .param("llmBaseUrl", "http://127.0.0.1:8000/v1")
                        .param("llmModel", "test-model")
                        .param("llmTemperature", "0.5")
                        .param("llmMaxTokens", "1024")
                        .param("llmTimeoutSeconds", "30")
                        .param("llmHistoryLimit", "12")
                        .param("llmStreamIntervalMs", "300"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/admin/login").with(csrf())
                        .param("username", "admin")
                        .param("password", "WrongPass12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error"));
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/login").with(csrf())
                        .param("username", "admin")
                        .param("password", "AdminPass12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
