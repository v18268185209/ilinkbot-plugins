package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.service;

import cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.config.WechathlinkPluginStartupProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WechathlinkPermissionBootstrapService {

    private static final String ROOT_MENU_CODE = "wechathlink_root";
    private static final String ROOT_ROUTER = "/dashboard/plugins/wechathlink";
    private static final String MICROAPP_ENTRY_COMPONENT = "/pages/common/microapp-entry.vue";

    private final JdbcTemplate jdbcTemplate;
    private final WechathlinkPluginStartupProperties startupProperties;

    public WechathlinkPermissionBootstrapService(JdbcTemplate jdbcTemplate,
                                                 WechathlinkPluginStartupProperties startupProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.startupProperties = startupProperties;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bootstrapAndGrant(Long operatorId, String fallbackRoleCode) {
        if (!tableExists("qixiaozhu_menu") || !tableExists("qixiaozhu_perms")) {
            return Map.of("skipped", true, "reason", "host permission tables unavailable");
        }

        Long appRootId = resolveAppRootId();
        if (appRootId == null || appRootId <= 0) {
            return Map.of("skipped", true, "reason", "host app root not found");
        }

        long userId = operatorId == null || operatorId <= 0 ? 1L : operatorId;
        LocalDateTime now = LocalDateTime.now();

        int insertedMenus = 0;
        int updatedMenus = 0;
        int insertedPerms = 0;
        int updatedPerms = 0;
        int insertedBindings = 0;
        int grantedRoleResources = 0;

        MenuRecord rootRecord = upsertMenu(new MenuSpec(
                startupProperties.getMicroappZhname(),
                "dir",
                ROOT_ROUTER,
                "microapp",
                appRootId,
                appRootId,
                startupProperties.getMicroappEnname(),
                MICROAPP_ENTRY_COMPONENT,
                ROOT_MENU_CODE,
                "fas fa-comments",
                "fontawesome"
        ), userId, now);
        insertedMenus += rootRecord.inserted ? 1 : 0;
        updatedMenus += rootRecord.inserted ? 0 : 1;

        Set<Long> resourceIdsToGrant = new LinkedHashSet<>();
        if (rootRecord.id != null && rootRecord.id > 0) {
            resourceIdsToGrant.add(rootRecord.id);
        }

        for (PageSpec pageSpec : pageSpecs()) {
            MenuRecord pageMenu = upsertMenu(new MenuSpec(
                    pageSpec.name(),
                    "menu",
                    pageSpec.router(),
                    "microapp",
                    rootRecord.id,
                    appRootId,
                    startupProperties.getMicroappEnname(),
                    MICROAPP_ENTRY_COMPONENT,
                    pageSpec.menuCode(),
                    pageSpec.icon(),
                    "fontawesome"
            ), userId, now);
            insertedMenus += pageMenu.inserted ? 1 : 0;
            updatedMenus += pageMenu.inserted ? 0 : 1;

            boolean pagePermInserted = upsertPerm(pageSpec.name(), "menu", pageSpec.router(), pageSpec.menuCode(), userId, now);
            insertedPerms += pagePermInserted ? 1 : 0;
            updatedPerms += pagePermInserted ? 0 : 1;

            insertedBindings += bindApiPerms(pageMenu.id, pageSpec.apiRouters(), userId, now);
            if (pageMenu.id != null && pageMenu.id > 0) {
                resourceIdsToGrant.add(pageMenu.id);
            }
        }

        for (ButtonSpec buttonSpec : buttonSpecs()) {
            Long parentId = queryMenuIdByCode(buttonSpec.parentMenuCode());
            if (parentId == null || parentId <= 0) {
                continue;
            }
            MenuRecord buttonMenu = upsertMenu(new MenuSpec(
                    buttonSpec.name(),
                    "button",
                    buttonSpec.router(),
                    null,
                    parentId,
                    appRootId,
                    startupProperties.getMicroappEnname(),
                    null,
                    buttonSpec.parentMenuCode(),
                    null,
                    null
            ), userId, now);
            insertedMenus += buttonMenu.inserted ? 1 : 0;
            updatedMenus += buttonMenu.inserted ? 0 : 1;

            boolean buttonPermInserted = upsertPerm(buttonSpec.name(), "button", buttonSpec.router(), buttonSpec.parentMenuCode(), userId, now);
            insertedPerms += buttonPermInserted ? 1 : 0;
            updatedPerms += buttonPermInserted ? 0 : 1;

            insertedBindings += bindApiPerms(buttonMenu.id, buttonSpec.apiRouters(), userId, now);
            if (buttonMenu.id != null && buttonMenu.id > 0) {
                resourceIdsToGrant.add(buttonMenu.id);
            }
        }

        grantedRoleResources = grantRoleResources(resolveRoleIds(fallbackRoleCode), resourceIdsToGrant, userId, now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("insertedMenus", insertedMenus);
        result.put("updatedMenus", updatedMenus);
        result.put("insertedPerms", insertedPerms);
        result.put("updatedPerms", updatedPerms);
        result.put("insertedBindings", insertedBindings);
        result.put("grantedRoleResources", grantedRoleResources);
        result.put("rootMenuCode", ROOT_MENU_CODE);
        return result;
    }

    private List<PageSpec> pageSpecs() {
        return List.of(
                new PageSpec("微信接入总览", "wechathlink_overview", "/dashboard/plugins/wechathlink/overview", "fas fa-chart-line",
                        List.of("/api/wechathlink/admin/dashboard/summary")),
                new PageSpec("微信账号管理", "wechathlink_accounts", "/dashboard/plugins/wechathlink/accounts", "fas fa-user-friends",
                        List.of("/api/wechathlink/admin/accounts/list", "/api/wechathlink/admin/accounts/detail")),
                new PageSpec("事件中心", "wechathlink_events", "/dashboard/plugins/wechathlink/events", "fas fa-stream",
                        List.of("/api/wechathlink/admin/events/summary", "/api/wechathlink/admin/events/list", "/api/wechathlink/admin/events/contacts")),
                new PageSpec("消息发送", "wechathlink_messages", "/dashboard/plugins/wechathlink/messages", "fas fa-paper-plane",
                        List.of("/api/wechathlink/admin/messages/peers", "/api/wechathlink/admin/events/list")),
                new PageSpec("审计中心", "wechathlink_audits", "/dashboard/plugins/wechathlink/audits", "fas fa-clipboard-list",
                        List.of("/api/wechathlink/admin/audit/list")),
                new PageSpec("设置中心", "wechathlink_settings", "/dashboard/plugins/wechathlink/settings", "fas fa-cogs",
                        List.of("/api/wechathlink/admin/settings/get"))
        );
    }

    private List<ButtonSpec> buttonSpecs() {
        return List.of(
                new ButtonSpec("新增账号", "btn:wechathlink_accounts:create", "wechathlink_accounts", List.of("/api/wechathlink/admin/accounts/save")),
                new ButtonSpec("启停账号", "btn:wechathlink_accounts:toggle", "wechathlink_accounts", List.of("/api/wechathlink/admin/accounts/toggle")),
                new ButtonSpec("成员授权", "btn:wechathlink_accounts:member", "wechathlink_accounts", List.of("/api/wechathlink/admin/accounts/member/save")),
                new ButtonSpec("扫码接入", "btn:wechathlink_accounts:loginstart", "wechathlink_accounts", List.of("/api/wechathlink/admin/login/start")),
                new ButtonSpec("刷新登录状态", "btn:wechathlink_accounts:loginstatus", "wechathlink_accounts", List.of("/api/wechathlink/admin/login/status")),
                new ButtonSpec("导出事件", "btn:wechathlink_events:export", "wechathlink_events", List.of("/api/wechathlink/admin/events/export")),
                new ButtonSpec("查看事件媒体", "btn:wechathlink_events:media", "wechathlink_events", List.of("/api/wechathlink/admin/events/media")),
                new ButtonSpec("上传文件", "btn:wechathlink_messages:upload", "wechathlink_messages", List.of("/api/wechathlink/admin/messages/upload")),
                new ButtonSpec("发送文本", "btn:wechathlink_messages:sendtext", "wechathlink_messages", List.of("/api/wechathlink/admin/messages/send-text")),
                new ButtonSpec("发送媒体", "btn:wechathlink_messages:sendmedia", "wechathlink_messages", List.of("/api/wechathlink/admin/messages/send-media")),
                new ButtonSpec("查看会话媒体", "btn:wechathlink_messages:media", "wechathlink_messages", List.of("/api/wechathlink/admin/events/media")),
                new ButtonSpec("审计详情", "btn:wechathlink_audits:detail", "wechathlink_audits", List.of("/api/wechathlink/admin/audit/detail")),
                new ButtonSpec("保存设置", "btn:wechathlink_settings:save", "wechathlink_settings", List.of("/api/wechathlink/admin/settings/save"))
        );
    }

    private MenuRecord upsertMenu(MenuSpec spec, Long operatorId, LocalDateTime now) {
        Long existingId = queryMenuIdByRouter(spec.router());
        if (existingId == null && spec.menuCode() != null) {
            existingId = queryMenuIdByCode(spec.menuCode());
        }
        Integer sort = existingId == null ? resolveNextSort(spec.parentId()) : queryMenuSort(existingId);
        Timestamp timestamp = Timestamp.valueOf(now);
        if (existingId == null) {
            jdbcTemplate.update("""
                    INSERT INTO qixiaozhu_menu
                    (name, `type`, icon, icon_type, router, open_type, parent_id, create_user_id, update_user_id, status, deleted, create_time, update_time, component, sort, app_id, microapp_name, menu_code)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ok', 0, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    spec.name(), spec.menuType(), spec.icon(), spec.iconType(), spec.router(), spec.openType(), spec.parentId(),
                    operatorId, operatorId, timestamp, timestamp, spec.component(), sort, spec.appId(), spec.microappName(), spec.menuCode());
            return new MenuRecord(queryMenuIdByRouter(spec.router()), true);
        }
        jdbcTemplate.update("""
                UPDATE qixiaozhu_menu
                   SET name=?, `type`=?, icon=?, icon_type=?, router=?, open_type=?, parent_id=?, update_user_id=?, status='ok', deleted=0, update_time=?, component=?, sort=?, app_id=?, microapp_name=?, menu_code=?
                 WHERE id=?
                """,
                spec.name(), spec.menuType(), spec.icon(), spec.iconType(), spec.router(), spec.openType(), spec.parentId(),
                operatorId, timestamp, spec.component(), sort, spec.appId(), spec.microappName(), spec.menuCode(), existingId);
        return new MenuRecord(existingId, false);
    }

    private boolean upsertPerm(String desc, String type, String router, String menuCode, Long operatorId, LocalDateTime now) {
        Long existingId = queryPermIdByRouter(router);
        Timestamp timestamp = Timestamp.valueOf(now);
        if (existingId == null) {
            jdbcTemplate.update("""
                    INSERT INTO qixiaozhu_perms
                    (`type`, `desc`, router, status, deleted, create_time, update_time, create_user_id, update_user_id, menu_code)
                    VALUES (?, ?, ?, 'ok', 0, ?, ?, ?, ?, ?)
                    """,
                    type, desc, router, timestamp, timestamp, operatorId, operatorId, menuCode);
            return true;
        }
        jdbcTemplate.update("""
                UPDATE qixiaozhu_perms
                   SET `type`=?, `desc`=?, router=?, status='ok', deleted=0, update_time=?, update_user_id=?, menu_code=?
                 WHERE id=?
                """,
                type, desc, router, timestamp, operatorId, menuCode, existingId);
        return false;
    }

    private int bindApiPerms(Long resourceId, List<String> apiRouters, Long operatorId, LocalDateTime now) {
        if (resourceId == null || resourceId <= 0 || apiRouters == null || apiRouters.isEmpty() || !tableExists("qixiaozhu_resource_api_bind")) {
            return 0;
        }
        Timestamp timestamp = Timestamp.valueOf(now);
        int inserted = 0;
        for (String apiRouter : apiRouters) {
            Long apiPermId = queryApiPermIdByRouter(apiRouter);
            if (apiPermId == null || apiPermId <= 0) {
                continue;
            }
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM qixiaozhu_resource_api_bind WHERE resource_id = ? AND api_perm_id = ?",
                    Integer.class,
                    resourceId,
                    apiPermId
            );
            if (count != null && count > 0) {
                jdbcTemplate.update(
                        "UPDATE qixiaozhu_resource_api_bind SET enabled = 1, update_user_id = ?, update_time = ? WHERE resource_id = ? AND api_perm_id = ?",
                        operatorId,
                        timestamp,
                        resourceId,
                        apiPermId
                );
                continue;
            }
            jdbcTemplate.update("""
                    INSERT INTO qixiaozhu_resource_api_bind
                    (resource_id, api_perm_id, enabled, create_user_id, update_user_id, create_time, update_time)
                    VALUES (?, ?, 1, ?, ?, ?, ?)
                    """,
                    resourceId, apiPermId, operatorId, operatorId, timestamp, timestamp);
            inserted++;
        }
        return inserted;
    }

    private int grantRoleResources(Set<Long> roleIds, Set<Long> resourceIds, Long operatorId, LocalDateTime now) {
        if (roleIds.isEmpty() || resourceIds.isEmpty() || !tableExists("qixiaozhu_role_resource_grant")) {
            return 0;
        }
        Timestamp timestamp = Timestamp.valueOf(now);
        int inserted = 0;
        for (Long roleId : roleIds) {
            for (Long resourceId : resourceIds) {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM qixiaozhu_role_resource_grant WHERE role_id = ? AND resource_id = ?",
                        Integer.class,
                        roleId,
                        resourceId
                );
                if (count != null && count > 0) {
                    continue;
                }
                jdbcTemplate.update("""
                        INSERT INTO qixiaozhu_role_resource_grant
                        (role_id, resource_id, create_user_id, update_user_id, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        roleId, resourceId, operatorId, operatorId, timestamp, timestamp);
                inserted++;
            }
        }
        return inserted;
    }

    private Set<Long> resolveRoleIds(String fallbackRoleCode) {
        if (!tableExists("qixiaozhu_role")) {
            return Collections.emptySet();
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM qixiaozhu_role WHERE deleted = 0 AND status = 'ok' AND code = ?",
                (rs, rowNum) -> rs.getLong("id"),
                fallbackRoleCode == null || fallbackRoleCode.isBlank() ? "sys" : fallbackRoleCode.trim()
        );
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Long resolveAppRootId() {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM qixiaozhu_menu WHERE deleted = 0 AND status = 'ok' AND `type` = 'app' ORDER BY sort ASC, id ASC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id")
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long queryMenuIdByRouter(String router) {
        if (router == null || router.isBlank()) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM qixiaozhu_menu WHERE deleted = 0 AND router = ? ORDER BY id ASC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                router
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long queryMenuIdByCode(String menuCode) {
        if (menuCode == null || menuCode.isBlank()) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM qixiaozhu_menu WHERE deleted = 0 AND menu_code = ? ORDER BY id ASC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                menuCode
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Integer queryMenuSort(Long id) {
        if (id == null || id <= 0) {
            return 1;
        }
        Integer sort = jdbcTemplate.query(
                "SELECT sort FROM qixiaozhu_menu WHERE id = ?",
                (rs, rowNum) -> rs.getObject("sort") == null ? null : rs.getInt("sort"),
                id
        ).stream().findFirst().orElse(null);
        return sort == null || sort <= 0 ? 1 : sort;
    }

    private Integer resolveNextSort(Long parentId) {
        Integer sort = jdbcTemplate.query(
                "SELECT sort FROM qixiaozhu_menu WHERE deleted = 0 AND parent_id = ? ORDER BY sort DESC, id DESC LIMIT 1",
                (rs, rowNum) -> rs.getObject("sort") == null ? null : rs.getInt("sort"),
                parentId == null ? 0L : parentId
        ).stream().findFirst().orElse(null);
        return sort == null ? 1 : sort + 1;
    }

    private Long queryPermIdByRouter(String router) {
        if (router == null || router.isBlank()) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM qixiaozhu_perms WHERE deleted = 0 AND router = ? ORDER BY id ASC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                router
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long queryApiPermIdByRouter(String router) {
        if (router == null || router.isBlank()) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM qixiaozhu_perms WHERE deleted = 0 AND status = 'ok' AND `type` = 'api' AND router = ? ORDER BY id ASC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                router
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean tableExists(String tableName) {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + tableName, Long.class);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private record PageSpec(String name, String menuCode, String router, String icon, List<String> apiRouters) {
    }

    private record ButtonSpec(String name, String router, String parentMenuCode, List<String> apiRouters) {
    }

    private record MenuSpec(String name,
                            String menuType,
                            String router,
                            String openType,
                            Long parentId,
                            Long appId,
                            String microappName,
                            String component,
                            String menuCode,
                            String icon,
                            String iconType) {
    }

    private record MenuRecord(Long id, boolean inserted) {
    }
}
