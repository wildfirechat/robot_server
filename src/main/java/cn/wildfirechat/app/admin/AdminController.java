package cn.wildfirechat.app.admin;

import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.app.config.AdminConfigForm;
import cn.wildfirechat.app.config.ConfigService;
import cn.wildfirechat.app.rag.KnowledgeIngestService;
import cn.wildfirechat.app.rag.KnowledgeRepository;
import cn.wildfirechat.app.rag.RagResult;
import cn.wildfirechat.app.rag.RagService;
import cn.wildfirechat.app.stats.DashboardStats;
import cn.wildfirechat.app.stats.StatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 管理后台页面：概览统计、配置管理、修改密码。
 */
@Controller
@RequestMapping("/admin")
public class AdminController {
    private final RobotConfig robotConfig;
    private final ConfigService configService;
    private final StatsService statsService;
    private final AdminUserService adminUserService;
    private final KnowledgeIngestService knowledgeIngestService;
    private final KnowledgeRepository knowledgeRepository;
    private final RagService ragService;

    public AdminController(RobotConfig robotConfig, ConfigService configService,
                           StatsService statsService, AdminUserService adminUserService,
                           KnowledgeIngestService knowledgeIngestService,
                           KnowledgeRepository knowledgeRepository, RagService ragService) {
        this.robotConfig = robotConfig;
        this.configService = configService;
        this.statsService = statsService;
        this.adminUserService = adminUserService;
        this.knowledgeIngestService = knowledgeIngestService;
        this.knowledgeRepository = knowledgeRepository;
        this.ragService = ragService;
    }

    @GetMapping
    public String dashboard(Model model) {
        DashboardStats stats = statsService.load();
        RobotConfig.Llm llm = robotConfig.getLlm();
        boolean robotConfigured = StringUtils.hasText(robotConfig.getIm_url())
                && StringUtils.hasText(robotConfig.getIm_id())
                && StringUtils.hasText(robotConfig.getIm_secret());
        boolean llmConfigured = llm.isEnabled() && StringUtils.hasText(llm.getModel());
        model.addAttribute("stats", stats);
        model.addAttribute("robotConfigured", robotConfigured);
        model.addAttribute("llmConfigured", llmConfigured);
        model.addAttribute("robotId", robotConfig.getIm_id());
        model.addAttribute("llmModel", llm.getModel());
        return "admin/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping("/config")
    public String config(Model model) {
        addConfigModel(model, AdminConfigForm.from(robotConfig));
        return "admin/config";
    }

    @PostMapping("/config")
    public String saveConfig(@ModelAttribute("form") AdminConfigForm form, BindingResult bindingResult,
                             Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addConfigModel(model, form);
            model.addAttribute("error", "配置格式不正确，请检查数字项是否填写正确");
            return "admin/config";
        }
        try {
            configService.save(form);
        } catch (IllegalArgumentException e) {
            addConfigModel(model, form);
            model.addAttribute("error", e.getMessage());
            return "admin/config";
        }
        redirectAttributes.addFlashAttribute("message", "配置已保存并立即生效");
        return "redirect:/admin/config";
    }

    @GetMapping("/password")
    public String password(Model model) {
        model.addAttribute("form", new ChangePasswordForm());
        return "admin/password";
    }

    @PostMapping("/password")
    public String changePassword(@ModelAttribute("form") ChangePasswordForm form, Model model,
                                 Authentication authentication, HttpServletRequest request) {
        try {
            adminUserService.changePassword(authentication.getName(),
                    form.getCurrentPassword(), form.getNewPassword(), form.getConfirmPassword());
        } catch (IllegalArgumentException e) {
            model.addAttribute("form", form);
            model.addAttribute("error", e.getMessage());
            return "admin/password";
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return "redirect:/admin/login?changed";
    }

    @GetMapping("/knowledge")
    public String knowledge(Model model) {
        addKnowledgeModel(model, RagResult.empty(), null);
        return "admin/knowledge";
    }

    @PostMapping("/knowledge/text")
    public String ingestKnowledgeText(@RequestParam(value = "title", required = false) String title,
                                      @RequestParam(value = "content", required = false) String content,
                                      RedirectAttributes redirectAttributes) {
        try {
            knowledgeIngestService.ingestText(title, content);
            redirectAttributes.addFlashAttribute("message", "文档已提交处理，请稍后刷新查看状态");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/knowledge";
    }

    @PostMapping("/knowledge/upload")
    public String uploadKnowledge(@RequestParam(value = "title", required = false) String title,
                                  @RequestParam("file") MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        try {
            knowledgeIngestService.ingestFile(title, file);
            redirectAttributes.addFlashAttribute("message", "文件已提交处理，请稍后刷新查看状态");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/knowledge";
    }

    @PostMapping("/knowledge/{id}/delete")
    public String deleteKnowledge(@PathVariable("id") long id, RedirectAttributes redirectAttributes) {
        try {
            knowledgeIngestService.deleteDocument(id);
            redirectAttributes.addFlashAttribute("message", "文档已删除");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/knowledge";
    }

    @PostMapping("/knowledge/reindex")
    public String reindexKnowledge(RedirectAttributes redirectAttributes) {
        try {
            knowledgeIngestService.reloadIndex();
            redirectAttributes.addFlashAttribute("message", "知识库索引已重新加载");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/knowledge";
    }

    @PostMapping("/knowledge/test")
    public String testKnowledge(@RequestParam(value = "query", required = false) String query, Model model) {
        RagResult result = query == null || query.trim().isEmpty()
                ? RagResult.empty() : ragService.retrieve(0, "admin-test", query);
        addKnowledgeModel(model, result, query);
        return "admin/knowledge";
    }

    private void addKnowledgeModel(Model model, RagResult result, String query) {
        model.addAttribute("documents", knowledgeRepository.listDocuments());
        model.addAttribute("ragEnabled", robotConfig.getRag().isEnabled());
        model.addAttribute("chunkCount", knowledgeRepository.countChunks());
        model.addAttribute("testResult", result);
        model.addAttribute("testQuery", query);
    }

    private void addConfigModel(Model model, AdminConfigForm form) {
        model.addAttribute("form", form);
        model.addAttribute("imSecretConfigured", StringUtils.hasText(robotConfig.getIm_secret()));
        model.addAttribute("llmApiKeyConfigured", StringUtils.hasText(robotConfig.getLlm().getApiKey()));
    }
}
