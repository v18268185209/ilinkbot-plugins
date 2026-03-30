import { createRouter, createWebHistory } from 'vue-router'
import appEnv from '../../config/env'
import OverviewPage from '../../pages/overview/index.vue'
import AccountsPage from '../../pages/accounts/index.vue'
import EventsPage from '../../pages/events/index.vue'
import MessagesPage from '../../pages/messages/index.vue'
import SettingsPage from '../../pages/settings/index.vue'

const routes = [
  { path: '/', redirect: '/overview' },
  { path: '/dashboard/plugins/wechathlink', redirect: '/dashboard/plugins/wechathlink/overview' },
  { path: '/overview', alias: '/dashboard/plugins/wechathlink/overview', component: OverviewPage },
  { path: '/accounts', alias: '/dashboard/plugins/wechathlink/accounts', component: AccountsPage },
  { path: '/events', alias: '/dashboard/plugins/wechathlink/events', component: EventsPage },
  { path: '/messages', alias: '/dashboard/plugins/wechathlink/messages', component: MessagesPage },
  { path: '/settings', alias: '/dashboard/plugins/wechathlink/settings', component: SettingsPage },
  { path: '/:pathMatch(.*)*', redirect: '/overview' }
]

export default createRouter({
  history: createWebHistory(appEnv.routerBase),
  routes
})
