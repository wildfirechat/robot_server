package cn.wildfirechat.app.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 首次登录使用随机密码时，强制先修改密码后才能访问其他后台页面。
 */
@Component
public class ForcePasswordChangeInterceptor implements HandlerInterceptor {
    public static final String SESSION_KEY = "FORCE_PASSWORD_CHANGE";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(SESSION_KEY))) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/admin/password") || path.equals("/admin/logout")
                || path.equals("/admin/login") || path.equals("/admin.css") || path.equals("/error")) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/admin/password?force");
        return false;
    }
}
