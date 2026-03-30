package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.annotation.WebLayer;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base.WechathlinkBaseController;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkDashboardService;
import cn.net.rjnetwork.qixiaozhu.result.ResultBody;
import com.zqzqq.bootkits.bootstrap.annotation.ResolveClassLoader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wechathlink/admin/dashboard")
public class WechathlinkDashboardAdminController extends WechathlinkBaseController {

    private final WechathlinkDashboardService dashboardService;

    public WechathlinkDashboardAdminController(WechathlinkDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Wechat hlink dashboard summary")
    @WebLayer(name = "Wechat hlink dashboard summary", code = "/api/wechathlink/admin/dashboard/summary")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> summary() {
        return renderSuccess(dashboardService.summary());
    }
}
