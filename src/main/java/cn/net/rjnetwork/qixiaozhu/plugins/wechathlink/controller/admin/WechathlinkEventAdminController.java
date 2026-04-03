package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.annotation.WebLayer;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base.WechathlinkBaseController;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAuditService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkEventService;
import cn.net.rjnetwork.qixiaozhu.result.ResultBody;
import com.zqzqq.bootkits.bootstrap.annotation.ResolveClassLoader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/wechathlink/admin/events")
public class WechathlinkEventAdminController extends WechathlinkBaseController {

    private final WechathlinkEventService eventService;
    private final WechathlinkAuditService auditService;

    public WechathlinkEventAdminController(WechathlinkEventService eventService,
                                           WechathlinkAuditService auditService) {
        this.eventService = eventService;
        this.auditService = auditService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Wechat hlink event summary by account")
    @WebLayer(name = "Wechat hlink event summary by account", code = "/api/wechathlink/admin/events/summary")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> summary(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer pageNum,
                                                   @RequestParam(required = false) Integer pageSize) {
        return renderSuccess(eventService.summary(keyword, pageNum, pageSize));
    }

    @GetMapping("/list")
    @Operation(summary = "Wechat hlink event list")
    @WebLayer(name = "Wechat hlink event list", code = "/api/wechathlink/admin/events/list")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> list(@RequestParam(required = false) Long wechatAccountId,
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
        return renderSuccess(eventService.list(wechatAccountId, eventId, contactId, direction, eventType, dateFrom, dateTo, keyword, hasMedia, pageNum, pageSize));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @Operation(summary = "Wechat hlink event export")
    @WebLayer(name = "Wechat hlink event export", code = "/api/wechathlink/admin/events/export")
    @ResolveClassLoader
    public ResponseEntity<byte[]> export(@RequestParam Long wechatAccountId,
                                         @RequestParam(required = false) String contactId,
                                         @RequestParam(required = false) String direction,
                                         @RequestParam(required = false) String eventType,
                                         @RequestParam(required = false) String dateFrom,
                                         @RequestParam(required = false) String dateTo,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer hasMedia) {
        byte[] content = eventService.export(wechatAccountId, contactId, direction, eventType, dateFrom, dateTo, keyword, hasMedia);
        String fileName = "wechathlink-events-" + wechatAccountId + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(content);
    }

    @GetMapping("/contacts")
    @Operation(summary = "Wechat hlink event contacts")
    @WebLayer(name = "Wechat hlink event contacts", code = "/api/wechathlink/admin/events/contacts")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> contacts(@RequestParam Long wechatAccountId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Integer pageNum,
                                                    @RequestParam(required = false) Integer pageSize) {
        return renderSuccess(eventService.contacts(wechatAccountId, keyword, pageNum, pageSize));
    }

    @GetMapping("/dispatches")
    @Operation(summary = "Wechat hlink dispatch list")
    @WebLayer(name = "Wechat hlink dispatch list", code = "/api/wechathlink/admin/events/dispatches")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> dispatches(@RequestParam(required = false) Long wechatAccountId,
                                                      @RequestParam(required = false) Long dispatchId,
                                                      @RequestParam(required = false) String contactId,
                                                      @RequestParam(required = false) String dispatchType,
                                                      @RequestParam(required = false) String dispatchStatus,
                                                      @RequestParam(required = false) String traceId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer pageNum,
                                                      @RequestParam(required = false) Integer pageSize) {
        return renderSuccess(eventService.dispatches(wechatAccountId, dispatchId, contactId, dispatchType, dispatchStatus, traceId, keyword, pageNum, pageSize));
    }

    @GetMapping("/media-assets")
    @Operation(summary = "Wechat hlink media asset list")
    @WebLayer(name = "Wechat hlink media asset list", code = "/api/wechathlink/admin/events/media-assets")
    @ResolveClassLoader
    public ResultBody<Map<String, Object>> mediaAssets(@RequestParam(required = false) Long wechatAccountId,
                                                       @RequestParam(required = false) Long assetId,
                                                       @RequestParam(required = false) Long eventId,
                                                       @RequestParam(required = false) Long dispatchId,
                                                       @RequestParam(required = false) String assetType,
                                                       @RequestParam(required = false) String downloadStatus,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer pageNum,
                                                       @RequestParam(required = false) Integer pageSize) {
        return renderSuccess(eventService.mediaAssets(wechatAccountId, assetId, eventId, dispatchId, assetType, downloadStatus, keyword, pageNum, pageSize));
    }

    @GetMapping("/media")
    public ResponseEntity<Resource> media(@RequestParam Long eventId) throws Exception {
        WechathlinkEvent event = null;
        try {
            event = eventService.getReadableEvent(eventId);
            if (event == null || event.getMediaPath() == null || event.getMediaPath().isBlank()) {
                throw new IllegalArgumentException("event media not found");
            }
            Path file = Path.of(event.getMediaPath()).toAbsolutePath().normalize();
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                throw new IllegalArgumentException("event media file not found");
            }
            String fileName = file.getFileName() == null ? "media.bin" : file.getFileName().toString();
            String mimeType = event.getMediaMimeType();
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = Files.probeContentType(file);
            }
            MediaType mediaType = (mimeType == null || mimeType.isBlank())
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(mimeType);
            auditService.recordSuccess(event.getWechatAccountId(), "EVENT_MEDIA_VIEW", "EVENT", event.getId(), "event media viewed",
                    Map.of(
                            "eventId", event.getId(),
                            "eventType", event.getEventType(),
                            "mediaFileName", fileName
                    ));
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(Files.size(file))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build().toString())
                    .body(new FileSystemResource(file));
        } catch (Exception ex) {
            auditService.recordFailure(event == null ? null : event.getWechatAccountId(), "EVENT_MEDIA_VIEW", "EVENT", eventId,
                    "event media view failed", ex,
                    Map.of("eventId", eventId == null ? "" : String.valueOf(eventId)));
            throw ex;
        }
    }
}
