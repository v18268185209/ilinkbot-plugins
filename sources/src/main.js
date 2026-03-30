import { createApp } from 'vue'
import App from './App.vue'
import router from './exts/router/index'
import appEnv from './config/env'
import {
  create,
  NAlert,
  NButton,
  NCard,
  NConfigProvider,
  NDataTable,
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

createApp(App).use(router).use(naive).mount('#wechatHlinkApp')
