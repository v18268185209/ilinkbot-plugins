package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.annotation.WebLayer;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base.WechathlinkBaseController;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAccountService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkDashboardService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkWebhookService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support.poller.WechathlinkPollerManager;
import cn.net.rjnetwork.qixiaozhu.result.ResultBody;
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
 * 提供插件级、账号级健康检查和 webhook 投递统计
 */
@RestController
@RequestMapping("/api/wechathlink/admin/health")
public class WechathlinkHealthAdminController extends WechathlinkBaseController {

    private final WechathlinkDashboardService dashboardService;
    private final WechathlinkAccountService accountService;
    private final WechathlinkPollerManager pollerManager;
    private final WechathlinkWebhookService webhookService;

    public WechathlinkHealthAdminController(WechathlinkDashboardService dashboardService,
                                            WechathlinkAccountService accountService,
                                            WechathlinkPollerManager pollerManager,
                                            WechathlinkWebhookService webhookService) {
        this.dashboardService = dashboardService;
        this.accountService = accountService;
        this.pollerManager = pollerManager;
        this.webhookService = webhookService;
    }

    /**
     * 插件全局健康检查
     */
    @GetMapping("/plugin")
    @Operation(summary = "Plugin health check")
    @WebLayer(name = "Plugin health check", code = "/api/wechathlink/admin/health/plugin")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> pluginHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("component", "ilinkbot-plugin");

        // Poller manager health
        long runningCount = pollerManager.runningCount();
        health.put("pollerRunningCount", runningCount);
        health.put("pollerHealthy", true);

        // Dashboard summary
        String overallStatus = "UP";
        String dashboardError = null;
        try {
            Map<String, Object> summary = dashboardService.summary();
            health.put("accountCount", summary.get("accountCount"));
            health.put("enabledAccountCount", summary.get("enabledAccountCount"));
            health.put("errorCount", summary.get("errorCount"));
        } catch (Exception ex) {
            overallStatus = "DEGRADED";
            dashboardError = ex.getMessage();
        }

        // Webhook delivery stats
        try {
            WechathlinkWebhookService.WechatWebhookStats ws = webhookService.getStats();
            health.put("webhookTotalDelivered", ws.totalDelivered());
            health.put("webhookSuccessRate", ws.successRate());
            health.put("webhookSuccess", ws.totalSuccess());
            health.put("webhookFailed", ws.totalFailed());
            health.put("webhookTimeout", ws.totalTimeout());
        } catch (Exception ex) {
            health.put("webhookStatus", "UNKNOWN");
        }

        health.put("status", overallStatus);
        health.put("timestamp", System.currentTimeMillis());
        boolean allHealthy = "UP".equals(overallStatus);
        health.put("allComponentsUp", allHealthy);
        
        if (dashboardError != null) {
            health.put("dashboardError", dashboardError);
        }
        return renderSuccess(health);
    }

    /**
     * 账号级健康检查
     */
    @GetMapping("/account")
    @Operation(summary = "Account health check")
    @WebLayer(name = "Account health check", code = "/api/wechathlink/admin/health/account")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> accountHealth(@RequestParam Long accountId) {
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
        return renderSuccess(result);
    }
}
