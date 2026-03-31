<template>
  <div class="page">
    <page-header
      kicker="设置"
      title="运行设置中心"
      description="统一维护独立模式与宿主模式下的插件运行配置。"
    >
      <template #actions>
        <n-button v-permission="'btn:wechathlink_settings:save'" type="primary" @click="settingsModalVisible = true">编辑配置</n-button>
      </template>
    </page-header>

    <n-card title="基础配置">
      <n-data-table :columns="columns" :data="rows" :pagination="false" />
    </n-card>

    <modal-frame v-model:show="settingsModalVisible" title="编辑运行配置" :height="560">
      <n-form label-placement="top">
        <n-form-item label="运行模式"><n-input v-model:value="form.runMode" disabled /></n-form-item>
        <n-form-item>
          <template #label>
            <field-hint label="监听地址" tip="独立模式下插件自身监听地址；宿主模式通常仅作展示。"/>
          </template>
          <n-input v-model:value="form.listenAddr" />
        </n-form-item>
        <n-form-item>
          <template #label>
            <field-hint label="默认接入地址" tip="创建登录会话和协议请求时默认使用的 iLink 服务地址。"/>
          </template>
          <n-input v-model:value="form.defaultBaseUrl" />
        </n-form-item>
        <n-form-item label="CDN 地址"><n-input v-model:value="form.cdnBaseUrl" /></n-form-item>
        <n-form-item label="轮询超时(ms)"><n-input-number v-model:value="form.pollTimeoutMs" :min="1000" /></n-form-item>
        <n-form-item label="媒体目录"><n-input v-model:value="form.mediaDir" /></n-form-item>
        <n-form-item label="回调地址"><n-input v-model:value="form.webhookUrl" /></n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="settingsModalVisible = false">取消</n-button>
        <n-button v-permission="'btn:wechathlink_settings:save'" type="primary" @click="saveSettings">保存配置</n-button>
      </template>
    </modal-frame>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api from '../../api.js'
import PageHeader from '../../components/PageHeader.vue'
import FieldHint from '../../components/FieldHint.vue'
import ModalFrame from '../../components/ModalFrame.vue'

const form = reactive({
  runMode: '',
  listenAddr: '',
  defaultBaseUrl: '',
  cdnBaseUrl: '',
  pollTimeoutMs: 35000,
  mediaDir: '',
  webhookUrl: ''
})
const settingsModalVisible = ref(false)

const rows = computed(() => [
  { key: '运行模式', value: 运行模式文本(form.runMode) },
  { key: '监听地址', value: form.listenAddr || '-' },
  { key: '默认接入地址', value: form.defaultBaseUrl || '-' },
  { key: 'CDN 地址', value: form.cdnBaseUrl || '-' },
  { key: '轮询超时(ms)', value: form.pollTimeoutMs || '-' },
  { key: '媒体目录', value: form.mediaDir || '-' },
  { key: '回调地址', value: form.webhookUrl || '-' }
])

const columns = [
  { title: '配置项', key: 'key', width: 220 },
  { title: '当前值', key: 'value' }
]

function 运行模式文本(value) {
  const map = {
    'standalone-sqlite': '独立模式 / SQLite',
    'host-mysql': '宿主模式 / MySQL8'
  }
  return map[value] || value || '-'
}

async function loadSettings() {
  Object.assign(form, await api.getSettings())
}

async function saveSettings() {
  Object.assign(form, await api.saveSettings(form))
  settingsModalVisible.value = false
}

onMounted(loadSettings)
</script>
