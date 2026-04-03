<template>
  <div class="page page--overview">
    <page-header
      kicker="首页"
      title="微信接入场景首页"
      description="把接入、会话处理、投递追踪和治理配置收敛到同一条业务链路上。"
    >
      <template #actions>
        <n-button v-permission="'/api/wechathlink/admin/dashboard/summary'" type="primary" @click="loadSummary">刷新数据</n-button>
      </template>
    </page-header>

    <section class="overview-hero">
      <div class="overview-hero__intro">
        <span class="overview-hero__kicker">今日优先动作</span>
        <strong>{{ primaryAction.title }}</strong>
        <p>{{ primaryAction.description }}</p>
      </div>

      <div class="overview-hero__signals">
        <span
          v-for="signal in overviewSignals"
          :key="signal.key"
          :class="['overview-signal', `overview-signal--${signal.tone}`]"
        >
          {{ signal.text }}
        </span>
      </div>
    </section>

    <section class="scene-grid scene-grid--overview">
      <button
        v-for="item in sceneCards"
        :key="item.key"
        type="button"
        class="scene-card"
        @click="go(item.path)"
      >
        <span class="scene-card__kicker">{{ item.kicker }}</span>
        <strong>{{ item.label }}</strong>
        <p>{{ item.description }}</p>
        <div class="scene-card__footer">
          <span>{{ item.status }}</span>
          <small>{{ item.action }}</small>
        </div>
      </button>
    </section>

    <section class="metric-grid metric-grid--overview">
      <article v-for="item in metrics" :key="item.key" class="metric-card">
        <span class="metric-label">{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <span class="metric-note">{{ item.note }}</span>
      </article>
    </section>

    <section class="panel-grid panel-grid--overview">
      <n-card title="最近账号" class="overview-panel-card">
        <n-data-table :columns="accountColumns" :data="recentAccounts" :pagination="false" :scroll-x="620" />
      </n-card>
      <n-card title="最近日志" class="overview-panel-card">
        <n-data-table :columns="logColumns" :data="recentLogs" :pagination="false" :scroll-x="620" />
      </n-card>
      <n-card title="临期 Runtime" class="overview-panel-card">
        <n-data-table :columns="runtimeColumns" :data="expiringRuntimes" :pagination="false" :scroll-x="920" />
      </n-card>
      <n-card title="窗口压力" class="overview-panel-card">
        <n-data-table :columns="windowColumns" :data="closingWindows" :pagination="false" :scroll-x="980" />
      </n-card>
    </section>
  </div>
</template>

<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NTag } from 'naive-ui'
import api from '../../api.js'
import PageHeader from '../../components/PageHeader.vue'
import { ensureArray } from '../../utils/http'
import { hasPermission } from '../../utils/permission'

const router = useRouter()
const summary = ref({})

const metrics = computed(() => [
  { key: 'account', label: '接入账号', value: summary.value.accountCount || 0, note: '当前可访问的微信账号资源' },
  { key: 'enabled', label: '启用账号', value: summary.value.enabledAccountCount || 0, note: '已经可参与会话与投递的账号' },
  { key: 'polling', label: '在线轮询', value: summary.value.pollingAccountCount || 0, note: '当前仍在持续拉取消息的账号' },
  { key: 'inbound', label: '今日入站', value: summary.value.inboundTodayCount || 0, note: '今日接收到的会话事件数量' },
  { key: 'outbound', label: '今日出站', value: summary.value.outboundTodayCount || 0, note: '今日已完成的回复与投递次数' },
  { key: 'error', label: '异常日志', value: summary.value.errorCount || 0, note: '最近错误日志数量' },
  { key: 'runtime', label: '临期 Runtime', value: summary.value.expiringRuntimeCount || 0, note: '2 小时内可能失效的机器人实例' },
  { key: 'window', label: '临期窗口', value: summary.value.closingWindowCount || 0, note: '2 小时内将关闭的联系人回复窗口' }
])

const recentAccounts = computed(() => ensureArray(summary.value.recentAccounts))
const recentLogs = computed(() => ensureArray(summary.value.recentLogs))
const expiringRuntimes = computed(() => ensureArray(summary.value.expiringRuntimes))
const closingWindows = computed(() => ensureArray(summary.value.closingWindows))

