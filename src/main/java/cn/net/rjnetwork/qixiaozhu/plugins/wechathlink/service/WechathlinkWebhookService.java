package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;

public interface WechathlinkWebhookService {
    void deliverEvent(WechathlinkAccount account, WechathlinkEvent event);

    /**
     * 手动重试 webhook 投递
     */
    void retryDelivery(Long deliveryId);

    /**
     * 获取 webhook 统计数据
     */
    WechatWebhookStats getStats();

    /**
     * Webhook 统计数据
     */
    record WechatWebhookStats(
        long totalDelivered,
        long totalSuccess,
        long totalFailed,
        long totalTimeout,
        double successRate
    ) {}
}
