package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import java.util.Map;

public interface WechathlinkSettingsService {
    Map<String, Object> get();

    Map<String, Object> save(Map<String, Object> body);
}
