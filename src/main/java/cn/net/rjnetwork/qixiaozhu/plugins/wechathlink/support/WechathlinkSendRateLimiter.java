package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkMessageDispatch;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkMessageDispatchMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks message send rate per account and provides throttling
 * to avoid triggering WeChat rate limits.
 *
 * WeChat ilink limits (empirical):
 * - Max ~10 messages per second per account recommended
 * - Burst up to 20/sec briefly, but sustained high rate risks ban
 */
@Component
@Slf4j
public class WechathlinkSendRateLimiter {

    /** Max messages per second per account */
    private static final int MAX_MESSAGES_PER_SECOND = 8;
    /** Max messages per minute per account */
    private static final int MAX_MESSAGES_PER_MINUTE = 120;
    /** Cooldown in ms when rate limit is hit */
    private static final long COOLDOWN_MS = 2000;

    private final ConcurrentHashMap<Long, AccountRateTracker> trackers = new ConcurrentHashMap<>();

    /**
     * Check if sending is allowed for this account.
     * Returns the number of milliseconds to wait before sending (0 = send now).
     */
    public long tryAcquire(Long accountId) {
        AccountRateTracker tracker = trackers.computeIfAbsent(accountId, k -> new AccountRateTracker());
        return tracker.tryAcquire();
    }

    /**
     * Wait until sending is allowed, then mark as sent.
     */
    public void acquireAndWait(Long accountId) throws InterruptedException {
        long waitMs;
        while ((waitMs = tryAcquire(accountId)) > 0) {
            log.debug("rate limit: accountId={}, waitMs={}", accountId, waitMs);
            Thread.sleep(waitMs);
        }
    }

    public void reset(Long accountId) {
        trackers.remove(accountId);
    }

    public ConcurrentHashMap<Long, AccountRateTracker> getTrackers() {
        return trackers;
    }

    static class AccountRateTracker {
        private final AtomicInteger secondCount = new AtomicInteger(0);
        private final AtomicInteger minuteCount = new AtomicInteger(0);
        private final AtomicLong lastSecond = new AtomicLong(0);
        private final AtomicLong lastMinute = new AtomicLong(0);
        private final AtomicLong lastSend = new AtomicLong(0);

        synchronized long tryAcquire() {
            long now = System.currentTimeMillis();
            long currentSecond = now / 1000;
            long currentMinute = now / 60000;

            // Reset counters if time window changed
            if (currentSecond != lastSecond.getAndSet(currentSecond)) {
                secondCount.set(0);
            }
            if (currentMinute != lastMinute.getAndSet(currentMinute)) {
                minuteCount.set(0);
            }

            // Check per-second limit
            if (secondCount.get() >= MAX_MESSAGES_PER_SECOND) {
                return 1000 - (now % 1000); // Wait until next second
            }

            // Check per-minute limit
            if (minuteCount.get() >= MAX_MESSAGES_PER_MINUTE) {
                return COOLDOWN_MS;
            }

            // All good, increment counters
            secondCount.incrementAndGet();
            minuteCount.incrementAndGet();
            lastSend.set(now);
            return 0;
        }
    }
}
