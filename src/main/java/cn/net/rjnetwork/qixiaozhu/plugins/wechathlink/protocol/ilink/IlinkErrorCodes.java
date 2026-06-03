package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink;

/**
 * WeChat ilink / 对话开放平台 错误码定义
 *
 * 参考文档:
 * - https://developers.weixin.qq.com/doc/aispeech/confapi/openinterface/errorcode.html
 * - https://developers.weixin.qq.com/doc/aispeech/confapi/thirdkefu/sendmsg.html
 */
public final class IlinkErrorCodes {

    private IlinkErrorCodes() {}

    // ========== 全局错误码 ==========
    /** 接口请求参数错误 */
    public static final int PARAM_ERROR = 110002;
    /** 内容黄反校验不通过 */
    public static final int CONTENT_VIOLATION = 110003;
    /** 无操作权限（检查BOT登录） */
    public static final int NO_PERMISSION = 210202;
    /** Bot 正在发布中 / 接口调用频繁 */
    public static final int BOT_PUBLISHING_OR_RATE_LIMITED = 210205;
    /** Json 参数解析失败 */
    public static final int JSON_PARSE_FAILED = 210106;
    /** DB 操作失败 */
    public static final int DB_OPERATION_FAILED = 210201;
    /** KV 异常 */
    public static final int KV_ERROR = 1003001;
    /** 数据解析失败 */
    public static final int DATA_PARSE_FAILED = 1000012;
    /** 服务器内部错误 */
    public static final int INTERNAL_ERROR = 1110001;

    // ========== 客服消息收发错误码 ==========
    /** token无效 */
    public static final int TOKEN_INVALID = 1001;
    /** 机器人审核没有通过 */
    public static final int BOT_NOT_APPROVED = 1002;
    /** 签名缺少userid字段 */
    public static final int SIGNATURE_MISSING_USERID = 1003;
    /** 签名字段为空 */
    public static final int SIGNATURE_EMPTY = 1004;
    /** 签名过期或无效 */
    public static final int SIGNATURE_EXPIRED = 1005;
    /** 签名校验失败 */
    public static final int SIGNATURE_INVALID = 1006;
    /** appid, category, label, desc 字段不能为空 */
    public static final int REQUIRED_FIELDS_EMPTY = 1007;
    /** appid, openid, msg 字段不能为空 */
    public static final int MSG_REQUIRED_FIELDS_EMPTY = 1008;
    /** appid不合法 */
    public static final int APPID_INVALID = 1011;
    /** 加解密参数不一致 */
    public static final int ENCRYPTION_MISMATCH = 1013;
    /** 没开通开放api / 数据加密不正确 */
    public static final int API_NOT_ENABLED = 1019;
    /** 公众号未认证 */
    public static final int OFFICIAL_ACCOUNT_NOT_VERIFIED = 3005;
    /** 敏感词 */
    public static final int SENSITIVE_WORD = 3014;

    /**
     * 判断错误码是否为可重试的错误
     */
    public static boolean isRetryable(int errcode) {
        return switch (errcode) {
            case BOT_PUBLISHING_OR_RATE_LIMITED,  // 210205: 频率限制，可重试
                 KV_ERROR,                         // 1003001: KV异常，临时错误
                 DB_OPERATION_FAILED,              // 210201: DB操作失败，临时错误
                 INTERNAL_ERROR,                    // 1110001: 服务器内部错误，可重试
                 DATA_PARSE_FAILED -> true;         // 1000012: 数据解析失败，临时错误
            default -> false;
        };
    }

    /**
     * 判断错误码是否为配置/权限类错误（不可通过重试解决）
     */
    public static boolean isConfigError(int errcode) {
        return switch (errcode) {
            case TOKEN_INVALID,           // 1001
                 BOT_NOT_APPROVED,        // 1002
                 API_NOT_ENABLED,         // 1019
                 OFFICIAL_ACCOUNT_NOT_VERIFIED, // 3005
                 NO_PERMISSION -> true;   // 210202
            default -> false;
        };
    }

    /**
     * 判断错误码是否为内容审核类错误
     */
    public static boolean isContentError(int errcode) {
        return switch (errcode) {
            case SENSITIVE_WORD,       // 3014
                 CONTENT_VIOLATION -> true;  // 110003
            default -> false;
        };
    }

    /**
     * 获取错误码的友好描述
     */
    public static String getDescription(int errcode) {
        return switch (errcode) {
            case PARAM_ERROR -> "接口请求参数错误";
            case CONTENT_VIOLATION -> "内容黄反校验不通过";
            case NO_PERMISSION -> "无操作权限（检查BOT登录）";
            case BOT_PUBLISHING_OR_RATE_LIMITED -> "接口调用频繁，请稍后重试";
            case JSON_PARSE_FAILED -> "Json参数解析失败";
            case DB_OPERATION_FAILED -> "DB操作失败";
            case KV_ERROR -> "KV异常";
            case INTERNAL_ERROR -> "服务器内部错误";
            case TOKEN_INVALID -> "token无效";
            case BOT_NOT_APPROVED -> "机器人审核没有通过";
            case SIGNATURE_MISSING_USERID -> "签名缺少userid字段";
            case SIGNATURE_EMPTY -> "签名字段为空";
            case SIGNATURE_EXPIRED -> "签名过期或无效";
            case SIGNATURE_INVALID -> "签名校验失败";
            case REQUIRED_FIELDS_EMPTY -> "必填字段为空";
            case MSG_REQUIRED_FIELDS_EMPTY -> "appid/openid/msg字段不能为空";
            case APPID_INVALID -> "appid不合法";
            case ENCRYPTION_MISMATCH -> "加解密参数不一致";
            case API_NOT_ENABLED -> "没开通开放api或数据加密不正确";
            case OFFICIAL_ACCOUNT_NOT_VERIFIED -> "公众号未认证";
            case SENSITIVE_WORD -> "消息包含敏感词";
            default -> "未知错误码: " + errcode;
        };
    }
}
