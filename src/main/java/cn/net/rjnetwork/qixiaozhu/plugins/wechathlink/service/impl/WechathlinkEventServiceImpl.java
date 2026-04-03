package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkMediaAsset;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkMessageDispatch;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkPeerContext;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkEventMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkMediaAssetMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkMessageDispatchMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkPeerContextMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAuditService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkEventService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support.WechathlinkReplyWindowSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WechathlinkEventServiceImpl implements WechathlinkEventService {

    private final WechathlinkAccountMapper accountMapper;
    private final WechathlinkEventMapper eventMapper;
    private final WechathlinkMessageDispatchMapper messageDispatchMapper;
    private final WechathlinkMediaAssetMapper mediaAssetMapper;
    private final WechathlinkPeerContextMapper peerContextMapper;
    private final WechathlinkPermissionService permissionService;
    private final WechathlinkAuditService auditService;

    public WechathlinkEventServiceImpl(WechathlinkAccountMapper accountMapper,
                                       WechathlinkEventMapper eventMapper,
                                       WechathlinkMessageDispatchMapper messageDispatchMapper,
                                       WechathlinkMediaAssetMapper mediaAssetMapper,
                                       WechathlinkPeerContextMapper peerContextMapper,
                                       WechathlinkPermissionService permissionService,
                                       WechathlinkAuditService auditService) {
        this.accountMapper = accountMapper;
        this.eventMapper = eventMapper;
        this.messageDispatchMapper = messageDispatchMapper;
        this.mediaAssetMapper = mediaAssetMapper;
        this.peerContextMapper = peerContextMapper;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @Override
    public Map<String, Object> summary(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<WechathlinkAccount> wrapper = new LambdaQueryWrapper<WechathlinkAccount>()
                .eq(WechathlinkAccount::getIsDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(WechathlinkAccount::getAccountCode, keyword.trim())
                    .or().like(WechathlinkAccount::getAccountName, keyword.trim())
                    .or().like(WechathlinkAccount::getBaseUrl, keyword.trim()));
        }
        List<WechathlinkAccount> accounts = accountMapper.selectList(wrapper.orderByDesc(WechathlinkAccount::getUpdateTime).orderByDesc(WechathlinkAccount::getId));
        if (!permissionService.standaloneMode() && !permissionService.superAccount()) {
            accounts = accounts.stream().filter(permissionService::canRead).toList();
        }
        int pageNumValue = normalizePageNum(pageNum);
        int pageSizeValue = normalizePageSize(pageSize);
        int fromIndex = Math.min((pageNumValue - 1) * pageSizeValue, accounts.size());
        int toIndex = Math.min(fromIndex + pageSizeValue, accounts.size());
        List<Map<String, Object>> rows = accounts.subList(fromIndex, toIndex).stream()
                .map(this::toSummaryView)
                .toList();
        return Map.of(
                "list", rows,
                "total", (long) accounts.size(),
                "pageNum", (long) pageNumValue,
                "pageSize", (long) pageSizeValue
        );
    }

    @Override
    public Map<String, Object> list(Long wechatAccountId,
                                    Long eventId,
                                    String contactId,
                                    String direction,
                                    String eventType,
                                    String dateFrom,
                                    String dateTo,
                                    String keyword,
                                    Integer hasMedia,
                                    Integer pageNum,
                                    Integer pageSize) {
        Page<WechathlinkEvent> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<WechathlinkEvent> wrapper = buildEventQuery(
                wechatAccountId,
                eventId,
                contactId,
                direction,
                eventType,
                dateFrom,
                dateTo,
                keyword,
                hasMedia
        );
        if (wrapper == null) {
            return pageResult(List.of(), 0L, page);
        }
        wrapper.orderByDesc(WechathlinkEvent::getCreateTime).orderByDesc(WechathlinkEvent::getId);
        Page<WechathlinkEvent> result = eventMapper.selectPage(page, wrapper);
        return pageResult(result.getRecords(), result.getTotal(), result);
    }

    @Override
    public byte[] export(Long wechatAccountId,
                         String contactId,
                         String direction,
                         String eventType,
                         String dateFrom,
                         String dateTo,
                         String keyword,
                         Integer hasMedia) {
        WechathlinkAccount account = requireReadableAccount(wechatAccountId);
        if (!permissionService.canExport(account)) {
            throw new IllegalArgumentException("wechat account not found or no permission");
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("wechatAccountId", wechatAccountId);
        auditPayload.put("contactId", contactId);
        auditPayload.put("direction", direction);
        auditPayload.put("eventType", eventType);
        auditPayload.put("dateFrom", dateFrom);
        auditPayload.put("dateTo", dateTo);
        auditPayload.put("keyword", keyword);
        auditPayload.put("hasMedia", hasMedia);
        try {
            LambdaQueryWrapper<WechathlinkEvent> wrapper = buildEventQuery(
                    wechatAccountId,
                    null,
                    contactId,
                    direction,
                    eventType,
                    dateFrom,
                    dateTo,
                    keyword,
                    hasMedia
            );
            if (wrapper == null) {
                auditPayload.put("rowCount", 0);
                auditService.recordSuccess(wechatAccountId, "EVENT_EXPORT", "EVENT", wechatAccountId, "event export completed", auditPayload);
                return csvHeader().getBytes(StandardCharsets.UTF_8);
            }
            wrapper.orderByDesc(WechathlinkEvent::getCreateTime).orderByDesc(WechathlinkEvent::getId);
            List<WechathlinkEvent> rows = eventMapper.selectList(wrapper);
            auditPayload.put("rowCount", rows.size());
            auditService.recordSuccess(wechatAccountId, "EVENT_EXPORT", "EVENT", wechatAccountId, "event export completed", auditPayload);
            return buildCsv(rows).getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            auditService.recordFailure(wechatAccountId, "EVENT_EXPORT", "EVENT", wechatAccountId, "event export failed", ex, auditPayload);
            throw ex;
        }
    }

    @Override
    public Map<String, Object> contacts(Long wechatAccountId, String keyword, Integer pageNum, Integer pageSize) {
        WechathlinkAccount account = requireReadableAccount(wechatAccountId);
        List<WechathlinkEvent> events = eventMapper.selectList(new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getWechatAccountId, wechatAccountId)
                .eq(WechathlinkEvent::getIsDeleted, 0)
                .orderByDesc(WechathlinkEvent::getCreateTime)
                .orderByDesc(WechathlinkEvent::getId));
        Map<String, WechathlinkPeerContext> peerContextMap = peerContextMapper.selectList(new LambdaQueryWrapper<WechathlinkPeerContext>()
                        .eq(WechathlinkPeerContext::getWechatAccountId, wechatAccountId)
                        .eq(WechathlinkPeerContext::getIsDeleted, 0))
                .stream()
                .collect(java.util.stream.Collectors.toMap(WechathlinkPeerContext::getPeerUserId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<String, ContactSummary> aggregate = new LinkedHashMap<>();
        for (WechathlinkEvent event : events) {
            touchContact(aggregate, account, event.getFromUserId(), true, event);
            touchContact(aggregate, account, event.getToUserId(), false, event);
        }
        List<Map<String, Object>> rows = aggregate.values().stream()
                .filter(item -> !StringUtils.hasText(keyword) || item.contactId().contains(keyword.trim()))
                .sorted((a, b) -> {
                    LocalDateTime left = a.lastSeenAt();
                    LocalDateTime right = b.lastSeenAt();
                    if (left == null && right == null) {
                        return a.contactId().compareTo(b.contactId());
                    }
                    if (left == null) {
                        return 1;
                    }
                    if (right == null) {
                        return -1;
                    }
                    int compare = right.compareTo(left);
                    return compare != 0 ? compare : a.contactId().compareTo(b.contactId());
                })
                .map(item -> item.toView(peerContextMap.get(item.contactId())))
                .toList();
        int page = normalizePageNum(pageNum);
        int size = normalizePageSize(pageSize);
        int fromIndex = Math.min((page - 1) * size, rows.size());
        int toIndex = Math.min(fromIndex + size, rows.size());
        List<Map<String, Object>> pagedRows = new ArrayList<>(rows.subList(fromIndex, toIndex));
        return Map.of(
                "list", pagedRows,
                "total", (long) rows.size(),
                "pageNum", (long) page,
                "pageSize", (long) size
        );
    }

    @Override
    public Map<String, Object> dispatches(Long wechatAccountId,
                                          Long dispatchId,
                                          String contactId,
                                          String dispatchType,
                                          String dispatchStatus,
                                          String traceId,
                                          String keyword,
                                          Integer pageNum,
                                          Integer pageSize) {
        Page<WechathlinkMessageDispatch> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<WechathlinkMessageDispatch> wrapper = buildDispatchQuery(
                wechatAccountId,
                dispatchId,
                contactId,
                dispatchType,
                dispatchStatus,
                traceId,
                keyword
        );
        if (wrapper == null) {
            return pageResult(List.of(), 0L, page);
        }
        wrapper.orderByDesc(WechathlinkMessageDispatch::getCreateTime).orderByDesc(WechathlinkMessageDispatch::getId);
        Page<WechathlinkMessageDispatch> result = messageDispatchMapper.selectPage(page, wrapper);
        return pageResult(toDispatchViews(result.getRecords()), result.getTotal(), result);
    }

    @Override
    public Map<String, Object> mediaAssets(Long wechatAccountId,
                                           Long assetId,
                                           Long eventId,
                                           Long dispatchId,
                                           String assetType,
                                           String downloadStatus,
                                           String keyword,
                                           Integer pageNum,
                                           Integer pageSize) {
        Page<WechathlinkMediaAsset> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<WechathlinkMediaAsset> wrapper = buildMediaAssetQuery(
                wechatAccountId,
                assetId,
                eventId,
                dispatchId,
                assetType,
                downloadStatus,
                keyword
        );
        if (wrapper == null) {
            return pageResult(List.of(), 0L, page);
        }
        wrapper.orderByDesc(WechathlinkMediaAsset::getCreateTime).orderByDesc(WechathlinkMediaAsset::getId);
        Page<WechathlinkMediaAsset> result = mediaAssetMapper.selectPage(page, wrapper);
        return pageResult(toMediaAssetViews(result.getRecords()), result.getTotal(), result);
    }

    @Override
    public WechathlinkEvent getReadableEvent(Long eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId required");
        }
        WechathlinkEvent event = eventMapper.selectById(eventId);
        if (event == null || event.getWechatAccountId() == null) {
            throw new IllegalArgumentException("event not found");
        }
        WechathlinkAccount account = requireReadableAccount(event.getWechatAccountId());
        if (!permissionService.canViewMedia(account)) {
            throw new IllegalArgumentException("event not found or no permission");
        }
        return event;
    }

    private List<Map<String, Object>> toDispatchViews(List<WechathlinkMessageDispatch> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Long, WechathlinkAccount> accountMap = loadAccountMap(rows.stream()
                .map(WechathlinkMessageDispatch::getWechatAccountId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Set<Long> eventIds = rows.stream()
                .filter(item -> "EVENT".equalsIgnoreCase(defaultValue(item.getSourceType(), "")))
                .map(item -> parseLongValue(item.getSourceId()))
                .filter(value -> value != null && value > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, WechathlinkEvent> eventMap = loadEventMap(eventIds);
        Map<Long, Long> mediaAssetCountMap = loadMediaAssetCountByDispatchId(rows.stream()
                .map(WechathlinkMessageDispatch::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return rows.stream().map((item) -> {
            WechathlinkAccount account = accountMap.get(item.getWechatAccountId());
            Long sourceEventId = "EVENT".equalsIgnoreCase(defaultValue(item.getSourceType(), "")) ? parseLongValue(item.getSourceId()) : null;
            WechathlinkEvent event = sourceEventId == null ? null : eventMap.get(sourceEventId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", item.getId());
            payload.put("wechatAccountId", item.getWechatAccountId());
            payload.put("accountCode", account == null ? "" : defaultValue(account.getAccountCode(), ""));
            payload.put("accountName", account == null ? "" : defaultValue(account.getAccountName(), ""));
            payload.put("runtimeId", item.getRuntimeId());
            payload.put("peerUserId", item.getPeerUserId());
            payload.put("dispatchType", item.getDispatchType());
            payload.put("payloadJson", item.getPayloadJson());
            payload.put("dispatchStatus", item.getDispatchStatus());
            payload.put("retryCount", item.getRetryCount());
            payload.put("errorMessage", item.getErrorMessage());
            payload.put("sourceType", item.getSourceType());
            payload.put("sourceId", item.getSourceId());
            payload.put("traceId", item.getTraceId());
            payload.put("eventId", sourceEventId);
            payload.put("eventType", event == null ? "" : defaultValue(event.getEventType(), ""));
            payload.put("eventDirection", event == null ? "" : defaultValue(event.getDirection(), ""));
            payload.put("eventCreateTime", event == null ? null : event.getCreateTime());
            payload.put("mediaAssetCount", mediaAssetCountMap.getOrDefault(item.getId(), 0L));
            payload.put("createTime", item.getCreateTime());
            payload.put("updateTime", item.getUpdateTime());
            return payload;
        }).toList();
    }

    private List<Map<String, Object>> toMediaAssetViews(List<WechathlinkMediaAsset> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Long, WechathlinkAccount> accountMap = loadAccountMap(rows.stream()
                .map(WechathlinkMediaAsset::getWechatAccountId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, WechathlinkEvent> eventMap = loadEventMap(rows.stream()
                .map(WechathlinkMediaAsset::getEventId)
                .filter(value -> value != null && value > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, WechathlinkMessageDispatch> dispatchMap = loadDispatchMap(rows.stream()
                .map(WechathlinkMediaAsset::getDispatchId)
                .filter(value -> value != null && value > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return rows.stream().map((item) -> {
            WechathlinkAccount account = accountMap.get(item.getWechatAccountId());
            WechathlinkEvent event = item.getEventId() == null ? null : eventMap.get(item.getEventId());
            WechathlinkMessageDispatch dispatch = item.getDispatchId() == null ? null : dispatchMap.get(item.getDispatchId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", item.getId());
            payload.put("wechatAccountId", item.getWechatAccountId());
            payload.put("accountCode", account == null ? "" : defaultValue(account.getAccountCode(), ""));
            payload.put("accountName", account == null ? "" : defaultValue(account.getAccountName(), ""));
            payload.put("eventId", item.getEventId());
            payload.put("dispatchId", item.getDispatchId());
            payload.put("assetType", item.getAssetType());
            payload.put("storagePath", item.getStoragePath());
            payload.put("fileName", item.getFileName());
            payload.put("mimeType", item.getMimeType());
            payload.put("sha256", item.getSha256());
            payload.put("downloadStatus", item.getDownloadStatus());
            payload.put("errorMessage", item.getErrorMessage());
            payload.put("eventType", event == null ? "" : defaultValue(event.getEventType(), ""));
            payload.put("eventDirection", event == null ? "" : defaultValue(event.getDirection(), ""));
            payload.put("dispatchStatus", dispatch == null ? "" : defaultValue(dispatch.getDispatchStatus(), ""));
            payload.put("traceId", dispatch == null ? "" : defaultValue(dispatch.getTraceId(), ""));
            payload.put("peerUserId", dispatch == null ? resolvePeerUserId(event, account) : defaultValue(dispatch.getPeerUserId(), resolvePeerUserId(event, account)));
            payload.put("canPreview", event != null && StringUtils.hasText(event.getMediaPath()));
            payload.put("createTime", item.getCreateTime());
            payload.put("updateTime", item.getUpdateTime());
            return payload;
        }).toList();
    }

    private Map<String, Object> toSummaryView(WechathlinkAccount account) {
        long totalCount = countEvents(account.getId(), null);
        long inboundCount = countEvents(account.getId(), "inbound");
        long outboundCount = countEvents(account.getId(), "outbound");
        WechathlinkEvent latestEvent = findLatestEvent(account.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("wechatAccountId", account.getId());
        payload.put("accountCode", account.getAccountCode());
        payload.put("accountName", account.getAccountName());
        payload.put("baseUrl", account.getBaseUrl());
        payload.put("loginStatus", account.getLoginStatus());
        payload.put("pollStatus", account.getPollStatus());
        payload.put("totalCount", totalCount);
        payload.put("inboundCount", inboundCount);
        payload.put("outboundCount", outboundCount);
        payload.put("lastEventAt", latestEvent == null ? null : latestEvent.getCreateTime());
        return payload;
    }

    private long countEvents(Long wechatAccountId, String direction) {
        LambdaQueryWrapper<WechathlinkEvent> wrapper = new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getWechatAccountId, wechatAccountId)
                .eq(WechathlinkEvent::getIsDeleted, 0);
        if (StringUtils.hasText(direction)) {
            wrapper.eq(WechathlinkEvent::getDirection, direction);
        }
        return eventMapper.selectCount(wrapper);
    }

    private WechathlinkEvent findLatestEvent(Long wechatAccountId) {
        return eventMapper.selectOne(new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getWechatAccountId, wechatAccountId)
                .eq(WechathlinkEvent::getIsDeleted, 0)
                .orderByDesc(WechathlinkEvent::getCreateTime)
                .orderByDesc(WechathlinkEvent::getId)
                .last("LIMIT 1"));
    }

    private void touchContact(Map<String, ContactSummary> aggregate,
                              WechathlinkAccount account,
                              String contactId,
                              boolean sender,
                              WechathlinkEvent event) {
        if (!StringUtils.hasText(contactId)) {
            return;
        }
        String normalized = contactId.trim();
        if (account != null && StringUtils.hasText(account.getAccountCode()) && account.getAccountCode().trim().equals(normalized)) {
            return;
        }
        ContactSummary summary = aggregate.get(normalized);
        if (summary == null) {
            summary = new ContactSummary(normalized);
            aggregate.put(summary.contactId(), summary);
        }
        if (sender) {
            summary.incrementSenderCount();
        } else {
            summary.incrementReceiverCount();
        }
        summary.touchEvent(event);
    }

    private WechathlinkAccount requireReadableAccount(Long wechatAccountId) {
        WechathlinkAccount account = accountMapper.selectById(wechatAccountId);
        if (account == null || !permissionService.canRead(account)) {
            throw new IllegalArgumentException("wechat account not found or no permission");
        }
        return account;
    }

    private WechathlinkAccount requireMediaReadableAccount(Long wechatAccountId) {
        WechathlinkAccount account = accountMapper.selectById(wechatAccountId);
        if (account == null || !permissionService.canViewMedia(account)) {
            throw new IllegalArgumentException("wechat account not found or no permission");
        }
        return account;
    }

    private LambdaQueryWrapper<WechathlinkMessageDispatch> buildDispatchQuery(Long wechatAccountId,
                                                                              Long dispatchId,
                                                                              String contactId,
                                                                              String dispatchType,
                                                                              String dispatchStatus,
                                                                              String traceId,
                                                                              String keyword) {
        if (wechatAccountId != null) {
            requireReadableAccount(wechatAccountId);
        }
        LambdaQueryWrapper<WechathlinkMessageDispatch> wrapper = new LambdaQueryWrapper<WechathlinkMessageDispatch>()
                .eq(WechathlinkMessageDispatch::getIsDeleted, 0);
        Set<Long> accountIds = permissionService.readableAccountIds();
        if (!permissionService.standaloneMode() && !permissionService.superAccount() && wechatAccountId == null) {
            if (accountIds.isEmpty()) {
                return null;
            }
            wrapper.in(WechathlinkMessageDispatch::getWechatAccountId, accountIds);
        }
        if (wechatAccountId != null) {
            wrapper.eq(WechathlinkMessageDispatch::getWechatAccountId, wechatAccountId);
        }
        if (dispatchId != null) {
            wrapper.eq(WechathlinkMessageDispatch::getId, dispatchId);
        }
        if (StringUtils.hasText(contactId)) {
            wrapper.eq(WechathlinkMessageDispatch::getPeerUserId, contactId.trim());
        }
        if (StringUtils.hasText(dispatchType)) {
            wrapper.eq(WechathlinkMessageDispatch::getDispatchType, dispatchType.trim());
        }
        if (StringUtils.hasText(dispatchStatus)) {
            wrapper.eq(WechathlinkMessageDispatch::getDispatchStatus, dispatchStatus.trim());
        }
        if (StringUtils.hasText(traceId)) {
            wrapper.like(WechathlinkMessageDispatch::getTraceId, traceId.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(w -> w.like(WechathlinkMessageDispatch::getPayloadJson, normalizedKeyword)
                    .or().like(WechathlinkMessageDispatch::getErrorMessage, normalizedKeyword)
                    .or().like(WechathlinkMessageDispatch::getSourceId, normalizedKeyword)
                    .or().like(WechathlinkMessageDispatch::getPeerUserId, normalizedKeyword));
        }
        return wrapper;
    }

    private LambdaQueryWrapper<WechathlinkMediaAsset> buildMediaAssetQuery(Long wechatAccountId,
                                                                           Long assetId,
                                                                           Long eventId,
                                                                           Long dispatchId,
                                                                           String assetType,
                                                                           String downloadStatus,
                                                                           String keyword) {
        Long resolvedAccountId = wechatAccountId;
        if (eventId != null && eventId > 0) {
            WechathlinkEvent event = getReadableEvent(eventId);
            resolvedAccountId = resolvedAccountId == null ? event.getWechatAccountId() : resolvedAccountId;
        }
        if (dispatchId != null && dispatchId > 0) {
            WechathlinkMessageDispatch dispatch = messageDispatchMapper.selectById(dispatchId);
            if (dispatch == null || dispatch.getWechatAccountId() == null) {
                throw new IllegalArgumentException("dispatch not found");
            }
            requireReadableAccount(dispatch.getWechatAccountId());
            resolvedAccountId = resolvedAccountId == null ? dispatch.getWechatAccountId() : resolvedAccountId;
        }
        if (resolvedAccountId != null) {
            requireMediaReadableAccount(resolvedAccountId);
        }
        LambdaQueryWrapper<WechathlinkMediaAsset> wrapper = new LambdaQueryWrapper<WechathlinkMediaAsset>()
                .eq(WechathlinkMediaAsset::getIsDeleted, 0);
        Set<Long> accountIds = mediaReadableAccountIds();
        if (!permissionService.standaloneMode() && !permissionService.superAccount() && resolvedAccountId == null) {
            if (accountIds.isEmpty()) {
                return null;
            }
            wrapper.in(WechathlinkMediaAsset::getWechatAccountId, accountIds);
        }
        if (resolvedAccountId != null) {
            wrapper.eq(WechathlinkMediaAsset::getWechatAccountId, resolvedAccountId);
        }
        if (assetId != null) {
            wrapper.eq(WechathlinkMediaAsset::getId, assetId);
        }
        if (eventId != null) {
            wrapper.eq(WechathlinkMediaAsset::getEventId, eventId);
        }
        if (dispatchId != null) {
            wrapper.eq(WechathlinkMediaAsset::getDispatchId, dispatchId);
        }
        if (StringUtils.hasText(assetType)) {
            wrapper.eq(WechathlinkMediaAsset::getAssetType, assetType.trim());
        }
        if (StringUtils.hasText(downloadStatus)) {
            wrapper.eq(WechathlinkMediaAsset::getDownloadStatus, downloadStatus.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(w -> w.like(WechathlinkMediaAsset::getFileName, normalizedKeyword)
                    .or().like(WechathlinkMediaAsset::getStoragePath, normalizedKeyword)
                    .or().like(WechathlinkMediaAsset::getSha256, normalizedKeyword)
                    .or().like(WechathlinkMediaAsset::getErrorMessage, normalizedKeyword));
        }
        return wrapper;
    }

    private Map<Long, WechathlinkAccount> loadAccountMap(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return accountMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(WechathlinkAccount::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, WechathlinkEvent> loadEventMap(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return eventMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(WechathlinkEvent::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, WechathlinkMessageDispatch> loadDispatchMap(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return messageDispatchMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(WechathlinkMessageDispatch::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, Long> loadMediaAssetCountByDispatchId(Set<Long> dispatchIds) {
        if (dispatchIds == null || dispatchIds.isEmpty()) {
            return Map.of();
        }
        return mediaAssetMapper.selectList(new LambdaQueryWrapper<WechathlinkMediaAsset>()
                        .in(WechathlinkMediaAsset::getDispatchId, dispatchIds)
                        .eq(WechathlinkMediaAsset::getIsDeleted, 0))
                .stream()
                .filter(item -> item.getDispatchId() != null)
                .collect(Collectors.groupingBy(WechathlinkMediaAsset::getDispatchId, LinkedHashMap::new, Collectors.counting()));
    }

    private Set<Long> mediaReadableAccountIds() {
        if (permissionService.standaloneMode() || permissionService.superAccount()) {
            return Set.of();
        }
        return accountMapper.selectList(new LambdaQueryWrapper<WechathlinkAccount>()
                        .eq(WechathlinkAccount::getIsDeleted, 0))
                .stream()
                .filter(permissionService::canViewMedia)
                .map(WechathlinkAccount::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Long parseLongValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String resolvePeerUserId(WechathlinkEvent event, WechathlinkAccount account) {
        if (event == null) {
            return "";
        }
        String accountCode = account == null ? "" : defaultValue(account.getAccountCode(), "");
        String from = defaultValue(event.getFromUserId(), "");
        String to = defaultValue(event.getToUserId(), "");
        if (StringUtils.hasText(accountCode)) {
            if (StringUtils.hasText(from) && !accountCode.equals(from) && accountCode.equals(to)) {
                return from;
            }
            if (StringUtils.hasText(to) && !accountCode.equals(to) && accountCode.equals(from)) {
                return to;
            }
        }
        if ("outbound".equalsIgnoreCase(defaultValue(event.getDirection(), ""))) {
            return StringUtils.hasText(to) ? to : from;
        }
        return StringUtils.hasText(from) ? from : to;
    }

    private LambdaQueryWrapper<WechathlinkEvent> buildEventQuery(Long wechatAccountId,
                                                                 Long eventId,
                                                                 String contactId,
                                                                 String direction,
                                                                 String eventType,
                                                                 String dateFrom,
                                                                 String dateTo,
                                                                 String keyword,
                                                                 Integer hasMedia) {
        WechathlinkAccount account = null;
        if (wechatAccountId != null) {
            account = requireReadableAccount(wechatAccountId);
        }
        LambdaQueryWrapper<WechathlinkEvent> wrapper = new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getIsDeleted, 0);
        Set<Long> accountIds = permissionService.readableAccountIds();
        if (!permissionService.standaloneMode() && !permissionService.superAccount()) {
            if (wechatAccountId == null) {
                if (accountIds.isEmpty()) {
                    return null;
                }
                wrapper.in(WechathlinkEvent::getWechatAccountId, accountIds);
            }
        }
        if (wechatAccountId != null) {
            wrapper.eq(WechathlinkEvent::getWechatAccountId, wechatAccountId);
        }
        if (eventId != null) {
            wrapper.eq(WechathlinkEvent::getId, eventId);
        }
        if (StringUtils.hasText(contactId)) {
            String target = contactId.trim();
            if (account != null && StringUtils.hasText(account.getAccountCode()) && account.getAccountCode().trim().equals(target)) {
                return null;
            }
            wrapper.and(w -> w.eq(WechathlinkEvent::getFromUserId, target)
                    .or().eq(WechathlinkEvent::getToUserId, target));
        }
        if (StringUtils.hasText(direction)) {
            wrapper.eq(WechathlinkEvent::getDirection, direction.trim());
        }
        if (StringUtils.hasText(eventType)) {
            wrapper.eq(WechathlinkEvent::getEventType, eventType.trim());
        }
        LocalDateTime from = parseDateTime(dateFrom, false);
        if (from != null) {
            wrapper.ge(WechathlinkEvent::getCreateTime, from);
        }
        LocalDateTime to = parseDateTime(dateTo, true);
        if (to != null) {
            wrapper.le(WechathlinkEvent::getCreateTime, to);
        }
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(w -> w.like(WechathlinkEvent::getBodyText, normalizedKeyword)
                    .or().like(WechathlinkEvent::getMediaFileName, normalizedKeyword)
                    .or().like(WechathlinkEvent::getFromUserId, normalizedKeyword)
                    .or().like(WechathlinkEvent::getToUserId, normalizedKeyword));
        }
        if (hasMedia != null) {
            if (Integer.valueOf(1).equals(hasMedia)) {
                wrapper.and(w -> w.ne(WechathlinkEvent::getMediaPath, "")
                        .or().ne(WechathlinkEvent::getMediaFileName, ""));
            } else if (Integer.valueOf(0).equals(hasMedia)) {
                wrapper.and(w -> w.and(i -> i.eq(WechathlinkEvent::getMediaPath, "").or().isNull(WechathlinkEvent::getMediaPath))
                        .and(i -> i.eq(WechathlinkEvent::getMediaFileName, "").or().isNull(WechathlinkEvent::getMediaFileName)));
            }
        }
        return wrapper;
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
        }
        try {
            LocalDate date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
            return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
        } catch (Exception ignored) {
        }
        return null;
    }

    private String buildCsv(List<WechathlinkEvent> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append(csvHeader());
        for (WechathlinkEvent row : rows) {
            builder.append(csvValue(row.getId())).append(',')
                    .append(csvValue(row.getWechatAccountId())).append(',')
                    .append(csvValue(row.getCreateTime())).append(',')
                    .append(csvValue(row.getDirection())).append(',')
                    .append(csvValue(row.getEventType())).append(',')
                    .append(csvValue(row.getFromUserId())).append(',')
                    .append(csvValue(row.getToUserId())).append(',')
                    .append(csvValue(row.getMessageId())).append(',')
                    .append(csvValue(row.getBodyText())).append(',')
                    .append(csvValue(row.getMediaFileName())).append(',')
                    .append(csvValue(row.getMediaMimeType())).append(',')
                    .append(csvValue(row.getMediaPath())).append(',')
                    .append(csvValue(row.getContextToken()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String csvHeader() {
        return "\uFEFFid,wechatAccountId,createTime,direction,eventType,fromUserId,toUserId,messageId,bodyText,mediaFileName,mediaMimeType,mediaPath,contextToken\n";
    }

    private String csvValue(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
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

    private Map<String, Object> pageResult(List<?> rows, long total, Page<?> page) {
        return Map.of(
                "list", rows,
                "total", total,
                "pageNum", page.getCurrent(),
                "pageSize", page.getSize()
        );
    }

    private static final class ContactSummary {
        private final String contactId;
        private int senderCount;
        private int receiverCount;
        private LocalDateTime lastSeenAt;
        private String lastDirection;
        private String lastEventType;
        private String lastMessagePreview;

        private ContactSummary(String contactId) {
            this.contactId = contactId;
        }

        private String contactId() {
            return contactId;
        }

        private LocalDateTime lastSeenAt() {
            return lastSeenAt;
        }

        private void incrementSenderCount() {
            senderCount++;
        }

        private void incrementReceiverCount() {
            receiverCount++;
        }

        private void touchEvent(WechathlinkEvent event) {
            if (event == null || event.getCreateTime() == null) {
                return;
            }
            if (lastSeenAt == null || event.getCreateTime().isAfter(lastSeenAt)) {
                lastSeenAt = event.getCreateTime();
                lastDirection = event.getDirection();
                lastEventType = event.getEventType();
                lastMessagePreview = buildPreview(event);
            }
        }

        private Map<String, Object> toView(WechathlinkPeerContext peerContext) {
            LocalDateTime lastInboundAt = peerContext == null ? null : (peerContext.getLastInboundAt() == null ? peerContext.getLastMessageAt() : peerContext.getLastInboundAt());
            LocalDateTime replyWindowExpiresAt = peerContext == null
                    ? null
                    : (peerContext.getReplyWindowExpiresAt() == null
                    ? WechathlinkReplyWindowSupport.calculateReplyWindowExpiresAt(lastInboundAt)
                    : peerContext.getReplyWindowExpiresAt());
            String windowStatus = WechathlinkReplyWindowSupport.resolveWindowStatus(replyWindowExpiresAt);
            String contextStatus = WechathlinkReplyWindowSupport.resolveContextStatus(peerContext == null ? null : peerContext.getContextToken(), replyWindowExpiresAt);
            boolean canReply = WechathlinkReplyWindowSupport.canReply(peerContext == null ? null : peerContext.getContextToken(), contextStatus, windowStatus);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("contactId", contactId);
            payload.put("senderCount", senderCount);
            payload.put("receiverCount", receiverCount);
            payload.put("totalCount", senderCount + receiverCount);
            payload.put("lastSeenAt", lastSeenAt);
            payload.put("lastDirection", lastDirection);
            payload.put("lastEventType", lastEventType);
            payload.put("lastMessagePreview", lastMessagePreview);
            payload.put("hasContextToken", canReply);
            payload.put("canReply", canReply);
            payload.put("contextStatus", contextStatus);
            payload.put("lastInboundAt", lastInboundAt);
            payload.put("replyWindowExpiresAt", replyWindowExpiresAt);
            payload.put("windowStatus", windowStatus);
            payload.put("label", buildLabel());
            return payload;
        }

        private String buildLabel() {
            String directionText = switch (StringUtils.hasText(lastDirection) ? lastDirection.trim().toLowerCase() : "") {
                case "inbound" -> "入站";
                case "outbound" -> "出站";
                default -> "";
            };
            if (StringUtils.hasText(directionText) && StringUtils.hasText(lastMessagePreview)) {
                return contactId + " | " + directionText + " | " + lastMessagePreview;
            }
            if (StringUtils.hasText(lastMessagePreview)) {
                return contactId + " | " + lastMessagePreview;
            }
            return contactId;
        }

        private String buildPreview(WechathlinkEvent event) {
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
            return StringUtils.hasText(event.getEventType()) ? event.getEventType().trim() : "";
        }
    }
}
