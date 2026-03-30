package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.config.WechathlinkResolvedDataSourceSpec;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkSetting;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkSettingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WechathlinkRuntimeConfigService {

    private final WechathlinkSettingMapper settingMapper;
    private final Environment environment;
    private final WechathlinkResolvedDataSourceSpec dataSourceSpec;

    public WechathlinkRuntimeConfigService(WechathlinkSettingMapper settingMapper,
                                           Environment environment,
                                           WechathlinkResolvedDataSourceSpec dataSourceSpec) {
        this.settingMapper = settingMapper;
        this.environment = environment;
        this.dataSourceSpec = dataSourceSpec;
    }

    public RuntimeSettings current() {
        return new RuntimeSettings(
                dataSourceSpec.mode(),
                value("listenAddr", environment.getProperty("eqadmin.wechathlink.runtime.listen-addr", "127.0.0.1:17890")),
                value("defaultBaseUrl", environment.getProperty("eqadmin.wechathlink.runtime.default-base-url", "https://ilinkai.weixin.qq.com")),
                value("cdnBaseUrl", environment.getProperty("eqadmin.wechathlink.runtime.cdn-base-url", "https://novac2c.cdn.weixin.qq.com/c2c")),
                intValue("pollTimeoutMs", 35000),
                value("mediaDir", environment.getProperty("eqadmin.wechathlink.runtime.media-dir", "./data/wechathlink/media")),
                value("webhookUrl", environment.getProperty("eqadmin.wechathlink.runtime.webhook-url", ""))
        );
    }

    private String value(String key, String fallback) {
        WechathlinkSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<WechathlinkSetting>()
                .eq(WechathlinkSetting::getConfigGroup, "runtime")
                .eq(WechathlinkSetting::getConfigKey, key)
                .eq(WechathlinkSetting::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (setting != null && StringUtils.hasText(setting.getConfigValue())) {
            return setting.getConfigValue().trim();
        }
        return fallback;
    }

    private int intValue(String key, int fallback) {
        String text = value(key, String.valueOf(fallback));
        try {
            return Integer.parseInt(text);
        } catch (Exception ex) {
            return fallback;
        }
    }

    public record RuntimeSettings(
            String runMode,
            String listenAddr,
            String defaultBaseUrl,
            String cdnBaseUrl,
            int pollTimeoutMs,
            String mediaDir,
            String webhookUrl
    ) {
    }
}
