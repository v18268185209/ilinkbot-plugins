package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;

public interface WechathlinkWebhookService {
    void deliverEvent(WechathlinkAccount account, WechathlinkEvent event);

    void retryDelivery(Long deliveryId);
}
