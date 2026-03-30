package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
public class IlinkClient implements IlinkApi {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType OCTET_STREAM_MEDIA_TYPE = MediaType.parse("application/octet-stream");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final OkHttpClient httpClient;

    public IlinkClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .followRedirects(true)
                .followSslRedirects(true)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();
    }

    @Override
    public IlinkModels.QrCodeResponse fetchQrCode(String baseUrl, int timeoutMs) {
        IlinkModels.QrCodeResponse response = get(
                baseUrl + "/ilink/bot/get_bot_qrcode?bot_type=3",
                null,
                timeoutMs,
                IlinkModels.QrCodeResponse.class,
                true
        );
        ensureSuccess("fetch qrcode", response == null ? null : response.ret(), response == null ? null : response.errcode(), response == null ? null : response.errmsg());
        return response;
    }

    @Override
    public IlinkModels.QrStatusResponse fetchQrCodeStatus(String baseUrl, String qrCode, int timeoutMs) {
        String endpoint = baseUrl + "/ilink/bot/get_qrcode_status?qrcode=" + urlEncode(qrCode);
        IlinkModels.QrStatusResponse response = get(endpoint, Map.of("iLink-App-ClientVersion", "1"), timeoutMs, IlinkModels.QrStatusResponse.class, true);
        ensureSuccess("fetch qrcode status", response == null ? null : response.ret(), response == null ? null : response.errcode(), response == null ? null : response.errmsg());
        return response;
    }

    @Override
    public IlinkModels.GetUpdatesResponse getUpdates(String baseUrl, String token, String getUpdatesBuf, String channelVersion, int timeoutMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("get_updates_buf", getUpdatesBuf == null ? "" : getUpdatesBuf);
        body.put("base_info", Map.of("channel_version", channelVersion));
        return post(baseUrl + "/ilink/bot/getupdates", token, body, timeoutMs, IlinkModels.GetUpdatesResponse.class);
    }

    @Override
    public void sendTextMessage(String baseUrl, String token, String toUserId, String text, String contextToken, String channelVersion, int timeoutMs) {
        Map<String, Object> msg = baseMessage(toUserId, contextToken);
        msg.put("item_list", List.of(Map.of(
                "type", 1,
                "text_item", Map.of("text", text)
        )));
        sendMessage(baseUrl, token, msg, channelVersion, timeoutMs);
    }

    @Override
    public void sendImageMessage(String baseUrl, String token, String toUserId, String contextToken, String text, IlinkModels.UploadedMedia uploadedMedia, String channelVersion, int timeoutMs) {
        sendMediaItems(baseUrl, token, toUserId, contextToken, text, Map.of(
                "type", 2,
                "image_item", Map.of(
                        "media", Map.of(
                                "encrypt_query_param", uploadedMedia.downloadEncryptedQueryParam(),
                                "aes_key", Base64.getEncoder().encodeToString(uploadedMedia.aesKeyHex().getBytes(StandardCharsets.UTF_8)),
                                "encrypt_type", 1
                        ),
                        "mid_size", uploadedMedia.cipherSize()
                )
        ), channelVersion, timeoutMs);
    }

    @Override
    public void sendVideoMessage(String baseUrl, String token, String toUserId, String contextToken, String text, IlinkModels.UploadedMedia uploadedMedia, String channelVersion, int timeoutMs) {
        sendMediaItems(baseUrl, token, toUserId, contextToken, text, Map.of(
                "type", 5,
                "video_item", Map.of(
                        "media", Map.of(
                                "encrypt_query_param", uploadedMedia.downloadEncryptedQueryParam(),
                                "aes_key", Base64.getEncoder().encodeToString(uploadedMedia.aesKeyHex().getBytes(StandardCharsets.UTF_8)),
                                "encrypt_type", 1
                        ),
                        "video_size", uploadedMedia.cipherSize()
                )
        ), channelVersion, timeoutMs);
    }

    @Override
    public void sendFileMessage(String baseUrl, String token, String toUserId, String contextToken, String text, String fileName, IlinkModels.UploadedMedia uploadedMedia, String channelVersion, int timeoutMs) {
        sendMediaItems(baseUrl, token, toUserId, contextToken, text, Map.of(
                "type", 4,
                "file_item", Map.of(
                        "media", Map.of(
                                "encrypt_query_param", uploadedMedia.downloadEncryptedQueryParam(),
                                "aes_key", Base64.getEncoder().encodeToString(uploadedMedia.aesKeyHex().getBytes(StandardCharsets.UTF_8)),
                                "encrypt_type", 1
                        ),
                        "file_name", fileName,
                        "len", String.valueOf(uploadedMedia.plainSize())
                )
        ), channelVersion, timeoutMs);
    }

    @Override
    public void sendVoiceMessage(String baseUrl, String token, String toUserId, String contextToken, String text, int encodeType, IlinkModels.UploadedMedia uploadedMedia, String channelVersion, int timeoutMs) {
        sendMediaItems(baseUrl, token, toUserId, contextToken, text, Map.of(
                "type", 3,
                "voice_item", Map.of(
                        "media", Map.of(
                                "encrypt_query_param", uploadedMedia.downloadEncryptedQueryParam(),
                                "aes_key", Base64.getEncoder().encodeToString(uploadedMedia.aesKeyHex().getBytes(StandardCharsets.UTF_8)),
                                "encrypt_type", 1
                        ),
                        "encode_type", encodeType,
                        "text", ""
                )
        ), channelVersion, timeoutMs);
    }

    @Override
    public IlinkModels.UploadedMedia uploadLocalMedia(String cdnBaseUrl, String baseUrl, String token, String toUserId, Path filePath, int mediaType, String channelVersion, int timeoutMs) {
        try {
            byte[] plaintext = Files.readAllBytes(filePath);
            String rawMd5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(plaintext));
            byte[] aesKey = new byte[16];
            byte[] fileKeyBytes = new byte[16];
            new SecureRandom().nextBytes(aesKey);
            new SecureRandom().nextBytes(fileKeyBytes);
            String fileKey = HexFormat.of().formatHex(fileKeyBytes);
            byte[] ciphertext = encryptAesEcb(plaintext, aesKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("filekey", fileKey);
            body.put("media_type", mediaType);
            body.put("to_user_id", toUserId);
            body.put("rawsize", plaintext.length);
            body.put("rawfilemd5", rawMd5);
            body.put("filesize", ciphertext.length);
            body.put("no_need_thumb", true);
            body.put("aeskey", HexFormat.of().formatHex(aesKey));
            body.put("base_info", Map.of("channel_version", channelVersion));

            IlinkModels.UploadUrlResponse uploadUrlResponse = post(baseUrl + "/ilink/bot/getuploadurl", token, body, timeoutMs, IlinkModels.UploadUrlResponse.class);
            String downloadParam = uploadCiphertextToCdn(cdnBaseUrl, uploadUrlResponse.uploadParam(), fileKey, ciphertext, timeoutMs);
            return new IlinkModels.UploadedMedia(downloadParam, HexFormat.of().formatHex(aesKey), plaintext.length, ciphertext.length);
        } catch (Exception ex) {
            throw new IllegalStateException("Upload local media failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public IlinkModels.DownloadedMedia downloadMessageMedia(String cdnBaseUrl, IlinkModels.MessageItem item, int timeoutMs) {
        try {
            if (item == null || item.type() == null) {
                throw new IllegalArgumentException("message item required");
            }
            return switch (item.type()) {
                case 2 -> {
                    IlinkModels.ImageItem imageItem = item.imageItem();
                    if (imageItem == null || imageItem.media() == null) {
                        throw new IllegalStateException("image media is missing");
                    }
                    String aes = imageItem.aeskey() == null ? imageItem.media().aesKey() : Base64.getEncoder().encodeToString(imageItem.aeskey().getBytes(StandardCharsets.UTF_8));
                    byte[] data = downloadCdnMedia(cdnBaseUrl, imageItem.media().encryptQueryParam(), aes, timeoutMs);
                    yield new IlinkModels.DownloadedMedia(data, "image.jpg", "image/jpeg");
                }
                case 3 -> {
                    IlinkModels.VoiceItem voiceItem = item.voiceItem();
                    if (voiceItem == null || voiceItem.media() == null) {
                        throw new IllegalStateException("voice media is missing");
                    }
                    byte[] data = downloadCdnMedia(cdnBaseUrl, voiceItem.media().encryptQueryParam(), voiceItem.media().aesKey(), timeoutMs);
                    yield new IlinkModels.DownloadedMedia(data, "voice.silk", "audio/silk");
                }
                case 4 -> {
                    IlinkModels.FileItem fileItem = item.fileItem();
                    if (fileItem == null || fileItem.media() == null) {
                        throw new IllegalStateException("file media is missing");
                    }
                    byte[] data = downloadCdnMedia(cdnBaseUrl, fileItem.media().encryptQueryParam(), fileItem.media().aesKey(), timeoutMs);
                    String fileName = StringUtils.hasText(fileItem.fileName()) ? fileItem.fileName() : "file.bin";
                    String mimeType = Files.probeContentType(Path.of(fileName));
                    yield new IlinkModels.DownloadedMedia(data, fileName, mimeType == null ? "application/octet-stream" : mimeType);
                }
                case 5 -> {
                    IlinkModels.VideoItem videoItem = item.videoItem();
                    if (videoItem == null || videoItem.media() == null) {
                        throw new IllegalStateException("video media is missing");
                    }
                    byte[] data = downloadCdnMedia(cdnBaseUrl, videoItem.media().encryptQueryParam(), videoItem.media().aesKey(), timeoutMs);
                    yield new IlinkModels.DownloadedMedia(data, "video.mp4", "video/mp4");
                }
                default -> throw new IllegalStateException("unsupported message item type: " + item.type());
            };
        } catch (Exception ex) {
            throw new IllegalStateException("Download message media failed: " + ex.getMessage(), ex);
        }
    }

    // ========== private: message assembly ==========

    private void sendMediaItems(String baseUrl, String token, String toUserId, String contextToken, String text, Map<String, Object> mediaItem, String channelVersion, int timeoutMs) {
        List<Map<String, Object>> items;
        if (StringUtils.hasText(text)) {
            items = List.of(
                    Map.of("type", 1, "text_item", Map.of("text", text)),
                    mediaItem
            );
        } else {
            items = List.of(mediaItem);
        }
        for (Map<String, Object> item : items) {
            Map<String, Object> msg = baseMessage(toUserId, contextToken);
            msg.put("item_list", List.of(item));
            sendMessage(baseUrl, token, msg, channelVersion, timeoutMs);
        }
    }

    private Map<String, Object> baseMessage(String toUserId, String contextToken) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("from_user_id", "");
        msg.put("to_user_id", toUserId);
        msg.put("client_id", "wechathlink-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999));
        msg.put("message_type", 2);
        msg.put("message_state", 2);
        if (StringUtils.hasText(contextToken)) {
            msg.put("context_token", contextToken.trim());
        }
        return msg;
    }

    private void sendMessage(String baseUrl, String token, Map<String, Object> msg, String channelVersion, int timeoutMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg", msg);
        body.put("base_info", Map.of("channel_version", channelVersion));
        IlinkModels.SendMessageResponse response = post(baseUrl + "/ilink/bot/sendmessage", token, body, timeoutMs, IlinkModels.SendMessageResponse.class);
        if ((response.errcode() != null && response.errcode() != 0) || (response.ret() != null && response.ret() != 0)) {
            throw new IllegalStateException((response.errmsg() == null ? "sendmessage returned non-zero status" : response.errmsg())
                    + " (ret=" + response.ret() + ", errcode=" + response.errcode() + ")");
        }
    }

    // ========== private: CDN operations ==========

    private String uploadCiphertextToCdn(String cdnBaseUrl, String uploadParam, String fileKey, byte[] ciphertext, int timeoutMs) {
        String url = cdnBaseUrl.replaceAll("/+$", "") + "/upload?encrypted_query_param=" + urlEncode(uploadParam) + "&filekey=" + urlEncode(fileKey);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(ciphertext, OCTET_STREAM_MEDIA_TYPE))
                .build();
        try (Response response = executeRaw(request, timeoutMs)) {
            if (!response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                throw new IllegalStateException("cdn upload http " + response.code() + ": " + respBody);
            }
            String encryptedParam = response.header("x-encrypted-param", "");
            if (!StringUtils.hasText(encryptedParam)) {
                throw new IllegalStateException("cdn upload response missing x-encrypted-param");
            }
            return encryptedParam;
        } catch (IOException ex) {
            throw new IllegalStateException("cdn upload failed: " + ex.getMessage(), ex);
        }
    }

    private byte[] downloadCdnMedia(String cdnBaseUrl, String encryptedQueryParam, String aesKeyBase64, int timeoutMs) throws Exception {
        String url = cdnBaseUrl.replaceAll("/+$", "") + "/download?encrypted_query_param=" + urlEncode(encryptedQueryParam);
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        byte[] responseBytes;
        try (Response response = executeRaw(request, timeoutMs)) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("cdn download http " + response.code());
            }
            ResponseBody body = response.body();
            responseBytes = body != null ? body.bytes() : new byte[0];
        }
        if (!StringUtils.hasText(aesKeyBase64)) {
            return responseBytes;
        }
        byte[] key = parseAesKey(aesKeyBase64);
        return decryptAesEcb(responseBytes, key);
    }

    // ========== private: HTTP transport (OkHttp) ==========

    private <T> T get(String url, Map<String, String> extraHeaders, int timeoutMs, Class<T> responseType, boolean ilinkHeaders) {
        Request.Builder builder = new Request.Builder().url(url).get();
        if (ilinkHeaders) {
            applyIlinkHeaders(builder);
        }
        if (extraHeaders != null) {
            extraHeaders.forEach(builder::addHeader);
        }
        return executeJson(builder.build(), timeoutMs, responseType);
    }

    private <T> T post(String url, String token, Object body, int timeoutMs, Class<T> responseType) {
        try {
            String json = objectMapper.writeValueAsString(body);
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON_MEDIA_TYPE));
            applyIlinkHeaders(builder);
            if (StringUtils.hasText(token)) {
                builder.addHeader("Authorization", "Bearer " + token.trim());
            }
            return executeJson(builder.build(), timeoutMs, responseType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Serialize request body failed: " + ex.getMessage(), ex);
        }
    }

    private <T> T executeJson(Request request, int timeoutMs, Class<T> responseType) {
        try (Response response = executeRaw(request, timeoutMs)) {
            if (!response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                throw new IllegalStateException("ilink http " + response.code() + ": " + respBody);
            }
            if (responseType == null) {
                return null;
            }
            ResponseBody body = response.body();
            byte[] bytes = body != null ? body.bytes() : new byte[0];
            if (bytes.length == 0) {
                return null;
            }
            return objectMapper.readValue(bytes, responseType);
        } catch (IOException ex) {
            throw new IllegalStateException("Ilink request failed: " + ex.getMessage(), ex);
        }
    }

    private Response executeRaw(Request request, int timeoutMs) throws IOException {
        OkHttpClient client = httpClient.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
        return client.newCall(request).execute();
    }

    private void applyIlinkHeaders(Request.Builder builder) {
        builder.addHeader("AuthorizationType", "ilink_bot_token");
        builder.addHeader("X-WECHAT-UIN", randomWechatUin());
    }

    private void ensureSuccess(String operation, Integer ret, Integer errcode, String errmsg) {
        if ((ret != null && ret != 0) || (errcode != null && errcode != 0)) {
            String message = StringUtils.hasText(errmsg) ? errmsg.trim() : operation + " returned non-zero status";
            throw new IllegalStateException(message + " (ret=" + ret + ", errcode=" + errcode + ")");
        }
    }

    // ========== private: crypto ==========

    private byte[] parseAesKey(String aesKeyBase64) {
        byte[] decoded = Base64.getDecoder().decode(aesKeyBase64);
        if (decoded.length == 16) {
            return decoded;
        }
        if (decoded.length == 32) {
            return HexFormat.of().parseHex(new String(decoded, StandardCharsets.UTF_8));
        }
        throw new IllegalStateException("unexpected aes_key length " + decoded.length);
    }

    private byte[] encryptAesEcb(byte[] plaintext, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(plaintext);
    }

    private byte[] decryptAesEcb(byte[] ciphertext, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(ciphertext);
    }

    // ========== private: utilities ==========

    private String randomWechatUin() {
        return Base64.getEncoder().encodeToString(("wechat-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
