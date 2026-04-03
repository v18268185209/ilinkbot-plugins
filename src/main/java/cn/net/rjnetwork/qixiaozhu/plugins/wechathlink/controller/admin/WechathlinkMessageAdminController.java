package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.annotation.WebLayer;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base.WechathlinkBaseController;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkMessageService;
import cn.net.rjnetwork.qixiaozhu.result.ResultBody;
import com.zqzqq.bootkits.bootstrap.annotation.ResolveClassLoader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/wechathlink/admin/messages")
public class WechathlinkMessageAdminController extends WechathlinkBaseController {

    private final WechathlinkMessageService messageService;

    public WechathlinkMessageAdminController(WechathlinkMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/peers")
    @Operation(summary = "Wechat hlink message peers")
    @WebLayer(name = "Wechat hlink message peers", code = "/api/wechathlink/admin/messages/peers")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> peers(@RequestParam Long wechatAccountId,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer pageNum,
                                                 @RequestParam(required = false) Integer pageSize) {
        return renderSuccess(messageService.listPeers(wechatAccountId, keyword, pageNum, pageSize));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Wechat hlink upload temp media")
    @WebLayer(name = "Wechat hlink upload temp media", code = "/api/wechathlink/admin/messages/upload")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return renderSuccess(messageService.uploadTempMedia(file));
    }

    @PostMapping("/send-text")
    @Operation(summary = "Wechat hlink send text")
    @WebLayer(name = "Wechat hlink send text", code = "/api/wechathlink/admin/messages/send-text")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> sendText(@RequestBody Map<String, Object> body) {
        return renderSuccess(messageService.sendText(body));
    }

    @PostMapping("/send-media")
    @Operation(summary = "Wechat hlink send media")
    @WebLayer(name = "Wechat hlink send media", code = "/api/wechathlink/admin/messages/send-media")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> sendMedia(@RequestBody Map<String, Object> body) {
        return renderSuccess(messageService.sendMedia(body));
    }

    @PostMapping("/retry-dispatch")
    @Operation(summary = "Wechat hlink retry dispatch")
    @WebLayer(name = "Wechat hlink retry dispatch", code = "/api/wechathlink/admin/messages/retry-dispatch")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> retryDispatch(@RequestBody Map<String, Object> body) {
        Long dispatchId = null;
        if (body != null && body.get("dispatchId") != null) {
            Object value = body.get("dispatchId");
            if (value instanceof Number number) {
                dispatchId = number.longValue();
            } else {
                dispatchId = Long.valueOf(String.valueOf(value));
            }
        }
        return renderSuccess(messageService.retryDispatch(dispatchId));
    }
}
