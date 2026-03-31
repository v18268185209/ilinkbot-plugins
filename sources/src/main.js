import { createApp } from 'vue'
import App from './App.vue'
import router from './exts/router/index'
import appEnv from './config/env'
import { hasAnyPermission, hasPermission, resolvePermissionVisible } from './utils/permission'
import {
  create,
  NAlert,
  NButton,
  NCard,
  NConfigProvider,
  NDataTable,
  NDatePicker,
  NDialogProvider,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NMessageProvider,
  NNotificationProvider,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NTag,
  NTabs,
  NTabPane,
  NTooltip
} from 'naive-ui'

const naive = create({
  components: [
    NButton,
    NCard,
    NConfigProvider,
    NDataTable,
    NDatePicker,
    NDialogProvider,
    NEmpty,
    NForm,
    NFormItem,
    NInput,
    NInputNumber,
    NLayout,
    NLayoutContent,
    NLayoutHeader,
    NMessageProvider,
    NModal,
    NNotificationProvider,
    NSelect,
    NPagination,
    NSpace,
    NAlert,
    NTag,
    NTabs,
    NTabPane,
    NTooltip
  ]
})

document.title = appEnv.title

const app = createApp(App)

const applyPermission = (el, binding) => {
  if (!Object.prototype.hasOwnProperty.call(el.dataset, '_permissionDisplay')) {
    el.dataset._permissionDisplay = el.style.display || ''
  }
  el.style.display = resolvePermissionVisible(binding?.value)
    ? (el.dataset._permissionDisplay || '')
    : 'none'
}

app.config.globalProperties.$hasPermission = hasPermission
app.config.globalProperties.$hasAnyPermission = hasAnyPermission
app.directive('permission', {
  mounted: applyPermission,
  updated: applyPermission
})

app.use(router).use(naive).mount('#wechatHlinkApp')
