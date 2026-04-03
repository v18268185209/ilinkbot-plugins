package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.annotation.WebLayer;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base.WechathlinkBaseController;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkPlatformService;
import cn.net.rjnetwork.qixiaozhu.result.ResultBody;
import com.zqzqq.bootkits.bootstrap.annotation.ResolveClassLoader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wechathlink/admin/platform")
public class WechathlinkPlatformAdminController extends WechathlinkBaseController {

    private final WechathlinkPlatformService platformService;

    public WechathlinkPlatformAdminController(WechathlinkPlatformService platformService) {
        this.platformService = platformService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Wechat hlink platform summary")
    @WebLayer(name = "Wechat hlink platform summary", code = "/api/wechathlink/admin/platform/summary")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> summary() {
        return renderSuccess(platformService.summary());
    }

    @GetMapping("/requests")
    @Operation(summary = "Wechat hlink open request logs")
    @WebLayer(name = "Wechat hlink open request logs", code = "/api/wechathlink/admin/platform/requests")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> requests(@RequestParam(required = false) String actionType,
                                                    @RequestParam(required = false) String resultStatus,
                                                    @RequestParam(required = false) Integer pageNum,
                                                    @RequestParam(required = false) Integer pageSize) {
        return renderSuccess(platformService.requestLogs(actionType, resultStatus, pageNum, pageSize));
    }

    @GetMapping("/deliveries")
    @Operation(summary = "Wechat hlink webhook deliveries")
    @WebLayer(name = "Wechat hlink webhook deliveries", code = "/api/wechathlink/admin/platform/deliveries")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> deliveries(@RequestParam(required = false) Long wechatAccountId,
                                                      @RequestParam(required = false) String deliveryStatus,
                                                      @RequestParam(required = false) Integer pageNum,
                                                      @RequestParam(required = false) Integer pageSize) {
        return renderSuccess(platformService.webhookDeliveries(wechatAccountId, deliveryStatus, pageNum, pageSize));
    }

    @GetMapping("/deliveries/detail")
    @Operation(summary = "Wechat hlink webhook delivery detail")
    @WebLayer(name = "Wechat hlink webhook delivery detail", code = "/api/wechathlink/admin/platform/deliveries/detail")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> deliveryDetail(@RequestParam Long id) {
        return renderSuccess(platformService.webhookDeliveryDetail(id));
    }

    @PostMapping("/deliveries/retry")
    @Operation(summary = "Wechat hlink webhook delivery retry")
    @WebLayer(name = "Wechat hlink webhook delivery retry", code = "/api/wechathlink/admin/platform/deliveries/retry")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> retryDelivery(@RequestBody Map<String, Object> body) {
        Long id = null;
        if (body != null && body.get("id") != null) {
            Object value = body.get("id");
            if (value instanceof Number number) {
                id = number.longValue();
            } else {
                id = Long.valueOf(String.valueOf(value));
            }
        }
        return renderSuccess(platformService.retryWebhookDelivery(id));
    }
}
