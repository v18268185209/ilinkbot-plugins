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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 账号轮询管理控制器
 * 提供启动/停止轮询和健康检查接口
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
}
