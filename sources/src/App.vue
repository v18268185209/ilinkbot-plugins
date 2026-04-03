<template>
  <n-config-provider :locale="zhCN" :date-locale="dateZhCN">
    <n-dialog-provider>
      <n-message-provider>
        <n-notification-provider>
          <n-layout :class="['shell', `shell--${shellMode}`]">
            <template v-if="shellMode === SHELL_MODE_STANDALONE">
              <aside class="shell-sidebar">
                <div class="brand brand--sidebar">
                  <span class="brand-kicker">Standalone Console</span>
                  <strong>微信接入控制台</strong>
                  <small>按业务链路组织接入、会话、追踪与治理。</small>
                </div>

                <section
                  v-for="section in standaloneNavigation"
                  :key="section.key"
                  class="nav-section"
                >
                  <header class="nav-section__header">
                    <strong>{{ section.label }}</strong>
                    <span>{{ section.description }}</span>
                  </header>

                  <div class="nav-list">
                    <button
                      v-for="item in section.items"
                      :key="item.key"
                      type="button"
                      :class="['nav-item', currentNavKey === item.key ? 'is-active' : '']"
                      @click="go(item)"
                    >
                      <span>{{ resolveNavLabel(item, shellMode) }}</span>
                      <small>{{ resolveNavKicker(item, shellMode) }}</small>
                    </button>
                  </div>
                </section>
              </aside>

              <div class="shell-main">
                <n-layout-header bordered class="shell-header shell-header--standalone">
                  <div class="shell-header__title">
                    <span class="route-kicker">{{ currentNavKicker }}</span>
                    <strong>{{ currentNavLabel }}</strong>
                    <small>{{ currentNavDescription }}</small>
                  </div>

                  <div class="shell-header__actions">
                    <span class="mode-chip">独立模式</span>
                    <span class="route-badge">{{ currentNavLabel }}</span>
                  </div>
                </n-layout-header>

                <n-layout-content class="shell-content shell-content--standalone">
                  <router-view />
                </n-layout-content>
              </div>
            </template>

            <template v-else>
              <n-layout-header bordered class="shell-header shell-header--plugin">
                <div class="brand">
                  <span class="brand-kicker">Plugin Workspace</span>
                  <strong>微信接入插件</strong>
                </div>

                <nav class="plugin-primary-nav">
                  <button
                    v-for="item in pluginPrimaryNavigation"
                    :key="item.key"
                    type="button"
                    :class="['plugin-primary-nav__item', currentNavKey === item.key ? 'is-active' : '']"
                    @click="go(item)"
                  >
                    {{ resolveNavLabel(item, shellMode) }}
                  </button>
                </nav>

                <div class="plugin-utility-nav">
                  <button
                    v-for="item in pluginUtilityNavigation"
                    :key="item.key"
                    type="button"
                    :class="['plugin-utility-nav__item', currentNavKey === item.key ? 'is-active' : '']"
                    @click="go(item)"
                  >
                    {{ resolveNavLabel(item, shellMode) }}
                  </button>
                </div>
              </n-layout-header>

              <section class="plugin-context-bar">
                <div class="plugin-context-bar__title">
                  <span class="route-kicker">{{ currentNavKicker }}</span>
                  <strong>{{ currentNavLabel }}</strong>
                </div>
                <div class="plugin-context-bar__meta">
                  <span>{{ currentNavDescription }}</span>
                  <span class="mode-chip mode-chip--subtle">插件模式</span>
                </div>
              </section>

              <n-layout-content class="shell-content shell-content--plugin">
                <router-view />
              </n-layout-content>
            </template>
          </n-layout>
        </n-notification-provider>
      </n-message-provider>
    </n-dialog-provider>
  </n-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dateZhCN, zhCN } from 'naive-ui'
import appEnv from './config/env'
import {
  resolveNavKicker,
  resolveNavLabel,
  resolveShellMode,
  routeMetaConfig,
  SHELL_MODE_PLUGIN,
  SHELL_MODE_STANDALONE,
  sortByOrder,
  standaloneSections
} from './config/navigation'
import { hasPermission } from './utils/permission'

const route = useRoute()
const router = useRouter()
const allNavItems = Object.values(routeMetaConfig)

const shellMode = computed(() => {
  void route.fullPath
  return resolveShellMode(appEnv, window.location.pathname)
})

const visibleNavItems = computed(() => allNavItems.filter((item) => hasPermission(item.permission)))
const currentNavKey = computed(() => route.meta?.key || visibleNavItems.value[0]?.key || 'overview')
const currentNav = computed(() => visibleNavItems.value.find((item) => item.key === currentNavKey.value)
  || allNavItems.find((item) => item.key === currentNavKey.value)
  || routeMetaConfig.overview)

const standaloneNavigation = computed(() => standaloneSections.map((section) => ({
  ...section,
  items: visibleNavItems.value
    .filter((item) => item.standaloneSection === section.key)
    .sort((a, b) => sortByOrder(a, b, 'standaloneOrder'))
})).filter((section) => section.items.length > 0))

const pluginPrimaryNavigation = computed(() => visibleNavItems.value
  .filter((item) => item.pluginArea === 'primary')
  .sort((a, b) => sortByOrder(a, b, 'pluginOrder')))

const pluginUtilityNavigation = computed(() => visibleNavItems.value
  .filter((item) => item.pluginArea === 'utility')
  .sort((a, b) => sortByOrder(a, b, 'pluginOrder')))

