package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkBotRuntime;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkLog;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkPeerContext;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkBotRuntimeMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkEventMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkLogMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkPeerContextMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkDashboardService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support.poller.WechathlinkPollerManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WechathlinkDashboardServiceImpl implements WechathlinkDashboardService {

    private final WechathlinkAccountMapper accountMapper;
    private final WechathlinkBotRuntimeMapper botRuntimeMapper;
    private final WechathlinkEventMapper eventMapper;
    private final WechathlinkLogMapper logMapper;
    private final WechathlinkPeerContextMapper peerContextMapper;
    private final WechathlinkPermissionService permissionService;
    private final WechathlinkPollerManager pollerManager;

    public WechathlinkDashboardServiceImpl(WechathlinkAccountMapper accountMapper,
                                           WechathlinkBotRuntimeMapper botRuntimeMapper,
                                           WechathlinkEventMapper eventMapper,
                                           WechathlinkLogMapper logMapper,
                                           WechathlinkPeerContextMapper peerContextMapper,
                                           WechathlinkPermissionService permissionService,
                                           WechathlinkPollerManager pollerManager) {
        this.accountMapper = accountMapper;
        this.botRuntimeMapper = botRuntimeMapper;
        this.eventMapper = eventMapper;
        this.logMapper = logMapper;
        this.peerContextMapper = peerContextMapper;
        this.permissionService = permissionService;
        this.pollerManager = pollerManager;
    }

    @Override
    public Map<String, Object> summary() {
        List<WechathlinkAccount> visibleAccounts = accountMapper.selectList(new LambdaQueryWrapper<WechathlinkAccount>()
                .eq(WechathlinkAccount::getIsDeleted, 0)
                .orderByDesc(WechathlinkAccount::getUpdateTime));
        if (!permissionService.standaloneMode() && !permissionService.superAccount()) {
            visibleAccounts = visibleAccounts.stream().filter(permissionService::canRead).toList();
        }
        if (visibleAccounts.isEmpty()) {
            return emptySummary();
        }
        Set<Long> accountIds = visibleAccounts.stream().map(WechathlinkAccount::getId).collect(java.util.stream.Collectors.toSet());
        List<WechathlinkAccount> accounts = visibleAccounts.stream().limit(6).toList();
        long total = visibleAccounts.size();
        long enabled = accounts.stream().filter(item -> Integer.valueOf(1).equals(item.getStatus())).count();

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountCount", total);
        payload.put("enabledAccountCount", enabled);
        payload.put("pollingAccountCount", pollerManager.runningCount());
        payload.put("inboundTodayCount", countEvents(startOfDay, accountIds, "inbound"));
        payload.put("outboundTodayCount", countEvents(startOfDay, accountIds, "outbound"));
        payload.put("errorCount", logMapper.selectCount(new LambdaQueryWrapper<WechathlinkLog>()
                .eq(WechathlinkLog::getIsDeleted, 0)
                .in(WechathlinkLog::getWechatAccountId, accountIds)
                .eq(WechathlinkLog::getLevel, "ERROR")));
        List<WechathlinkBotRuntime> expiringRuntimes = loadExpiringRuntimes(accountIds);
        List<WechathlinkPeerContext> closingWindows = loadClosingWindows(accountIds);
        payload.put("expiringRuntimeCount", expiringRuntimes.size());
        payload.put("closingWindowCount", closingWindows.size());
        payload.put("recentAccounts", accounts);
        payload.put("recentLogs", logMapper.selectList(new LambdaQueryWrapper<WechathlinkLog>()
                .eq(WechathlinkLog::getIsDeleted, 0)
                .in(WechathlinkLog::getWechatAccountId, accountIds)
                .orderByDesc(WechathlinkLog::getCreateTime)
                .last("LIMIT 6")));
        payload.put("expiringRuntimes", expiringRuntimes.stream().map(this::toRuntimeView).toList());
        payload.put("closingWindows", closingWindows.stream().map(this::toWindowView).toList());
        payload.put("recentEvents", eventMapper.selectList(new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getIsDeleted, 0)
                .in(WechathlinkEvent::getWechatAccountId, accountIds)
                .orderByDesc(WechathlinkEvent::getCreateTime)
                .last("LIMIT 6")));
        return payload;
    }

    private Map<String, Object> emptySummary() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountCount", 0L);
        payload.put("enabledAccountCount", 0L);
        payload.put("pollingAccountCount", 0L);
        payload.put("inboundTodayCount", 0L);
        payload.put("outboundTodayCount", 0L);
        payload.put("errorCount", 0L);
        payload.put("expiringRuntimeCount", 0L);
        payload.put("closingWindowCount", 0L);
        payload.put("recentAccounts", List.of());
        payload.put("recentLogs", List.of());
        payload.put("expiringRuntimes", List.of());
        payload.put("closingWindows", List.of());
        payload.put("recentEvents", List.of());
        return payload;
    }

    private long countEvents(LocalDateTime startOfDay, Set<Long> accountIds, String direction) {
        if (accountIds == null || accountIds.isEmpty()) {
            return 0L;
        }
        LambdaQueryWrapper<WechathlinkEvent> wrapper = new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getIsDeleted, 0)
                .ge(WechathlinkEvent::getCreateTime, startOfDay)
                .eq(WechathlinkEvent::getDirection, direction)
                .in(WechathlinkEvent::getWechatAccountId, accountIds);
        return eventMapper.selectCount(wrapper);
    }

    private List<WechathlinkBotRuntime> loadExpiringRuntimes(Set<Long> accountIds) {
        LocalDateTime horizon = LocalDateTime.now().plusHours(2);
        return botRuntimeMapper.selectList(new LambdaQueryWrapper<WechathlinkBotRuntime>()
                .eq(WechathlinkBotRuntime::getIsDeleted, 0)
                .eq(WechathlinkBotRuntime::getIsActive, 1)
                .in(WechathlinkBotRuntime::getWechatAccountId, accountIds)
                .isNotNull(WechathlinkBotRuntime::getExpiresAt)
                .le(WechathlinkBotRuntime::getExpiresAt, horizon)
                .orderByAsc(WechathlinkBotRuntime::getExpiresAt)
                .last("LIMIT 10"));
    }

    private List<WechathlinkPeerContext> loadClosingWindows(Set<Long> accountIds) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime horizon = now.plusHours(2);
        return peerContextMapper.selectList(new LambdaQueryWrapper<WechathlinkPeerContext>()
                .eq(WechathlinkPeerContext::getIsDeleted, 0)
                .in(WechathlinkPeerContext::getWechatAccountId, accountIds)
                .eq(WechathlinkPeerContext::getWindowStatus, "CLOSING_SOON")
                .isNotNull(WechathlinkPeerContext::getReplyWindowExpiresAt)
                .ge(WechathlinkPeerContext::getReplyWindowExpiresAt, now)
                .le(WechathlinkPeerContext::getReplyWindowExpiresAt, horizon)
                .orderByAsc(WechathlinkPeerContext::getReplyWindowExpiresAt)
                .last("LIMIT 10"));
    }

    private Map<String, Object> toRuntimeView(WechathlinkBotRuntime runtime) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", runtime.getId());
        payload.put("wechatAccountId", runtime.getWechatAccountId());
        payload.put("runtimeStatus", runtime.getRuntimeStatus());
        payload.put("expiresAt", runtime.getExpiresAt());
        payload.put("lastHeartbeatAt", runtime.getLastHeartbeatAt());
        payload.put("lastError", runtime.getLastError());
        payload.put("baseUrl", runtime.getBaseUrl());
        return payload;
    }

    private Map<String, Object> toWindowView(WechathlinkPeerContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("wechatAccountId", context.getWechatAccountId());
        payload.put("peerUserId", context.getPeerUserId());
        payload.put("contextStatus", context.getContextStatus());
        payload.put("windowStatus", context.getWindowStatus());
        payload.put("lastInboundAt", context.getLastInboundAt());
        payload.put("replyWindowExpiresAt", context.getReplyWindowExpiresAt());
        return payload;
    }
}
