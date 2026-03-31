import { createRouter, createWebHistory } from 'vue-router'
import appEnv from '../../config/env'
import { hasPermission } from '../../utils/permission'
import OverviewPage from '../../pages/overview/index.vue'
import AccountsPage from '../../pages/accounts/index.vue'
import EventsPage from '../../pages/events/index.vue'
import MessagesPage from '../../pages/messages/index.vue'
import AuditsPage from '../../pages/audits/index.vue'
import SettingsPage from '../../pages/settings/index.vue'

const routes = [
  { path: '/', redirect: '/overview' },
  { path: '/dashboard/plugins/wechathlink', redirect: '/dashboard/plugins/wechathlink/overview' },
  { path: '/overview', alias: '/dashboard/plugins/wechathlink/overview', component: OverviewPage, meta: { permission: '/dashboard/plugins/wechathlink/overview' } },
  { path: '/accounts', alias: '/dashboard/plugins/wechathlink/accounts', component: AccountsPage, meta: { permission: '/dashboard/plugins/wechathlink/accounts' } },
  { path: '/events', alias: '/dashboard/plugins/wechathlink/events', component: EventsPage, meta: { permission: '/dashboard/plugins/wechathlink/events' } },
  { path: '/messages', alias: '/dashboard/plugins/wechathlink/messages', component: MessagesPage, meta: { permission: '/dashboard/plugins/wechathlink/messages' } },
  { path: '/audits', alias: '/dashboard/plugins/wechathlink/audits', component: AuditsPage, meta: { permission: '/dashboard/plugins/wechathlink/audits' } },
  { path: '/settings', alias: '/dashboard/plugins/wechathlink/settings', component: SettingsPage, meta: { permission: '/dashboard/plugins/wechathlink/settings' } },
  { path: '/:pathMatch(.*)*', redirect: '/overview' }
]

const router = createRouter({
  history: createWebHistory(appEnv.routerBase),
  routes
})

const fallbackRoute = () => {
  const candidate = routes.find((item) => item.meta?.permission && hasPermission(item.meta.permission))
  return candidate?.path || '/overview'
}

router.beforeEach((to) => {
  if (!to.meta?.permission) {
    return true
  }
  if (hasPermission(to.meta.permission)) {
    return true
  }
  if (to.path === fallbackRoute()) {
    return true
  }
  return fallbackRoute()
})

export default router
