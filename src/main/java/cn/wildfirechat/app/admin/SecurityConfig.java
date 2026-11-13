package cn.wildfirechat.app.admin;

import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * 管理后台安全配置：BCrypt 密码、表单登录、CSRF、会话固定防护、登录限流和安全响应头。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                    + "form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * 该过滤器只挂在 Spring Security 过滤链上，避免被 Spring Boot 再全局注册一次。
     */
    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilterRegistration(LoginRateLimitFilter filter) {
        FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LoginRateLimitFilter rateLimitFilter,
                                                   LoginAttemptService loginAttempts,
                                                   UserDetailsService userDetailsService,
                                                   AdminUserRepository adminUserRepository,
                                                   PasswordEncoder passwordEncoder) throws Exception {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        http
                // IM 回调来自内网服务器，不带浏览器会话，不参与 CSRF 校验
                .csrf(csrf -> csrf.ignoringRequestMatchers("/robot/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/admin/login", "/admin.css", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/robot/**").permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider)
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .successHandler((request, response, authentication) -> {
                            loginAttempts.onSuccess(LoginRateLimitFilter.clientIp(request));
                            AdminUser user = adminUserRepository.findByUsername(authentication.getName())
                                    .orElse(null);
                            boolean forceChange = user != null && user.isMustChangePassword();
                            if (forceChange) {
                                request.getSession(true).setAttribute(
                                        ForcePasswordChangeInterceptor.SESSION_KEY, Boolean.TRUE);
                            } else {
                                request.getSession(true).removeAttribute(ForcePasswordChangeInterceptor.SESSION_KEY);
                            }
                            response.sendRedirect(request.getContextPath()
                                    + (forceChange ? "/admin/password?force" : "/admin"));
                        })
                        .failureHandler((request, response, exception) -> {
                            loginAttempts.onFailure(LoginRateLimitFilter.clientIp(request));
                            LOG.warn("管理员登录失败, ip={}, username={}",
                                    request.getRemoteAddr(), sanitize(request.getParameter("username")));
                            response.sendRedirect(request.getContextPath() + "/admin/login?error");
                        })
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("ROBOT_ADMIN_SESSION")
                        .permitAll())
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/admin/login?expired"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .frameOptions(frame -> frame.deny()))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 会话销毁事件用于单会话控制（maximumSessions(1)）。
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * 会话 Cookie 使用 HttpOnly + SameSite=Lax，30 分钟超时。
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> sessionCookieCustomizer() {
        return factory -> {
            factory.addContextCustomizers(context -> {
                Rfc6265CookieProcessor cookieProcessor = new Rfc6265CookieProcessor();
                cookieProcessor.setSameSiteCookies("lax");
                context.setCookieProcessor(cookieProcessor);
            });
            factory.addInitializers(servletContext -> {
                servletContext.getSessionCookieConfig().setName("ROBOT_ADMIN_SESSION");
                servletContext.getSessionCookieConfig().setHttpOnly(true);
                servletContext.setSessionTimeout(30);
            });
        };
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\r', '_').replace('\n', '_');
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
