package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.annotation.WebLayer;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base.WechathlinkBaseController;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAuditService;
import cn.net.rjnetwork.qixiaozhu.result.ResultBody;
import com.zqzqq.bootkits.bootstrap.annotation.ResolveClassLoader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wechathlink/admin/audit")
public class WechathlinkAuditAdminController extends WechathlinkBaseController {

    private final WechathlinkAuditService auditService;

    public WechathlinkAuditAdminController(WechathlinkAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/list")
    @Operation(summary = "Wechat hlink audit list")
    @WebLayer(name = "Wechat hlink audit list", code = "/api/wechathlink/admin/audit/list")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> list(@RequestParam(required = false) Long wechatAccountId,
                                                @RequestParam(required = false) Long operatorUserId,
                                                @RequestParam(required = false) String actionType,
                                                @RequestParam(required = false) String resultStatus,
                                                @RequestParam(required = false) Integer pageNum,
                                                @RequestParam(required = false) Integer pageSize) {
        return renderSuccess(auditService.list(wechatAccountId, operatorUserId, actionType, resultStatus, pageNum, pageSize));
    }

    @GetMapping("/detail")
    @Operation(summary = "Wechat hlink audit detail")
    @WebLayer(name = "Wechat hlink audit detail", code = "/api/wechathlink/admin/audit/detail")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> detail(@RequestParam Long id) {
        return renderSuccess(auditService.detail(id));
    }
}
