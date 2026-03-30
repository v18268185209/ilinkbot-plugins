package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink;

import com.zqzqq.bootkits.bootstrap.SpringPluginBootstrap;
import com.zqzqq.bootkits.bootstrap.coexist.CoexistAllowAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.actuate.autoconfigure.metrics.jdbc.DataSourcePoolMetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        RedisAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class,
        DataSourcePoolMetricsAutoConfiguration.class
})
@EnableConfigurationProperties
@EnableScheduling
@EnableAsync
@Slf4j
@MapperScan(basePackages = {"cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.mapper"})
public class WechathlinkPluginsApp extends SpringPluginBootstrap {

    public static void main(String[] args) {
        log.info("starting wechat-hlink plugin at {}", System.currentTimeMillis());
        new WechathlinkPluginsApp().run(enrichStandaloneArgs(args));
    }

    @Override
    protected void configCoexistAllowAutoConfiguration(CoexistAllowAutoConfiguration c) {
        c.add("com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration");
        c.add("org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration");
    }

    private static String[] enrichStandaloneArgs(String[] args) {
        String[] safeArgs = args == null ? new String[0] : args;
        List<String> extraArgs = new ArrayList<>();
        if (!containsArg(safeArgs, "spring.config.import")) {
            extraArgs.add("--spring.config.import=optional:classpath:wechathlink-application-local.yaml,optional:classpath:application-local.yaml");
        }
        if (extraArgs.isEmpty()) {
            return safeArgs;
        }
        List<String> merged = new ArrayList<>(extraArgs);
        merged.addAll(Arrays.asList(safeArgs));
        return merged.toArray(new String[0]);
    }

    private static boolean containsArg(String[] args, String key) {
        String prefix = "--" + key + "=";
        String propertyPrefix = "-D" + key + "=";
        for (String arg : args) {
            if (arg == null) {
                continue;
            }
            String value = arg.trim();
            if (value.equals("--" + key) || value.startsWith(prefix) || value.startsWith(propertyPrefix)) {
                return true;
            }
        }
        return false;
    }
}
