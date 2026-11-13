package cn.wildfirechat.app.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 登录接口限流过滤器。
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginAttemptService attempts;

    public LoginRateLimitFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/admin/login".equals(request.getServletPath())
                && attempts.isBlocked(clientIp(request))) {
            response.sendRedirect(request.getContextPath() + "/admin/login?locked");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