const primaryAction = computed(() => {
  if (Number(summary.value.enabledAccountCount || 0) <= 0) {
    return {
      title: '先完成账号接入与绑定',
      description: '当前还没有可用账号，先去接入页完成扫码绑定、Runtime 确认和成员授权。'
    }
  }
  if (Number(summary.value.closingWindowCount || 0) > 0) {
    return {
      title: '优先处理即将关闭的回复窗口',
      description: '存在联系人回复窗口即将到期，应先进入会话工作台处理高优先级会话，避免错失回复机会。'
    }
  }
  if (Number(summary.value.errorCount || 0) > 0 || Number(summary.value.expiringRuntimeCount || 0) > 0) {
    return {
      title: '排查 Runtime 与链路异常',
      description: '当前存在错误日志或临期 Runtime，建议先进入追踪与治理相关页面确认链路稳定性。'
    }
  }
  return {
    title: '进入会话工作台处理今日消息',
    description: '接入状态平稳，优先在会话页处理联系人消息，再进入追踪页核对投递和媒体链路。'
  }
})

const overviewSignals = computed(() => {
  const items = []
  if (Number(summary.value.enabledAccountCount || 0) <= 0) {
    items.push({ key: 'access', tone: 'warning', text: '尚未形成可用接入账号' })
  }
  if (Number(summary.value.closingWindowCount || 0) > 0) {
    items.push({ key: 'window', tone: 'danger', text: `${summary.value.closingWindowCount} 个窗口即将关闭` })
  }
  if (Number(summary.value.expiringRuntimeCount || 0) > 0) {
    items.push({ key: 'runtime', tone: 'warning', text: `${summary.value.expiringRuntimeCount} 个 Runtime 临期` })
  }
  if (Number(summary.value.errorCount || 0) > 0) {
    items.push({ key: 'error', tone: 'danger', text: `${summary.value.errorCount} 条错误日志待排查` })
  }
  if (items.length === 0) {
    items.push({ key: 'stable', tone: 'success', text: '当前接入、会话与追踪链路整体平稳' })
  }
  return items
})

const sceneCards = computed(() => ([
  {
    key: 'accounts',
    label: '接入',
    kicker: 'Access',
    path: '/accounts',
    permission: '/dashboard/plugins/wechathlink/accounts',
    description: '管理账号、扫码绑定、Runtime 和成员授权。',
    status: `${summary.value.enabledAccountCount || 0} 个账号可用`,
    action: '处理账号接入与授权'
  },
  {
    key: 'messages',
    label: '会话',
    kicker: 'Conversation',
    path: '/messages',
    permission: '/dashboard/plugins/wechathlink/messages',
    description: '围绕联系人处理回复窗口、文本消息和媒体消息。',
    status: `${summary.value.inboundTodayCount || 0} 条今日入站`,
    action: '进入会话工作台'
  },
  {
    key: 'events',
    label: '投递追踪',
    kicker: 'Tracing',
    path: '/events',
    permission: '/dashboard/plugins/wechathlink/events',
    description: '追踪账号、联系人、媒体和消息链路，定位异常与投递细节。',
    status: `${summary.value.outboundTodayCount || 0} 条今日出站`,
    action: '查看链路与事件流'
  },
  {
    key: 'audits',
    label: '审计治理',
    kicker: 'Governance',
    path: '/audits',
    permission: '/dashboard/plugins/wechathlink/audits',
    description: '查看操作留痕、失败记录和治理行为。',
    status: `${summary.value.errorCount || 0} 条异常日志`,
    action: '进入审计与治理'
  },
  {
    key: 'settings',
    label: '系统配置',
    kicker: 'Config',
    path: '/settings',
    permission: '/dashboard/plugins/wechathlink/settings',
    description: '维护运行模式、轮询超时、媒体目录和回调地址。',
    status: `${summary.value.pollingAccountCount || 0} 个账号在线轮询`,
    action: '检查运行配置'
  }
]).filter((item) => hasPermission(item.permission)))

function 登录状态文本(value) {
  const map = {
    CREATED: '已创建',
    CONFIRMED: '已确认',
    WAIT_SCAN: '待扫码'
  }
  return map[value] || value || '-'
}

function 轮询状态文本(value) {
  const map = {
    RUNNING: '运行中',
    STOPPED: '已停止',
    ERROR: '异常'
  }
  return map[value] || value || '-'
}

const accountColumns = [
  { title: '账号编码', key: 'accountCode' },
  { title: '账号名称', key: 'accountName' },
  { title: '登录状态', key: 'loginStatus', render: (row) => h(NTag, { type: 'info' }, { default: () => 登录状态文本(row.loginStatus) }) },
  { title: '轮询状态', key: 'pollStatus', render: (row) => h(NTag, { type: row.pollStatus === 'RUNNING' ? 'success' : 'warning' }, { default: () => 轮询状态文本(row.pollStatus) }) }
]

