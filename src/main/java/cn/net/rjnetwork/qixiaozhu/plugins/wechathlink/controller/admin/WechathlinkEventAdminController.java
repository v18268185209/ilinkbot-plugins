package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.admin;

import cn.net.rjnetwork.qixiaozhu.annotation.WebLayer;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base.WechathlinkBaseController;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
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

    public WechathlinkEventAdminController(WechathlinkEventService eventService) {
        this.eventService = eventService;
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
                                                @RequestParam(required = false) String direction,
                                                @RequestParam(required = false) String eventType,
                                                @RequestParam(required = false) Integer pageNum,
                                                @RequestParam(required = false) Integer pageSize) {
        return renderSuccess(eventService.list(wechatAccountId, direction, eventType, pageNum, pageSize));
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

    @GetMapping("/media")
    public ResponseEntity<Resource> media(@RequestParam Long eventId) throws Exception {
        WechathlinkEvent event = eventService.getReadableEvent(eventId);
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
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(file))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file));
    }
}
