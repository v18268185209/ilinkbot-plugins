package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkEvent;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkPeerContext;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkEventMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkPeerContextMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WechathlinkEventServiceImpl implements WechathlinkEventService {

    private final WechathlinkAccountMapper accountMapper;
    private final WechathlinkEventMapper eventMapper;
    private final WechathlinkPeerContextMapper peerContextMapper;
    private final WechathlinkPermissionService permissionService;

    public WechathlinkEventServiceImpl(WechathlinkAccountMapper accountMapper,
                                       WechathlinkEventMapper eventMapper,
                                       WechathlinkPeerContextMapper peerContextMapper,
                                       WechathlinkPermissionService permissionService) {
        this.accountMapper = accountMapper;
        this.eventMapper = eventMapper;
        this.peerContextMapper = peerContextMapper;
        this.permissionService = permissionService;
    }

    @Override
    public Map<String, Object> summary(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<WechathlinkAccount> wrapper = new LambdaQueryWrapper<WechathlinkAccount>()
                .eq(WechathlinkAccount::getIsDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(WechathlinkAccount::getAccountCode, keyword.trim())
                    .or().like(WechathlinkAccount::getAccountName, keyword.trim())
                    .or().like(WechathlinkAccount::getBaseUrl, keyword.trim()));
        }
        List<WechathlinkAccount> accounts = accountMapper.selectList(wrapper.orderByDesc(WechathlinkAccount::getUpdateTime).orderByDesc(WechathlinkAccount::getId));
        if (!permissionService.standaloneMode() && !permissionService.superAccount()) {
            accounts = accounts.stream().filter(permissionService::canRead).toList();
        }
        int pageNumValue = normalizePageNum(pageNum);
        int pageSizeValue = normalizePageSize(pageSize);
        int fromIndex = Math.min((pageNumValue - 1) * pageSizeValue, accounts.size());
        int toIndex = Math.min(fromIndex + pageSizeValue, accounts.size());
        List<Map<String, Object>> rows = accounts.subList(fromIndex, toIndex).stream()
                .map(this::toSummaryView)
                .toList();
        return Map.of(
                "list", rows,
                "total", (long) accounts.size(),
                "pageNum", (long) pageNumValue,
                "pageSize", (long) pageSizeValue
        );
    }

    @Override
    public Map<String, Object> list(Long wechatAccountId, String contactId, String direction, String eventType, Integer pageNum, Integer pageSize) {
        WechathlinkAccount account = null;
        if (wechatAccountId != null) {
            account = requireReadableAccount(wechatAccountId);
        }
        Page<WechathlinkEvent> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<WechathlinkEvent> wrapper = new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getIsDeleted, 0);
        Set<Long> accountIds = permissionService.readableAccountIds();
        if (!permissionService.standaloneMode() && !permissionService.superAccount()) {
            if (wechatAccountId == null) {
                if (accountIds.isEmpty()) {
                    return pageResult(List.of(), 0L, page);
                }
                wrapper.in(WechathlinkEvent::getWechatAccountId, accountIds);
            }
        }
        if (wechatAccountId != null) {
            wrapper.eq(WechathlinkEvent::getWechatAccountId, wechatAccountId);
        }
        if (StringUtils.hasText(contactId)) {
            String target = contactId.trim();
            if (account != null && StringUtils.hasText(account.getAccountCode()) && account.getAccountCode().trim().equals(target)) {
                return pageResult(List.of(), 0L, page);
            }
            wrapper.and(w -> w.eq(WechathlinkEvent::getFromUserId, target)
                    .or().eq(WechathlinkEvent::getToUserId, target));
        }
        if (StringUtils.hasText(direction)) {
            wrapper.eq(WechathlinkEvent::getDirection, direction.trim());
        }
        if (StringUtils.hasText(eventType)) {
            wrapper.eq(WechathlinkEvent::getEventType, eventType.trim());
        }
        wrapper.orderByDesc(WechathlinkEvent::getCreateTime).orderByDesc(WechathlinkEvent::getId);
        Page<WechathlinkEvent> result = eventMapper.selectPage(page, wrapper);
        return pageResult(result.getRecords(), result.getTotal(), result);
    }

    @Override
    public Map<String, Object> contacts(Long wechatAccountId, String keyword, Integer pageNum, Integer pageSize) {
        WechathlinkAccount account = requireReadableAccount(wechatAccountId);
        List<WechathlinkEvent> events = eventMapper.selectList(new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getWechatAccountId, wechatAccountId)
                .eq(WechathlinkEvent::getIsDeleted, 0)
                .orderByDesc(WechathlinkEvent::getCreateTime)
                .orderByDesc(WechathlinkEvent::getId));
        Map<String, WechathlinkPeerContext> peerContextMap = peerContextMapper.selectList(new LambdaQueryWrapper<WechathlinkPeerContext>()
                        .eq(WechathlinkPeerContext::getWechatAccountId, wechatAccountId)
                        .eq(WechathlinkPeerContext::getIsDeleted, 0))
                .stream()
                .collect(java.util.stream.Collectors.toMap(WechathlinkPeerContext::getPeerUserId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<String, ContactSummary> aggregate = new LinkedHashMap<>();
        for (WechathlinkEvent event : events) {
            touchContact(aggregate, account, event.getFromUserId(), true, event);
            touchContact(aggregate, account, event.getToUserId(), false, event);
        }
        List<Map<String, Object>> rows = aggregate.values().stream()
                .filter(item -> !StringUtils.hasText(keyword) || item.contactId().contains(keyword.trim()))
                .sorted((a, b) -> {
                    LocalDateTime left = a.lastSeenAt();
                    LocalDateTime right = b.lastSeenAt();
                    if (left == null && right == null) {
                        return a.contactId().compareTo(b.contactId());
                    }
                    if (left == null) {
                        return 1;
                    }
                    if (right == null) {
                        return -1;
                    }
                    int compare = right.compareTo(left);
                    return compare != 0 ? compare : a.contactId().compareTo(b.contactId());
                })
                .map(item -> item.toView(peerContextMap.containsKey(item.contactId())))
                .toList();
        int page = normalizePageNum(pageNum);
        int size = normalizePageSize(pageSize);
        int fromIndex = Math.min((page - 1) * size, rows.size());
        int toIndex = Math.min(fromIndex + size, rows.size());
        List<Map<String, Object>> pagedRows = new ArrayList<>(rows.subList(fromIndex, toIndex));
        return Map.of(
                "list", pagedRows,
                "total", (long) rows.size(),
                "pageNum", (long) page,
                "pageSize", (long) size
        );
    }

    @Override
    public WechathlinkEvent getReadableEvent(Long eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId required");
        }
        WechathlinkEvent event = eventMapper.selectById(eventId);
        if (event == null || event.getWechatAccountId() == null) {
            throw new IllegalArgumentException("event not found");
        }
        requireReadableAccount(event.getWechatAccountId());
        return event;
    }

    private Map<String, Object> toSummaryView(WechathlinkAccount account) {
        long totalCount = countEvents(account.getId(), null);
        long inboundCount = countEvents(account.getId(), "inbound");
        long outboundCount = countEvents(account.getId(), "outbound");
        WechathlinkEvent latestEvent = findLatestEvent(account.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("wechatAccountId", account.getId());
        payload.put("accountCode", account.getAccountCode());
        payload.put("accountName", account.getAccountName());
        payload.put("baseUrl", account.getBaseUrl());
        payload.put("loginStatus", account.getLoginStatus());
        payload.put("pollStatus", account.getPollStatus());
        payload.put("totalCount", totalCount);
        payload.put("inboundCount", inboundCount);
        payload.put("outboundCount", outboundCount);
        payload.put("lastEventAt", latestEvent == null ? null : latestEvent.getCreateTime());
        return payload;
    }

    private long countEvents(Long wechatAccountId, String direction) {
        LambdaQueryWrapper<WechathlinkEvent> wrapper = new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getWechatAccountId, wechatAccountId)
                .eq(WechathlinkEvent::getIsDeleted, 0);
        if (StringUtils.hasText(direction)) {
            wrapper.eq(WechathlinkEvent::getDirection, direction);
        }
        return eventMapper.selectCount(wrapper);
    }

    private WechathlinkEvent findLatestEvent(Long wechatAccountId) {
        return eventMapper.selectOne(new LambdaQueryWrapper<WechathlinkEvent>()
                .eq(WechathlinkEvent::getWechatAccountId, wechatAccountId)
                .eq(WechathlinkEvent::getIsDeleted, 0)
                .orderByDesc(WechathlinkEvent::getCreateTime)
                .orderByDesc(WechathlinkEvent::getId)
                .last("LIMIT 1"));
    }

    private void touchContact(Map<String, ContactSummary> aggregate,
                              WechathlinkAccount account,
                              String contactId,
                              boolean sender,
                              WechathlinkEvent event) {
        if (!StringUtils.hasText(contactId)) {
            return;
        }
        String normalized = contactId.trim();
        if (account != null && StringUtils.hasText(account.getAccountCode()) && account.getAccountCode().trim().equals(normalized)) {
            return;
        }
        ContactSummary summary = aggregate.get(normalized);
        if (summary == null) {
            summary = new ContactSummary(normalized);
            aggregate.put(summary.contactId(), summary);
        }
        if (sender) {
            summary.incrementSenderCount();
        } else {
            summary.incrementReceiverCount();
        }
        summary.touchEvent(event);
    }

    private WechathlinkAccount requireReadableAccount(Long wechatAccountId) {
        WechathlinkAccount account = accountMapper.selectById(wechatAccountId);
        if (account == null || !permissionService.canRead(account)) {
            throw new IllegalArgumentException("wechat account not found or no permission");
        }
        return account;
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private Map<String, Object> pageResult(List<?> rows, long total, Page<?> page) {
        return Map.of(
                "list", rows,
                "total", total,
                "pageNum", page.getCurrent(),
                "pageSize", page.getSize()
        );
    }

    private static final class ContactSummary {
        private final String contactId;
        private int senderCount;
        private int receiverCount;
        private LocalDateTime lastSeenAt;
        private String lastDirection;
        private String lastEventType;
        private String lastMessagePreview;

        private ContactSummary(String contactId) {
            this.contactId = contactId;
        }

        private String contactId() {
            return contactId;
        }

        private LocalDateTime lastSeenAt() {
            return lastSeenAt;
        }

        private void incrementSenderCount() {
            senderCount++;
        }

        private void incrementReceiverCount() {
            receiverCount++;
        }

        private void touchEvent(WechathlinkEvent event) {
            if (event == null || event.getCreateTime() == null) {
                return;
            }
            if (lastSeenAt == null || event.getCreateTime().isAfter(lastSeenAt)) {
                lastSeenAt = event.getCreateTime();
                lastDirection = event.getDirection();
                lastEventType = event.getEventType();
                lastMessagePreview = buildPreview(event);
            }
        }

        private Map<String, Object> toView(boolean hasContextToken) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("contactId", contactId);
            payload.put("senderCount", senderCount);
            payload.put("receiverCount", receiverCount);
            payload.put("totalCount", senderCount + receiverCount);
            payload.put("lastSeenAt", lastSeenAt);
            payload.put("lastDirection", lastDirection);
            payload.put("lastEventType", lastEventType);
            payload.put("lastMessagePreview", lastMessagePreview);
            payload.put("hasContextToken", hasContextToken);
            payload.put("label", buildLabel());
            return payload;
        }

        private String buildLabel() {
            String directionText = switch (StringUtils.hasText(lastDirection) ? lastDirection.trim().toLowerCase() : "") {
                case "inbound" -> "入站";
                case "outbound" -> "出站";
                default -> "";
            };
            if (StringUtils.hasText(directionText) && StringUtils.hasText(lastMessagePreview)) {
                return contactId + " | " + directionText + " | " + lastMessagePreview;
            }
            if (StringUtils.hasText(lastMessagePreview)) {
                return contactId + " | " + lastMessagePreview;
            }
            return contactId;
        }

        private String buildPreview(WechathlinkEvent event) {
            if (event == null) {
                return "";
            }
            if (StringUtils.hasText(event.getBodyText())) {
                String text = event.getBodyText().trim();
                return text.length() > 24 ? text.substring(0, 24) + "..." : text;
            }
            if (StringUtils.hasText(event.getMediaFileName())) {
                return event.getMediaFileName().trim();
            }
            return StringUtils.hasText(event.getEventType()) ? event.getEventType().trim() : "";
        }
    }
}
