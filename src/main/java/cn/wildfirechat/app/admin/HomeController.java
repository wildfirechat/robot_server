package cn.wildfirechat.app.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 根路径跳转到管理后台（未登录时由 Spring Security 跳转登录页）。
 */
@Controller
public class HomeController {
    @GetMapping("/")
    public String home() {
        return "redirect:/admin";
    }
}
