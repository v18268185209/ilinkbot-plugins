package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.open;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/wechathlink/open")
public class WechathlinkOpenController {

    @GetMapping("/health/live")
    public Map<String, Object> live() {
        return Map.of("ok", true, "timestamp", LocalDateTime.now(), "service", "wechat-hlink");
    }

    @GetMapping("/health/ready")
    public Map<String, Object> ready() {
        return Map.of("ok", true, "timestamp", LocalDateTime.now(), "service", "wechat-hlink");
    }

    @GetMapping("/version")
    public Map<String, Object> version() {
        return Map.of("version", "0.0.1", "service", "wechat-hlink");
    }
}
