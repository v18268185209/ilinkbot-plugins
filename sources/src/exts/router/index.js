import { createRouter, createWebHistory } from 'vue-router'
import appEnv from '../../config/env'
import { routeMetaConfig } from '../../config/navigation'
import { hasPermission } from '../../utils/permission'
import OverviewPage from '../../pages/overview/index.vue'
import AccountsPage from '../../pages/accounts/index.vue'
import EventsPage from '../../pages/events/index.vue'
import MessagesPage from '../../pages/messages/index.vue'
import AuditsPage from '../../pages/audits/index.vue'
import PlatformPage from '../../pages/platform/index.vue'
import SettingsPage from '../../pages/settings/index.vue'

const routes = [
  { path: '/', redirect: '/overview' },
  { path: '/dashboard/plugins/wechathlink', redirect: '/dashboard/plugins/wechathlink/overview' },
  { path: '/overview', alias: '/dashboard/plugins/wechathlink/overview', component: OverviewPage, meta: routeMetaConfig.overview },
  { path: '/accounts', alias: '/dashboard/plugins/wechathlink/accounts', component: AccountsPage, meta: routeMetaConfig.accounts },
  { path: '/events', alias: '/dashboard/plugins/wechathlink/events', component: EventsPage, meta: routeMetaConfig.events },
  { path: '/messages', alias: '/dashboard/plugins/wechathlink/messages', component: MessagesPage, meta: routeMetaConfig.messages },
  { path: '/audits', alias: '/dashboard/plugins/wechathlink/audits', component: AuditsPage, meta: routeMetaConfig.audits },
  { path: '/platform', alias: '/dashboard/plugins/wechathlink/platform', component: PlatformPage, meta: routeMetaConfig.platform },
  { path: '/settings', alias: '/dashboard/plugins/wechathlink/settings', component: SettingsPage, meta: routeMetaConfig.settings },
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
