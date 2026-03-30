package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.annotation.WebLayer;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base.WechathlinkBaseController;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl.WechathlinkAccountServiceImpl;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support.qrcode.WechathlinkQrCodeSupport;
import cn.net.rjnetwork.qixiaozhu.result.ResultBody;
import com.zqzqq.bootkits.bootstrap.annotation.ResolveClassLoader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wechathlink/admin")
public class WechathlinkAccountAdminController extends WechathlinkBaseController {

    private final WechathlinkAccountServiceImpl accountService;
    private final WechathlinkQrCodeSupport qrCodeSupport;

    public WechathlinkAccountAdminController(WechathlinkAccountServiceImpl accountService,
                                             WechathlinkQrCodeSupport qrCodeSupport) {
        this.accountService = accountService;
        this.qrCodeSupport = qrCodeSupport;
    }

    @GetMapping("/accounts/list")
    @Operation(summary = "Wechat hlink account list")
    @WebLayer(name = "Wechat hlink account list", code = "/api/wechathlink/admin/accounts/list")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> list(@RequestParam(required = false) String keyword) {
        return renderSuccess(accountService.list(keyword));
    }

    @GetMapping("/accounts/detail")
    @Operation(summary = "Wechat hlink account detail")
    @WebLayer(name = "Wechat hlink account detail", code = "/api/wechathlink/admin/accounts/detail")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> detail(@RequestParam Long id) {
        return renderSuccess(accountService.detail(id));
    }

    @PostMapping("/accounts/save")
    @Operation(summary = "Wechat hlink account save")
    @WebLayer(name = "Wechat hlink account save", code = "/api/wechathlink/admin/accounts/save")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        return renderSuccess(accountService.save(body));
    }

    @PostMapping("/accounts/toggle")
    @Operation(summary = "Wechat hlink account toggle")
    @WebLayer(name = "Wechat hlink account toggle", code = "/api/wechathlink/admin/accounts/toggle")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> toggle(@RequestBody Map<String, Object> body) {
        return renderSuccess(accountService.toggle(
                body.get("id") == null ? null : Long.parseLong(String.valueOf(body.get("id"))),
                body.get("status") == null ? null : Integer.parseInt(String.valueOf(body.get("status")))
        ));
    }

    @PostMapping("/accounts/member/save")
    @Operation(summary = "Wechat hlink account member save")
    @WebLayer(name = "Wechat hlink account member save", code = "/api/wechathlink/admin/accounts/member/save")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> saveMember(@RequestBody Map<String, Object> body) {
        return renderSuccess(accountService.saveMember(body));
    }

    @PostMapping("/login/start")
    @Operation(summary = "Wechat hlink login start")
    @WebLayer(name = "Wechat hlink login start", code = "/api/wechathlink/admin/login/start")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> startLogin(@RequestBody(required = false) Map<String, Object> body) {
        return renderSuccess(accountService.startLogin(body));
    }

    @GetMapping("/login/status")
    @Operation(summary = "Wechat hlink login status")
    @WebLayer(name = "Wechat hlink login status", code = "/api/wechathlink/admin/login/status")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> loginStatus(@RequestParam String sessionCode) {
        return renderSuccess(accountService.loginStatus(sessionCode));
    }

    @GetMapping(value = "/login/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> loginQr(@RequestParam String sessionCode) {
        byte[] png = qrCodeSupport.toPng(accountService.getLoginSession(sessionCode).getQrCodeContent());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }
}
