package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.open;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkEventService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkMessageService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkSettingsService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl.WechathlinkAccountServiceImpl;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support.qrcode.WechathlinkQrCodeSupport;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wechathlink/open")
public class WechathlinkOpenApiController {

    private final WechathlinkAccountServiceImpl accountService;
    private final WechathlinkEventService eventService;
    private final WechathlinkMessageService messageService;
    private final WechathlinkSettingsService settingsService;
    private final WechathlinkQrCodeSupport qrCodeSupport;

    public WechathlinkOpenApiController(WechathlinkAccountServiceImpl accountService,
                                        WechathlinkEventService eventService,
                                        WechathlinkMessageService messageService,
                                        WechathlinkSettingsService settingsService,
                                        WechathlinkQrCodeSupport qrCodeSupport) {
        this.accountService = accountService;
        this.eventService = eventService;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.qrCodeSupport = qrCodeSupport;
    }

    @PostMapping("/accounts/login/start")
    public Map<String, Object> startLogin(@RequestBody(required = false) Map<String, Object> body) {
        return accountService.startLogin(body);
    }

    @GetMapping("/accounts/login/status")
    public Map<String, Object> loginStatus(@RequestParam String sessionCode) {
        return accountService.loginStatus(sessionCode);
    }

    @GetMapping(value = "/accounts/login/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> loginQr(@RequestParam String sessionCode) {
        byte[] png = qrCodeSupport.toPng(accountService.getLoginSession(sessionCode).getQrCodeContent());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping("/accounts")
    public Map<String, Object> accounts(@RequestParam(required = false) String keyword) {
        return accountService.list(keyword);
    }

    @GetMapping("/events")
    public Map<String, Object> events(@RequestParam(required = false) Long wechatAccountId,
                                      @RequestParam(required = false) String direction,
                                      @RequestParam(required = false) String eventType,
                                      @RequestParam(required = false) Integer pageNum,
                                      @RequestParam(required = false) Integer pageSize) {
        return eventService.list(wechatAccountId, direction, eventType, pageNum, pageSize);
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return settingsService.get();
    }

    @GetMapping("/messages/peers")
    public Map<String, Object> peers(@RequestParam Long wechatAccountId,
                                     @RequestParam(required = false) String keyword) {
        return messageService.listPeers(wechatAccountId, keyword);
    }

    @PostMapping("/messages/send-text")
    public Map<String, Object> sendText(@RequestBody Map<String, Object> body) {
        return messageService.sendText(body);
    }

    @PostMapping("/messages/send-media")
    public Map<String, Object> sendMedia(@RequestBody Map<String, Object> body) {
        return messageService.sendMedia(body);
    }
}
