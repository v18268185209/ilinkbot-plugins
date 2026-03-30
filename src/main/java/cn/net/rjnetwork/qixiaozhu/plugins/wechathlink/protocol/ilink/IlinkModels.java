package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class IlinkModels {

    private IlinkModels() {
    }

    public record QrCodeResponse(
            @JsonProperty("qrcode") String qrcode,
            @JsonProperty("qrcode_img_content") String qrcodeImgContent,
            @JsonProperty("ret") Integer ret,
            @JsonProperty("errcode") Integer errcode,
            @JsonProperty("errmsg") String errmsg
    ) {
    }

    public record QrStatusResponse(
            @JsonProperty("status") String status,
            @JsonProperty("bot_token") String botToken,
            @JsonProperty("ilink_bot_id") String accountId,
            @JsonProperty("baseurl") String baseUrl,
            @JsonProperty("ilink_user_id") String ilinkUserId,
            @JsonProperty("ret") Integer ret,
            @JsonProperty("errcode") Integer errcode,
            @JsonProperty("errmsg") String errmsg
    ) {
    }

    public record GetUpdatesResponse(
            @JsonProperty("ret") Integer ret,
            @JsonProperty("errcode") Integer errcode,
            @JsonProperty("errmsg") String errmsg,
            @JsonProperty("msgs") List<WeixinMessage> msgs,
            @JsonProperty("get_updates_buf") String getUpdatesBuf,
            @JsonProperty("longpolling_timeout_ms") Integer longpollingTimeoutMs
    ) {
    }

    public record SendMessageResponse(
            @JsonProperty("ret") Integer ret,
            @JsonProperty("errcode") Integer errcode,
            @JsonProperty("errmsg") String errmsg
    ) {
    }

    public record UploadUrlResponse(
            @JsonProperty("upload_param") String uploadParam,
            @JsonProperty("thumb_upload_param") String thumbUploadParam
    ) {
    }

    public record CdnMedia(
            @JsonProperty("encrypt_query_param") String encryptQueryParam,
            @JsonProperty("aes_key") String aesKey,
            @JsonProperty("encrypt_type") Integer encryptType
    ) {
    }

    public record TextItem(@JsonProperty("text") String text) {
    }

    public record VoiceItem(
            @JsonProperty("media") CdnMedia media,
            @JsonProperty("text") String text,
            @JsonProperty("encode_type") Integer encodeType,
            @JsonProperty("playtime") Integer playtime
    ) {
    }

    public record FileItem(
            @JsonProperty("media") CdnMedia media,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("md5") String md5,
            @JsonProperty("len") String len
    ) {
    }

    public record ImageItem(
            @JsonProperty("media") CdnMedia media,
            @JsonProperty("thumb_media") CdnMedia thumbMedia,
            @JsonProperty("aeskey") String aeskey,
            @JsonProperty("mid_size") Integer midSize
    ) {
    }

    public record VideoItem(
            @JsonProperty("media") CdnMedia media,
            @JsonProperty("thumb_media") CdnMedia thumbMedia,
            @JsonProperty("video_size") Integer videoSize
    ) {
    }

    public record MessageItem(
            @JsonProperty("type") Integer type,
            @JsonProperty("text_item") TextItem textItem,
            @JsonProperty("voice_item") VoiceItem voiceItem,
            @JsonProperty("file_item") FileItem fileItem,
            @JsonProperty("image_item") ImageItem imageItem,
            @JsonProperty("video_item") VideoItem videoItem
    ) {
    }

    public record WeixinMessage(
            @JsonProperty("seq") Long seq,
            @JsonProperty("message_id") Long messageId,
            @JsonProperty("from_user_id") String fromUserId,
            @JsonProperty("to_user_id") String toUserId,
            @JsonProperty("client_id") String clientId,
            @JsonProperty("create_time_ms") Long createTimeMs,
            @JsonProperty("message_type") Integer messageType,
            @JsonProperty("message_state") Integer messageState,
            @JsonProperty("item_list") List<MessageItem> itemList,
            @JsonProperty("context_token") String contextToken
    ) {
    }

    public record UploadedMedia(
            String downloadEncryptedQueryParam,
            String aesKeyHex,
            int plainSize,
            int cipherSize
    ) {
    }

    public record DownloadedMedia(byte[] bytes, String fileName, String mimeType) {
    }
}
