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

    /**
     * 手动启动账号轮询（长轮询拉取消息）
     */
    Map<String, Object> startPoller(Long id);

    /**
     * 手动停止账号轮询
     */
    Map<String, Object> stopPoller(Long id);

    /**
     * 账号健康检查（返回轮询状态、运行时状态、最近错误）
     */
    Map<String, Object> healthCheck(Long id);
}
