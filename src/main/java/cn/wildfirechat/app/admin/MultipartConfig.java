package cn.wildfirechat.app.admin;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库文件上传限制：单文件 10MB，请求 12MB。
 */
@Configuration
public class MultipartConfig {
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement("", 10 * 1024 * 1024, 12 * 1024 * 1024, 0);
    }
}
