package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkLog;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkMediaAsset;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkMessageDispatch;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkPeerContext;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkEventMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkLogMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkMediaAssetMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkMessageDispatchMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkPeerContextMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAuditService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkMessageService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkWebhookService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink.IlinkApi;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink.IlinkModels;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support.WechathlinkReplyWindowSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WechathlinkMessageServiceImpl extends WechathlinkServiceSupport implements WechathlinkMessageService {

    private final WechathlinkAccountMapper accountMapper;
    private final WechathlinkEventMapper eventMapper;
    private final WechathlinkLogMapper logMapper;
    private final WechathlinkMessageDispatchMapper messageDispatchMapper;
    private final WechathlinkMediaAssetMapper mediaAssetMapper;
    private final WechathlinkPeerContextMapper peerContextMapper;
    private final WechathlinkPermissionService permissionService;
    private final WechathlinkRuntimeConfigService runtimeConfigService;
    private final IlinkApi ilinkClient;
    private final WechathlinkAuditService auditService;
    private final WechathlinkWebhookService webhookService;

    public WechathlinkMessageServiceImpl(WechathlinkAccountMapper accountMapper,
                                         WechathlinkEventMapper eventMapper,
                                         WechathlinkLogMapper logMapper,
                                         WechathlinkMessageDispatchMapper messageDispatchMapper,
                                         WechathlinkMediaAssetMapper mediaAssetMapper,
                                         WechathlinkPeerContextMapper peerContextMapper,
                                         WechathlinkPermissionService permissionService,
                                         WechathlinkRuntimeConfigService runtimeConfigService,
                                         IlinkApi ilinkClient,
                                         WechathlinkAuditService auditService,
                                         WechathlinkWebhookService webhookService) {
        this.accountMapper = accountMapper;
        this.eventMapper = eventMapper;
        this.logMapper = logMapper;
        this.messageDispatchMapper = messageDispatchMapper;
        this.mediaAssetMapper = mediaAssetMapper;
        this.peerContextMapper = peerContextMapper;
        this.permissionService = permissionService;
        this.runtimeConfigService = runtimeConfigService;
        this.ilinkClient = ilinkClient;
        this.auditService = auditService;
        this.webhookService = webhookService;
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
        String traceId = resolveTraceId(body);
        Map<String, Object> auditPayload = Map.of(
                "wechatAccountId", account.getId(),
                "toUserId", toUserId,
                "eventType", "text",
                "textLength", text.length(),
                "traceId", traceId
        );
        String contextToken = resolveContextToken(account.getId(), toUserId, asString(body.get("contextToken")));
        if (!StringUtils.hasText(contextToken)) {
            throw new IllegalArgumentException("context token not found for this user; current text sending only supports replying to users who have already sent a message");
        }
        LinkedHashMap<String, Object> dispatchPayload = new LinkedHashMap<>(body);
        dispatchPayload.put("contextToken", contextToken);
        dispatchPayload.put("traceId", traceId);
        WechathlinkMessageDispatch dispatch = null;
        try {
            dispatch = createDispatchRecord(account, toUserId, "text", dispatchPayload, traceId, "REQUEST", traceId);
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
            event.setRawJson(writeJson(dispatchPayload));
            fillCreateAudit(event);
            eventMapper.insert(event);
            webhookService.deliverEvent(account, event);
            updateDispatchRecord(dispatch, "SENT", "", "EVENT", event.getId());
            touchPeerContext(account, toUserId, contextToken);
            createLog(account.getId(), "INFO", "outbound text message queued", "send-text", event.getRawJson());
            Map<String, Object> successPayload = new LinkedHashMap<>(auditPayload);
            successPayload.put("eventId", event.getId());
            successPayload.put("dispatchId", dispatch.getId());
            auditService.recordSuccess(account.getId(), "MESSAGE_SEND_TEXT", "MESSAGE", event.getId(), "outbound text message queued", successPayload);
            return Map.of("ok", true, "eventId", event.getId(), "dispatchId", dispatch.getId(), "traceId", traceId);
        } catch (RuntimeException ex) {
            updateDispatchRecord(dispatch, "FAILED", ex.getMessage(), "REQUEST", traceId);
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
        Path outboundFile = Path.of(filePath).toAbsolutePath().normalize();
        String fileName = outboundFile.getFileName() == null ? "media.bin" : outboundFile.getFileName().toString();
        String traceId = resolveTraceId(body);
        Map<String, Object> auditPayload = Map.of(
                "wechatAccountId", account.getId(),
                "toUserId", toUserId,
                "eventType", mediaType,
                "fileName", fileName,
                "traceId", traceId
        );
        String contextToken = resolveContextToken(account.getId(), toUserId, asString(body.get("contextToken")));
        if (!StringUtils.hasText(contextToken)) {
            throw new IllegalArgumentException("context token not found for this user; current media sending only supports replying to users who have already sent a message");
        }
        String normalizedType = defaultValue(asString(body.get("type")), detectMediaType(outboundFile.toString()));
        LinkedHashMap<String, Object> dispatchPayload = new LinkedHashMap<>(body);
        dispatchPayload.put("contextToken", contextToken);
        dispatchPayload.put("type", normalizedType);
        dispatchPayload.put("traceId", traceId);
        WechathlinkMessageDispatch dispatch = null;
        try {
            dispatch = createDispatchRecord(account, toUserId, normalizedType, dispatchPayload, traceId, "REQUEST", traceId);
            var runtime = runtimeConfigService.current();
            int uploadType = toUploadType(normalizedType);
            IlinkModels.UploadedMedia uploadedMedia = ilinkClient.uploadLocalMedia(
                    runtime.cdnBaseUrl(),
                    account.getBaseUrl(),
                    account.getBotToken(),
                    toUserId,
                    outboundFile,
                    uploadType,
                    "2.0.1",
                    runtime.pollTimeoutMs()
            );
            String text = asString(body.get("text"));
            switch (normalizedType) {
                case "image" -> ilinkClient.sendImageMessage(account.getBaseUrl(), account.getBotToken(), toUserId, contextToken, text, uploadedMedia, "2.0.1", runtime.pollTimeoutMs());
                case "video" -> ilinkClient.sendVideoMessage(account.getBaseUrl(), account.getBotToken(), toUserId, contextToken, text, uploadedMedia, "2.0.1", runtime.pollTimeoutMs());
                case "voice" -> ilinkClient.sendVoiceMessage(account.getBaseUrl(), account.getBotToken(), toUserId, contextToken, text, detectVoiceEncodeType(outboundFile.toString()), uploadedMedia, "2.0.1", runtime.pollTimeoutMs());
                default -> ilinkClient.sendFileMessage(account.getBaseUrl(), account.getBotToken(), toUserId, contextToken, text, fileName, uploadedMedia, "2.0.1", runtime.pollTimeoutMs());
            }
            WechathlinkEvent event = new WechathlinkEvent();
            event.setWechatAccountId(account.getId());
            event.setDirection("outbound");
            event.setEventType(normalizedType);
            event.setToUserId(toUserId);
            event.setBodyText(text);
            event.setMediaPath(outboundFile.toString());
            event.setMediaFileName(fileName);
            event.setMediaMimeType(detectOutboundMime(normalizedType, outboundFile.toString()));
            event.setContextToken(contextToken);
            event.setOwnerUserId(account.getOwnerUserId());
            event.setRawJson(writeJson(dispatchPayload));
            fillCreateAudit(event);
            eventMapper.insert(event);
            webhookService.deliverEvent(account, event);
            updateDispatchRecord(dispatch, "SENT", "", "EVENT", event.getId());
            WechathlinkMediaAsset mediaAsset = createMediaAssetRecord(
                    account,
                    event.getId(),
                    dispatch.getId(),
                    normalizedType,
                    outboundFile,
                    fileName,
                    event.getMediaMimeType(),
                    "READY",
                    ""
            );
            touchPeerContext(account, toUserId, contextToken);
            createLog(account.getId(), "INFO", "outbound media message queued", "send-media", event.getRawJson());
            Map<String, Object> successPayload = new LinkedHashMap<>(auditPayload);
            successPayload.put("eventId", event.getId());
            successPayload.put("dispatchId", dispatch.getId());
            successPayload.put("mediaAssetId", mediaAsset.getId());
            successPayload.put("normalizedType", normalizedType);
            auditService.recordSuccess(account.getId(), "MESSAGE_SEND_MEDIA", "MESSAGE", event.getId(), "outbound media message queued", successPayload);
            return Map.of(
                    "ok", true,
                    "eventId", event.getId(),
                    "dispatchId", dispatch.getId(),
                    "mediaAssetId", mediaAsset.getId(),
                    "traceId", traceId
            );
        } catch (RuntimeException ex) {
            updateDispatchRecord(dispatch, "FAILED", ex.getMessage(), "REQUEST", traceId);
            auditService.recordFailure(account.getId(), "MESSAGE_SEND_MEDIA", "MESSAGE", null, "outbound media message send failed", ex, auditPayload);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> retryDispatch(Long dispatchId) {
        if (dispatchId == null) {
            throw new IllegalArgumentException("dispatchId required");
        }
        WechathlinkMessageDispatch originalDispatch = messageDispatchMapper.selectById(dispatchId);
        if (originalDispatch == null) {
            throw new IllegalArgumentException("dispatch not found");
        }
        WechathlinkAccount account = requireSendableAccount(originalDispatch.getWechatAccountId());
        if (!"FAILED".equalsIgnoreCase(defaultValue(originalDispatch.getDispatchStatus(), ""))) {
            throw new IllegalArgumentException("only failed dispatch can retry");
        }
        Map<String, Object> payload = parseDispatchPayload(originalDispatch.getPayloadJson());
        payload.put("wechatAccountId", account.getId());
        payload.put("toUserId", defaultValue(asString(payload.get("toUserId")), defaultValue(originalDispatch.getPeerUserId(), "")));
        payload.put("traceId", defaultValue(originalDispatch.getTraceId(), ""));
        payload.put("sourceType", "DISPATCH");
        payload.put("sourceId", String.valueOf(originalDispatch.getId()));
        if (!payload.containsKey("type") && !"text".equalsIgnoreCase(defaultValue(originalDispatch.getDispatchType(), ""))) {
            payload.put("type", originalDispatch.getDispatchType());
        }
        originalDispatch.setRetryCount((originalDispatch.getRetryCount() == null ? 0 : originalDispatch.getRetryCount()) + 1);
        fillUpdateAudit(originalDispatch);
        messageDispatchMapper.updateById(originalDispatch);

        Map<String, Object> auditPayload = Map.of(
                "wechatAccountId", account.getId(),
                "dispatchId", originalDispatch.getId(),
                "dispatchType", defaultValue(originalDispatch.getDispatchType(), ""),
                "traceId", defaultValue(originalDispatch.getTraceId(), "")
        );
        try {
            Map<String, Object> result = "text".equalsIgnoreCase(defaultValue(originalDispatch.getDispatchType(), ""))
                    ? sendText(payload)
                    : sendMedia(payload);
            Long newDispatchId = asLong(result.get("dispatchId"));
            if (newDispatchId != null) {
                WechathlinkMessageDispatch retriedDispatch = messageDispatchMapper.selectById(newDispatchId);
                if (retriedDispatch != null) {
                    retriedDispatch.setSourceType("DISPATCH");
                    retriedDispatch.setSourceId(String.valueOf(originalDispatch.getId()));
                    fillUpdateAudit(retriedDispatch);
                    messageDispatchMapper.updateById(retriedDispatch);
                }
            }
            Map<String, Object> successPayload = new LinkedHashMap<>(auditPayload);
            successPayload.put("newDispatchId", result.get("dispatchId"));
            successPayload.put("eventId", result.get("eventId"));
            successPayload.put("mediaAssetId", result.get("mediaAssetId"));
            auditService.recordSuccess(account.getId(), "MESSAGE_RETRY_DISPATCH", "DISPATCH", originalDispatch.getId(), "dispatch retry queued", successPayload);
            LinkedHashMap<String, Object> response = new LinkedHashMap<>(result);
            response.put("retriedFromDispatchId", originalDispatch.getId());
            response.put("retryCount", originalDispatch.getRetryCount());
            return response;
        } catch (RuntimeException ex) {
            auditService.recordFailure(account.getId(), "MESSAGE_RETRY_DISPATCH", "DISPATCH", originalDispatch.getId(), "dispatch retry failed", ex, auditPayload);
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

    private String resolveTraceId(Map<String, Object> body) {
        String traceId = asString(body.get("traceId"));
        return StringUtils.hasText(traceId) ? traceId : UUID.randomUUID().toString().replace("-", "");
    }

    private WechathlinkMessageDispatch createDispatchRecord(WechathlinkAccount account,
                                                            String peerUserId,
                                                            String dispatchType,
                                                            Map<String, Object> payload,
                                                            String traceId,
                                                            String sourceType,
                                                            Object sourceId) {
        WechathlinkMessageDispatch dispatch = new WechathlinkMessageDispatch();
        dispatch.setWechatAccountId(account.getId());
        dispatch.setRuntimeId(account.getCurrentRuntimeId());
        dispatch.setPeerUserId(defaultValue(peerUserId, ""));
        dispatch.setDispatchType(defaultValue(dispatchType, "unknown"));
        dispatch.setPayloadJson(writeJson(new LinkedHashMap<>(payload)));
        dispatch.setDispatchStatus("CREATED");
        dispatch.setRetryCount(0);
        dispatch.setErrorMessage("");
        dispatch.setSourceType(defaultValue(sourceType, "REQUEST"));
        dispatch.setSourceId(sourceId == null ? defaultValue(traceId, "") : String.valueOf(sourceId));
        dispatch.setTraceId(defaultValue(traceId, ""));
        fillCreateAudit(dispatch);
        messageDispatchMapper.insert(dispatch);
        return dispatch;
    }

    private void updateDispatchRecord(WechathlinkMessageDispatch dispatch,
                                      String dispatchStatus,
                                      String errorMessage,
                                      String sourceType,
                                      Object sourceId) {
        if (dispatch == null || dispatch.getId() == null) {
            return;
        }
        dispatch.setDispatchStatus(defaultValue(dispatchStatus, dispatch.getDispatchStatus()));
        dispatch.setErrorMessage(defaultValue(errorMessage, ""));
        if (StringUtils.hasText(sourceType)) {
            dispatch.setSourceType(sourceType.trim());
        }
        dispatch.setSourceId(sourceId == null ? "" : String.valueOf(sourceId));
        fillUpdateAudit(dispatch);
        messageDispatchMapper.updateById(dispatch);
    }

    private WechathlinkMediaAsset createMediaAssetRecord(WechathlinkAccount account,
                                                         Long eventId,
                                                         Long dispatchId,
                                                         String assetType,
                                                         Path storagePath,
                                                         String fileName,
                                                         String mimeType,
                                                         String downloadStatus,
                                                         String errorMessage) {
        WechathlinkMediaAsset mediaAsset = new WechathlinkMediaAsset();
        mediaAsset.setWechatAccountId(account.getId());
        mediaAsset.setEventId(eventId);
        mediaAsset.setDispatchId(dispatchId);
        mediaAsset.setAssetType(defaultValue(assetType, "file"));
        mediaAsset.setStoragePath(storagePath == null ? "" : storagePath.toString());
        mediaAsset.setFileName(defaultValue(fileName, ""));
        mediaAsset.setMimeType(defaultValue(mimeType, ""));
        mediaAsset.setSha256(sha256Hex(storagePath));
        mediaAsset.setDownloadStatus(defaultValue(downloadStatus, "READY"));
        mediaAsset.setErrorMessage(defaultValue(errorMessage, ""));
        fillCreateAudit(mediaAsset);
        mediaAssetMapper.insert(mediaAsset);
        return mediaAsset;
    }

    private String sha256Hex(Path filePath) {
        if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return "";
        }
        try (var in = Files.newInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            return "";
        }
    }

    private Map<String, Object> parseDispatchPayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(payloadJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("dispatch payload json invalid");
        }
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
        if (peerContext == null) {
            return "";
        }
        normalizePeerContextState(peerContext);
        return WechathlinkReplyWindowSupport.canReply(peerContext.getContextToken(), peerContext.getContextStatus(), peerContext.getWindowStatus())
                ? defaultValue(peerContext.getContextToken(), "")
                : "";
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
            peerContext.setContextStatus(WechathlinkReplyWindowSupport.CONTEXT_ACTIVE);
            peerContext.setWindowStatus(WechathlinkReplyWindowSupport.WINDOW_OPEN);
            fillCreateAudit(peerContext);
            peerContextMapper.insert(peerContext);
            return;
        }
        peerContext.setContextToken(contextToken.trim());
        peerContext.setLastMessageAt(now);
        normalizePeerContextState(peerContext);
        fillUpdateAudit(peerContext);
        peerContextMapper.updateById(peerContext);
    }

    private Map<String, Object> toPeerView(WechathlinkPeerContext peerContext) {
        normalizePeerContextState(peerContext);
        WechathlinkEvent latestEvent = findLatestEvent(peerContext.getWechatAccountId(), peerContext.getPeerUserId());
        String lastMessagePreview = latestEvent == null ? "" : buildEventPreview(latestEvent);
        String lastDirection = latestEvent == null ? "" : defaultValue(latestEvent.getDirection(), "");
        String lastEventType = latestEvent == null ? "" : defaultValue(latestEvent.getEventType(), "");
        long totalCount = countConversationEvents(peerContext.getWechatAccountId(), peerContext.getPeerUserId());
        boolean canReply = WechathlinkReplyWindowSupport.canReply(peerContext.getContextToken(), peerContext.getContextStatus(), peerContext.getWindowStatus());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", peerContext.getId());
        payload.put("wechatAccountId", peerContext.getWechatAccountId());
        payload.put("peerUserId", peerContext.getPeerUserId());
        payload.put("contactId", peerContext.getPeerUserId());
        payload.put("lastMessageAt", peerContext.getLastMessageAt());
        payload.put("lastSeenAt", peerContext.getLastMessageAt());
        payload.put("hasContextToken", canReply);
        payload.put("canReply", canReply);
        payload.put("contextStatus", peerContext.getContextStatus());
        payload.put("lastInboundAt", peerContext.getLastInboundAt());
        payload.put("replyWindowExpiresAt", peerContext.getReplyWindowExpiresAt());
        payload.put("windowStatus", peerContext.getWindowStatus());
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

    private void normalizePeerContextState(WechathlinkPeerContext peerContext) {
        if (peerContext == null) {
            return;
        }
        if (peerContext.getLastInboundAt() == null && peerContext.getLastMessageAt() != null) {
            peerContext.setLastInboundAt(peerContext.getLastMessageAt());
        }
        if (peerContext.getReplyWindowExpiresAt() == null && peerContext.getLastInboundAt() != null) {
            peerContext.setReplyWindowExpiresAt(WechathlinkReplyWindowSupport.calculateReplyWindowExpiresAt(peerContext.getLastInboundAt()));
        }
        peerContext.setWindowStatus(WechathlinkReplyWindowSupport.resolveWindowStatus(peerContext.getReplyWindowExpiresAt()));
        peerContext.setContextStatus(WechathlinkReplyWindowSupport.resolveContextStatus(peerContext.getContextToken(), peerContext.getReplyWindowExpiresAt()));
    }
}
