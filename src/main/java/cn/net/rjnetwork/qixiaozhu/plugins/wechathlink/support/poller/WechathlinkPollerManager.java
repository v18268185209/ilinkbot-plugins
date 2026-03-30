package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support.poller;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkLog;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkPeerContext;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkEventMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkLogMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkPeerContextMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl.WechathlinkRuntimeConfigService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink.IlinkApi;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink.IlinkModels;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@Slf4j
public class WechathlinkPollerManager {

    private final WechathlinkAccountMapper accountMapper;
    private final WechathlinkEventMapper eventMapper;
    private final WechathlinkLogMapper logMapper;
    private final WechathlinkPeerContextMapper peerContextMapper;
    private final WechathlinkRuntimeConfigService runtimeConfigService;
    private final IlinkApi ilinkClient;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<Long, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public WechathlinkPollerManager(WechathlinkAccountMapper accountMapper,
                                    WechathlinkEventMapper eventMapper,
                                    WechathlinkLogMapper logMapper,
                                    WechathlinkPeerContextMapper peerContextMapper,
                                    WechathlinkRuntimeConfigService runtimeConfigService,
                                    IlinkApi ilinkClient) {
        this.accountMapper = accountMapper;
        this.eventMapper = eventMapper;
        this.logMapper = logMapper;
        this.peerContextMapper = peerContextMapper;
        this.runtimeConfigService = runtimeConfigService;
        this.ilinkClient = ilinkClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        List<WechathlinkAccount> accounts = accountMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WechathlinkAccount>()
                .eq(WechathlinkAccount::getIsDeleted, 0)
                .eq(WechathlinkAccount::getStatus, 1));
        for (WechathlinkAccount account : accounts) {
            if (account.getBotToken() != null && !account.getBotToken().isBlank()) {
                start(account.getId());
            }
        }
    }

    public void start(Long accountId) {
        if (accountId == null || runningTasks.containsKey(accountId)) {
            return;
        }
        Future<?> future = executorService.submit(() -> pollLoop(accountId));
        runningTasks.put(accountId, future);
    }

    public void stop(Long accountId) {
        Future<?> future = runningTasks.remove(accountId);
        if (future != null) {
            future.cancel(true);
        }
    }

    public boolean isRunning(Long accountId) {
        return accountId != null && runningTasks.containsKey(accountId);
    }

    public long runningCount() {
        return runningTasks.size();
    }

    @PreDestroy
    public void destroy() {
        for (Long accountId : Set.copyOf(runningTasks.keySet())) {
            stop(accountId);
        }
        executorService.shutdownNow();
    }

    private void pollLoop(Long accountId) {
        while (!Thread.currentThread().isInterrupted()) {
            WechathlinkAccount account = accountMapper.selectById(accountId);
            if (account == null || !Integer.valueOf(1).equals(account.getStatus()) || account.getBotToken() == null || account.getBotToken().isBlank()) {
                stop(accountId);
                return;
            }
            try {
                var runtime = runtimeConfigService.current();
                IlinkModels.GetUpdatesResponse response = ilinkClient.getUpdates(
                        account.getBaseUrl(),
                        account.getBotToken(),
                        account.getGetUpdatesBuf(),
                        "2.0.1",
                        runtime.pollTimeoutMs()
                );
                if (response.getUpdatesBuf() != null && !response.getUpdatesBuf().isBlank()) {
                    account.setGetUpdatesBuf(response.getUpdatesBuf());
                }
                account.setPollStatus("RUNNING");
                account.setLastError("");
                account.setLastPollAt(LocalDateTime.now());
                accountMapper.updateById(account);
                if (response.msgs() != null) {
                    for (IlinkModels.WeixinMessage message : response.msgs()) {
                        saveInboundMessage(account, message, runtime);
                    }
                }
                Thread.sleep(response.longpollingTimeoutMs() != null && response.longpollingTimeoutMs() > 0 ? 50L : 1000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.warn("wechathlink poll failed. accountId={}, error={}", accountId, ex.getMessage());
                WechathlinkAccount failed = accountMapper.selectById(accountId);
                if (failed != null) {
                    failed.setPollStatus("ERROR");
                    failed.setLastError(ex.getMessage());
                    failed.setLastPollAt(LocalDateTime.now());
                    accountMapper.updateById(failed);
                }
                createLog(accountId, "ERROR", "poll failed", "poller", "{\"error\":\"" + ex.getMessage().replace("\"", "'") + "\"}");
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void saveInboundMessage(WechathlinkAccount account,
                                    IlinkModels.WeixinMessage message,
                                    WechathlinkRuntimeConfigService.RuntimeSettings runtime) {
        String mediaPath = "";
        String mediaFileName = "";
        String mediaMimeType = "";
        try {
            IlinkModels.MessageItem mediaItem = firstInboundMediaItem(message);
            if (mediaItem != null) {
                IlinkModels.DownloadedMedia downloadedMedia = ilinkClient.downloadMessageMedia(runtime.cdnBaseUrl(), mediaItem, runtime.pollTimeoutMs());
                mediaPath = saveMedia(runtime.mediaDir(), account.getAccountCode(), message.messageId(), downloadedMedia.fileName(), downloadedMedia.bytes());
                mediaFileName = downloadedMedia.fileName();
                mediaMimeType = downloadedMedia.mimeType();
            }
        } catch (Exception ex) {
            createLog(account.getId(), "ERROR", "download inbound media failed", "media", "{\"error\":\"" + ex.getMessage().replace("\"", "'") + "\"}");
        }

        WechathlinkEvent event = new WechathlinkEvent();
        event.setWechatAccountId(account.getId());
        event.setDirection("inbound");
        event.setEventType(detectEventType(message));
        event.setFromUserId(message.fromUserId());
        event.setToUserId(message.toUserId());
        event.setMessageId(message.messageId());
        event.setContextToken(message.contextToken());
        event.setBodyText(extractBodyText(message));
        event.setMediaPath(mediaPath);
        event.setMediaFileName(mediaFileName);
        event.setMediaMimeType(mediaMimeType);
        event.setRawJson(writeMessageJson(message));
        event.setOwnerUserId(account.getOwnerUserId());
        fillAudit(account, event);
        eventMapper.insert(event);

        if (message.fromUserId() != null && !message.fromUserId().isBlank() && message.contextToken() != null && !message.contextToken().isBlank()) {
            WechathlinkPeerContext peerContext = peerContextMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WechathlinkPeerContext>()
                    .eq(WechathlinkPeerContext::getWechatAccountId, account.getId())
                    .eq(WechathlinkPeerContext::getPeerUserId, message.fromUserId())
                    .eq(WechathlinkPeerContext::getIsDeleted, 0)
                    .last("LIMIT 1"));
            if (peerContext == null) {
                peerContext = new WechathlinkPeerContext();
                peerContext.setWechatAccountId(account.getId());
                peerContext.setPeerUserId(message.fromUserId());
                peerContext.setContextToken(message.contextToken());
                peerContext.setLastMessageAt(LocalDateTime.now());
                fillAudit(account, peerContext);
                peerContextMapper.insert(peerContext);
            } else {
                peerContext.setContextToken(message.contextToken());
                peerContext.setLastMessageAt(LocalDateTime.now());
                peerContext.setUpdateTime(LocalDateTime.now());
                peerContext.setUpdateUserId(account.getOwnerUserId());
                peerContextMapper.updateById(peerContext);
            }
        }

        account.setLastInboundAt(LocalDateTime.now());
        accountMapper.updateById(account);
    }

    private String saveMedia(String mediaDir, String accountCode, Long messageId, String fileName, byte[] bytes) throws Exception {
        Path baseDir = Path.of(mediaDir).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);
        String safeAccount = accountCode == null ? "default" : accountCode.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeFile = (messageId == null ? System.currentTimeMillis() : messageId) + "_" + (fileName == null ? "media.bin" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_"));
        Path target = baseDir.resolve(safeAccount).resolve(safeFile);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        return target.toString();
    }

    private IlinkModels.MessageItem firstInboundMediaItem(IlinkModels.WeixinMessage message) {
        if (message.itemList() == null) {
            return null;
        }
        for (IlinkModels.MessageItem item : message.itemList()) {
            if (item != null && item.type() != null && List.of(2, 3, 4, 5).contains(item.type())) {
                return item;
            }
        }
        return null;
    }

    private String detectEventType(IlinkModels.WeixinMessage message) {
        if (message.itemList() == null) {
            return "unknown";
        }
        for (IlinkModels.MessageItem item : message.itemList()) {
            if (item == null || item.type() == null) {
                continue;
            }
            return switch (item.type()) {
                case 1 -> "text";
                case 2 -> "image";
                case 3 -> "voice";
                case 4 -> "file";
                case 5 -> "video";
                default -> "unknown";
            };
        }
        return "unknown";
    }

    private String extractBodyText(IlinkModels.WeixinMessage message) {
        if (message.itemList() == null) {
            return "";
        }
        for (IlinkModels.MessageItem item : message.itemList()) {
            if (item == null || item.type() == null) {
                continue;
            }
            if (item.type() == 1 && item.textItem() != null && item.textItem().text() != null) {
                return item.textItem().text();
            }
            if (item.type() == 3 && item.voiceItem() != null && item.voiceItem().text() != null && !item.voiceItem().text().isBlank()) {
                return item.voiceItem().text();
            }
        }
        return "";
    }

    private String writeMessageJson(IlinkModels.WeixinMessage message) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(message);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void fillAudit(WechathlinkAccount account, cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAuditEntity entity) {
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setCreateUserId(account.getOwnerUserId());
        entity.setUpdateUserId(account.getOwnerUserId());
        entity.setCompanyId(account.getCompanyId());
        entity.setDeptId(account.getDeptId());
        entity.setIsDeleted(0);
        entity.setStatus(1);
    }

    private void createLog(Long accountId, String level, String message, String source, String metaJson) {
        WechathlinkLog log = new WechathlinkLog();
        log.setWechatAccountId(accountId);
        log.setLevel(level);
        log.setMessage(message);
        log.setSource(source);
        log.setMetaJson(metaJson);
        log.setCreateTime(LocalDateTime.now());
        log.setUpdateTime(LocalDateTime.now());
        log.setIsDeleted(0);
        log.setStatus(1);
        logMapper.insert(log);
    }
}
