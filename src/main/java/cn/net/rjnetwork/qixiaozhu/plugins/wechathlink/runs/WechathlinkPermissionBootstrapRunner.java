package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.runs;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service.WechathlinkPermissionBootstrapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 28)
public class WechathlinkPermissionBootstrapRunner implements ApplicationRunner {

    @Autowired(required = false)
    private WechathlinkPermissionBootstrapService permissionBootstrapService;

    @Value("${eqadmin.wechathlink.permission.bootstrap-on-startup:true}")
    private boolean bootstrapOnStartup;

    @Value("${eqadmin.wechathlink.permission.bootstrap-operator-id:1}")
    private Long bootstrapOperatorId;

    @Value("${eqadmin.wechathlink.permission.fallback-role-code:sys}")
    private String fallbackRoleCode;

    @Value("${eqadmin.install.required:false}")
    private boolean installRequired;

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapOnStartup) {
            log.info("wechathlink permission bootstrap skipped: bootstrap-on-startup disabled");
            return;
        }
        if (installRequired) {
            log.info("wechathlink permission bootstrap skipped: system is in install mode");
            return;
        }
        if (permissionBootstrapService == null) {
            log.warn("wechathlink permission bootstrap skipped: service unavailable");
            return;
        }
        Long operatorId = bootstrapOperatorId == null || bootstrapOperatorId <= 0 ? 1L : bootstrapOperatorId;
        try {
            Map<String, Object> result = permissionBootstrapService.bootstrapAndGrant(operatorId, fallbackRoleCode);
            log.info("wechathlink permission bootstrap completed: {}", result);
        } catch (Exception ex) {
            log.error("wechathlink permission bootstrap failed: {}", ex.getMessage(), ex);
        }
    }
}
