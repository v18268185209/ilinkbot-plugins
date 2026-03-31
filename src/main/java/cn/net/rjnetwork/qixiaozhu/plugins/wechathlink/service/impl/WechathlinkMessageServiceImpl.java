package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkLog;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkPeerContext;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkEventMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkLogMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkPeerContextMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAuditService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkMessageService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink.IlinkApi;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink.IlinkModels;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WechathlinkMessageServiceImpl extends WechathlinkServiceSupport implements WechathlinkMessageService {

    private final WechathlinkAccountMapper accountMapper;
    private final WechathlinkEventMapper eventMapper;
    private final WechathlinkLogMapper logMapper;
    private final WechathlinkPeerContextMapper peerContextMapper;
    private final WechathlinkPermissionService permissionService;
    private final WechathlinkRuntimeConfigService runtimeConfigService;
    private final IlinkApi ilinkClient;
    private final WechathlinkAuditService auditService;

    public WechathlinkMessageServiceImpl(WechathlinkAccountMapper accountMapper,
                                         WechathlinkEventMapper eventMapper,
                                         WechathlinkLogMapper logMapper,
                                         WechathlinkPeerContextMapper peerContextMapper,
                                         WechathlinkPermissionService permissionService,
                                         WechathlinkRuntimeConfigService runtimeConfigService,
                                         IlinkApi ilinkClient,
                                         WechathlinkAuditService auditService) {
        this.accountMapper = accountMapper;
        this.eventMapper = eventMapper;
        this.logMapper = logMapper;
        this.peerContextMapper = peerContextMapper;
        this.permissionService = permissionService;
        this.runtimeConfigService = runtimeConfigService;
        this.ilinkClient = ilinkClient;
        this.auditService = auditService;
    }

    @Override
    public Map<String, Object> listPeers(Long wechatAccountId, String keyword, Integer pageNum, Integer pageSize) {
        WechathlinkAccount account = requireReadableAccount(wechatAccountId);
        Page<WechathlinkPeerContext> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<WechathlinkPeerContext> wrapper = new LambdaQueryWrapper<WechathlinkPeerContext>()
                .eq(WechathlinkPeerContext::getWechatAccountId, account.getId())
                .eq(WechathlinkPeerContext::getIsDeleted, 0);
        if (StringUtils.hasText(account.getAccountCode())) {
            wrapper.ne(WechathlinkPeerContext::getPeerUserId, account.getAccountCode().trim());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(WechathlinkPeerContext::getPeerUserId, keyword.trim());
        }
        Page<WechathlinkPeerContext> result = peerContextMapper.selectPage(
                page,
                wrapper.orderByDesc(WechathlinkPeerContext::getLastMessageAt)
                        .orderByDesc(WechathlinkPeerContext::getUpdateTime)
                        .orderByDesc(WechathlinkPeerContext::getId)
        );
        List<Map<String, Object>> rows = result.getRecords().stream()
                .map(this::toPeerView)
                .toList();
        return Map.of(
                "list", rows,
                "total", result.getTotal(),
                "pageNum", result.getCurrent(),
                "pageSize", result.getSize()
        );
    }

    @Override
    public Map<String, Object> uploadTempMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("upload file required");
        }
        try {
            var runtime = runtimeConfigService.current();
            String originalName = file.getOriginalFilename();
            String fileName = StringUtils.hasText(originalName) ? originalName.trim() : "upload.bin";
            String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String datedDir = LocalDateTime.now().toLocalDate().toString();
            Path baseDir = Path.of(runtime.mediaDir()).toAbsolutePath().normalize().resolve("_uploads").resolve(datedDir);
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(UUID.randomUUID().toString().replace("-", "") + "_" + safeName).normalize();
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String contentType = file.getContentType();
            if (!StringUtils.hasText(contentType)) {
                contentType = Files.probeContentType(target);
            }
            return Map.of(
                    "filePath", target.toString(),
                    "fileName", fileName,
                    "size", file.getSize(),
                    "mimeType", contentType == null ? "application/octet-stream" : contentType,
                    "detectedType", detectMediaType(fileName)
            );
        } catch (Exception ex) {
            throw new IllegalStateException("upload temp media failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sendText(Map<String, Object> body) {
        WechathlinkAccount account = requireSendableAccount(asLong(body.get("wechatAccountId")));
        String toUserId = asString(body.get("toUserId"));
        String text = asString(body.get("text"));
        if (!StringUtils.hasText(toUserId) || !StringUtils.hasText(text)) {
            throw new IllegalArgumentException("wechatAccountId, toUserId and text are required");
        }
        Map<String, Object> auditPayload = Map.of(
                "wechatAccountId", account.getId(),
                "toUserId", toUserId,
                "eventType", "text",
                "textLength", text.length()
        );
        String contextToken = resolveContextToken(account.getId(), toUserId, asString(body.get("contextToken")));
        if (!StringUtils.hasText(contextToken)) {
            throw new IllegalArgumentException("context token not found for this user; current text sending only supports replying to users who have already sent a message");
        }
        try {
            var runtime = runtimeConfigService.current();
            ilinkClient.sendTextMessage(account.getBaseUrl(), account.getBotToken(), toUserId, text, contextToken, "2.0.1", runtime.pollTimeoutMs());
            WechathlinkEvent event = new WechathlinkEvent();
            event.setWechatAccountId(account.getId());
            event.setDirection("outbound");
            event.setEventType("text");
            event.setToUserId(toUserId);
            event.setBodyText(text);
            event.setContextToken(contextToken);
            event.setOwnerUserId(account.getOwnerUserId());
            event.setRawJson(writeJson(new LinkedHashMap<>(body)));
            fillCreateAudit(event);
            eventMapper.insert(event);
            touchPeerContext(account, toUserId, contextToken);
            createLog(account.getId(), "INFO", "outbound text message queued", "send-text", event.getRawJson());
            Map<String, Object> successPayload = new LinkedHashMap<>(auditPayload);
            successPayload.put("eventId", event.getId());
            auditService.recordSuccess(account.getId(), "MESSAGE_SEND_TEXT", "MESSAGE", event.getId(), "outbound text message queued", successPayload);
            return Map.of("ok", true, "eventId", event.getId());
        } catch (RuntimeException ex) {
            auditService.recordFailure(account.getId(), "MESSAGE_SEND_TEXT", "MESSAGE", null, "outbound text message send failed", ex, auditPayload);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sendMedia(Map<String, Object> body) {
        WechathlinkAccount account = requireSendableAccount(asLong(body.get("wechatAccountId")));
        String toUserId = asString(body.get("toUserId"));
        String mediaType = defaultValue(asString(body.get("type")), "image");
        String filePath = asString(body.get("filePath"));
        if (!StringUtils.hasText(toUserId) || !StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("wechatAccountId, toUserId and filePath are required");
        }
        Map<String, Object> auditPayload = Map.of(
                "wechatAccountId", account.getId(),
                "toUserId", toUserId,
                "eventType", mediaType,
                "fileName", Path.of(filePath).getFileName().toString()
        );
        String contextToken = resolveContextToken(account.getId(), toUserId, asString(body.get("contextToken")));
        if (!StringUtils.hasText(contextToken)) {
            throw new IllegalArgumentException("context token not found for this user; current media sending only supports replying to users who have already sent a message");
        }
        try {
            var runtime = runtimeConfigService.current();
            String normalizedType = defaultValue(asString(body.get("type")), detectMediaType(filePath));
            int uploadType = toUploadType(normalizedType);
            IlinkModels.UploadedMedia uploadedMedia = ilinkClient.uploadLocalMedia(
                    runtime.cdnBaseUrl(),
                    account.getBaseUrl(),
                    account.getBotToken(),
                    toUserId,
                    Path.of(filePath),
                    uploadType,
                    "2.0.1",
                    runtime.pollTimeoutMs()
            );
            String text = asString(body.get("text"));
            switch (normalizedType) {
                case "image" -> ilinkClient.sendImageMessage(account.getBaseUrl(), account.getBotToken(), toUserId, contextToken, text, uploadedMedia, "2.0.1", runtime.pollTimeoutMs());
                case "video" -> ilinkClient.sendVideoMessage(account.getBaseUrl(), account.getBotToken(), toUserId, contextToken, text, uploadedMedia, "2.0.1", runtime.pollTimeoutMs());
                case "voice" -> ilinkClient.sendVoiceMessage(account.getBaseUrl(), account.getBotToken(), toUserId, contextToken, text, detectVoiceEncodeType(filePath), uploadedMedia, "2.0.1", runtime.pollTimeoutMs());
                default -> ilinkClient.sendFileMessage(account.getBaseUrl(), account.getBotToken(), toUserId, contextToken, text, Path.of(filePath).getFileName().toString(), uploadedMedia, "2.0.1", runtime.pollTimeoutMs());
            }
            WechathlinkEvent event = new WechathlinkEvent();
            event.setWechatAccountId(account.getId());
            event.setDirection("outbound");
            event.setEventType(normalizedType);
            event.setToUserId(toUserId);
            event.setBodyText(text);
            event.setMediaPath(filePath);
            event.setMediaFileName(Path.of(filePath).getFileName().toString());
            event.setMediaMimeType(detectOutboundMime(normalizedType, filePath));
            event.setContextToken(contextToken);
            event.setOwnerUserId(account.getOwnerUserId());
            event.setRawJson(writeJson(new LinkedHashMap<>(body)));
            fillCreateAudit(event);
            eventMapper.insert(event);
            touchPeerContext(account, toUserId, contextToken);
            createLog(account.getId(), "INFO", "outbound media message queued", "send-media", event.getRawJson());
            Map<String, Object> successPayload = new LinkedHashMap<>(auditPayload);
            successPayload.put("eventId", event.getId());
            successPayload.put("normalizedType", normalizedType);
            auditService.recordSuccess(account.getId(), "MESSAGE_SEND_MEDIA", "MESSAGE", event.getId(), "outbound media message queued", successPayload);
            return Map.of("ok", true, "eventId", event.getId());
        } catch (RuntimeException ex) {
            auditService.recordFailure(account.getId(), "MESSAGE_SEND_MEDIA", "MESSAGE", null, "outbound media message send failed", ex, auditPayload);
            throw ex;
        }
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String detectMediaType(String filePath) {
        String ext = filePath == null ? "" : filePath.toLowerCase();
        if (ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".png") || ext.endsWith(".gif") || ext.endsWith(".webp")) {
            return "image";
        }
        if (ext.endsWith(".mp4") || ext.endsWith(".mov") || ext.endsWith(".m4v")) {
            return "video";
        }
        if (ext.endsWith(".amr") || ext.endsWith(".silk") || ext.endsWith(".mp3") || ext.endsWith(".ogg")) {
            return "voice";
        }
        return "file";
    }

    private int toUploadType(String mediaType) {
        return switch (mediaType) {
            case "image" -> IlinkApi.UPLOAD_MEDIA_TYPE_IMAGE;
            case "video" -> IlinkApi.UPLOAD_MEDIA_TYPE_VIDEO;
            case "voice" -> IlinkApi.UPLOAD_MEDIA_TYPE_VOICE;
            default -> IlinkApi.UPLOAD_MEDIA_TYPE_FILE;
        };
    }

    private int detectVoiceEncodeType(String filePath) {
        String lower = filePath == null ? "" : filePath.toLowerCase();
        if (lower.endsWith(".amr")) {
            return 1;
        }
        if (lower.endsWith(".mp3")) {
            return 4;
        }
        if (lower.endsWith(".ogg")) {
            return 5;
        }
        return 0;
    }

    private String detectOutboundMime(String mediaType, String filePath) {
        return switch (mediaType) {
            case "image" -> filePath != null && filePath.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
            case "video" -> "video/mp4";
            case "voice" -> "audio/silk";
            default -> "application/octet-stream";
        };
    }

    private String resolveContextToken(Long wechatAccountId, String toUserId, String explicitContextToken) {
        if (StringUtils.hasText(explicitContextToken)) {
            return explicitContextToken.trim();
        }
        WechathlinkPeerContext peerContext = peerContextMapper.selectOne(new LambdaQueryWrapper<WechathlinkPeerContext>()
                .eq(WechathlinkPeerContext::getWechatAccountId, wechatAccountId)
                .eq(WechathlinkPeerContext::getPeerUserId, toUserId)
                .eq(WechathlinkPeerContext::getIsDeleted, 0)
                .last("LIMIT 1"));
        return peerContext == null ? "" : defaultValue(peerContext.getContextToken(), "");
    }

    private void touchPeerContext(WechathlinkAccount account, String peerUserId, String contextToken) {
        if (account == null || account.getId() == null || !StringUtils.hasText(peerUserId) || !StringUtils.hasText(contextToken)) {
            return;
        }
        WechathlinkPeerContext peerContext = peerContextMapper.selectOne(new LambdaQueryWrapper<WechathlinkPeerContext>()
                .eq(WechathlinkPeerContext::getWechatAccountId, account.getId())
                .eq(WechathlinkPeerContext::getPeerUserId, peerUserId.trim())
                .eq(WechathlinkPeerContext::getIsDeleted, 0)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (peerContext == null) {
            peerContext = new WechathlinkPeerContext();
            peerContext.setWechatAccountId(account.getId());
            peerContext.setPeerUserId(peerUserId.trim());
            peerContext.setContextToken(contextToken.trim());
            peerContext.setLastMessageAt(now);
            fillCreateAudit(peerContext);
            peerContextMapper.insert(peerContext);
            return;
        }
        peerContext.setContextToken(contextToken.trim());
        peerContext.setLastMessageAt(now);
        fillUpdateAudit(peerContext);
        peerContextMapper.updateById(peerContext);
    }

    private Map<String, Object> toPeerView(WechathlinkPeerContext peerContext) {
        WechathlinkEvent latestEvent = findLatestEvent(peerContext.getWechatAccountId(), peerContext.getPeerUserId());
        String lastMessagePreview = latestEvent == null ? "" : buildEventPreview(latestEvent);
        String lastDirection = latestEvent == null ? "" : defaultValue(latestEvent.getDirection(), "");
        String lastEventType = latestEvent == null ? "" : defaultValue(latestEvent.getEventType(), "");
        long totalCount = countConversationEvents(peerContext.getWechatAccountId(), peerContext.getPeerUserId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", peerContext.getId());
        payload.put("wechatAccountId", peerContext.getWechatAccountId());
        payload.put("peerUserId", peerContext.getPeerUserId());
        payload.put("contactId", peerContext.getPeerUserId());
        payload.put("lastMessageAt", peerContext.getLastMessageAt());
        payload.put("lastSeenAt", peerContext.getLastMessageAt());
        payload.put("hasContextToken", StringUtils.hasText(peerContext.getContextToken()));
        payload.put("lastDirection", lastDirection);
        payload.put("lastEventType", lastEventType);
        payload.put("lastMessagePreview", lastMessagePreview);
        payload.put("totalCount", totalCount);
        payload.put("label", buildPeerLabel(peerContext.getPeerUserId(), lastDirection, lastMessagePreview));
        return payload;
    }

    private long countConversationEvents(Long wechatAccountId, String peerUserId) {
        if (wechatAccountId == null || !StringUtils.hasText(peerUserId)) {
            return 0L;
        }
        return eventMapper.selectCount(new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getWechatAccountId, wechatAccountId)
                .and(w -> w.eq(WechathlinkEvent::getFromUserId, peerUserId)
                        .or().eq(WechathlinkEvent::getToUserId, peerUserId))
                .eq(WechathlinkEvent::getIsDeleted, 0));
    }

    private WechathlinkEvent findLatestEvent(Long wechatAccountId, String peerUserId) {
        if (wechatAccountId == null || !StringUtils.hasText(peerUserId)) {
            return null;
        }
        return eventMapper.selectOne(new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getWechatAccountId, wechatAccountId)
                .and(w -> w.eq(WechathlinkEvent::getFromUserId, peerUserId)
                        .or().eq(WechathlinkEvent::getToUserId, peerUserId))
                .eq(WechathlinkEvent::getIsDeleted, 0)
                .orderByDesc(WechathlinkEvent::getCreateTime)
                .orderByDesc(WechathlinkEvent::getId)
                .last("LIMIT 1"));
    }

    private String buildEventPreview(WechathlinkEvent event) {
        if (event == null) {
            return "";
        }
        if (StringUtils.hasText(event.getBodyText())) {
            String text = event.getBodyText().trim();
            return text.length() > 24 ? text.substring(0, 24) + "..." : text;
        }
        if (StringUtils.hasText(event.getMediaFileName())) {
            return event.getMediaFileName().trim();
        }
        return defaultValue(event.getEventType(), "");
    }

    private String buildPeerLabel(String peerUserId, String direction, String preview) {
        String directionText = switch (defaultValue(direction, "").toLowerCase()) {
            case "inbound" -> "入站";
            case "outbound" -> "出站";
            default -> "";
        };
        if (StringUtils.hasText(directionText) && StringUtils.hasText(preview)) {
            return peerUserId + " | " + directionText + " | " + preview;
        }
        if (StringUtils.hasText(preview)) {
            return peerUserId + " | " + preview;
        }
        return peerUserId;
    }

    private WechathlinkAccount requireReadableAccount(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("wechatAccountId required");
        }
        WechathlinkAccount account = accountMapper.selectById(accountId);
        if (account == null || !permissionService.canRead(account)) {
            throw new IllegalArgumentException("wechat account not found or no permission");
        }
        return account;
    }

    private WechathlinkAccount requireSendableAccount(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("wechatAccountId required");
        }
        WechathlinkAccount account = accountMapper.selectById(accountId);
        if (account == null || !permissionService.canSend(account)) {
            throw new IllegalArgumentException("wechat account not found or no permission");
        }
        return account;
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private void createLog(Long accountId, String level, String message, String source, String metaJson) {
        WechathlinkLog log = new WechathlinkLog();
        log.setWechatAccountId(accountId);
        log.setLevel(level);
        log.setMessage(message);
        log.setSource(source);
        log.setMetaJson(metaJson);
        fillCreateAudit(log);
        logMapper.insert(log);
    }
}
