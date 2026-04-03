package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.open;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAuditService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/wechathlink/open")
public class WechathlinkOpenApiController {

    private final WechathlinkAccountServiceImpl accountService;
    private final WechathlinkEventService eventService;
    private final WechathlinkMessageService messageService;
    private final WechathlinkSettingsService settingsService;
    private final WechathlinkAuditService auditService;
    private final WechathlinkQrCodeSupport qrCodeSupport;

    public WechathlinkOpenApiController(WechathlinkAccountServiceImpl accountService,
                                        WechathlinkEventService eventService,
                                        WechathlinkMessageService messageService,
                                        WechathlinkSettingsService settingsService,
                                        WechathlinkAuditService auditService,
                                        WechathlinkQrCodeSupport qrCodeSupport) {
        this.accountService = accountService;
        this.eventService = eventService;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.qrCodeSupport = qrCodeSupport;
    }

    @PostMapping("/accounts/login/start")
    public Map<String, Object> startLogin(@RequestBody(required = false) Map<String, Object> body) {
        return executeOpenAction(
                null,
                "OPEN_LOGIN_START",
                "open login session started",
                () -> accountService.startLogin(body),
                sanitizeBody(body)
        );
    }

    @GetMapping("/accounts/login/status")
    public Map<String, Object> loginStatus(@RequestParam String sessionCode) {
        return executeOpenAction(
                null,
                "OPEN_LOGIN_STATUS",
                "open login status queried",
                () -> accountService.loginStatus(sessionCode),
                Map.of("sessionCode", sessionCode)
        );
    }

    @GetMapping(value = "/accounts/login/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> loginQr(@RequestParam String sessionCode) {
        byte[] png = qrCodeSupport.toPng(accountService.getLoginSession(sessionCode).getQrCodeContent());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping("/accounts")
    public Map<String, Object> accounts(@RequestParam(required = false) String keyword) {
        return executeOpenAction(
                null,
                "OPEN_ACCOUNTS_LIST",
                "open account list queried",
                () -> accountService.list(keyword),
                Map.of("keyword", keyword == null ? "" : keyword)
        );
    }

    @GetMapping("/events")
    public Map<String, Object> events(@RequestParam(required = false) Long wechatAccountId,
                                      @RequestParam(required = false) Long eventId,
                                      @RequestParam(required = false) String contactId,
                                      @RequestParam(required = false) String direction,
                                      @RequestParam(required = false) String eventType,
                                      @RequestParam(required = false) String dateFrom,
                                      @RequestParam(required = false) String dateTo,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer hasMedia,
                                      @RequestParam(required = false) Integer pageNum,
                                      @RequestParam(required = false) Integer pageSize) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("wechatAccountId", wechatAccountId);
        detail.put("eventId", eventId);
        detail.put("contactId", contactId);
        detail.put("direction", direction);
        detail.put("eventType", eventType);
        detail.put("dateFrom", dateFrom);
        detail.put("dateTo", dateTo);
        detail.put("keyword", keyword);
        detail.put("hasMedia", hasMedia);
        detail.put("pageNum", pageNum);
        detail.put("pageSize", pageSize);
        return executeOpenAction(
                wechatAccountId,
                "OPEN_EVENTS_LIST",
                "open events queried",
                () -> eventService.list(wechatAccountId, eventId, contactId, direction, eventType, dateFrom, dateTo, keyword, hasMedia, pageNum, pageSize),
                detail
        );
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return executeOpenAction(
                null,
                "OPEN_SETTINGS_GET",
                "open settings queried",
                settingsService::get,
                Map.of()
        );
    }

    @GetMapping("/messages/peers")
    public Map<String, Object> peers(@RequestParam Long wechatAccountId,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Integer pageNum,
                                     @RequestParam(required = false) Integer pageSize) {
        return executeOpenAction(
                wechatAccountId,
                "OPEN_MESSAGE_PEERS",
                "open message peers queried",
                () -> messageService.listPeers(wechatAccountId, keyword, pageNum, pageSize),
                Map.of(
                        "wechatAccountId", wechatAccountId,
                        "keyword", keyword == null ? "" : keyword,
                        "pageNum", pageNum == null ? "" : String.valueOf(pageNum),
                        "pageSize", pageSize == null ? "" : String.valueOf(pageSize)
                )
        );
    }

    @PostMapping(value = "/messages/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        return executeOpenAction(
                null,
                "OPEN_MESSAGE_UPLOAD",
                "open message media uploaded",
                () -> messageService.uploadTempMedia(file),
                Map.of(
                        "fileName", file == null ? "" : String.valueOf(file.getOriginalFilename()),
                        "size", file == null ? "" : String.valueOf(file.getSize())
                )
        );
    }

    @PostMapping("/messages/send-text")
    public Map<String, Object> sendText(@RequestBody Map<String, Object> body) {
        Long wechatAccountId = body == null || body.get("wechatAccountId") == null ? null : Long.valueOf(String.valueOf(body.get("wechatAccountId")));
        return executeOpenAction(
                wechatAccountId,
                "OPEN_SEND_TEXT",
                "open text message requested",
                () -> messageService.sendText(body),
                sanitizeBody(body)
        );
    }

    @PostMapping("/messages/send-media")
    public Map<String, Object> sendMedia(@RequestBody Map<String, Object> body) {
        Long wechatAccountId = body == null || body.get("wechatAccountId") == null ? null : Long.valueOf(String.valueOf(body.get("wechatAccountId")));
        return executeOpenAction(
                wechatAccountId,
                "OPEN_SEND_MEDIA",
                "open media message requested",
                () -> messageService.sendMedia(body),
                sanitizeBody(body)
        );
    }

    private Map<String, Object> executeOpenAction(Long wechatAccountId,
                                                  String actionType,
                                                  String summary,
                                                  Supplier<Map<String, Object>> supplier,
                                                  Map<String, Object> detail) {
        try {
            Map<String, Object> result = supplier.get();
            Map<String, Object> payload = new LinkedHashMap<>();
            if (detail != null) {
                payload.putAll(detail);
            }
            if (result != null && !result.isEmpty()) {
                payload.put("result", result);
            }
            auditService.recordSuccess(wechatAccountId, actionType, "OPEN_API", "", summary, payload);
            return result;
        } catch (RuntimeException ex) {
            auditService.recordFailure(wechatAccountId, actionType, "OPEN_API", "", summary, ex, detail == null ? Map.of() : detail);
            throw ex;
        }
    }

    private Map<String, Object> sanitizeBody(Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (body == null) {
            return payload;
        }
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey();
            Object value = entry.getValue();
            boolean secret = key.toLowerCase().contains("token") || key.toLowerCase().contains("secret") || key.toLowerCase().contains("password");
            payload.put(key, secret ? "******" : (value == null ? "" : String.valueOf(value)));
        }
        return payload;
    }
}
