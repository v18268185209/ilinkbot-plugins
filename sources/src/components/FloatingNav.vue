<template>
  <div class="floating-nav">
    <transition name="floating-nav-panel">
      <div v-if="expanded" class="floating-nav__panel">
        <button
          v-for="item in visibleNavItems"
          :key="item.path"
          type="button"
          class="floating-nav__item"
          :class="{ 'is-active': route.path === item.path }"
          @click="go(item.path)"
        >
          <span>{{ item.label }}</span>
          <small>{{ item.kicker }}</small>
        </button>
      </div>
    </transition>
    <button type="button" class="floating-nav__trigger" @click="expanded = !expanded">
      {{ expanded ? '收起' : '菜单' }}
    </button>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { hasPermission } from '../utils/permission'

const route = useRoute()
const router = useRouter()
const expanded = ref(false)

const navItems = [
  { label: '工作台', kicker: '总览', path: '/overview', permission: '/dashboard/plugins/wechathlink/overview' },
  { label: '微信账号', kicker: '账号', path: '/accounts', permission: '/dashboard/plugins/wechathlink/accounts' },
  { label: '事件中心', kicker: '事件', path: '/events', permission: '/dashboard/plugins/wechathlink/events' },
  { label: '消息发送', kicker: '发送', path: '/messages', permission: '/dashboard/plugins/wechathlink/messages' },
  { label: '审计中心', kicker: '审计', path: '/audits', permission: '/dashboard/plugins/wechathlink/audits' },
  { label: '设置中心', kicker: '设置', path: '/settings', permission: '/dashboard/plugins/wechathlink/settings' }
]

const visibleNavItems = computed(() => navItems.filter((item) => hasPermission(item.permission)))

function go(path) {
  expanded.value = false
  router.push(path)
}
</script>

<style scoped>
.floating-nav {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.floating-nav__panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  width: 220px;
  border-radius: 18px;
  background: rgba(15, 23, 42, 0.92);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.2);
}

.floating-nav__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 12px 14px;
  border: none;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.06);
  color: #f8fafc;
  cursor: pointer;
  text-align: left;
}

.floating-nav__item small {
  color: rgba(248, 250, 252, 0.64);
}

.floating-nav__item.is-active {
  background: linear-gradient(135deg, #0f766e 0%, #0284c7 100%);
}

.floating-nav__trigger {
  min-width: 84px;
  height: 46px;
  padding: 0 18px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #0f766e 0%, #0284c7 100%);
  color: #f8fafc;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 18px 34px rgba(8, 47, 73, 0.28);
}

.floating-nav-panel-enter-active,
.floating-nav-panel-leave-active {
  transition: all 0.18s ease;
}

.floating-nav-panel-enter-from,
.floating-nav-panel-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.96);
}
</style>
