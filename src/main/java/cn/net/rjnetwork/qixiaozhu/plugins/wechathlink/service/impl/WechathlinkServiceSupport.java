package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.account.CurrentAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAuditEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public abstract class WechathlinkServiceSupport {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ObjectProvider<WechathlinkPermissionService> permissionServiceProvider;

    protected CurrentAccount currentAccount() {
        WechathlinkPermissionService permissionService = permissionServiceProvider.getIfAvailable();
        return permissionService == null ? null : permissionService.currentAccount();
    }

    protected void fillCreateAudit(WechathlinkAuditEntity entity) {
        CurrentAccount current = currentAccount();
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setCreateUserId(current == null ? null : current.getId());
        entity.setUpdateUserId(current == null ? null : current.getId());
        entity.setCompanyId(current == null ? null : current.getCompanyId());
        entity.setDeptId(current == null ? null : current.getDeptId());
        entity.setStatus(entity.getStatus() == null ? 1 : entity.getStatus());
        entity.setIsDeleted(entity.getIsDeleted() == null ? 0 : entity.getIsDeleted());
    }

    protected void fillUpdateAudit(WechathlinkAuditEntity entity) {
        CurrentAccount current = currentAccount();
        entity.setUpdateTime(LocalDateTime.now());
        entity.setUpdateUserId(current == null ? null : current.getId());
    }

    protected String asString(Object value) {
        return value == null ? null : value.toString().trim();
    }

    protected Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (Exception ex) {
            return null;
        }
    }

    protected Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (Exception ex) {
            return null;
        }
    }

    protected String writeJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    protected String writeJsonObject(Object payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    protected boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    protected String resolveCurrentAccountName() {
        CurrentAccount current = currentAccount();
        if (current == null) {
            return "standalone";
        }
        for (String methodName : List.of("getRealName", "getUserName", "getUsername", "getLoginName", "getAccountName", "getName", "getNickName")) {
            try {
                Method method = current.getClass().getMethod(methodName);
                Object value = method.invoke(current);
                if (value != null && hasText(String.valueOf(value))) {
                    return String.valueOf(value).trim();
                }
            } catch (Exception ignored) {
            }
        }
        return current.getId() == null ? "unknown" : "user#" + current.getId();
    }
}
