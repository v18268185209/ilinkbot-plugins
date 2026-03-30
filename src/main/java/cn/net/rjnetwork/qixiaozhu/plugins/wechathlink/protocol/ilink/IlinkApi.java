package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink;

import java.nio.file.Path;

public interface IlinkApi {

    int UPLOAD_MEDIA_TYPE_IMAGE = 1;
    int UPLOAD_MEDIA_TYPE_VIDEO = 2;
    int UPLOAD_MEDIA_TYPE_FILE = 3;
    int UPLOAD_MEDIA_TYPE_VOICE = 4;

    IlinkModels.QrCodeResponse fetchQrCode(String baseUrl, int timeoutMs);

    IlinkModels.QrStatusResponse fetchQrCodeStatus(String baseUrl, String qrCode, int timeoutMs);

    IlinkModels.GetUpdatesResponse getUpdates(String baseUrl, String token, String getUpdatesBuf, String channelVersion, int timeoutMs);

    void sendTextMessage(String baseUrl, String token, String toUserId, String text, String contextToken, String channelVersion, int timeoutMs);

    void sendImageMessage(String baseUrl, String token, String toUserId, String contextToken, String text, IlinkModels.UploadedMedia uploadedMedia, String channelVersion, int timeoutMs);

    void sendVideoMessage(String baseUrl, String token, String toUserId, String contextToken, String text, IlinkModels.UploadedMedia uploadedMedia, String channelVersion, int timeoutMs);

    void sendFileMessage(String baseUrl, String token, String toUserId, String contextToken, String text, String fileName, IlinkModels.UploadedMedia uploadedMedia, String channelVersion, int timeoutMs);

    void sendVoiceMessage(String baseUrl, String token, String toUserId, String contextToken, String text, int encodeType, IlinkModels.UploadedMedia uploadedMedia, String channelVersion, int timeoutMs);

    IlinkModels.UploadedMedia uploadLocalMedia(String cdnBaseUrl, String baseUrl, String token, String toUserId, Path filePath, int mediaType, String channelVersion, int timeoutMs);

    IlinkModels.DownloadedMedia downloadMessageMedia(String cdnBaseUrl, IlinkModels.MessageItem item, int timeoutMs);
}
