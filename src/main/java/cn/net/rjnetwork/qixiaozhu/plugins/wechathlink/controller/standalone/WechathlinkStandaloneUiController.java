package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.standalone;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(prefix = "eqadmin.wechathlink.standalone-web", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WechathlinkStandaloneUiController {

    @GetMapping({
            "/",
            "/overview",
            "/accounts",
            "/events",
            "/messages",
            "/audits",
            "/platform",
            "/settings",
            "/dashboard/plugins/wechathlink",
            "/dashboard/plugins/wechathlink/**"
    })
    public String index() {
        return "forward:/index.html";
    }
}
