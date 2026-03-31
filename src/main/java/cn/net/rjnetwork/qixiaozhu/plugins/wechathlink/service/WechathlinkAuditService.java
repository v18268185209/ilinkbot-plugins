package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import java.util.Map;

public interface WechathlinkAuditService {
    void recordSuccess(Long wechatAccountId,
                       String actionType,
                       String resourceType,
                       Object resourceId,
                       String summary,
                       Map<String, Object> detail);

    void recordFailure(Long wechatAccountId,
                       String actionType,
                       String resourceType,
                       Object resourceId,
                       String summary,
                       Throwable error,
                       Map<String, Object> detail);

    Map<String, Object> list(Long wechatAccountId,
                             Long operatorUserId,
                             String actionType,
                             String resultStatus,
                             Integer pageNum,
                             Integer pageSize);

    Map<String, Object> detail(Long id);
}
