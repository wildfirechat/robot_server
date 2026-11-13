package cn.wildfirechat.app.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册强制修改密码拦截器。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final ForcePasswordChangeInterceptor forcePasswordChangeInterceptor;

    public WebMvcConfig(ForcePasswordChangeInterceptor forcePasswordChangeInterceptor) {
        this.forcePasswordChangeInterceptor = forcePasswordChangeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(forcePasswordChangeInterceptor).addPathPatterns("/admin/**");
    }
}
