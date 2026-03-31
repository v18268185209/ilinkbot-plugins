package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface WechathlinkMessageService {
    Map<String, Object> listPeers(Long wechatAccountId, String keyword, Integer pageNum, Integer pageSize);

    Map<String, Object> uploadTempMedia(MultipartFile file);

    Map<String, Object> sendText(Map<String, Object> body);

    Map<String, Object> sendMedia(Map<String, Object> body);
}
