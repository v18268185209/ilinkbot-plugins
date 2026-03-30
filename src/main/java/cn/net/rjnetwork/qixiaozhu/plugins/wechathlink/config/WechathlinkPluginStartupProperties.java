package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eqadmin.wechathlink.startup")
@Data
public class WechathlinkPluginStartupProperties {

    private boolean enabled = true;
    private boolean failFast = false;
    private boolean staticSyncEnabled = true;
    private boolean staticSyncRequired = false;
    private String staticClasspathRoot = "wechat-hlink-plugins/web/childrens/wechathlink";
    private String targetChildDirName = "wechathlink";
    private String backupDirName = "_backup";
    private boolean microappSyncEnabled = true;
    private Long microappCreateUserId = 1L;
    private String microappEnname = "wechatHlinkPlugins";
    private String microappZhname = "微信接入";
    private String microappBaseUrl = "/childrens/wechathlink/";
    private Integer microappTimeout = 0;
    private Integer microappIframe = 1;
    private String microappContainer = "iframe";
    private Integer microappStatus = 1;
    private String microappSchem = "https";
}
