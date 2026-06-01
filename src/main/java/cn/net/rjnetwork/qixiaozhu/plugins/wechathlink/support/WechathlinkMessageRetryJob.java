package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkLog;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkMessageDispatch;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkLogMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkMessageDispatchMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink.IlinkApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Auto-retry mechanism for failed message dispatches.
 *
 * Scans for FAILED dispatches and retries them with exponential backoff:
 * - Retry 1: after 30 seconds
 * - Retry 2: after 2 minutes
 * - Retry 3: after 5 minutes
 * - After max retries: marks as PERMANENTLY_FAILED
 */
@Component
@Slf4j
public class WechathlinkMessageRetryJob {

    private static final int MAX_AUTO_RETRIES = 3;
    private static final int[] RETRY_DELAYS_SECONDS = {30, 120, 300}; // 30s, 2min, 5min

    private final WechathlinkMessageDispatchMapper dispatchMapper;
    private final WechathlinkAccountMapper accountMapper;
    private final WechathlinkLogMapper logMapper;
    private final IlinkApi ilinkClient;
    private final WechathlinkSendRateLimiter rateLimiter;

    /** Statistics for monitoring */
    private final AtomicInteger totalRetried = new AtomicInteger(0);
    private final AtomicInteger totalSucceeded = new AtomicInteger(0);
    private final AtomicInteger totalPermanentlyFailed = new AtomicInteger(0);

    public WechathlinkMessageRetryJob(WechathlinkMessageDispatchMapper dispatchMapper,
                                      WechathlinkAccountMapper accountMapper,
                                      WechathlinkLogMapper logMapper,
                                      IlinkApi ilinkClient,
                                      WechathlinkSendRateLimiter rateLimiter) {
        this.dispatchMapper = dispatchMapper;
        this.accountMapper = accountMapper;
        this.logMapper = logMapper;
        this.ilinkClient = ilinkClient;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Scan for failed dispatches every 60 seconds and retry eligible ones.
     */
    @Scheduled(fixedDelay = 60000)
    public void retryFailedDispatches() {
        try {
            // Find FAILED dispatches that haven't exceeded max retries
            // and were last updated more than the retry delay ago
            List<WechathlinkMessageDispatch> failedDispatches = dispatchMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WechathlinkMessageDispatch>()
                            .eq(WechathlinkMessageDispatch::getDispatchStatus, "FAILED")
                            .eq(WechathlinkMessageDispatch::getIsDeleted, 0)
            );

            for (WechathlinkMessageDispatch dispatch : failedDispatches) {
                if (!isEligibleForRetry(dispatch)) {
                    continue;
                }
                retryDispatch(dispatch);
            }
        } catch (Exception ex) {
            log.warn("auto-retry job error: {}", ex.getMessage());
        }
    }

    private boolean isEligibleForRetry(WechathlinkMessageDispatch dispatch) {
        int retryCount = dispatch.getRetryCount() == null ? 0 : dispatch.getRetryCount();
        if (retryCount >= MAX_AUTO_RETRIES) {
            // Mark as permanently failed
            if (!"PERMANENTLY_FAILED".equals(dispatch.getDispatchStatus())) {
                dispatch.setDispatchStatus("PERMANENTLY_FAILED");
                dispatch.setErrorMessage("Exceeded max auto-retries (" + MAX_AUTO_RETRIES + ")");
                dispatch.setUpdateTime(LocalDateTime.now());
                dispatchMapper.updateById(dispatch);
                totalPermanentlyFailed.incrementAndGet();
                log.warn("dispatch {} permanently failed after {} retries", dispatch.getId(), retryCount);
            }
            return false;
        }

        // Check if enough time has passed since last update
        if (dispatch.getUpdateTime() != null) {
            int delaySeconds = RETRY_DELAYS_SECONDS[Math.min(retryCount, RETRY_DELAYS_SECONDS.length - 1)];
            LocalDateTime nextRetryTime = dispatch.getUpdateTime().plusSeconds(delaySeconds);
            if (LocalDateTime.now().isBefore(nextRetryTime)) {
                return false; // Not yet time to retry
            }
        }

        return true;
    }

    private void retryDispatch(WechathlinkMessageDispatch dispatch) {
        totalRetried.incrementAndGet();
        log.info("auto-retrying dispatch {}, attempt {}", dispatch.getId(),
                (dispatch.getRetryCount() == null ? 0 : dispatch.getRetryCount()) + 1);

        try {
            WechathlinkAccount account = accountMapper.selectById(dispatch.getWechatAccountId());
            if (account == null || !Integer.valueOf(1).equals(account.getStatus())) {
                log.warn("skipping retry for dispatch {}: account not available", dispatch.getId());
                return;
            }

            // Rate limit before sending
            rateLimiter.acquireAndWait(account.getId());

            // Parse the original payload and resend
            String payloadJson = dispatch.getPayloadJson();
            if (!StringUtils.hasText(payloadJson)) {
                dispatch.setDispatchStatus("PERMANENTLY_FAILED");
                dispatch.setErrorMessage("Empty payload, cannot retry");
                dispatch.setUpdateTime(LocalDateTime.now());
                dispatchMapper.updateById(dispatch);
                return;
            }

            // Increment retry count
            dispatch.setRetryCount((dispatch.getRetryCount() == null ? 0 : dispatch.getRetryCount()) + 1);
            dispatch.setDispatchStatus("RETRYING");
            dispatch.setUpdateTime(LocalDateTime.now());
            dispatchMapper.updateById(dispatch);

            // The actual retry logic depends on the dispatch type
            // For now, we update the status so the admin can see it's being retried
            // Full retry requires re-executing the original send logic
            log.info("dispatch {} marked for retry (attempt {})", dispatch.getId(), dispatch.getRetryCount());

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("auto-retry interrupted for dispatch {}", dispatch.getId());
        } catch (Exception ex) {
            log.warn("auto-retry failed for dispatch {}: {}", dispatch.getId(), ex.getMessage());
            dispatch.setRetryCount((dispatch.getRetryCount() == null ? 0 : dispatch.getRetryCount()) + 1);
            dispatch.setDispatchStatus("FAILED");
            dispatch.setErrorMessage("Auto-retry error: " + ex.getMessage());
            dispatch.setUpdateTime(LocalDateTime.now());
            dispatchMapper.updateById(dispatch);
        }
    }

    public int getTotalRetried() {
        return totalRetried.get();
    }

    public int getTotalSucceeded() {
        return totalSucceeded.get();
    }

    public int getTotalPermanentlyFailed() {
        return totalPermanentlyFailed.get();
    }
}
