package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.config.WechathlinkResolvedDataSourceSpec;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAuditLog;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkWebhookDelivery;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAuditLogMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkWebhookDeliveryMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkPlatformService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkWebhookService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WechathlinkPlatformServiceImpl extends WechathlinkServiceSupport implements WechathlinkPlatformService {

    private static final String RESOURCE_TYPE_OPEN_API = "OPEN_API";

    private final WechathlinkAuditLogMapper auditLogMapper;
    private final WechathlinkWebhookDeliveryMapper webhookDeliveryMapper;
    private final WechathlinkResolvedDataSourceSpec dataSourceSpec;
    private final Environment environment;
    private final WechathlinkPermissionService permissionService;
    private final WechathlinkWebhookService webhookService;
    private final WechathlinkAuditService auditService;

    public WechathlinkPlatformServiceImpl(WechathlinkAuditLogMapper auditLogMapper,
                                          WechathlinkWebhookDeliveryMapper webhookDeliveryMapper,
                                          WechathlinkResolvedDataSourceSpec dataSourceSpec,
                                          Environment environment,
                                          WechathlinkPermissionService permissionService,
                                          WechathlinkWebhookService webhookService,
                                          WechathlinkAuditService auditService) {
        this.auditLogMapper = auditLogMapper;
        this.webhookDeliveryMapper = webhookDeliveryMapper;
        this.dataSourceSpec = dataSourceSpec;
        this.environment = environment;
        this.permissionService = permissionService;
        this.webhookService = webhookService;
        this.auditService = auditService;
    }

    @Override
    public Map<String, Object> summary() {
        requirePlatformPermission();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        long totalToday = countOpenRequestLogs(null, dayStart);
        long successToday = countOpenRequestLogs(WechathlinkAuditServiceImpl.RESULT_SUCCESS, dayStart);
        long failureToday = countOpenRequestLogs(WechathlinkAuditServiceImpl.RESULT_FAILURE, dayStart);
        long webhookSuccessToday = countWebhookDeliveries("SUCCESS", dayStart);
        long webhookFailureToday = countWebhookDeliveries("FAILED", dayStart);
        List<Map<String, Object>> recentLogs = auditLogMapper.selectList(new LambdaQueryWrapper<WechathlinkAuditLog>()
                        .eq(WechathlinkAuditLog::getIsDeleted, 0)
                        .eq(WechathlinkAuditLog::getResourceType, RESOURCE_TYPE_OPEN_API)
                        .orderByDesc(WechathlinkAuditLog::getCreateTime)
                        .orderByDesc(WechathlinkAuditLog::getId)
                        .last("LIMIT 10"))
                .stream()
                .map(this::toView)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runMode", dataSourceSpec.mode());
        payload.put("listenAddr", environment.getProperty("eqadmin.wechathlink.runtime.listen-addr", "127.0.0.1:17890"));
        payload.put("defaultBaseUrl", environment.getProperty("eqadmin.wechathlink.runtime.default-base-url", "https://ilinkai.weixin.qq.com"));
        payload.put("cdnBaseUrl", environment.getProperty("eqadmin.wechathlink.runtime.cdn-base-url", "https://novac2c.cdn.weixin.qq.com/c2c"));
        payload.put("webhookUrl", environment.getProperty("eqadmin.wechathlink.runtime.webhook-url", ""));
        payload.put("requestCountToday", totalToday);
        payload.put("successCountToday", successToday);
        payload.put("failureCountToday", failureToday);
        payload.put("webhookSuccessToday", webhookSuccessToday);
        payload.put("webhookFailureToday", webhookFailureToday);
        payload.put("recentRequests", recentLogs);
        return payload;
    }

    @Override
    public Map<String, Object> requestLogs(String actionType,
                                           String resultStatus,
                                           Integer pageNum,
                                           Integer pageSize) {
        requirePlatformPermission();
        Page<WechathlinkAuditLog> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<WechathlinkAuditLog> wrapper = new LambdaQueryWrapper<WechathlinkAuditLog>()
                .eq(WechathlinkAuditLog::getIsDeleted, 0)
                .eq(WechathlinkAuditLog::getResourceType, RESOURCE_TYPE_OPEN_API);
        if (hasText(actionType)) {
            wrapper.eq(WechathlinkAuditLog::getActionType, actionType.trim().toUpperCase());
        }
        if (hasText(resultStatus)) {
            wrapper.eq(WechathlinkAuditLog::getResultStatus, resultStatus.trim().toUpperCase());
        }
        Page<WechathlinkAuditLog> result = auditLogMapper.selectPage(
                page,
                wrapper.orderByDesc(WechathlinkAuditLog::getCreateTime).orderByDesc(WechathlinkAuditLog::getId)
        );
        return Map.of(
                "list", result.getRecords().stream().map(this::toView).toList(),
                "total", result.getTotal(),
                "pageNum", result.getCurrent(),
                "pageSize", result.getSize()
        );
    }

    @Override
    public Map<String, Object> webhookDeliveries(Long wechatAccountId,
                                                 String deliveryStatus,
                                                 Integer pageNum,
                                                 Integer pageSize) {
        requirePlatformPermission();
        Page<WechathlinkWebhookDelivery> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<WechathlinkWebhookDelivery> wrapper = new LambdaQueryWrapper<WechathlinkWebhookDelivery>()
                .eq(WechathlinkWebhookDelivery::getIsDeleted, 0);
        applyVisibleWebhookFilter(wrapper, wechatAccountId);
        if (wechatAccountId != null) {
            wrapper.eq(WechathlinkWebhookDelivery::getWechatAccountId, wechatAccountId);
        }
        if (hasText(deliveryStatus)) {
            wrapper.eq(WechathlinkWebhookDelivery::getDeliveryStatus, deliveryStatus.trim().toUpperCase());
        }
        Page<WechathlinkWebhookDelivery> result = webhookDeliveryMapper.selectPage(
                page,
                wrapper.orderByDesc(WechathlinkWebhookDelivery::getCreateTime).orderByDesc(WechathlinkWebhookDelivery::getId)
        );
        return Map.of(
                "list", result.getRecords().stream().map(this::toWebhookView).toList(),
                "total", result.getTotal(),
                "pageNum", result.getCurrent(),
                "pageSize", result.getSize()
        );
    }

    @Override
    public Map<String, Object> webhookDeliveryDetail(Long id) {
        requirePlatformPermission();
        if (id == null) {
            throw new IllegalArgumentException("delivery id required");
        }
        WechathlinkWebhookDelivery delivery = webhookDeliveryMapper.selectById(id);
        if (delivery == null || Integer.valueOf(1).equals(delivery.getIsDeleted())) {
            throw new IllegalArgumentException("webhook delivery not found");
        }
        applyVisibleWebhookFilter(new LambdaQueryWrapper<>(), delivery.getWechatAccountId());
        Map<String, Object> payload = toWebhookView(delivery);
        payload.put("requestBody", delivery.getRequestBody());
        payload.put("responseBody", delivery.getResponseBody());
        payload.put("targetUrl", delivery.getTargetUrl());
        return payload;
    }

    @Override
    public Map<String, Object> retryWebhookDelivery(Long id) {
        requirePlatformPermission();
        if (id == null) {
            throw new IllegalArgumentException("delivery id required");
        }
        WechathlinkWebhookDelivery delivery = webhookDeliveryMapper.selectById(id);
        if (delivery == null || Integer.valueOf(1).equals(delivery.getIsDeleted())) {
            throw new IllegalArgumentException("webhook delivery not found");
        }
        applyVisibleWebhookFilter(new LambdaQueryWrapper<>(), delivery.getWechatAccountId());
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("deliveryId", delivery.getId());
        auditPayload.put("wechatAccountId", delivery.getWechatAccountId());
        auditPayload.put("eventId", delivery.getEventId());
        auditPayload.put("deliveryStatus", delivery.getDeliveryStatus());
        try {
            webhookService.retryDelivery(id);
            WechathlinkWebhookDelivery updated = webhookDeliveryMapper.selectById(id);
            auditService.recordSuccess(delivery.getWechatAccountId(), "WEBHOOK_RETRY", "WEBHOOK_DELIVERY", id, "webhook delivery retried", auditPayload);
            return webhookDeliveryDetail(updated.getId());
        } catch (RuntimeException ex) {
            auditService.recordFailure(delivery.getWechatAccountId(), "WEBHOOK_RETRY", "WEBHOOK_DELIVERY", id, "webhook delivery retry failed", ex, auditPayload);
            throw ex;
        }
    }

    private long countOpenRequestLogs(String resultStatus, LocalDateTime dayStart) {
        LambdaQueryWrapper<WechathlinkAuditLog> wrapper = new LambdaQueryWrapper<WechathlinkAuditLog>()
                .eq(WechathlinkAuditLog::getIsDeleted, 0)
                .eq(WechathlinkAuditLog::getResourceType, RESOURCE_TYPE_OPEN_API)
                .ge(WechathlinkAuditLog::getCreateTime, dayStart);
        if (hasText(resultStatus)) {
            wrapper.eq(WechathlinkAuditLog::getResultStatus, resultStatus.trim().toUpperCase());
        }
        return auditLogMapper.selectCount(wrapper);
    }

    private long countWebhookDeliveries(String deliveryStatus, LocalDateTime dayStart) {
        LambdaQueryWrapper<WechathlinkWebhookDelivery> wrapper = new LambdaQueryWrapper<WechathlinkWebhookDelivery>()
                .eq(WechathlinkWebhookDelivery::getIsDeleted, 0)
                .ge(WechathlinkWebhookDelivery::getCreateTime, dayStart);
        if (hasText(deliveryStatus)) {
            wrapper.eq(WechathlinkWebhookDelivery::getDeliveryStatus, deliveryStatus.trim().toUpperCase());
        }
        return webhookDeliveryMapper.selectCount(wrapper);
    }

    private Map<String, Object> toView(WechathlinkAuditLog log) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", log.getId());
        payload.put("wechatAccountId", log.getWechatAccountId());
        payload.put("actionType", log.getActionType());
        payload.put("resourceType", log.getResourceType());
        payload.put("resourceId", log.getResourceId());
        payload.put("resultStatus", log.getResultStatus());
        payload.put("summary", log.getSummary());
        payload.put("detailJson", log.getDetailJson());
        payload.put("createTime", log.getCreateTime());
        return payload;
    }

    private Map<String, Object> toWebhookView(WechathlinkWebhookDelivery delivery) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", delivery.getId());
        payload.put("wechatAccountId", delivery.getWechatAccountId());
        payload.put("eventId", delivery.getEventId());
        payload.put("deliveryType", delivery.getDeliveryType());
        payload.put("targetUrl", delivery.getTargetUrl());
        payload.put("responseCode", delivery.getResponseCode());
        payload.put("deliveryStatus", delivery.getDeliveryStatus());
        payload.put("attemptCount", delivery.getAttemptCount());
        payload.put("errorMessage", delivery.getErrorMessage());
        payload.put("traceId", delivery.getTraceId());
        payload.put("createTime", delivery.getCreateTime());
        return payload;
    }

    private void applyVisibleWebhookFilter(LambdaQueryWrapper<WechathlinkWebhookDelivery> wrapper, Long wechatAccountId) {
        if (permissionService.standaloneMode() || permissionService.superAccount()) {
            return;
        }
        Set<Long> readable = permissionService.readableAccountIds();
        if (wechatAccountId != null) {
            if (!readable.contains(wechatAccountId)) {
                throw new IllegalArgumentException("webhook delivery not found or no permission");
            }
            return;
        }
        if (readable.isEmpty()) {
            wrapper.isNull(WechathlinkWebhookDelivery::getWechatAccountId);
            return;
        }
        wrapper.in(WechathlinkWebhookDelivery::getWechatAccountId, readable);
    }

    private void requirePlatformPermission() {
        if (!permissionService.canViewAudit() && !permissionService.canManageSettings()) {
            throw new IllegalArgumentException("platform not found or no permission");
        }
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
}
