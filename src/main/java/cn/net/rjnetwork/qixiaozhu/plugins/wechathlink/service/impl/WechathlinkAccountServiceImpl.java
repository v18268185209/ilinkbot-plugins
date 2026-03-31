package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.account.CurrentAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccountMember;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkLoginSession;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMemberMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkLoginSessionMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAuditService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAccountService;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink.IlinkClient;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.protocol.ilink.IlinkModels;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.support.poller.WechathlinkPollerManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WechathlinkAccountServiceImpl extends WechathlinkServiceSupport implements WechathlinkAccountService {

    private final WechathlinkAccountMapper accountMapper;
    private final WechathlinkAccountMemberMapper memberMapper;
    private final WechathlinkLoginSessionMapper loginSessionMapper;
    private final WechathlinkPermissionService permissionService;
    private final WechathlinkPollerManager pollerManager;
    private final IlinkClient ilinkClient;
    private final WechathlinkRuntimeConfigService runtimeConfigService;
    private final WechathlinkAuditService auditService;

    public WechathlinkAccountServiceImpl(WechathlinkAccountMapper accountMapper,
                                         WechathlinkAccountMemberMapper memberMapper,
                                         WechathlinkLoginSessionMapper loginSessionMapper,
                                         WechathlinkPermissionService permissionService,
                                         WechathlinkPollerManager pollerManager,
                                         IlinkClient ilinkClient,
                                         WechathlinkRuntimeConfigService runtimeConfigService,
                                         WechathlinkAuditService auditService) {
        this.accountMapper = accountMapper;
        this.memberMapper = memberMapper;
        this.loginSessionMapper = loginSessionMapper;
        this.permissionService = permissionService;
        this.pollerManager = pollerManager;
        this.ilinkClient = ilinkClient;
        this.runtimeConfigService = runtimeConfigService;
        this.auditService = auditService;
    }

    @Override
    public Map<String, Object> list(String keyword) {
        LambdaQueryWrapper<WechathlinkAccount> wrapper = new LambdaQueryWrapper<WechathlinkAccount>()
                .eq(WechathlinkAccount::getIsDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(WechathlinkAccount::getAccountCode, keyword)
                    .or().like(WechathlinkAccount::getAccountName, keyword)
                    .or().like(WechathlinkAccount::getBaseUrl, keyword));
        }
        List<WechathlinkAccount> accounts = accountMapper.selectList(wrapper.orderByDesc(WechathlinkAccount::getUpdateTime));
        if (!permissionService.standaloneMode() && !permissionService.superAccount()) {
            accounts = accounts.stream().filter(permissionService::canRead).toList();
        }
        List<Map<String, Object>> rows = accounts.stream().map(this::toView).toList();
        return Map.of("list", rows, "total", (long) rows.size());
    }

    @Override
    public Map<String, Object> detail(Long id) {
        WechathlinkAccount account = getReadableAccount(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("account", toView(account));
        payload.put("members", memberMapper.selectList(new LambdaQueryWrapper<WechathlinkAccountMember>()
                .eq(WechathlinkAccountMember::getWechatAccountId, id)
                .eq(WechathlinkAccountMember::getIsDeleted, 0)
                .orderByDesc(WechathlinkAccountMember::getId)));
        payload.put("loginSessions", loginSessionMapper.selectList(new LambdaQueryWrapper<WechathlinkLoginSession>()
                .eq(WechathlinkLoginSession::getIsDeleted, 0)
                .eq(WechathlinkLoginSession::getWechatAccountId, id)
                .orderByDesc(WechathlinkLoginSession::getCreateTime)
                .last("LIMIT 5")));
        return payload;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("account payload required");
        }
        Long id = asLong(body.get("id"));
        WechathlinkAccount account = id == null ? new WechathlinkAccount() : getOperableAccount(id);
        boolean created = account.getId() == null;
        String accountCode = asString(body.get("accountCode"));
        String accountName = asString(body.get("accountName"));
        if (!StringUtils.hasText(accountCode) || !StringUtils.hasText(accountName)) {
            throw new IllegalArgumentException("accountCode and accountName are required");
        }
        account.setAccountCode(accountCode);
        account.setAccountName(accountName);
        account.setBaseUrl(asString(body.get("baseUrl")));
        account.setBotToken(asString(body.get("botToken")));
        account.setIlinkUserId(asString(body.get("ilinkUserId")));
        account.setLoginStatus(defaultValue(asString(body.get("loginStatus")), created ? "CREATED" : account.getLoginStatus(), "CREATED"));
        account.setPollStatus(defaultValue(asString(body.get("pollStatus")), created ? "STOPPED" : account.getPollStatus(), "STOPPED"));
        account.setLastError(asString(body.get("lastError")));
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("id", id);
        auditPayload.put("accountCode", accountCode);
        auditPayload.put("accountName", accountName);
        auditPayload.put("baseUrl", account.getBaseUrl() == null ? "" : account.getBaseUrl());
        auditPayload.put("created", created);
        try {
            if (created) {
                CurrentAccount current = currentAccount();
                account.setOwnerUserId(current == null ? null : current.getId());
                fillCreateAudit(account);
                accountMapper.insert(account);
                createDefaultOwnerMembership(account);
            } else {
                fillUpdateAudit(account);
                accountMapper.updateById(account);
            }
            auditService.recordSuccess(account.getId(), created ? "ACCOUNT_CREATE" : "ACCOUNT_UPDATE", "ACCOUNT", account.getId(),
                    created ? "wechat account created" : "wechat account updated", auditPayload);
        } catch (RuntimeException ex) {
            auditService.recordFailure(account.getId(), created ? "ACCOUNT_CREATE" : "ACCOUNT_UPDATE", "ACCOUNT", id,
                    created ? "wechat account create failed" : "wechat account update failed", ex, auditPayload);
            throw ex;
        }
        return detail(account.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> toggle(Long id, Integer status) {
        WechathlinkAccount account = getOperableAccount(id);
        account.setStatus(status == null ? 0 : status);
        account.setPollStatus(Integer.valueOf(1).equals(account.getStatus()) ? "RUNNING" : "STOPPED");
        Map<String, Object> auditPayload = Map.of(
                "accountId", account.getId(),
                "status", account.getStatus(),
                "pollStatus", account.getPollStatus()
        );
        try {
            if (Integer.valueOf(1).equals(account.getStatus())) {
                pollerManager.start(account.getId());
            } else {
                pollerManager.stop(account.getId());
            }
            fillUpdateAudit(account);
            accountMapper.updateById(account);
            auditService.recordSuccess(account.getId(), "ACCOUNT_TOGGLE", "ACCOUNT", account.getId(),
                    "wechat account toggled", auditPayload);
        } catch (RuntimeException ex) {
            auditService.recordFailure(account.getId(), "ACCOUNT_TOGGLE", "ACCOUNT", account.getId(),
                    "wechat account toggle failed", ex, auditPayload);
            throw ex;
        }
        return detail(account.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveMember(Map<String, Object> body) {
        Long wechatAccountId = asLong(body.get("wechatAccountId"));
        Long userId = asLong(body.get("userId"));
        String roleCode = asString(body.get("roleCode"));
        if (wechatAccountId == null || userId == null || !StringUtils.hasText(roleCode)) {
            throw new IllegalArgumentException("wechatAccountId, userId and roleCode are required");
        }
        WechathlinkAccount account = getOperableAccount(wechatAccountId);
        WechathlinkAccountMember member = memberMapper.selectOne(new LambdaQueryWrapper<WechathlinkAccountMember>()
                .eq(WechathlinkAccountMember::getWechatAccountId, wechatAccountId)
                .eq(WechathlinkAccountMember::getUserId, userId)
                .eq(WechathlinkAccountMember::getIsDeleted, 0)
                .last("LIMIT 1"));
        boolean created = member == null;
        String permissionScope = defaultValue(asString(body.get("permissionScope")), created ? "FULL" : member.getPermissionScope(), "FULL");
        Map<String, Object> auditPayload = Map.of(
                "wechatAccountId", wechatAccountId,
                "userId", userId,
                "roleCode", roleCode,
                "permissionScope", permissionScope
        );
        try {
            if (member == null) {
                member = new WechathlinkAccountMember();
                member.setWechatAccountId(wechatAccountId);
                member.setUserId(userId);
                member.setRoleCode(roleCode);
                member.setPermissionScope(permissionScope);
                member.setIsPrimaryOwner(0);
                member.setCompanyId(account.getCompanyId());
                member.setDeptId(account.getDeptId());
                fillCreateAudit(member);
                memberMapper.insert(member);
            } else {
                member.setRoleCode(roleCode);
                member.setPermissionScope(permissionScope);
                fillUpdateAudit(member);
                memberMapper.updateById(member);
            }
            auditService.recordSuccess(wechatAccountId, created ? "ACCOUNT_MEMBER_CREATE" : "ACCOUNT_MEMBER_UPDATE",
                    "ACCOUNT_MEMBER", member.getId(), created ? "wechat account member created" : "wechat account member updated", auditPayload);
        } catch (RuntimeException ex) {
            auditService.recordFailure(wechatAccountId, created ? "ACCOUNT_MEMBER_CREATE" : "ACCOUNT_MEMBER_UPDATE",
                    "ACCOUNT_MEMBER", member == null ? null : member.getId(),
                    created ? "wechat account member create failed" : "wechat account member update failed", ex, auditPayload);
            throw ex;
        }
        return detail(wechatAccountId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startLogin(Map<String, Object> body) {
        String baseUrl = body == null ? null : asString(body.get("baseUrl"));
        var runtime = runtimeConfigService.current();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = runtime.defaultBaseUrl();
        }
        IlinkModels.QrCodeResponse qrCodeResponse = ilinkClient.fetchQrCode(baseUrl, runtime.pollTimeoutMs());
        String qrCodeTicket = requireText(qrCodeResponse == null ? null : qrCodeResponse.qrcode(), "ilink qrcode is empty");
        String qrCodeContent = requireText(
                StringUtils.hasText(qrCodeResponse.qrcodeImgContent()) ? qrCodeResponse.qrcodeImgContent() : qrCodeResponse.qrcode(),
                "ilink qrcode content is empty"
        );
        String sessionCode = "login_" + UUID.randomUUID().toString().replace("-", "");
        WechathlinkLoginSession session = new WechathlinkLoginSession();
        session.setSessionCode(sessionCode);
        session.setBaseUrl(baseUrl);
        session.setQrCodeContent(qrCodeContent);
        session.setQrCodeUrl(buildLoginQrUrl(sessionCode, qrCodeTicket));
        session.setSessionStatus("WAIT_SCAN");
        session.setExpiredAt(LocalDateTime.now().plusMinutes(10));
        fillCreateAudit(session);
        loginSessionMapper.insert(session);
        return Map.of(
                "sessionCode", session.getSessionCode(),
                "qrCodeUrl", session.getQrCodeUrl(),
                "status", session.getSessionStatus(),
                "expiredAt", session.getExpiredAt()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> loginStatus(String sessionCode) {
        WechathlinkLoginSession session = getLoginSession(sessionCode);
        if (session.getWechatAccountId() == null || !"CONFIRMED".equalsIgnoreCase(session.getSessionStatus())) {
            var runtime = runtimeConfigService.current();
            IlinkModels.QrStatusResponse statusResponse = ilinkClient.fetchQrCodeStatus(
                    session.getBaseUrl(),
                    resolveQrCodeTicket(session),
                    runtime.pollTimeoutMs()
            );
            if (statusResponse.status() != null && !statusResponse.status().isBlank()) {
                session.setSessionStatus(statusResponse.status().toUpperCase());
            }
            if (statusResponse.status() != null && "confirmed".equalsIgnoreCase(statusResponse.status())) {
                WechathlinkAccount account = accountMapper.selectOne(new LambdaQueryWrapper<WechathlinkAccount>()
                        .eq(WechathlinkAccount::getAccountCode, statusResponse.accountId())
                        .eq(WechathlinkAccount::getIsDeleted, 0)
                        .last("LIMIT 1"));
                boolean created = account == null;
                if (account == null) {
                    account = new WechathlinkAccount();
                    account.setAccountCode(statusResponse.accountId());
                    account.setAccountName(statusResponse.accountId());
                    fillCreateAudit(account);
                    CurrentAccount current = currentAccount();
                    account.setOwnerUserId(current == null ? null : current.getId());
                } else {
                    fillUpdateAudit(account);
                }
                account.setBaseUrl(StringUtils.hasText(statusResponse.baseUrl()) ? statusResponse.baseUrl() : session.getBaseUrl());
                account.setBotToken(statusResponse.botToken());
                account.setIlinkUserId(statusResponse.ilinkUserId());
                account.setLoginStatus("CONFIRMED");
                account.setPollStatus("RUNNING");
                account.setLastError("");
                account.setStatus(1);
                if (created) {
                    accountMapper.insert(account);
                    createDefaultOwnerMembership(account);
                } else {
                    accountMapper.updateById(account);
                }
                session.setWechatAccountId(account.getId());
                pollerManager.start(account.getId());
            }
            fillUpdateAudit(session);
            loginSessionMapper.updateById(session);
        }
        return Map.of(
                "sessionCode", session.getSessionCode(),
                "qrCodeUrl", session.getQrCodeUrl(),
                "status", session.getSessionStatus(),
                "expiredAt", session.getExpiredAt(),
                "accountId", session.getWechatAccountId()
        );
    }

    public WechathlinkLoginSession getLoginSession(String sessionCode) {
        WechathlinkLoginSession session = loginSessionMapper.selectOne(new LambdaQueryWrapper<WechathlinkLoginSession>()
                .eq(WechathlinkLoginSession::getSessionCode, sessionCode)
                .eq(WechathlinkLoginSession::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (session == null) {
            throw new IllegalArgumentException("login session not found");
        }
        return session;
    }

    private void createDefaultOwnerMembership(WechathlinkAccount account) {
        CurrentAccount current = currentAccount();
        if (current == null || current.getId() == null) {
            return;
        }
        WechathlinkAccountMember member = new WechathlinkAccountMember();
        member.setWechatAccountId(account.getId());
        member.setUserId(current.getId());
        member.setRoleCode(WechathlinkPermissionService.ROLE_OWNER);
        member.setPermissionScope("FULL");
        member.setIsPrimaryOwner(1);
        fillCreateAudit(member);
        memberMapper.insert(member);
    }

    private WechathlinkAccount getReadableAccount(Long id) {
        WechathlinkAccount account = accountMapper.selectById(id);
        if (account == null || !permissionService.canRead(account)) {
            throw new IllegalArgumentException("wechat account not found or no permission");
        }
        return account;
    }

    private WechathlinkAccount getOperableAccount(Long id) {
        WechathlinkAccount account = accountMapper.selectById(id);
        if (account == null || !permissionService.canOperate(account)) {
            throw new IllegalArgumentException("wechat account not found or no permission");
        }
        return account;
    }

    private String defaultValue(String preferred, String fallback, String defaultValue) {
        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        if (StringUtils.hasText(fallback)) {
            return fallback;
        }
        return defaultValue;
    }

    private String buildLoginQrUrl(String sessionCode, String qrCodeTicket) {
        return "/api/wechathlink/admin/login/qr?sessionCode=" + sessionCode
                + "&qrcode=" + URLEncoder.encode(qrCodeTicket, StandardCharsets.UTF_8);
    }

    private String resolveQrCodeTicket(WechathlinkLoginSession session) {
        String qrCodeUrl = session == null ? null : session.getQrCodeUrl();
        if (!StringUtils.hasText(qrCodeUrl)) {
            return requireText(session == null ? null : session.getQrCodeContent(), "login session qrcode is empty");
        }
        String flag = "qrcode=";
        int index = qrCodeUrl.indexOf(flag);
        if (index < 0) {
            return requireText(session.getQrCodeContent(), "login session qrcode is empty");
        }
        String value = qrCodeUrl.substring(index + flag.length());
        int ampersand = value.indexOf('&');
        if (ampersand >= 0) {
            value = value.substring(0, ampersand);
        }
        return requireText(URLDecoder.decode(value, StandardCharsets.UTF_8), "login session qrcode is empty");
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private Map<String, Object> toView(WechathlinkAccount account) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", account.getId());
        payload.put("accountCode", account.getAccountCode());
        payload.put("accountName", account.getAccountName());
        payload.put("baseUrl", account.getBaseUrl());
        payload.put("ilinkUserId", account.getIlinkUserId());
        payload.put("loginStatus", account.getLoginStatus());
        payload.put("pollStatus", pollerManager.isRunning(account.getId()) ? "RUNNING" : account.getPollStatus());
        payload.put("lastError", account.getLastError());
        payload.put("ownerUserId", account.getOwnerUserId());
        payload.put("status", account.getStatus());
        payload.put("lastPollAt", account.getLastPollAt());
        payload.put("lastInboundAt", account.getLastInboundAt());
        return payload;
    }
}
