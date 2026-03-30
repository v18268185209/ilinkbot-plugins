package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.config;

import com.baomidou.mybatisplus.annotation.DbType;

public record WechathlinkResolvedDataSourceSpec(
        String mode,
        DbType dbType,
        String jdbcUrl
) {
    public boolean sqlite() {
        return dbType == DbType.SQLITE;
    }
}
