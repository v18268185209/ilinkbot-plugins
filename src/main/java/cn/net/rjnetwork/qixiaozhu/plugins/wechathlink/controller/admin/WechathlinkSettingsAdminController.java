package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.annotation.WebLayer;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base.WechathlinkBaseController;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkSettingsService;
import cn.net.rjnetwork.qixiaozhu.result.ResultBody;
import com.zqzqq.bootkits.bootstrap.annotation.ResolveClassLoader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wechathlink/admin/settings")
public class WechathlinkSettingsAdminController extends WechathlinkBaseController {

    private final WechathlinkSettingsService settingsService;

    public WechathlinkSettingsAdminController(WechathlinkSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/get")
    @Operation(summary = "Wechat hlink settings get")
    @WebLayer(name = "Wechat hlink settings get", code = "/api/wechathlink/admin/settings/get")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> get() {
        return renderSuccess(settingsService.get());
    }

    @PostMapping("/save")
    @Operation(summary = "Wechat hlink settings save")
    @WebLayer(name = "Wechat hlink settings save", code = "/api/wechathlink/admin/settings/save")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        return renderSuccess(settingsService.save(body));
    }
}
