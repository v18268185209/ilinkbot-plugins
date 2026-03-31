package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.impl;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.config.WechathlinkResolvedDataSourceSpec;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity.WechathlinkSetting;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper.WechathlinkSettingMapper;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkAuditService;
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
    private final WechathlinkPermissionService permissionService;
    private final WechathlinkAuditService auditService;

    public WechathlinkSettingsServiceImpl(WechathlinkSettingMapper settingMapper,
                                          WechathlinkResolvedDataSourceSpec dataSourceSpec,
                                          Environment environment,
                                          WechathlinkPermissionService permissionService,
                                          WechathlinkAuditService auditService) {
        this.settingMapper = settingMapper;
        this.dataSourceSpec = dataSourceSpec;
        this.environment = environment;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @Override
    public Map<String, Object> get() {
        requireSettingsPermission();
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
        requireSettingsPermission();
        if (body == null) {
            return get();
        }
        Map<String, Object> auditPayload = sanitizeSettingsBody(body);
        try {
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
            auditService.recordSuccess(null, "SETTINGS_SAVE", "SETTING", "runtime", "runtime settings saved", auditPayload);
        } catch (RuntimeException ex) {
            auditService.recordFailure(null, "SETTINGS_SAVE", "SETTING", "runtime", "runtime settings save failed", ex, auditPayload);
            throw ex;
        }
        return get();
    }

    private void requireSettingsPermission() {
        if (!permissionService.canManageSettings()) {
            throw new IllegalArgumentException("settings not found or no permission");
        }
    }

    private Map<String, Object> sanitizeSettingsBody(Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getKey() == null || "runMode".equals(entry.getKey())) {
                continue;
            }
            String key = entry.getKey();
            Object value = entry.getValue();
            boolean secret = key.toLowerCase().contains("token") || key.toLowerCase().contains("secret") || key.toLowerCase().contains("password");
            payload.put(key, secret ? "******" : (value == null ? "" : String.valueOf(value)));
        }
        return payload;
    }
}
