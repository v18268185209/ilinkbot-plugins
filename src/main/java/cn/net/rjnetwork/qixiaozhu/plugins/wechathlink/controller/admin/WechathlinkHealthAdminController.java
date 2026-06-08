package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAccountService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkDashboardService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support.poller.WechathlinkPollerManager;
import com.zqzqq.bootkits.bootstrap.annotation.ResolveClassLoader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件健康检查控制器
 * 提供插件级和账号级的健康检查端点
 */
@RestController
@RequestMapping("/api/wechathlink/admin/health")
public class WechathlinkHealthAdminController {

    private final WechathlinkDashboardService dashboardService;
    private final WechathlinkAccountService accountService;
    private final WechathlinkPollerManager pollerManager;

    public WechathlinkHealthAdminController(WechathlinkDashboardService dashboardService,
                                            WechathlinkAccountService accountService,
                                            WechathlinkPollerManager pollerManager) {
        this.dashboardService = dashboardService;
        this.accountService = accountService;
        this.pollerManager = pollerManager;
    }

    /**
     * 插件全局健康检查
     */
    @GetMapping("/plugin")
    @Operation(summary = "Plugin health check")
    @ResolveClassLoader
    public Map<String, Object> pluginHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("component", "ilinkbot-plugin");
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        // Poller manager health
        health.put("pollerRunningCount", pollerManager.runningCount());
        health.put("pollerHealthy", true);

        // Dashboard summary for overview
        try {
            Map<String, Object> summary = dashboardService.summary();
            health.put("accountCount", summary.get("accountCount"));
            health.put("enabledAccountCount", summary.get("enabledAccountCount"));
            health.put("errorCount", summary.get("errorCount"));
        } catch (Exception ex) {
            health.put("status", "DEGRADED");
            health.put("dashboardError", ex.getMessage());
        }

        health.put("allComponentsUp", health.get("status").equals("UP") && (Boolean) health.get("pollerHealthy"));
        return health;
    }

    /**
     * 账号级健康检查
     */
    @GetMapping("/account")
    @Operation(summary = "Account health check")
    @ResolveClassLoader
    public Map<String, Object> accountHealth(@RequestParam Long accountId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("component", "ilinkbot-plugin-account");
        try {
            Map<String, Object> health = accountService.healthCheck(accountId);
            boolean healthy = (Boolean) health.remove("healthy");
            result.putAll(health);
            result.put("status", healthy ? "UP" : "DOWN");
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception ex) {
            result.put("status", "UNKNOWN");
            result.put("error", ex.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        return result;
    }
}
