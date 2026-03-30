package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.config.WechathlinkResolvedDataSourceSpec;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkSetting;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkSettingMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkSettingsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WechathlinkSettingsServiceImpl extends WechathlinkServiceSupport implements WechathlinkSettingsService {

    private final WechathlinkSettingMapper settingMapper;
    private final WechathlinkResolvedDataSourceSpec dataSourceSpec;
    private final Environment environment;

    public WechathlinkSettingsServiceImpl(WechathlinkSettingMapper settingMapper,
                                          WechathlinkResolvedDataSourceSpec dataSourceSpec,
                                          Environment environment) {
        this.settingMapper = settingMapper;
        this.dataSourceSpec = dataSourceSpec;
        this.environment = environment;
    }

    @Override
    public Map<String, Object> get() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("runMode", dataSourceSpec.mode());
        values.put("listenAddr", environment.getProperty("eqadmin.wechathlink.runtime.listen-addr", "127.0.0.1:17890"));
        values.put("defaultBaseUrl", environment.getProperty("eqadmin.wechathlink.runtime.default-base-url", "https://ilinkai.weixin.qq.com"));
        values.put("cdnBaseUrl", environment.getProperty("eqadmin.wechathlink.runtime.cdn-base-url", "https://novac2c.cdn.weixin.qq.com/c2c"));
        values.put("pollTimeoutMs", environment.getProperty("eqadmin.wechathlink.runtime.poll-timeout-ms", "35000"));
        values.put("mediaDir", environment.getProperty("eqadmin.wechathlink.runtime.media-dir", "./data/wechathlink/media"));
        values.put("webhookUrl", environment.getProperty("eqadmin.wechathlink.runtime.webhook-url", ""));
        List<WechathlinkSetting> settings = settingMapper.selectList(new LambdaQueryWrapper<WechathlinkSetting>()
                .eq(WechathlinkSetting::getIsDeleted, 0));
        for (WechathlinkSetting item : settings) {
            values.put(item.getConfigKey(), item.getConfigValue());
        }
        return values;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(Map<String, Object> body) {
        if (body == null) {
            return get();
        }
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getKey() == null || "runMode".equals(entry.getKey())) {
                continue;
            }
            WechathlinkSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<WechathlinkSetting>()
                    .eq(WechathlinkSetting::getConfigGroup, "runtime")
                    .eq(WechathlinkSetting::getConfigKey, entry.getKey())
                    .eq(WechathlinkSetting::getIsDeleted, 0)
                    .last("LIMIT 1"));
            if (setting == null) {
                setting = new WechathlinkSetting();
                setting.setConfigGroup("runtime");
                setting.setConfigKey(entry.getKey());
                setting.setConfigType("string");
                setting.setIsSecret(entry.getKey().toLowerCase().contains("token") ? 1 : 0);
                setting.setConfigValue(entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
                fillCreateAudit(setting);
                settingMapper.insert(setting);
            } else {
                setting.setConfigValue(entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
                fillUpdateAudit(setting);
                settingMapper.updateById(setting);
            }
        }
        return get();
    }
}
