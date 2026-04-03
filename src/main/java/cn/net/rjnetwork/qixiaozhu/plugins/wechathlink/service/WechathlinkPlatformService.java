package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import java.util.Map;

public interface WechathlinkPlatformService {
    Map<String, Object> summary();

    Map<String, Object> requestLogs(String actionType,
                                    String resultStatus,
                                    Integer pageNum,
                                    Integer pageSize);

    Map<String, Object> webhookDeliveries(Long wechatAccountId,
                                          String deliveryStatus,
                                          Integer pageNum,
                                          Integer pageSize);

    Map<String, Object> webhookDeliveryDetail(Long id);

    Map<String, Object> retryWebhookDelivery(Long id);
}
