package cn.wildfirechat.app.admin;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库文件上传：服务层限制 50MB，Servlet 上限略大以便返回友好错误。
 */
@Configuration
public class MultipartConfig {
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement("", 55 * 1024 * 1024, 60 * 1024 * 1024, 0);
    }
}
