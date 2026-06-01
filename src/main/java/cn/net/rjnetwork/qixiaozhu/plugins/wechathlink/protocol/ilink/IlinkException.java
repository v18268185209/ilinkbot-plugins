package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink;

/**
 * ilink 协议调用异常，携带微信错误码以便上层做精细化处理
 */
public class IlinkException extends RuntimeException {

    private final Integer errcode;
    private final String errmsg;

    public IlinkException(Integer errcode, String errmsg) {
        super(buildMessage(errcode, errmsg));
        this.errcode = errcode;
        this.errmsg = errmsg;
    }

    public IlinkException(Integer errcode, String errmsg, Throwable cause) {
        super(buildMessage(errcode, errmsg), cause);
        this.errcode = errcode;
        this.errmsg = errmsg;
    }

    public Integer getErrcode() {
        return errcode;
    }

    public String getErrmsg() {
        return errmsg;
    }

    /**
     * 是否可重试
     */
    public boolean isRetryable() {
        return errcode != null && IlinkErrorCodes.isRetryable(errcode);
    }

    /**
     * 是否为配置/权限错误
     */
    public boolean isConfigError() {
        return errcode != null && IlinkErrorCodes.isConfigError(errcode);
    }

    /**
     * 是否为内容审核错误
     */
    public boolean isContentError() {
        return errcode != null && IlinkErrorCodes.isContentError(errcode);
    }

    private static String buildMessage(Integer errcode, String errmsg) {
        if (errcode == null) {
            return errmsg != null ? errmsg : "ilink error";
        }
        String desc = IlinkErrorCodes.getDescription(errcode);
        if (errmsg != null && !errmsg.equals(desc)) {
            return desc + " - " + errmsg + " (errcode=" + errcode + ")";
        }
        return desc + " (errcode=" + errcode + ")";
    }
}
