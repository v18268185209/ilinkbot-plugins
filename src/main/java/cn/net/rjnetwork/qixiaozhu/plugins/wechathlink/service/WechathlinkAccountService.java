package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import java.util.Map;

public interface WechathlinkAccountService {
    Map<String, Object> list(String keyword);

    Map<String, Object> detail(Long id);

    Map<String, Object> save(Map<String, Object> body);

    Map<String, Object> toggle(Long id, Integer status);

    Map<String, Object> saveMember(Map<String, Object> body);

    Map<String, Object> startLogin(Map<String, Object> body);

    Map<String, Object> loginStatus(String sessionCode);
}
