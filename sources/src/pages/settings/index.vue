<template>
  <div class="page page--settings">
    <page-header
      kicker="配置"
      title="系统配置工作台"
      description="集中管理独立模式与插件模式下的运行参数、接入链路、媒体目录和回调入口。"
    >
      <template #actions>
        <n-button v-permission="'btn:wechathlink_settings:save'" type="primary" @click="settingsModalVisible = true">编辑配置</n-button>
      </template>
    </page-header>

    <section class="settings-grid">
      <article v-for="item in settingGroups" :key="item.key" class="settings-card">
        <span class="settings-card__kicker">{{ item.kicker }}</span>
        <strong>{{ item.label }}</strong>
        <p>{{ item.description }}</p>
        <div class="settings-card__rows">
          <div v-for="row in item.rows" :key="row.key" class="settings-card__row">
            <span>{{ row.key }}</span>
            <strong>{{ row.value }}</strong>
          </div>
        </div>
      </article>
    </section>

    <n-card title="当前配置明细">
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
import FieldHint from '../../components/FieldHint.vue'
import ModalFrame from '../../components/ModalFrame.vue'
import PageHeader from '../../components/PageHeader.vue'

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

const settingGroups = computed(() => [
  {
    key: 'runtime',
    kicker: 'Runtime',
    label: '运行环境',
    description: '决定插件当前是独立运行还是挂载到宿主环境，并展示监听入口。',
    rows: [
      { key: '运行模式', value: 运行模式文本(form.runMode) },
      { key: '监听地址', value: form.listenAddr || '-' }
    ]
  },
  {
    key: 'access',
    kicker: 'Access',
    label: '协议接入',
    description: '维护默认 iLink 接入地址和 CDN 链路，直接影响登录、发送和媒体收发。',
    rows: [
      { key: '默认接入地址', value: form.defaultBaseUrl || '-' },
      { key: 'CDN 地址', value: form.cdnBaseUrl || '-' }
    ]
  },
  {
    key: 'polling',
    kicker: 'Polling',
    label: '轮询与回调',
    description: '控制收消息超时与回调出口，影响消息接收时延和对外联动。',
    rows: [
      { key: '轮询超时(ms)', value: form.pollTimeoutMs || '-' },
      { key: '回调地址', value: form.webhookUrl || '-' }
    ]
  },
  {
    key: 'media',
    kicker: 'Media',
    label: '媒体落地',
    description: '确定媒体文件的本地落盘目录，支撑媒体查看、资产留痕和后续治理。',
    rows: [
      { key: '媒体目录', value: form.mediaDir || '-' }
    ]
  }
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

<style scoped>
.page--settings {
  gap: 20px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.settings-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.05);
}

.settings-card__kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #0f766e;
}

.settings-card strong {
  font-size: 22px;
}

.settings-card p {
  margin: 0;
  color: #475569;
  line-height: 1.65;
}

.settings-card__rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: auto;
}

.settings-card__row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.settings-card__row span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.settings-card__row strong {
  font-size: 15px;
  line-height: 1.55;
  word-break: break-all;
}
</style>
