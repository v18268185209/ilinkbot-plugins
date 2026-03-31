<template>
  <n-config-provider :locale="zhCN" :date-locale="dateZhCN">
    <n-dialog-provider>
      <n-message-provider>
        <n-notification-provider>
          <n-layout class="shell">
            <n-layout-header bordered class="shell-header">
              <div class="brand">
                <span class="brand-kicker">微信接入插件</span>
                <strong>微信接入工作台</strong>
              </div>
              <n-space align="center">
                <span class="route-badge">{{ currentNav?.label || '工作台' }}</span>
              </n-space>
            </n-layout-header>
            <n-layout-content class="shell-content">
              <router-view />
            </n-layout-content>
            <floating-nav />
          </n-layout>
        </n-notification-provider>
      </n-message-provider>
    </n-dialog-provider>
  </n-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import { dateZhCN, zhCN } from 'naive-ui'
import { useRoute } from 'vue-router'
import FloatingNav from './components/FloatingNav.vue'
import { hasPermission } from './utils/permission'

const route = useRoute()

const navItems = [
  { label: '工作台', path: '/overview', permission: '/dashboard/plugins/wechathlink/overview' },
  { label: '微信账号', path: '/accounts', permission: '/dashboard/plugins/wechathlink/accounts' },
  { label: '事件中心', path: '/events', permission: '/dashboard/plugins/wechathlink/events' },
  { label: '消息发送', path: '/messages', permission: '/dashboard/plugins/wechathlink/messages' },
  { label: '审计中心', path: '/audits', permission: '/dashboard/plugins/wechathlink/audits' },
  { label: '设置', path: '/settings', permission: '/dashboard/plugins/wechathlink/settings' }
]

const visibleNavItems = computed(() => navItems.filter((item) => hasPermission(item.permission)))
const currentNav = computed(() => visibleNavItems.value.find((item) => route.path.startsWith(item.path)) || navItems.find((item) => route.path.startsWith(item.path)))
</script>

<style>
:root {
  color-scheme: light;
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  background:
    radial-gradient(circle at top left, rgba(15, 118, 110, 0.12), transparent 28%),
    radial-gradient(circle at top right, rgba(2, 132, 199, 0.12), transparent 25%),
    linear-gradient(180deg, #f7fbff 0%, #f4f7fb 100%);
}

html, body, #wechatHlinkApp {
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

.shell-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.84);
  backdrop-filter: blur(16px);
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-kicker {
  font-size: 12px;
  letter-spacing: 0.12em;
  color: #0f766e;
  text-transform: uppercase;
}

.shell-content {
  padding: 20px;
}

.route-badge {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
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
</style>
