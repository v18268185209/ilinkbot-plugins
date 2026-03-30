package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.account.CurrentAccount;
import cn.net.rjnetwork.qixiaozhu.core.constpara.BaseConst;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccount;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkAccountMember;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkAccountMemberMapper;
import cn.net.rjnetwork.qixiaozhu.spi.account.CurrentAccountProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WechathlinkPermissionService {

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_OPERATOR = "OPERATOR";
    public static final String ROLE_ANALYST = "ANALYST";
    public static final String ROLE_AUDITOR = "AUDITOR";

    private final ObjectProvider<CurrentAccountProvider> currentAccountProvider;
    private final WechathlinkAccountMemberMapper accountMemberMapper;

    public WechathlinkPermissionService(ObjectProvider<CurrentAccountProvider> currentAccountProvider,
                                        WechathlinkAccountMemberMapper accountMemberMapper) {
        this.currentAccountProvider = currentAccountProvider;
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
        CurrentAccount current = currentAccount();
        if (current == null || current.getId() == null) {
            return Set.of();
        }
        List<WechathlinkAccountMember> members = accountMemberMapper.selectList(new LambdaQueryWrapper<WechathlinkAccountMember>()
                .eq(WechathlinkAccountMember::getUserId, current.getId())
                .eq(WechathlinkAccountMember::getIsDeleted, 0)
                .eq(WechathlinkAccountMember::getStatus, 1));
        return members.stream().map(WechathlinkAccountMember::getWechatAccountId).collect(Collectors.toSet());
    }

    public boolean canRead(WechathlinkAccount account) {
        if (account == null) {
            return false;
        }
        if (standaloneMode() || superAccount()) {
            return true;
        }
        Set<Long> readable = readableAccountIds();
        return readable.contains(account.getId()) || (currentAccount() != null && currentAccount().getId() != null
                && currentAccount().getId().equals(account.getOwnerUserId()));
    }

    public boolean canOperate(WechathlinkAccount account) {
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
        WechathlinkAccountMember member = accountMemberMapper.selectOne(new LambdaQueryWrapper<WechathlinkAccountMember>()
                .eq(WechathlinkAccountMember::getWechatAccountId, account.getId())
                .eq(WechathlinkAccountMember::getUserId, current.getId())
                .eq(WechathlinkAccountMember::getIsDeleted, 0)
                .eq(WechathlinkAccountMember::getStatus, 1)
                .last("LIMIT 1"));
        if (member == null) {
            return false;
        }
        return ROLE_OWNER.equalsIgnoreCase(member.getRoleCode()) || ROLE_OPERATOR.equalsIgnoreCase(member.getRoleCode());
    }
}