const logColumns = [
  { title: '级别', key: 'level' },
  { title: '消息', key: 'message' },
  { title: '来源', key: 'source' }
]

const runtimeColumns = [
  { title: '账号ID', key: 'wechatAccountId', width: 100 },
  { title: '状态', key: 'runtimeStatus', width: 100, render: (row) => h(NTag, { bordered: false, type: row.runtimeStatus === 'ONLINE' ? 'success' : 'warning' }, { default: () => row.runtimeStatus || '-' }) },
  { title: '到期时间', key: 'expiresAt', width: 180, render: (row) => formatDateTime(row.expiresAt) },
  { title: '心跳时间', key: 'lastHeartbeatAt', width: 180, render: (row) => formatDateTime(row.lastHeartbeatAt) },
  { title: '最近错误', key: 'lastError' }
]

const windowColumns = [
  { title: '账号ID', key: 'wechatAccountId', width: 100 },
  { title: '联系人', key: 'peerUserId', minWidth: 220 },
  { title: '窗口状态', key: 'windowStatus', width: 120, render: (row) => h(NTag, { bordered: false, type: row.windowStatus === 'CLOSING_SOON' ? 'warning' : 'default' }, { default: () => row.windowStatus || '-' }) },
  { title: '最后入站', key: 'lastInboundAt', width: 180, render: (row) => formatDateTime(row.lastInboundAt) },
  { title: '关闭时间', key: 'replyWindowExpiresAt', width: 180, render: (row) => formatDateTime(row.replyWindowExpiresAt) }
]

function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  const hour = `${date.getHours()}`.padStart(2, '0')
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  const second = `${date.getSeconds()}`.padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

function go(path) {
  if (!path) {
    return
  }
  router.push(path)
}

async function loadSummary() {
  summary.value = await api.dashboardSummary()
}

onMounted(loadSummary)
</script>

<style scoped>
.page--overview {
  gap: 20px;
}

.page--overview > * {
  min-width: 0;
}

.overview-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(260px, 0.9fr);
  gap: 18px;
  padding: 22px 24px;
  border-radius: 24px;
  background:
    radial-gradient(circle at top left, rgba(15, 118, 110, 0.16), transparent 34%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(241, 245, 249, 0.94));
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 20px 44px rgba(15, 23, 42, 0.08);
}

.overview-hero__intro {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.overview-hero__kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #0f766e;
}

.overview-hero__intro strong {
  font-size: 30px;
  line-height: 1.15;
}

.overview-hero__intro p {
  margin: 0;
  max-width: 720px;
  color: #475569;
  font-size: 15px;
  line-height: 1.7;
}

.overview-hero__signals {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 10px;
  min-width: 0;
}

.overview-signal {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  min-height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.overview-signal--success {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.overview-signal--warning {
  background: rgba(245, 158, 11, 0.16);
  color: #b45309;
}

.overview-signal--danger {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}

.scene-grid--overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.scene-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  min-height: 188px;
  padding: 18px;
  appearance: none;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.05);
  cursor: pointer;
  color: inherit;
  font: inherit;
  text-align: left;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.scene-card:hover {
  transform: translateY(-2px);
  border-color: rgba(15, 118, 110, 0.28);
  box-shadow: 0 22px 38px rgba(15, 23, 42, 0.08);
}

.scene-card:focus-visible {
  outline: 3px solid rgba(14, 116, 144, 0.22);
  outline-offset: 3px;
}

.scene-card__kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #0369a1;
}

.scene-card strong {
  font-size: 22px;
}

.scene-card p {
  margin: 0;
  color: #475569;
  line-height: 1.65;
}

.scene-card__footer {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.scene-card__footer span {
  color: #0f172a;
  font-weight: 700;
}

.scene-card__footer small {
  color: #64748b;
}

.metric-grid--overview {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.panel-grid--overview {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
}

.panel-grid--overview > * {
  min-width: 0;
}

.overview-panel-card {
  min-width: 0;
}

.overview-panel-card :deep(.n-card-content) {
  min-width: 0;
}

.overview-panel-card :deep(.n-data-table),
.overview-panel-card :deep(.n-data-table-wrapper),
.overview-panel-card :deep(.n-data-table-base-table),
.overview-panel-card :deep(.n-scrollbar-container) {
  max-width: 100%;
}

@media (max-width: 960px) {
  .overview-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1280px) {
  .metric-grid--overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .panel-grid--overview {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .scene-grid--overview,
  .metric-grid--overview {
    grid-template-columns: 1fr;
  }

  .scene-card {
    min-height: auto;
  }
}
</style>
