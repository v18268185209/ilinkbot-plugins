package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support;

import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public final class WechathlinkReplyWindowSupport {

    public static final String CONTEXT_ACTIVE = "ACTIVE";
    public static final String CONTEXT_INVALID = "INVALID";
    public static final String CONTEXT_EXPIRED = "EXPIRED";

    public static final String WINDOW_OPEN = "OPEN";
    public static final String WINDOW_CLOSING_SOON = "CLOSING_SOON";
    public static final String WINDOW_CLOSED = "CLOSED";

    private static final long REPLY_WINDOW_HOURS = 24L;
    private static final long CLOSING_SOON_HOURS = 2L;

    private WechathlinkReplyWindowSupport() {
    }

    public static LocalDateTime calculateReplyWindowExpiresAt(LocalDateTime lastInboundAt) {
        if (lastInboundAt == null) {
            return null;
        }
        return lastInboundAt.plusHours(REPLY_WINDOW_HOURS);
    }

    public static String resolveWindowStatus(LocalDateTime replyWindowExpiresAt) {
        if (replyWindowExpiresAt == null) {
            return WINDOW_CLOSED;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!replyWindowExpiresAt.isAfter(now)) {
            return WINDOW_CLOSED;
        }
        if (!replyWindowExpiresAt.isAfter(now.plusHours(CLOSING_SOON_HOURS))) {
            return WINDOW_CLOSING_SOON;
        }
        return WINDOW_OPEN;
    }

    public static String resolveContextStatus(String contextToken, LocalDateTime replyWindowExpiresAt) {
        if (!StringUtils.hasText(contextToken)) {
            return CONTEXT_INVALID;
        }
        String windowStatus = resolveWindowStatus(replyWindowExpiresAt);
        if (WINDOW_CLOSED.equals(windowStatus)) {
            return CONTEXT_EXPIRED;
        }
        return CONTEXT_ACTIVE;
    }

    public static boolean canReply(String contextToken, String contextStatus, String windowStatus) {
        return StringUtils.hasText(contextToken)
                && CONTEXT_ACTIVE.equalsIgnoreCase(defaultText(contextStatus))
                && (WINDOW_OPEN.equalsIgnoreCase(defaultText(windowStatus))
                || WINDOW_CLOSING_SOON.equalsIgnoreCase(defaultText(windowStatus)));
    }

    public static String defaultText(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
