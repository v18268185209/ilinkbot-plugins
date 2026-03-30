package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkLog;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkEventMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkLogMapper;
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

@Service
public class WechathlinkDashboardServiceImpl implements WechathlinkDashboardService {

    private final WechathlinkAccountMapper accountMapper;
    private final WechathlinkEventMapper eventMapper;
    private final WechathlinkLogMapper logMapper;
    private final WechathlinkPermissionService permissionService;
    private final WechathlinkPollerManager pollerManager;

    public WechathlinkDashboardServiceImpl(WechathlinkAccountMapper accountMapper,
                                           WechathlinkEventMapper eventMapper,
                                           WechathlinkLogMapper logMapper,
                                           WechathlinkPermissionService permissionService,
                                           WechathlinkPollerManager pollerManager) {
        this.accountMapper = accountMapper;
        this.eventMapper = eventMapper;
        this.logMapper = logMapper;
        this.permissionService = permissionService;
        this.pollerManager = pollerManager;
    }

    @Override
    public Map<String, Object> summary() {
        LambdaQueryWrapper<WechathlinkAccount> accountWrapper = new LambdaQueryWrapper<WechathlinkAccount>()
                .eq(WechathlinkAccount::getIsDeleted, 0);
        Set<Long> accountIds = permissionService.readableAccountIds();
        if (!permissionService.standaloneMode() && !permissionService.superAccount()) {
            if (accountIds.isEmpty()) {
                return emptySummary();
            }
            accountWrapper.in(WechathlinkAccount::getId, accountIds);
        }
        List<WechathlinkAccount> accounts = accountMapper.selectList(accountWrapper.orderByDesc(WechathlinkAccount::getUpdateTime).last("LIMIT 6"));
        long total = accountMapper.selectCount(accountWrapper);
        long enabled = accounts.stream().filter(item -> Integer.valueOf(1).equals(item.getStatus())).count();

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LambdaQueryWrapper<WechathlinkEvent> eventWrapper = new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getIsDeleted, 0)
                .ge(WechathlinkEvent::getCreateTime, startOfDay);
        if (!permissionService.standaloneMode() && !permissionService.superAccount()) {
            eventWrapper.in(WechathlinkEvent::getWechatAccountId, accountIds);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountCount", total);
        payload.put("enabledAccountCount", enabled);
        payload.put("pollingAccountCount", pollerManager.runningCount());
        payload.put("inboundTodayCount", countEvents(startOfDay, accountIds, "inbound"));
        payload.put("outboundTodayCount", countEvents(startOfDay, accountIds, "outbound"));
        payload.put("errorCount", logMapper.selectCount(new LambdaQueryWrapper<WechathlinkLog>()
                .eq(WechathlinkLog::getIsDeleted, 0)
                .eq(WechathlinkLog::getLevel, "ERROR")));
        payload.put("recentAccounts", accounts);
        payload.put("recentLogs", logMapper.selectList(new LambdaQueryWrapper<WechathlinkLog>()
                .eq(WechathlinkLog::getIsDeleted, 0)
                .orderByDesc(WechathlinkLog::getCreateTime)
                .last("LIMIT 6")));
        payload.put("recentEvents", eventMapper.selectList(new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getIsDeleted, 0)
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
        payload.put("recentAccounts", List.of());
        payload.put("recentLogs", List.of());
        payload.put("recentEvents", List.of());
        return payload;
    }

    private long countEvents(LocalDateTime startOfDay, Set<Long> accountIds, String direction) {
        LambdaQueryWrapper<WechathlinkEvent> wrapper = new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getIsDeleted, 0)
                .ge(WechathlinkEvent::getCreateTime, startOfDay)
                .eq(WechathlinkEvent::getDirection, direction);
        if (!permissionService.standaloneMode() && !permissionService.superAccount()) {
            if (accountIds.isEmpty()) {
                return 0L;
            }
            wrapper.in(WechathlinkEvent::getWechatAccountId, accountIds);
        }
        return eventMapper.selectCount(wrapper);
    }
}
