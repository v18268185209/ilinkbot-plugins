package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;

import java.util.Map;

public interface WechathlinkEventService {
    Map<String, Object> summary(String keyword, Integer pageNum, Integer pageSize);

    Map<String, Object> list(Long wechatAccountId, String direction, String eventType, Integer pageNum, Integer pageSize);

    Map<String, Object> contacts(Long wechatAccountId, String keyword, Integer pageNum, Integer pageSize);

    WechathlinkEvent getReadableEvent(Long eventId);
}
