package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.annotation.WebLayer;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base.WechathlinkBaseController;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAccountService;
import cn.net.rjnetwork.qixiaozhu.result.ResultBody;
import com.zqzqq.bootkits.bootstrap.annotation.ResolveClassLoader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 账号轮询管理控制器
 * 提供启动/停止轮询、健康检查和批量管理接口
 */
@RestController
@RequestMapping("/api/wechathlink/admin/account")
public class WechathlinkAccountPollAdminController extends WechathlinkBaseController {

    private final WechathlinkAccountService accountService;

    public WechathlinkAccountPollAdminController(WechathlinkAccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 启动账号轮询
     */
    @PostMapping("/{id}/poller/start")
    @Operation(summary = "Start account poller")
    @WebLayer(name = "Start account poller", code = "/api/wechathlink/admin/account/{id}/poller/start")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> startPoller(@PathVariable Long id) {
        return renderSuccess(accountService.startPoller(id));
    }

    /**
     * 停止账号轮询
     */
    @PostMapping("/{id}/poller/stop")
    @Operation(summary = "Stop account poller")
    @WebLayer(name = "Stop account poller", code = "/api/wechathlink/admin/account/{id}/poller/stop")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> stopPoller(@PathVariable Long id) {
        return renderSuccess(accountService.stopPoller(id));
    }

    /**
     * 账号健康检查
     */
    @GetMapping("/{id}/health")
    @Operation(summary = "Account health check")
    @WebLayer(name = "Account health check", code = "/api/wechathlink/admin/account/{id}/health")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> healthCheck(@PathVariable Long id) {
        return renderSuccess(accountService.healthCheck(id));
    }

    /**
     * 批量启停账号轮询
     */
    @PostMapping("/batch/poller")
    @Operation(summary = "Batch toggle poller")
    @WebLayer(name = "Batch toggle poller", code = "/api/wechathlink/admin/account/batch/poller")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> batchTogglePoller(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        boolean start = Boolean.TRUE.equals(body.get("start"));
        return renderSuccess(accountService.batchTogglePoller(ids, start));
    }

    /**
     * 批量健康检查
     */
    @GetMapping("/batch/health")
    @Operation(summary = "Batch health check")
    @WebLayer(name = "Batch health check", code = "/api/wechathlink/admin/account/batch/health")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> batchHealthCheck(@RequestParam String ids) {
        List<Long> idList = java.util.Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
        return renderSuccess(accountService.batchHealthCheck(idList));
    }
}
