package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAuditLog;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAuditLogMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WechathlinkAuditServiceImpl extends WechathlinkServiceSupport implements WechathlinkAuditService {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";

    private final WechathlinkAuditLogMapper auditLogMapper;
    private final WechathlinkPermissionService permissionService;

    public WechathlinkAuditServiceImpl(WechathlinkAuditLogMapper auditLogMapper,
                                       WechathlinkPermissionService permissionService) {
        this.auditLogMapper = auditLogMapper;
        this.permissionService = permissionService;
    }

    @Override
    public void recordSuccess(Long wechatAccountId,
                              String actionType,
                              String resourceType,
                              Object resourceId,
                              String summary,
                              Map<String, Object> detail) {
        createAuditLog(wechatAccountId, actionType, resourceType, resourceId, RESULT_SUCCESS, summary, detail);
    }

    @Override
    public void recordFailure(Long wechatAccountId,
                              String actionType,
                              String resourceType,
                              Object resourceId,
                              String summary,
                              Throwable error,
                              Map<String, Object> detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (detail != null && !detail.isEmpty()) {
            payload.putAll(detail);
        }
        if (error != null) {
            payload.put("error", error.getMessage());
        }
        createAuditLog(wechatAccountId, actionType, resourceType, resourceId, RESULT_FAILURE, summary, payload);
    }

    @Override
    public Map<String, Object> list(Long wechatAccountId,
                                    Long operatorUserId,
                                    String actionType,
                                    String resultStatus,
                                    Integer pageNum,
                                    Integer pageSize) {
        requireAuditPermission();
        Page<WechathlinkAuditLog> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<WechathlinkAuditLog> wrapper = new LambdaQueryWrapper<WechathlinkAuditLog>()
                .eq(WechathlinkAuditLog::getIsDeleted, 0);
        applyVisibleAccountFilter(wrapper, wechatAccountId);
        if (operatorUserId != null) {
            wrapper.eq(WechathlinkAuditLog::getOperatorUserId, operatorUserId);
        }
        if (hasText(actionType)) {
            wrapper.eq(WechathlinkAuditLog::getActionType, actionType.trim().toUpperCase());
        }
        if (hasText(resultStatus)) {
            wrapper.eq(WechathlinkAuditLog::getResultStatus, resultStatus.trim().toUpperCase());
        }
        Page<WechathlinkAuditLog> result = auditLogMapper.selectPage(
                page,
                wrapper.orderByDesc(WechathlinkAuditLog::getCreateTime).orderByDesc(WechathlinkAuditLog::getId)
        );
        List<Map<String, Object>> rows = result.getRecords().stream().map(this::toView).toList();
        return Map.of(
                "list", rows,
                "total", result.getTotal(),
                "pageNum", result.getCurrent(),
                "pageSize", result.getSize()
        );
    }

    @Override
    public Map<String, Object> detail(Long id) {
        requireAuditPermission();
        if (id == null) {
            throw new IllegalArgumentException("audit id required");
        }
        WechathlinkAuditLog log = auditLogMapper.selectById(id);
        if (log == null || Integer.valueOf(1).equals(log.getIsDeleted())) {
            throw new IllegalArgumentException("audit log not found");
        }
        if (!canViewAuditLog(log)) {
            throw new IllegalArgumentException("audit log not found or no permission");
        }
        return toView(log);
    }

    private void createAuditLog(Long wechatAccountId,
                                String actionType,
                                String resourceType,
                                Object resourceId,
                                String resultStatus,
                                String summary,
                                Map<String, Object> detail) {
        WechathlinkAuditLog log = new WechathlinkAuditLog();
        log.setWechatAccountId(wechatAccountId);
        log.setOperatorUserId(currentAccount() == null ? null : currentAccount().getId());
        log.setOperatorUserName(resolveCurrentAccountName());
        log.setActionType(normalizeLabel(actionType));
        log.setResourceType(normalizeLabel(resourceType));
        log.setResourceId(resourceId == null ? "" : String.valueOf(resourceId));
        log.setResultStatus(normalizeLabel(resultStatus));
        log.setSummary(defaultIfBlank(summary, log.getActionType() + " " + log.getResultStatus()));
        log.setDetailJson(writeJsonObject(detail == null ? Map.of() : detail));
        fillCreateAudit(log);
        auditLogMapper.insert(log);
    }

    private void requireAuditPermission() {
        if (!permissionService.canViewAudit()) {
            throw new IllegalArgumentException("audit log not found or no permission");
        }
    }

    private boolean canViewAuditLog(WechathlinkAuditLog log) {
        if (log == null) {
            return false;
        }
        if (permissionService.standaloneMode() || permissionService.superAccount()) {
            return true;
        }
        if (log.getWechatAccountId() == null) {
            return true;
        }
        Set<Long> readable = permissionService.readableAccountIds();
        return readable.contains(log.getWechatAccountId());
    }

    private void applyVisibleAccountFilter(LambdaQueryWrapper<WechathlinkAuditLog> wrapper, Long wechatAccountId) {
        if (wechatAccountId != null) {
            if (!permissionService.standaloneMode() && !permissionService.superAccount()) {
                Set<Long> readable = permissionService.readableAccountIds();
                if (!readable.contains(wechatAccountId)) {
                    throw new IllegalArgumentException("wechat account not found or no permission");
                }
            }
            wrapper.eq(WechathlinkAuditLog::getWechatAccountId, wechatAccountId);
            return;
        }
        if (permissionService.standaloneMode() || permissionService.superAccount()) {
            return;
        }
        Set<Long> readable = permissionService.readableAccountIds();
        if (readable.isEmpty()) {
            wrapper.isNull(WechathlinkAuditLog::getWechatAccountId);
            return;
        }
        wrapper.and(w -> w.in(WechathlinkAuditLog::getWechatAccountId, readable)
                .or().isNull(WechathlinkAuditLog::getWechatAccountId));
    }

    private Map<String, Object> toView(WechathlinkAuditLog log) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", log.getId());
        payload.put("wechatAccountId", log.getWechatAccountId());
        payload.put("operatorUserId", log.getOperatorUserId());
        payload.put("operatorUserName", log.getOperatorUserName());
        payload.put("actionType", log.getActionType());
        payload.put("resourceType", log.getResourceType());
        payload.put("resourceId", log.getResourceId());
        payload.put("resultStatus", log.getResultStatus());
        payload.put("summary", log.getSummary());
        payload.put("detailJson", log.getDetailJson());
        payload.put("createTime", log.getCreateTime());
        return payload;
    }

    private String normalizeLabel(String value) {
        return hasText(value) ? value.trim().toUpperCase() : "";
    }

    private String defaultIfBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred.trim() : fallback;
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
}
