package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.zqzqq.bootkits.bootstrap.PluginContextHolder;
import com.zqzqq.bootkits.spring.SpringBeanFactory;
import lombok.extern.slf4j.Slf4j;
import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class WechathlinkDataSourceConfig {

    private static final String MYSQL_SCHEMA = "wechathlink";

    private final Environment environment;

    public WechathlinkDataSourceConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public WechathlinkResolvedDataSourceSpec wechathlinkResolvedDataSourceSpec() {
        DataSource hostDataSource = resolveHostDataSource();
        String forceMode = normalize(environment.getProperty("eqadmin.wechathlink.datasource.mode"));
        if (!"standalone".equals(forceMode) && hostDataSource instanceof DruidDataSource druidDataSource) {
            String mysqlUrl = deriveMysqlPluginUrl(druidDataSource.getUrl());
            if (StringUtils.hasText(mysqlUrl)) {
                ensureMysqlSchema(hostDataSource);
                return new WechathlinkResolvedDataSourceSpec("host-mysql", DbType.MYSQL, mysqlUrl);
            }
        }
        String sqliteUrl = resolveStandaloneSqliteUrl();
        return new WechathlinkResolvedDataSourceSpec("standalone-sqlite", DbType.SQLITE, sqliteUrl);
    }

    @Bean
    @Primary
    public DataSource dataSource(WechathlinkResolvedDataSourceSpec spec) {
        if (spec.sqlite()) {
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl(spec.jdbcUrl());
            log.info("wechathlink datasource mode: {}", spec.mode());
            return dataSource;
        }
        DruidDataSource hostDataSource = (DruidDataSource) resolveHostDataSource();
        if (hostDataSource == null) {
            throw new IllegalStateException("Host mainDataSource is required for MySQL plugin mode.");
        }
        DruidDataSource cloned = hostDataSource.cloneDruidDataSource();
        cloned.setUrl(spec.jdbcUrl());
        cloned.setValidationQuery("SELECT 1");
        log.info("wechathlink datasource mode: {}", spec.mode());
        return cloned;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("mybatisPlusInterceptorBean")
    public MybatisPlusInterceptor mybatisPlusInterceptor(WechathlinkResolvedDataSourceSpec spec) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(spec.dbType()));
        return interceptor;
    }

    private DataSource resolveHostDataSource() {
        try {
            if (PluginContextHolder.getMainApplicationContext() == null) {
                return null;
            }
            SpringBeanFactory springBeanFactory = PluginContextHolder.getMainApplicationContext().getSpringBeanFactory();
            if (springBeanFactory == null) {
                return null;
            }
            return springBeanFactory.getBean("mainDataSource", DataSource.class);
        } catch (Exception ex) {
            log.debug("wechathlink host datasource unavailable: {}", ex.getMessage());
            return null;
        }
    }

    private void ensureMysqlSchema(DataSource hostDataSource) {
        try (Connection connection = hostDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + MYSQL_SCHEMA + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (Exception ex) {
            throw new IllegalStateException("Create MySQL schema wechathlink failed: " + ex.getMessage(), ex);
        }
    }

    private String deriveMysqlPluginUrl(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.startsWith("jdbc:mysql:")) {
            return null;
        }
        String raw = jdbcUrl.substring("jdbc:".length());
        try {
            URI uri = new URI(raw);
            String query = uri.getRawQuery();
            return "jdbc:mysql://" + uri.getAuthority() + "/" + MYSQL_SCHEMA + (StringUtils.hasText(query) ? "?" + query : "");
        } catch (URISyntaxException ex) {
            int queryIndex = jdbcUrl.indexOf('?');
            String suffix = queryIndex >= 0 ? jdbcUrl.substring(queryIndex) : "";
            int schemeIndex = jdbcUrl.indexOf("://");
            if (schemeIndex < 0) {
                return null;
            }
            int slashAfterHost = jdbcUrl.indexOf('/', schemeIndex + 3);
            if (slashAfterHost < 0) {
                return jdbcUrl + "/" + MYSQL_SCHEMA + suffix;
            }
            return jdbcUrl.substring(0, slashAfterHost + 1) + MYSQL_SCHEMA + suffix;
        }
    }

    private String resolveStandaloneSqliteUrl() {
        String configured = environment.getProperty("eqadmin.wechathlink.datasource.sqlite.path");
        Path sqlitePath;
        if (StringUtils.hasText(configured)) {
            sqlitePath = Path.of(configured.trim()).toAbsolutePath().normalize();
        } else {
            sqlitePath = Path.of("./data/wechathlink.db").toAbsolutePath().normalize();
        }
        try {
            Files.createDirectories(sqlitePath.getParent());
        } catch (Exception ex) {
            throw new IllegalStateException("Create sqlite parent directory failed: " + ex.getMessage(), ex);
        }
        return "jdbc:sqlite:" + sqlitePath;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
