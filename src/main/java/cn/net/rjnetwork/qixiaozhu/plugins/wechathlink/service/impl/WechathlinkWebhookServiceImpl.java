package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkWebhookDelivery;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkWebhookDeliveryMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkWebhookService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WechathlinkWebhookServiceImpl extends WechathlinkServiceSupport implements WechathlinkWebhookService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final WechathlinkWebhookDeliveryMapper webhookDeliveryMapper;
    private final WechathlinkRuntimeConfigService runtimeConfigService;
    private final OkHttpClient httpClient;

    public WechathlinkWebhookServiceImpl(WechathlinkWebhookDeliveryMapper webhookDeliveryMapper,
                                         WechathlinkRuntimeConfigService runtimeConfigService) {
        this.webhookDeliveryMapper = webhookDeliveryMapper;
        this.runtimeConfigService = runtimeConfigService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .writeTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void deliverEvent(WechathlinkAccount account, WechathlinkEvent event) {
        if (account == null || account.getId() == null || event == null || event.getId() == null) {
            return;
        }
        String webhookUrl = runtimeConfigService.current().webhookUrl();
        if (!StringUtils.hasText(webhookUrl)) {
            return;
        }
        String normalizedUrl = webhookUrl.trim();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String requestBody = writeJsonObject(buildPayload(account, event, traceId));
        WechathlinkWebhookDelivery delivery = new WechathlinkWebhookDelivery();
        delivery.setWechatAccountId(account.getId());
        delivery.setEventId(event.getId());
        delivery.setDeliveryType(defaultValue(event.getDirection(), "event").toUpperCase() + "_EVENT");
        delivery.setTargetUrl(normalizedUrl);
        delivery.setRequestBody(requestBody);
        delivery.setResponseBody("");
        delivery.setResponseCode(null);
        delivery.setDeliveryStatus("PENDING");
        delivery.setAttemptCount(1);
        delivery.setErrorMessage("");
        delivery.setTraceId(traceId);
        fillAudit(delivery, account);
        executeDelivery(delivery);
        delivery.setUpdateTime(LocalDateTime.now());
        delivery.setUpdateUserId(delivery.getCreateUserId());
        webhookDeliveryMapper.insert(delivery);
    }

    @Override
    public void retryDelivery(Long deliveryId) {
        if (deliveryId == null) {
            throw new IllegalArgumentException("delivery id required");
        }
        WechathlinkWebhookDelivery delivery = webhookDeliveryMapper.selectById(deliveryId);
        if (delivery == null || Integer.valueOf(1).equals(delivery.getIsDeleted())) {
            throw new IllegalArgumentException("webhook delivery not found");
        }
        delivery.setAttemptCount((delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount()) + 1);
        delivery.setDeliveryStatus("PENDING");
        delivery.setErrorMessage("");
        executeDelivery(delivery);
        delivery.setUpdateTime(LocalDateTime.now());
        webhookDeliveryMapper.updateById(delivery);
    }

    private void executeDelivery(WechathlinkWebhookDelivery delivery) {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(delivery.getTargetUrl())
                    .post(RequestBody.create(defaultValue(delivery.getRequestBody(), ""), JSON_MEDIA_TYPE))
                    .header("X-Wechathlink-TraceId", defaultValue(delivery.getTraceId(), ""));
            if (delivery.getEventId() != null) {
                builder.header("X-Wechathlink-EventId", String.valueOf(delivery.getEventId()));
            }
            try (Response response = httpClient.newCall(builder.build()).execute()) {
                ResponseBody body = response.body();
                String responseBody = body == null ? "" : body.string();
                delivery.setResponseCode(response.code());
                delivery.setResponseBody(responseBody);
                delivery.setDeliveryStatus(response.isSuccessful() ? "SUCCESS" : "FAILED");
                delivery.setErrorMessage(response.isSuccessful() ? "" : "webhook http " + response.code());
            }
        } catch (Exception ex) {
            delivery.setResponseCode(null);
            delivery.setResponseBody("");
            delivery.setDeliveryStatus("FAILED");
            delivery.setErrorMessage(ex.getMessage());
        }
    }

    private Map<String, Object> buildPayload(WechathlinkAccount account, WechathlinkEvent event, String traceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("id", event.getId());
        eventPayload.put("wechatAccountId", event.getWechatAccountId());
        eventPayload.put("direction", defaultValue(event.getDirection(), ""));
        eventPayload.put("eventType", defaultValue(event.getEventType(), ""));
        eventPayload.put("fromUserId", defaultValue(event.getFromUserId(), ""));
        eventPayload.put("toUserId", defaultValue(event.getToUserId(), ""));
        eventPayload.put("messageId", event.getMessageId());
        eventPayload.put("contextToken", defaultValue(event.getContextToken(), ""));
        eventPayload.put("bodyText", defaultValue(event.getBodyText(), ""));
        eventPayload.put("mediaPath", defaultValue(event.getMediaPath(), ""));
        eventPayload.put("mediaFileName", defaultValue(event.getMediaFileName(), ""));
        eventPayload.put("mediaMimeType", defaultValue(event.getMediaMimeType(), ""));
        eventPayload.put("createTime", event.getCreateTime());
        Map<String, Object> accountPayload = new LinkedHashMap<>();
        accountPayload.put("id", account.getId());
        accountPayload.put("accountCode", defaultValue(account.getAccountCode(), ""));
        accountPayload.put("accountName", defaultValue(account.getAccountName(), ""));
        payload.put("traceId", traceId);
        payload.put("service", "wechat-hlink");
        payload.put("event", eventPayload);
        payload.put("account", accountPayload);
        return payload;
    }

    private void fillAudit(WechathlinkWebhookDelivery entity, WechathlinkAccount account) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setCreateUserId(account.getOwnerUserId());
        entity.setUpdateUserId(account.getOwnerUserId());
        entity.setCompanyId(account.getCompanyId());
        entity.setDeptId(account.getDeptId());
        entity.setIsDeleted(0);
        entity.setStatus(1);
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
