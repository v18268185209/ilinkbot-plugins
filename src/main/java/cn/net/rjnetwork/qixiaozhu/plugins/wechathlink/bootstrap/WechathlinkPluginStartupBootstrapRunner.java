package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.bootstrap;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.config.WechathlinkPluginStartupProperties;
import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.config.WechathlinkResolvedDataSourceSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class WechathlinkPluginStartupBootstrapRunner implements ApplicationRunner {

    private static final DateTimeFormatter BACKUP_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbcTemplate;
    private final WechathlinkResolvedDataSourceSpec dataSourceSpec;
    private final WechathlinkPluginStartupProperties properties;

    public WechathlinkPluginStartupBootstrapRunner(JdbcTemplate jdbcTemplate,
                                                   WechathlinkResolvedDataSourceSpec dataSourceSpec,
                                                   WechathlinkPluginStartupProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSourceSpec = dataSourceSpec;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.isEnabled()) {
            return;
        }
        executeSchema();
        if (properties.isStaticSyncEnabled()) {
            ensureStaticAssets();
        }
        ensureMicroappInfo();
    }

    private void executeSchema() throws Exception {
        String mode = dataSourceSpec.sqlite() ? "sqlite" : "mysql";
        ensureSchemaHistoryTable();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());
        Resource[] resources = resolver.getResources("classpath*:db/changelog/sql/" + mode + "/**/*.sql");
        List<Resource> scripts = new ArrayList<>();
        for (Resource resource : resources) {
            if (resource != null && resource.isReadable() && resource.getFilename() != null) {
                scripts.add(resource);
            }
        }
        scripts.sort(Comparator.comparing(this::resourceKey));
        for (Resource resource : scripts) {
            String resourceKey = resourceKey(resource);
            if (schemaScriptExecuted(resourceKey)) {
                continue;
            }
            executeSqlScript(resource);
            markSchemaScriptExecuted(resourceKey);
        }
        log.info("wechathlink schema initialized in mode={}", mode);
    }

    private void ensureSchemaHistoryTable() {
        if (dataSourceSpec.sqlite()) {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS wcf_schema_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        script_key TEXT NOT NULL UNIQUE,
                        executed_at TEXT NOT NULL
                    )
                    """);
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS wcf_schema_history (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    script_key VARCHAR(512) NOT NULL,
                    executed_at DATETIME NOT NULL,
                    UNIQUE KEY uk_wcf_schema_history_script_key (script_key)
                )
                """);
    }

    private boolean schemaScriptExecuted(String scriptKey) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wcf_schema_history WHERE script_key = ?",
                Long.class,
                scriptKey
        );
        return count != null && count > 0;
    }

    private void markSchemaScriptExecuted(String scriptKey) {
        if (dataSourceSpec.sqlite()) {
            jdbcTemplate.update(
                    "INSERT INTO wcf_schema_history (script_key, executed_at) VALUES (?, ?)",
                    scriptKey,
                    LocalDateTime.now().toString()
            );
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO wcf_schema_history (script_key, executed_at) VALUES (?, ?)",
                scriptKey,
                LocalDateTime.now()
        );
    }

    private void executeSqlScript(Resource resource) throws Exception {
        String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        for (String statement : script.split(";\\s*(\\r?\\n|$)")) {
            String sql = statement.trim();
            if (!sql.isEmpty()) {
                jdbcTemplate.execute(sql);
            }
        }
    }

    private String resourceKey(Resource resource) {
        try {
            String uri = resource.getURI().toString();
            int index = uri.indexOf("db/changelog/sql/");
            if (index >= 0) {
                return uri.substring(index);
            }
            return resource.getFilename() == null ? uri : resource.getFilename();
        } catch (Exception ex) {
            return resource.getFilename() == null ? String.valueOf(resource) : resource.getFilename();
        }
    }

    private void ensureMicroappInfo() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(1) FROM qixiaozhu_microapp_info", Long.class);
        } catch (Exception ignored) {
            return;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM qixiaozhu_microapp_info WHERE microapp_enname = ?",
                Long.class,
                properties.getMicroappEnname()
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    "UPDATE qixiaozhu_microapp_info SET microapp_zhname=?,base_url=?,timeout=?,iframe=?,container=?,`status`=?,schem=? WHERE microapp_enname=?",
                    properties.getMicroappZhname(),
                    properties.getMicroappBaseUrl(),
                    properties.getMicroappTimeout(),
                    properties.getMicroappIframe(),
                    properties.getMicroappContainer(),
                    properties.getMicroappStatus(),
                    properties.getMicroappSchem(),
                    properties.getMicroappEnname()
            );
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO qixiaozhu_microapp_info (microapp_enname,microapp_zhname,base_url,timeout,iframe,container,create_time,create_user_id,`status`,schem) VALUES (?,?,?,?,?,?,NOW(),?,?,?)",
                properties.getMicroappEnname(),
                properties.getMicroappZhname(),
                properties.getMicroappBaseUrl(),
                properties.getMicroappTimeout(),
                properties.getMicroappIframe(),
                properties.getMicroappContainer(),
                properties.getMicroappCreateUserId(),
                properties.getMicroappStatus(),
                properties.getMicroappSchem()
        );
    }

    private void ensureStaticAssets() throws Exception {
        String root = properties.getStaticClasspathRoot();
        if (!StringUtils.hasText(root)) {
            return;
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());
        Resource[] resources = resolver.getResources("classpath*:" + root + "/**/*");
        Map<String, Resource> fileMap = new TreeMap<>();
        for (Resource resource : resources) {
            if (resource.isReadable()) {
                String uri = resource.getURI().toString();
                int index = uri.indexOf(root + "/");
                if (index >= 0) {
                    String relative = uri.substring(index + root.length() + 1);
                    if (!relative.isBlank()) {
                        fileMap.put(relative, resource);
                    }
                }
            }
        }
        if (fileMap.isEmpty()) {
            if (properties.isStaticSyncRequired()) {
                throw new IllegalStateException("wechathlink static assets not found in classpath root: " + root);
            }
            return;
        }
        Path staticRoot = Path.of("./webapps").toAbsolutePath().normalize();
        Path target = staticRoot.resolve("childrens").resolve(properties.getTargetChildDirName()).normalize();
        if (Files.exists(target) && Files.isDirectory(target)) {
            Path backup = target.getParent().resolve(properties.getBackupDirName())
                    .resolve(target.getFileName() + "-" + BACKUP_TS_FORMATTER.format(LocalDateTime.now()))
                    .normalize();
            Files.createDirectories(backup.getParent());
            copyDir(target, backup);
            deleteDir(target);
        }
        Files.createDirectories(target);
        for (Map.Entry<String, Resource> entry : fileMap.entrySet()) {
            Path file = target.resolve(entry.getKey()).normalize();
            Files.createDirectories(file.getParent());
            try (var in = entry.getValue().getInputStream()) {
                Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void copyDir(Path source, Path target) throws Exception {
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path next = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(next);
                } else {
                    Files.createDirectories(next.getParent());
                    Files.copy(path, next, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void deleteDir(Path source) throws Exception {
        try (var stream = Files.walk(source)) {
            for (Path path : stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
