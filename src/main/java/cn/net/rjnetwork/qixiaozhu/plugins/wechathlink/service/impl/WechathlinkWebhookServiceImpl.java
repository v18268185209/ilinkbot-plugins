package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkWebhookDelivery;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkWebhookDeliveryMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkWebhookService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.EventListener.Factory;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.Proxy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Webhook 投递服务 — 生产级增强版
 *
 * 核心能力：
 * - 指数退避重试：1s → 5s → 30s → 2min → 5min（最多10次）
 * - HTTP 超时控制：连接/读/写各 10s
 * - 响应码分类：4xx 不重试，5xx/超时 指数退避重试
 * - 投递统计：总投递数、成功数、失败数、超时数、成功率
 * - 线程安全：统计通过 AtomicLong 保障
 */
@Service
@Slf4j
public class WechathlinkWebhookServiceImpl extends WechathlinkServiceSupport
        implements WechathlinkWebhookService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_DELIVERY_ATTEMPTS = 10;
    private static final int[] RETRY_DELAYS_SECONDS = { 1, 5, 30, 120, 300 }; // 指数退避
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final WechathlinkWebhookDeliveryMapper webhookDeliveryMapper;
    private final WechathlinkRuntimeConfigService runtimeConfigService;
    private final OkHttpClient httpClient;

    // 投递统计（线程安全）
    private final AtomicLong totalDelivered = new AtomicLong(0);
    private final AtomicLong totalSuccess = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalTimeout = new AtomicLong(0);

    public WechathlinkWebhookServiceImpl(WechathlinkWebhookDeliveryMapper webhookDeliveryMapper,
            WechathlinkRuntimeConfigService runtimeConfigService) {
        this.webhookDeliveryMapper = webhookDeliveryMapper;
        this.runtimeConfigService = runtimeConfigService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(HTTP_TIMEOUT)
                .readTimeout(HTTP_TIMEOUT)
                .writeTimeout(HTTP_TIMEOUT)
                .eventListenerFactory(call -> new EventListener() {
                    @Override
                    public void callStart(Call call) {
                        totalDelivered.incrementAndGet();
                    }

                    @Override
                    public void callEnd(Call call) {
                        totalSuccess.incrementAndGet();
                    }
                })
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
        delivery.setDeliveryType(
                (defaultValue(event.getDirection(), "event")).toUpperCase() + "_EVENT");
        delivery.setTargetUrl(normalizedUrl);
        delivery.setRequestBody(requestBody);
        delivery.setResponseBody("");
        delivery.setResponseCode(null);
        delivery.setDeliveryStatus("PENDING");
        delivery.setAttemptCount(0);
        delivery.setErrorMessage("");
        delivery.setTraceId(traceId);
        fillAudit(delivery, account);
        delivery.setCreateTime(LocalDateTime.now());
        delivery.setUpdateTime(LocalDateTime.now());
        webhookDeliveryMapper.insert(delivery);
        // 立即尝试投递
        try {
            retryDeliveryInternal(delivery);
        } catch (Exception ex) {
            log.error("webhook initial delivery error for deliveryId={}: {}",
                    delivery.getId(), ex.getMessage());
        }
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
        try {
            retryDeliveryInternal(delivery);
        } catch (Exception ex) {
            delivery.setDeliveryStatus("FAILED");
            delivery.setErrorMessage("manual retry error: " + ex.getMessage());
            delivery.setUpdateTime(LocalDateTime.now());
            webhookDeliveryMapper.updateById(delivery);
        }
    }

    /**
     * 内部投递逻辑 - 支持指数退避重试
     */
    private void retryDeliveryInternal(WechathlinkWebhookDelivery delivery) {
        int attempt = delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount();
        if (attempt >= MAX_DELIVERY_ATTEMPTS) {
            markPermanentlyFailed(delivery, "Exceeded max delivery attempts ("
                    + MAX_DELIVERY_ATTEMPTS + ")");
            return;
        }

        String requestBody = delivery.getRequestBody();
        if (!StringUtils.hasText(requestBody)) {
            markDeliveryFailed(delivery, "empty request body");
            return;
        }

        Request request = new Request.Builder()
                .url(delivery.getTargetUrl())
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-Wechathlink-TraceId",
                        defaultValue(delivery.getTraceId(), ""))
                .header("X-Wechathlink-EventId",
                        String.valueOf(delivery.getEventId()))
                .header("X-Wechathlink-Delivery-Attempt",
                        String.valueOf(attempt + 1))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String responseBody = body != null ? body.string() : "";

            if (response.isSuccessful()) {
                markDeliverySuccess(delivery, response.code(), responseBody);
            } else {
                handleNonSuccessResponse(delivery, response.code(), responseBody);
            }
        } catch (IOException ex) {
            handleIoError(delivery, ex.getMessage());
        } catch (Exception ex) {
            markDeliveryFailed(delivery, "delivery error: "
                    + ex.getMessage());
        }
    }

    private void handleNonSuccessResponse(WechathlinkWebhookDelivery delivery,
            int code, String responseBody) {
        String errorMsg = "HTTP " + code + " - "
                + responseBody.substring(0, Math.min(200, responseBody.length()));

        // 客户端错误（4xx 除了 429）不重试
        if (code >= 400 && code < 500 && code != 429) {
            markDeliveryFailed(delivery, errorMsg);
            return;
        }

        // 服务器错误或限流 - 安排重试
        delivery.setAttemptCount(
                (delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount()) + 1);
        delivery.setDeliveryStatus("FAILED");
        delivery.setErrorMessage(errorMsg);
        delivery.setResponseCode(code);
        delivery.setUpdateTime(LocalDateTime.now());
        webhookDeliveryMapper.updateById(delivery);

        scheduleRetry(delivery);
    }

    private void handleIoError(WechathlinkWebhookDelivery delivery, String errorMsg) {
        delivery.setAttemptCount(
                (delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount()) + 1);
        delivery.setDeliveryStatus("FAILED");
        delivery.setErrorMessage("connection error: " + errorMsg);
        delivery.setResponseCode(null);
        delivery.setUpdateTime(LocalDateTime.now());
        webhookDeliveryMapper.updateById(delivery);
        totalTimeout.incrementAndGet();
        scheduleRetry(delivery);
    }

    private void scheduleRetry(WechathlinkWebhookDelivery delivery) {
        int attempt = delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount();
        int delayIndex = Math.min(attempt, RETRY_DELAYS_SECONDS.length - 1);
        int delaySeconds = RETRY_DELAYS_SECONDS[delayIndex];

        log.debug("scheduling webhook retry for deliveryId={} after {}s (attempt {}/{})",
                delivery.getId(), delaySeconds, attempt + 1, MAX_DELIVERY_ATTEMPTS);

        new Thread(() -> {
            try {
                Thread.sleep(delaySeconds * 1000L);
                retryDeliveryInternal(delivery);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("webhook retry interrupted for deliveryId={}", delivery.getId());
            }
        }, "webhook-retry-" + delivery.getId()).start();
    }

    private void markDeliverySuccess(WechathlinkWebhookDelivery delivery, int code,
            String responseBody) {
        delivery.setDeliveryStatus("SUCCESS");
        delivery.setResponseCode(code);
        delivery.setResponseBody(
                responseBody.length() > 2000 ? responseBody.substring(0, 2000) : responseBody);
        delivery.setErrorMessage("");
        delivery.setUpdateTime(LocalDateTime.now());
        webhookDeliveryMapper.updateById(delivery);
        log.debug("webhook delivery success for deliveryId={} (HTTP {})", delivery.getId(), code);
    }

    private void markDeliveryFailed(WechathlinkWebhookDelivery delivery, String errorMsg) {
        int attempt = delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount();
        if (attempt >= MAX_DELIVERY_ATTEMPTS) {
            markPermanentlyFailed(delivery, errorMsg);
        } else {
            delivery.setDeliveryStatus("FAILED");
            delivery.setErrorMessage(errorMsg);
            delivery.setUpdateTime(LocalDateTime.now());
            webhookDeliveryMapper.updateById(delivery);
            totalFailed.incrementAndGet();
            log.warn("webhook delivery failed for deliveryId={}, attempt={}, error={}",
                    delivery.getId(), attempt, errorMsg);
        }
    }

    private void markPermanentlyFailed(WechathlinkWebhookDelivery delivery, String errorMsg) {
        delivery.setDeliveryStatus("FAILED");
        delivery.setErrorMessage(errorMsg + " [MAX_ATTEMPTS_EXCEEDED]");
        delivery.setUpdateTime(LocalDateTime.now());
        webhookDeliveryMapper.updateById(delivery);
        log.error("webhook delivery PERMANENTLY FAILED for deliveryId={}, max attempts exceeded: {}",
                delivery.getId(), errorMsg);
    }

    private Map<String, Object> buildPayload(WechathlinkAccount account,
            WechathlinkEvent event, String traceId) {
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

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private void fillAudit(WechathlinkWebhookDelivery entity, WechathlinkAccount account) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setCreateUserId(account == null ? null : account.getOwnerUserId());
        entity.setUpdateUserId(account == null ? null : account.getOwnerUserId());
        entity.setCompanyId(account == null ? null : account.getCompanyId());
        entity.setDeptId(account == null ? null : account.getDeptId());
        entity.setIsDeleted(entity.getIsDeleted() == null ? 0 : entity.getIsDeleted());
        entity.setStatus(entity.getStatus() == null ? 1 : entity.getStatus());
    }

    /**
     * 获取 webhook 投递统计
     */
    @Override
    public WechatWebhookStats getStats() {
        long totalDeliveredVal = totalDelivered.get();
        long totalSuccessVal = totalSuccess.get();
        long totalFailedVal = totalFailed.get();
        long totalTimeoutVal = totalTimeout.get();
        double successRate = totalDeliveredVal > 0
                ? (double) totalSuccessVal / totalDeliveredVal * 100
                : 100.0;
        return new WechatWebhookStats(totalDeliveredVal, totalSuccessVal, totalFailedVal,
                totalTimeoutVal,
                Math.round(successRate * 100.0) / 100.0);
    }
}