const currentNavLabel = computed(() => resolveNavLabel(currentNav.value, shellMode.value))
const currentNavKicker = computed(() => resolveNavKicker(currentNav.value, shellMode.value))
const currentNavDescription = computed(() => currentNav.value?.description || '')

function go(item) {
  if (!item?.path || item.path === route.path) {
    return
  }
  router.push(item.path)
}
</script>

<style>
:root {
  color-scheme: light;
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  background:
    radial-gradient(circle at top left, rgba(15, 118, 110, 0.14), transparent 30%),
    radial-gradient(circle at top right, rgba(2, 132, 199, 0.12), transparent 24%),
    linear-gradient(180deg, #f7fbff 0%, #edf3f8 100%);
}

html,
body,
#wechatHlinkApp {
  margin: 0;
  min-height: 100%;
}

body {
  color: #0f172a;
}

* {
  box-sizing: border-box;
}

.shell {
  min-height: 100vh;
}

.shell > .n-layout-scroll-container {
  min-height: inherit;
}

.shell--standalone > .n-layout-scroll-container {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
}

.shell--plugin > .n-layout-scroll-container {
  display: flex;
  flex-direction: column;
}

.shell-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-height: 100vh;
  padding: 28px 22px;
  background:
    linear-gradient(180deg, rgba(12, 31, 43, 0.96) 0%, rgba(15, 35, 52, 0.94) 100%);
  color: #e2e8f0;
  border-right: 1px solid rgba(148, 163, 184, 0.14);
}

.shell-main {
  min-width: 0;
}

.shell-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(18px);
}

.shell-header--standalone {
  padding: 18px 28px;
}

.shell-header--plugin {
  padding: 14px 22px;
  flex-wrap: wrap;
}

.shell-content {
  padding: 20px;
}

.shell-content--standalone {
  padding: 24px 28px 28px;
}

.shell-content--plugin {
  padding-top: 18px;
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.brand--sidebar {
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

.brand strong {
  font-size: 20px;
  line-height: 1.2;
}

.brand small {
  color: rgba(226, 232, 240, 0.72);
  line-height: 1.5;
}

.brand-kicker,
.route-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #0f766e;
}

.brand--sidebar .brand-kicker {
  color: #67e8f9;
}

.nav-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.nav-section__header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-section__header strong {
  font-size: 13px;
  color: #f8fafc;
}

.nav-section__header span {
  color: rgba(226, 232, 240, 0.62);
  font-size: 12px;
  line-height: 1.5;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-item,
.plugin-primary-nav__item,
.plugin-utility-nav__item {
  border: none;
  cursor: pointer;
  transition: all 0.18s ease;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.06);
  color: #f8fafc;
  text-align: left;
}

.nav-item small {
  color: rgba(226, 232, 240, 0.62);
}

.nav-item.is-active {
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.96) 0%, rgba(2, 132, 199, 0.92) 100%);
  box-shadow: 0 16px 30px rgba(8, 47, 73, 0.22);
}

.nav-item.is-active small {
  color: rgba(255, 255, 255, 0.82);
}

.shell-header__title {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.shell-header__title strong {
  font-size: 28px;
  line-height: 1.15;
}

.shell-header__title small {
  color: #64748b;
  font-size: 14px;
}

.shell-header__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.mode-chip,
.route-badge {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.mode-chip {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

.mode-chip--subtle {
  min-height: 30px;
  padding: 0 12px;
}

.route-badge {
  background: rgba(2, 132, 199, 0.1);
  color: #0369a1;
}

.plugin-primary-nav,
.plugin-utility-nav {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.plugin-primary-nav {
  flex: 1;
  justify-content: center;
}

.plugin-primary-nav__item {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
  color: #334155;
  font-weight: 700;
}

.plugin-primary-nav__item.is-active {
  background: linear-gradient(135deg, #0f766e 0%, #0284c7 100%);
  color: #f8fafc;
  box-shadow: 0 16px 30px rgba(14, 116, 144, 0.2);
}

.plugin-utility-nav__item {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.12);
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.plugin-utility-nav__item.is-active {
  background: rgba(15, 118, 110, 0.14);
  color: #0f766e;
}

.plugin-context-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 22px 0;
}

.plugin-context-bar__title,
.plugin-context-bar__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.plugin-context-bar__title strong {
  font-size: 24px;
  line-height: 1.1;
}

.plugin-context-bar__meta {
  align-items: flex-end;
  color: #64748b;
  font-size: 13px;
  text-align: right;
}

.page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.metric-card {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.06);
}

.metric-card strong {
  display: block;
  font-size: 28px;
  margin: 10px 0 6px;
}

.metric-label {
  color: #475569;
}

.metric-note {
  color: #64748b;
  font-size: 13px;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

@media (max-width: 1180px) {
  .shell--standalone {
    grid-template-columns: 1fr;
  }

  .shell-sidebar {
    position: static;
    min-height: auto;
  }
}

@media (max-width: 860px) {
  .shell-header--plugin,
  .plugin-context-bar,
  .shell-header--standalone {
    flex-direction: column;
    align-items: flex-start;
  }

  .plugin-primary-nav {
    justify-content: flex-start;
  }

  .plugin-context-bar__meta {
    align-items: flex-start;
    text-align: left;
  }
}
</style>
