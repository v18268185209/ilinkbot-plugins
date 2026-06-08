package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkWebhookDelivery;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkWebhookDeliveryMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl.WechathlinkRuntimeConfigService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Webhook 交付自动重试调度器
 * 自动重试失败和超时的 webhook 投递记录
 */
@Component
@Slf4j
public class WechathlinkWebhookRetryScheduler {

    private static final Set<String> RETRYABLE_STATUSES = Set.of("PENDING", "FAILED", "TIMEOUT");
    private static final int MAX_RETRIES_BEFORE_SUSPEND = 10;

    private final WechathlinkWebhookDeliveryMapper deliveryMapper;
    private final WechathlinkWebhookService webhookService;
    private final WechathlinkRuntimeConfigService runtimeConfigService;

    public WechathlinkWebhookRetryScheduler(WechathlinkWebhookDeliveryMapper deliveryMapper,
                                            WechathlinkWebhookService webhookService,
                                            WechathlinkRuntimeConfigService runtimeConfigService) {
        this.deliveryMapper = deliveryMapper;
        this.webhookService = webhookService;
        this.runtimeConfigService = runtimeConfigService;
    }

    /**
     * 每5分钟重试一次失败的 webhook 投递（最多重试10次）
     */
    @Scheduled(fixedRate = 300000)
    public void retryFailedDeliveries() {
        if (!hasWebhookConfig()) {
            return;
        }
        try {
            List<WechathlinkWebhookDelivery> pending = deliveryMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WechathlinkWebhookDelivery>()
                            .in(WechathlinkWebhookDelivery::getDeliveryStatus, RETRYABLE_STATUSES)
                            .le(WechathlinkWebhookDelivery::getAttemptCount, MAX_RETRIES_BEFORE_SUSPEND)
                            .eq(WechathlinkWebhookDelivery::getIsDeleted, 0)
                            .orderByAsc(WechathlinkWebhookDelivery::getCreateTime)
                            .last("LIMIT 20")
            );
            for (WechathlinkWebhookDelivery delivery : pending) {
                try {
                    webhookService.retryDelivery(delivery.getId());
                    log.debug("webhook retry succeeded for deliveryId={}", delivery.getId());
                } catch (Exception ex) {
                    log.warn("webhook retry failed for deliveryId={}, error={}", delivery.getId(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("webhook retry scheduler error: {}", ex.getMessage());
        }
    }

    private boolean hasWebhookConfig() {
        try {
            return runtimeConfigService.current().webhookUrl() != null
                    && !runtimeConfigService.current().webhookUrl().isBlank();
        } catch (Exception ex) {
            return false;
        }
    }
}
