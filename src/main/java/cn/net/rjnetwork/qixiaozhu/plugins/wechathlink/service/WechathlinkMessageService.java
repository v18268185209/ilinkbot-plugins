package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import java.util.Map;

public interface WechathlinkMessageService {
    Map<String, Object> listPeers(Long wechatAccountId, String keyword);

    Map<String, Object> sendText(Map<String, Object> body);

    Map<String, Object> sendMedia(Map<String, Object> body);
}
