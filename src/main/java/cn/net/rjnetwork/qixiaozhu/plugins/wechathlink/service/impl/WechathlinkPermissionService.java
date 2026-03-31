package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.account.CurrentAccount;
import cn.net.rjnetwork.qixiaozhu.core.constpara.BaseConst;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccountMember;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMemberMapper;
import cn.net.rjnetwork.qixiaozhu.spi.account.CurrentAccountProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WechathlinkPermissionService {

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_OPERATOR = "OPERATOR";
    public static final String ROLE_ANALYST = "ANALYST";
    public static final String ROLE_AUDITOR = "AUDITOR";
    public static final String SCOPE_FULL = "FULL";
    public static final String SCOPE_READ = "READ";
    public static final String SCOPE_OPERATE = "OPERATE";
    public static final String SCOPE_SEND = "SEND";
    public static final String SCOPE_EXPORT = "EXPORT";
    public static final String SCOPE_AUDIT = "AUDIT";
    public static final String SCOPE_MEDIA = "MEDIA";
    public static final String SCOPE_SETTINGS = "SETTINGS";

    private final ObjectProvider<CurrentAccountProvider> currentAccountProvider;
    private final WechathlinkAccountMapper accountMapper;
    private final WechathlinkAccountMemberMapper accountMemberMapper;

    public WechathlinkPermissionService(ObjectProvider<CurrentAccountProvider> currentAccountProvider,
                                        WechathlinkAccountMapper accountMapper,
                                        WechathlinkAccountMemberMapper accountMemberMapper) {
        this.currentAccountProvider = currentAccountProvider;
        this.accountMapper = accountMapper;
        this.accountMemberMapper = accountMemberMapper;
    }

    public CurrentAccount currentAccount() {
        CurrentAccountProvider provider = currentAccountProvider.getIfAvailable();
        return provider == null ? null : provider.getCurrentAccount();
    }

    public boolean standaloneMode() {
        return currentAccount() == null;
    }

    public boolean superAccount() {
        CurrentAccount current = currentAccount();
        return current != null && BaseConst.isSuperAccountType(current.getAccountType());
    }

    public Set<Long> readableAccountIds() {
        if (standaloneMode() || superAccount()) {
            return Set.of();
        }
        Set<Long> readable = new LinkedHashSet<>();
        List<WechathlinkAccountMember> members = activeMembersForCurrentUser();
        readable.addAll(members.stream()
                .filter((member) -> hasGrantedScope(member, SCOPE_READ))
                .map(WechathlinkAccountMember::getWechatAccountId)
                .collect(Collectors.toSet()));
        CurrentAccount current = currentAccount();
        if (current != null && current.getId() != null) {
            readable.addAll(accountMapper.selectList(new LambdaQueryWrapper<WechathlinkAccount>()
                            .eq(WechathlinkAccount::getIsDeleted, 0))
                    .stream()
                    .filter(this::canReadByHostScope)
                    .map(WechathlinkAccount::getId)
                    .collect(Collectors.toSet()));
        }
        return readable;
    }

    public boolean canRead(WechathlinkAccount account) {
        return hasAccountPermission(account, SCOPE_READ, true);
    }

    public boolean canOperate(WechathlinkAccount account) {
        return hasAccountPermission(account, SCOPE_OPERATE);
    }

    public boolean canSend(WechathlinkAccount account) {
        return hasAccountPermission(account, SCOPE_SEND);
    }

    public boolean canViewMedia(WechathlinkAccount account) {
        return hasAccountPermission(account, SCOPE_MEDIA, true);
    }

    public boolean canExport(WechathlinkAccount account) {
        return hasAccountPermission(account, SCOPE_EXPORT, true);
    }

    public boolean canViewAudit() {
        if (standaloneMode() || superAccount()) {
            return true;
        }
        return currentAccount() != null;
    }

    public boolean canViewSettings() {
        return canManageSettings();
    }

    public boolean canManageSettings() {
        if (standaloneMode() || superAccount()) {
            return true;
        }
        return currentAccount() != null;
    }

    private boolean hasGlobalPermission(String scope) {
        if (standaloneMode() || superAccount()) {
            return true;
        }
        return activeMembersForCurrentUser().stream().anyMatch((member) -> hasGrantedScope(member, scope));
    }

    private boolean hasAccountPermission(WechathlinkAccount account, String scope) {
        return hasAccountPermission(account, scope, false);
    }

    private boolean hasAccountPermission(WechathlinkAccount account, String scope, boolean allowHostReadFallback) {
        if (account == null) {
            return false;
        }
        if (standaloneMode() || superAccount()) {
            return true;
        }
        CurrentAccount current = currentAccount();
        if (current == null || current.getId() == null) {
            return false;
        }
        if (current.getId().equals(account.getOwnerUserId())) {
            return true;
        }
        WechathlinkAccountMember member = activeMember(account.getId(), current.getId());
        if (member != null && hasGrantedScope(member, scope)) {
            return true;
        }
        return allowHostReadFallback && canReadByHostScope(account);
    }

    private List<WechathlinkAccountMember> activeMembersForCurrentUser() {
        CurrentAccount current = currentAccount();
        if (current == null || current.getId() == null) {
            return List.of();
        }
        return accountMemberMapper.selectList(new LambdaQueryWrapper<WechathlinkAccountMember>()
                .eq(WechathlinkAccountMember::getUserId, current.getId())
                .eq(WechathlinkAccountMember::getIsDeleted, 0)
                .eq(WechathlinkAccountMember::getStatus, 1));
    }

    private WechathlinkAccountMember activeMember(Long accountId, Long userId) {
        return accountMemberMapper.selectOne(new LambdaQueryWrapper<WechathlinkAccountMember>()
                .eq(WechathlinkAccountMember::getWechatAccountId, accountId)
                .eq(WechathlinkAccountMember::getUserId, userId)
                .eq(WechathlinkAccountMember::getIsDeleted, 0)
                .eq(WechathlinkAccountMember::getStatus, 1)
                .last("LIMIT 1"));
    }

    private boolean hasGrantedScope(WechathlinkAccountMember member, String scope) {
        if (member == null || scope == null) {
            return false;
        }
        Set<String> granted = resolveScopes(member);
        return granted.contains(SCOPE_FULL) || granted.contains(scope.trim().toUpperCase());
    }

    private Set<String> resolveScopes(WechathlinkAccountMember member) {
        Set<String> scopes = new LinkedHashSet<>(defaultScopesForRole(member == null ? null : member.getRoleCode()));
        if (member == null || member.getPermissionScope() == null) {
            return scopes;
        }
        String normalized = member.getPermissionScope()
                .replace('[', ' ')
                .replace(']', ' ')
                .replace('"', ' ')
                .replace('\'', ' ')
                .trim();
        if (normalized.isEmpty()) {
            return scopes;
        }
        Arrays.stream(normalized.split("[,;\\s]+"))
                .filter((token) -> !token.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .forEach((token) -> addScope(scopes, token));
        return scopes;
    }

    private Set<String> defaultScopesForRole(String roleCode) {
        String normalizedRole = roleCode == null ? "" : roleCode.trim().toUpperCase();
        return switch (normalizedRole) {
            case ROLE_OWNER -> new LinkedHashSet<>(Set.of(SCOPE_FULL, SCOPE_READ, SCOPE_OPERATE, SCOPE_SEND, SCOPE_EXPORT, SCOPE_AUDIT, SCOPE_MEDIA, SCOPE_SETTINGS));
            case ROLE_OPERATOR -> new LinkedHashSet<>(Set.of(SCOPE_READ, SCOPE_OPERATE, SCOPE_SEND, SCOPE_EXPORT, SCOPE_MEDIA));
            case ROLE_ANALYST -> new LinkedHashSet<>(Set.of(SCOPE_READ, SCOPE_EXPORT, SCOPE_MEDIA));
            case ROLE_AUDITOR -> new LinkedHashSet<>(Set.of(SCOPE_READ, SCOPE_EXPORT, SCOPE_MEDIA, SCOPE_AUDIT));
            default -> new LinkedHashSet<>(Set.of(SCOPE_READ));
        };
    }

    private void addScope(Set<String> scopes, String scope) {
        if (scope == null || scope.isBlank()) {
            return;
        }
        scopes.add(scope);
        if (SCOPE_FULL.equals(scope)) {
            scopes.addAll(Set.of(SCOPE_READ, SCOPE_OPERATE, SCOPE_SEND, SCOPE_EXPORT, SCOPE_AUDIT, SCOPE_MEDIA, SCOPE_SETTINGS));
            return;
        }
        if (Set.of(SCOPE_OPERATE, SCOPE_SEND, SCOPE_EXPORT, SCOPE_AUDIT, SCOPE_MEDIA, SCOPE_SETTINGS).contains(scope)) {
            scopes.add(SCOPE_READ);
        }
    }

    private boolean canReadByHostScope(WechathlinkAccount account) {
        if (account == null) {
            return false;
        }
        if (standaloneMode() || superAccount()) {
            return true;
        }
        CurrentAccount current = currentAccount();
        if (current == null || current.getId() == null) {
            return false;
        }
        if (current.getId().equals(account.getOwnerUserId())) {
            return true;
        }
        Object context = dataPermissionContext();
        if (context == null || contextFlag(context, "isSkip") || !contextFlag(context, "isEnabled")) {
            return sameCompany(account, current.getCompanyId());
        }
        if (contextFlag(context, "isSuperAdmin") || contextFlag(context, "isAllPermission")) {
            return true;
        }
        if (contextFlag(context, "isSelfPermission")) {
            return current.getId().equals(account.getOwnerUserId()) || current.getId().equals(account.getCreateUserId());
        }
        if (contextFlag(context, "isCompanyPermission")) {
            return matchesCompanyScope(account, context, current.getCompanyId());
        }
        if (contextFlag(context, "isDeptRelatedPermission")) {
            return matchesDeptScope(account, context, current.getDeptId());
        }
        if (contextFlag(context, "isCustomPermission")) {
            return matchesCompanyScope(account, context, current.getCompanyId()) || matchesDeptScope(account, context, current.getDeptId());
        }
        return false;
    }

    private boolean matchesCompanyScope(WechathlinkAccount account, Object context, Long fallbackCompanyId) {
        if (account.getCompanyId() == null) {
            return false;
        }
        List<Long> companyIds = contextLongList(context, "getCompanyIds");
        if (companyIds != null && !companyIds.isEmpty()) {
            return companyIds.contains(account.getCompanyId());
        }
        return sameCompany(account, fallbackCompanyId);
    }

    private boolean matchesDeptScope(WechathlinkAccount account, Object context, Long fallbackDeptId) {
        if (account.getDeptId() == null) {
            return false;
        }
        List<Long> deptAndChildIds = contextLongList(context, "getDeptAndChildIds");
        if (deptAndChildIds != null && !deptAndChildIds.isEmpty()) {
            return deptAndChildIds.contains(account.getDeptId());
        }
        List<Long> deptIds = contextLongList(context, "getDeptIds");
        if (deptIds != null && !deptIds.isEmpty()) {
            return deptIds.contains(account.getDeptId());
        }
        return fallbackDeptId != null && fallbackDeptId.equals(account.getDeptId());
    }

    private boolean sameCompany(WechathlinkAccount account, Long companyId) {
        return companyId != null && companyId.equals(account.getCompanyId());
    }

    private Object dataPermissionContext() {
        try {
            Class<?> holderClass = Class.forName("cn.net.rjnetwork.qixiaozhu.core.context.DataPermissionContextHolder");
            Method method = holderClass.getMethod("getContext");
            return method.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean contextFlag(Object context, String methodName) {
        if (context == null || methodName == null) {
            return false;
        }
        try {
            Method method = context.getClass().getMethod(methodName);
            Object value = method.invoke(context);
            return value instanceof Boolean bool && bool;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> contextLongList(Object context, String methodName) {
        if (context == null || methodName == null) {
            return List.of();
        }
        try {
            Method method = context.getClass().getMethod(methodName);
            Object value = method.invoke(context);
            if (value instanceof List<?> list) {
                return (List<Long>) list;
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }
}
