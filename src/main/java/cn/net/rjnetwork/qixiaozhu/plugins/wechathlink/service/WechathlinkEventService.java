package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;

import java.util.Map;

public interface WechathlinkEventService {
    Map<String, Object> summary(String keyword, Integer pageNum, Integer pageSize);

    Map<String, Object> list(Long wechatAccountId,
                            Long eventId,
                            String contactId,
                            String direction,
                            String eventType,
                            String dateFrom,
                            String dateTo,
                            String keyword,
                            Integer hasMedia,
                            Integer pageNum,
                            Integer pageSize);

    byte[] export(Long wechatAccountId,
                  String contactId,
                  String direction,
                  String eventType,
                  String dateFrom,
                  String dateTo,
                  String keyword,
                  Integer hasMedia);

    Map<String, Object> contacts(Long wechatAccountId, String keyword, Integer pageNum, Integer pageSize);

    Map<String, Object> dispatches(Long wechatAccountId,
                                   Long dispatchId,
                                   String contactId,
                                   String dispatchType,
                                   String dispatchStatus,
                                   String traceId,
                                   String keyword,
                                   Integer pageNum,
                                   Integer pageSize);

    Map<String, Object> mediaAssets(Long wechatAccountId,
                                    Long assetId,
                                    Long eventId,
                                    Long dispatchId,
                                    String assetType,
                                    String downloadStatus,
                                    String keyword,
                                    Integer pageNum,
                                    Integer pageSize);

    WechathlinkEvent getReadableEvent(Long eventId);
}
