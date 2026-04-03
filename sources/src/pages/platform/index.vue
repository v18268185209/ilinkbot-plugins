<template>
  <div class="page page--platform">
    <page-header
      kicker="Open Platform"
      title="开放平台"
      description="统一查看健康检查、开放接口、接入示例和对外联动注意项，支撑开源展示与业务接入。"
    >
      <template #actions>
        <n-button tertiary @click="loadPlatformData">刷新数据</n-button>
        <n-button tertiary @click="go('/settings')">查看配置</n-button>
        <n-button type="primary" @click="copyText(openBaseUrl)">复制开放前缀</n-button>
      </template>
    </page-header>

    <section class="platform-hero">
      <div class="platform-hero__intro">
        <span class="platform-hero__kicker">Current Entry</span>
        <strong>{{ openBaseUrl }}</strong>
        <p>开放接口前缀固定为 `/api/wechathlink/open`。健康检查、账号接入、事件查询和消息发送都从这里对外暴露。</p>
      </div>

      <div class="platform-hero__meta">
        <article v-for="item in heroCards" :key="item.key" class="platform-hero__card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </article>
      </div>
    </section>

    <section class="platform-capabilities">
      <article v-for="item in capabilityCards" :key="item.key" class="capability-card">
        <span class="capability-card__kicker">{{ item.kicker }}</span>
        <strong>{{ item.label }}</strong>
        <p>{{ item.description }}</p>
        <div class="capability-card__chips">
          <span v-for="tag in item.tags" :key="tag" class="capability-chip">{{ tag }}</span>
        </div>
      </article>
    </section>

    <section class="panel-grid">
      <n-card title="Webhook 与运行配置">
        <div class="runtime-list">
          <article v-for="item in runtimeItems" :key="item.key" class="runtime-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.note }}</small>
          </article>
        </div>
      </n-card>

      <n-card title="最近开放请求">
        <div class="request-toolbar">
          <n-select
            v-model:value="requestFilters.actionType"
            :options="actionTypeOptions"
            clearable
            placeholder="请求动作"
          />
          <n-select
            v-model:value="requestFilters.resultStatus"
            :options="resultStatusOptions"
            clearable
            placeholder="结果状态"
          />
          <n-button type="primary" @click="applyRequestFilters">查询</n-button>
        </div>

        <n-data-table
          :columns="requestColumns"
          :data="requestRows"
          :pagination="false"
        />

        <div class="request-pagination">
          <n-pagination
            :page="requestPagination.page"
            :page-size="requestPagination.pageSize"
            :item-count="requestPagination.itemCount"
            :page-sizes="[10, 20, 50]"
            show-size-picker
            @update:page="handleRequestPageChange"
            @update:page-size="handleRequestPageSizeChange"
          />
        </div>
      </n-card>
    </section>

    <section class="panel-grid">
      <n-card title="最近 Webhook 投递">
        <div class="request-toolbar">
          <n-select
            v-model:value="deliveryFilters.deliveryStatus"
            :options="deliveryStatusOptions"
            clearable
            placeholder="投递状态"
          />
          <n-input
            v-model:value="deliveryFilters.wechatAccountId"
            clearable
            placeholder="账号ID"
          />
          <n-button type="primary" @click="applyDeliveryFilters">查询</n-button>
        </div>

        <n-data-table
          :columns="deliveryColumns"
          :data="deliveryRows"
          :pagination="false"
        />

        <div class="request-pagination">
          <n-pagination
            :page="deliveryPagination.page"
            :page-size="deliveryPagination.pageSize"
            :item-count="deliveryPagination.itemCount"
            :page-sizes="[10, 20, 50]"
            show-size-picker
            @update:page="handleDeliveryPageChange"
            @update:page-size="handleDeliveryPageSizeChange"
          />
        </div>
      </n-card>
    </section>

    <modal-frame
      v-model:show="deliveryDetailVisible"
      title="Webhook 投递明细"
      :width="920"
      :height="720"
    >
      <div v-if="selectedDeliveryDetail" class="delivery-detail">
        <section class="delivery-detail__meta">
          <article class="runtime-item">
            <span>投递ID</span>
            <strong>{{ selectedDeliveryDetail.id }}</strong>
            <small>状态 {{ selectedDeliveryDetail.deliveryStatus || '-' }}</small>
          </article>
          <article class="runtime-item">
            <span>目标地址</span>
            <strong>{{ selectedDeliveryDetail.targetUrl || '-' }}</strong>
            <small>HTTP {{ selectedDeliveryDetail.responseCode || '-' }}</small>
          </article>
          <article class="runtime-item">
            <span>TraceId</span>
            <strong>{{ selectedDeliveryDetail.traceId || '-' }}</strong>
            <small>尝试次数 {{ selectedDeliveryDetail.attemptCount || 0 }}</small>
          </article>
        </section>

        <section class="example-list">
          <article class="example-card">
            <div class="example-card__head">
              <strong>请求体</strong>
              <n-button size="small" tertiary @click="copyText(selectedDeliveryDetail.requestBody || '')">复制请求体</n-button>
            </div>
            <pre>{{ prettyJson(selectedDeliveryDetail.requestBody) }}</pre>
          </article>

          <article class="example-card">
            <div class="example-card__head">
              <strong>响应体</strong>
              <n-button size="small" tertiary @click="copyText(selectedDeliveryDetail.responseBody || '')">复制响应体</n-button>
            </div>
            <pre>{{ prettyJson(selectedDeliveryDetail.responseBody) }}</pre>
          </article>
        </section>
      </div>
      <n-empty v-else description="请选择一条 webhook 投递查看明细" />
      <template #footer>
        <n-button @click="deliveryDetailVisible = false">关闭</n-button>
        <n-button
          v-if="selectedDeliveryDetail && `${selectedDeliveryDetail.deliveryStatus || ''}`.toUpperCase() === 'FAILED'"
          type="primary"
          :loading="retryingDelivery"
          @click="retryCurrentDelivery"
        >
          重试投递
        </n-button>
      </template>
    </modal-frame>

    <section class="panel-grid">
      <n-card title="健康与基础接口">
        <div class="endpoint-list">
          <article v-for="item in baseEndpoints" :key="item.path" class="endpoint-row">
            <div class="endpoint-row__head">
              <span :class="['endpoint-method', `endpoint-method--${item.method.toLowerCase()}`]">{{ item.method }}</span>
              <strong>{{ item.path }}</strong>
            </div>
            <p>{{ item.description }}</p>
            <div class="endpoint-row__actions">
              <n-button size="small" tertiary @click="copyText(item.fullUrl)">复制地址</n-button>
              <n-button size="small" tertiary @click="copyText(item.curl)">复制 cURL</n-button>
            </div>
          </article>
        </div>
      </n-card>

      <n-card title="业务开放接口">
        <div class="endpoint-list">
          <article v-for="item in businessEndpoints" :key="item.path" class="endpoint-row">
            <div class="endpoint-row__head">
              <span :class="['endpoint-method', `endpoint-method--${item.method.toLowerCase()}`]">{{ item.method }}</span>
              <strong>{{ item.path }}</strong>
            </div>
            <p>{{ item.description }}</p>
            <div class="endpoint-row__chips">
              <span v-for="tag in item.tags" :key="tag" class="capability-chip">{{ tag }}</span>
            </div>
            <div class="endpoint-row__actions">
              <n-button size="small" tertiary @click="copyText(item.fullUrl)">复制地址</n-button>
              <n-button size="small" tertiary @click="copyText(item.curl)">复制 cURL</n-button>
            </div>
          </article>
        </div>
      </n-card>
    </section>

    <section class="panel-grid">
      <n-card title="请求示例">
        <div class="example-list">
          <article v-for="item in requestExamples" :key="item.key" class="example-card">
            <div class="example-card__head">
              <strong>{{ item.label }}</strong>
              <n-button size="small" tertiary @click="copyText(item.content)">复制示例</n-button>
            </div>
            <pre>{{ item.content }}</pre>
          </article>
        </div>
      </n-card>

      <n-card title="接入与运维说明">
        <div class="notes-list">
          <article v-for="item in operationNotes" :key="item.key" class="note-card">
            <strong>{{ item.label }}</strong>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </n-card>
    </section>
  </div>
</template>

<script setup>
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, useMessage } from 'naive-ui'
import appEnv from '../../config/env'
import api from '../../api.js'
import ModalFrame from '../../components/ModalFrame.vue'
import PageHeader from '../../components/PageHeader.vue'
import { ensureArray } from '../../utils/http'

const router = useRouter()
const message = useMessage()
const platformSummary = ref({})
const requestRows = ref([])
const deliveryRows = ref([])
const selectedDeliveryDetail = ref(null)
const deliveryDetailVisible = ref(false)
const retryingDelivery = ref(false)
const requestPagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})
const deliveryPagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})
const requestFilters = reactive({
  actionType: null,
  resultStatus: null
})
const deliveryFilters = reactive({
  wechatAccountId: '',
  deliveryStatus: null
})

const origin = computed(() => {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin
  }
  return `${appEnv.apiBaseUrl}`.replace(/\/api\/?$/, '')
})

const openBaseUrl = computed(() => `${origin.value}/api/wechathlink/open`)

const heroCards = computed(() => [
  {
    key: 'health',
    label: '健康检查',
    value: '2 个端点',
    note: '用于存活与就绪探针'
  },
  {
    key: 'business',
    label: '业务接口',
    value: '8 个端点',
    note: '覆盖登录、账号、事件、会话和发送'
  },
  {
    key: 'request',
    label: '今日请求',
    value: platformSummary.value.requestCountToday || 0,
    note: `${platformSummary.value.successCountToday || 0} 成功 / ${platformSummary.value.failureCountToday || 0} 失败`
  },
  {
    key: 'webhook',
    label: '今日 Webhook',
    value: (platformSummary.value.webhookSuccessToday || 0) + (platformSummary.value.webhookFailureToday || 0),
    note: `${platformSummary.value.webhookSuccessToday || 0} 成功 / ${platformSummary.value.webhookFailureToday || 0} 失败`
  }
])

const runtimeItems = computed(() => [
  {
    key: 'runMode',
    label: '运行模式',
    value: platformSummary.value.runMode || '-',
    note: '决定服务当前的独立或宿主运行形态'
  },
  {
    key: 'listenAddr',
    label: '监听地址',
    value: platformSummary.value.listenAddr || '-',
    note: '独立模式下实际服务监听入口'
  },
  {
    key: 'defaultBaseUrl',
    label: '默认接入地址',
    value: platformSummary.value.defaultBaseUrl || '-',
    note: '协议层默认 iLink 服务地址'
  },
  {
    key: 'webhookUrl',
    label: 'Webhook 地址',
    value: platformSummary.value.webhookUrl || '未配置',
    note: platformSummary.value.webhookUrl ? '已配置对外联动地址' : '当前尚未配置回调地址'
  }
])

const actionTypeOptions = [
  { label: '登录启动', value: 'OPEN_LOGIN_START' },
  { label: '登录状态', value: 'OPEN_LOGIN_STATUS' },
  { label: '账号列表', value: 'OPEN_ACCOUNTS_LIST' },
  { label: '事件查询', value: 'OPEN_EVENTS_LIST' },
  { label: '设置读取', value: 'OPEN_SETTINGS_GET' },
  { label: '联系人查询', value: 'OPEN_MESSAGE_PEERS' },
  { label: '媒体上传', value: 'OPEN_MESSAGE_UPLOAD' },
  { label: '发送文本', value: 'OPEN_SEND_TEXT' },
  { label: '发送媒体', value: 'OPEN_SEND_MEDIA' }
]

const resultStatusOptions = [
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILURE' }
]

const deliveryStatusOptions = [
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' }
]

const requestColumns = [
  { title: '时间', key: 'createTime', width: 180, render: (row) => formatDateTime(row.createTime) },
  { title: '动作', key: 'actionType', width: 180 },
  { title: '结果', key: 'resultStatus', width: 100 },
  { title: '账号ID', key: 'wechatAccountId', width: 100, render: (row) => row.wechatAccountId || '-' },
  { title: '摘要', key: 'summary', minWidth: 240 },
  { title: '明细', key: 'detailJson', minWidth: 320, render: (row) => compactJson(row.detailJson) }
]

const deliveryColumns = [
  { title: '时间', key: 'createTime', width: 180, render: (row) => formatDateTime(row.createTime) },
  { title: '账号ID', key: 'wechatAccountId', width: 100, render: (row) => row.wechatAccountId || '-' },
  { title: '事件ID', key: 'eventId', width: 100, render: (row) => row.eventId || '-' },
  { title: '类型', key: 'deliveryType', width: 140 },
  { title: '状态', key: 'deliveryStatus', width: 100 },
  { title: 'HTTP', key: 'responseCode', width: 80, render: (row) => row.responseCode || '-' },
  { title: 'TraceId', key: 'traceId', minWidth: 180 },
  { title: '错误信息', key: 'errorMessage', minWidth: 220, render: (row) => row.errorMessage || '-' },
  {
    title: '操作',
    key: 'actions',
    width: 170,
    render: (row) => h('div', { class: 'table-actions' }, [
      h(NButton, { size: 'small', tertiary: true, onClick: () => openDeliveryDetail(row.id) }, { default: () => '明细' }),
      `${row.deliveryStatus || ''}`.toUpperCase() === 'FAILED'
        ? h(NButton, { size: 'small', tertiary: true, onClick: () => retryDelivery(row.id) }, { default: () => '重试' })
        : null
    ].filter(Boolean))
  }
]

const capabilityCards = computed(() => [
  {
    key: 'health',
    kicker: 'Health',
    label: '健康探针',
    description: '提供存活、就绪和版本接口，方便负载均衡、容器编排和外部探针接入。',
    tags: ['health/live', 'health/ready', 'version']
  },
  {
    key: 'account',
    kicker: 'Access',
    label: '账号接入',
    description: '对外暴露扫码登录、登录状态查询和账号列表，方便第三方系统驱动接入流程。',
    tags: ['login/start', 'login/status', 'accounts']
  },
  {
    key: 'conversation',
    kicker: 'Conversation',
    label: '会话查询',
    description: '可以按账号、联系人、方向和类型查询事件，以及查看会话联系人集合。',
    tags: ['events', 'messages/peers']
  },
  {
    key: 'dispatch',
    kicker: 'Dispatch',
    label: '发送能力',
    description: '支持文本发送、媒体发送和媒体上传，为后续开放平台和外部编排做基础。',
    tags: ['send-text', 'send-media', 'upload']
  }
])

const baseEndpoints = computed(() => [
  buildEndpoint('GET', '/health/live', '服务存活探针。适合 Liveness Probe。'),
  buildEndpoint('GET', '/health/ready', '服务就绪探针。适合 Readiness Probe。'),
  buildEndpoint('GET', '/version', '返回插件版本与服务名。')
])

const businessEndpoints = computed(() => [
  buildEndpoint('POST', '/accounts/login/start', '启动账号扫码接入流程。', ['接入', '登录会话'], JSON.stringify({ baseUrl: 'https://ilinkai.weixin.qq.com' }, null, 2)),
  buildEndpoint('GET', '/accounts/login/status?sessionCode=demo-session', '查询扫码登录状态。', ['接入', '轮询确认']),
  buildEndpoint('GET', '/accounts', '按关键字列出账号。', ['账号', '查询']),
  buildEndpoint('GET', '/events?wechatAccountId=1&contactId=wxid_demo', '按账号和联系人查询事件。', ['事件', '会话']),
  buildEndpoint('GET', '/messages/peers?wechatAccountId=1', '列出可交互联系人。', ['会话', '联系人']),
  buildEndpoint('POST', '/messages/upload', '上传待发送媒体文件，返回临时文件路径。', ['媒体', '上传'], '<multipart/form-data>'),
  buildEndpoint('POST', '/messages/send-text', '发送文本消息。', ['发送', '文本'], JSON.stringify({ wechatAccountId: 1, toUserId: 'wxid_demo', text: 'hello world' }, null, 2)),
  buildEndpoint('POST', '/messages/send-media', '发送媒体消息。', ['发送', '媒体'], JSON.stringify({ wechatAccountId: 1, toUserId: 'wxid_demo', type: 'image', filePath: 'D:/tmp/demo.png', text: '资料已补充' }, null, 2))
])

const requestExamples = computed(() => [
  {
    key: 'health',
    label: '健康检查 cURL',
    content: `curl -X GET "${openBaseUrl.value}/health/ready"`
  },
  {
    key: 'events',
    label: '事件查询 cURL',
    content: `curl -X GET "${openBaseUrl.value}/events?wechatAccountId=1&contactId=wxid_demo"`
  },
  {
    key: 'send-text',
    label: '发送文本 cURL',
    content: `curl -X POST "${openBaseUrl.value}/messages/send-text" \\\n  -H "Content-Type: application/json" \\\n  -d '{\n    "wechatAccountId": 1,\n    "toUserId": "wxid_demo",\n    "text": "您好，已收到您的消息。"\n  }'`
  },
  {
    key: 'send-media',
    label: '发送媒体 JSON',
    content: `{\n  "wechatAccountId": 1,\n  "toUserId": "wxid_demo",\n  "type": "image",\n  "filePath": "D:/tmp/demo.png",\n  "text": "请查收附件"\n}`
  }
])

const operationNotes = computed(() => [
  {
    key: 'context',
    label: '上下文限制',
    description: '发送接口依赖有效 context token。当前仍以“对方先发消息”作为会话前提，因此外部接入时要优先处理回复窗口逻辑。'
  },
  {
    key: 'trace',
    label: '链路追踪',
    description: '发送侧已经写入 message_dispatch，媒体链路已经写入 media_asset。开放接口调用后，可以在追踪工作台按 trace、分发和媒体继续排查。'
  },
  {
    key: 'media',
    label: '媒体流程',
    description: '媒体发送建议先走 upload 接口拿到 filePath，再调用 send-media。这样更符合当前业务链路，也利于后续做资产留痕。'
  },
  {
    key: 'governance',
    label: '配置与治理',
    description: '默认接入地址、CDN 地址、轮询超时和 webhook 相关参数统一在系统配置页维护，对外接入页本身不暴露敏感凭证。'
  }
])

function buildEndpoint(method, path, description, tags = [], body = '') {
  const fullUrl = `${openBaseUrl.value}${path}`
  return {
    method,
    path,
    fullUrl,
    description,
    tags,
    curl: buildCurl(method, fullUrl, body)
  }
}

function buildCurl(method, fullUrl, body) {
  if (method === 'POST' && body && body !== '<multipart/form-data>') {
    return `curl -X ${method} "${fullUrl}" \\\n  -H "Content-Type: application/json" \\\n  -d '${body.replace(/\n/g, '\n  ')}'`
  }
  if (method === 'POST' && body === '<multipart/form-data>') {
    return `curl -X POST "${fullUrl}" \\\n  -F "file=@/path/to/demo.png"`
  }
  return `curl -X ${method} "${fullUrl}"`
}

async function loadPlatformData() {
  platformSummary.value = await api.getPlatformSummary()
  await Promise.all([loadPlatformRequests(), loadPlatformDeliveries()])
}

async function loadPlatformRequests() {
  const payload = await api.listPlatformRequests({
    actionType: requestFilters.actionType,
    resultStatus: requestFilters.resultStatus,
    pageNum: requestPagination.page,
    pageSize: requestPagination.pageSize
  })
  requestRows.value = ensureArray(payload?.list)
  requestPagination.itemCount = Number(payload?.total || 0)
}

async function loadPlatformDeliveries() {
  const payload = await api.listPlatformDeliveries({
    wechatAccountId: deliveryFilters.wechatAccountId || null,
    deliveryStatus: deliveryFilters.deliveryStatus,
    pageNum: deliveryPagination.page,
    pageSize: deliveryPagination.pageSize
  })
  deliveryRows.value = ensureArray(payload?.list)
  deliveryPagination.itemCount = Number(payload?.total || 0)
}

async function openDeliveryDetail(id) {
  if (!id) {
    return
  }
  selectedDeliveryDetail.value = await api.getPlatformDeliveryDetail(id)
  deliveryDetailVisible.value = true
}

async function retryDelivery(id) {
  if (!id || retryingDelivery.value) {
    return
  }
  retryingDelivery.value = true
  try {
    const payload = await api.retryPlatformDelivery({ id })
    selectedDeliveryDetail.value = payload
    deliveryDetailVisible.value = true
    await Promise.all([loadPlatformData(), loadPlatformDeliveries()])
    message.success(`Webhook 投递 #${id} 已重试`)
  } finally {
    retryingDelivery.value = false
  }
}

async function retryCurrentDelivery() {
  if (!selectedDeliveryDetail.value?.id) {
    return
  }
  await retryDelivery(selectedDeliveryDetail.value.id)
}

async function applyRequestFilters() {
  requestPagination.page = 1
  await loadPlatformRequests()
}

async function applyDeliveryFilters() {
  deliveryPagination.page = 1
  await loadPlatformDeliveries()
}

function handleRequestPageChange(page) {
  requestPagination.page = page
  loadPlatformRequests()
}

function handleRequestPageSizeChange(pageSize) {
  requestPagination.pageSize = pageSize
  requestPagination.page = 1
  loadPlatformRequests()
}

function handleDeliveryPageChange(page) {
  deliveryPagination.page = page
  loadPlatformDeliveries()
}

function handleDeliveryPageSizeChange(pageSize) {
  deliveryPagination.pageSize = pageSize
  deliveryPagination.page = 1
  loadPlatformDeliveries()
}

function compactJson(value) {
  const text = `${value || ''}`.trim()
  if (!text) {
    return '-'
  }
  try {
    return JSON.stringify(JSON.parse(text))
  } catch (_error) {
    return text
  }
}

function prettyJson(value) {
  const text = `${value || ''}`.trim()
  if (!text) {
    return '-'
  }
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch (_error) {
    return text
  }
}

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

async function copyText(value) {
  const text = `${value || ''}`.trim()
  if (!text) {
    return
  }
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      message.success('已复制到剪贴板')
      return
    }
  } catch (_error) {
  }
  message.warning('当前环境不支持直接复制，请手动复制')
}

function go(path) {
  if (!path) {
    return
  }
  router.push(path)
}

onMounted(loadPlatformData)
</script>

<style scoped>
.page--platform {
  gap: 20px;
}

.platform-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(280px, 0.9fr);
  gap: 16px;
  padding: 20px 22px;
  border-radius: 24px;
  background:
    radial-gradient(circle at top left, rgba(2, 132, 199, 0.14), transparent 32%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(241, 245, 249, 0.96));
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.06);
}

.platform-hero__intro {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.platform-hero__kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #0369a1;
}

.platform-hero__intro strong {
  font-size: 30px;
  line-height: 1.15;
  word-break: break-all;
}

.platform-hero__intro p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.platform-hero__meta {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.platform-hero__card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.platform-hero__card span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.platform-hero__card strong {
  font-size: 22px;
}

.platform-hero__card small {
  color: #64748b;
  line-height: 1.5;
}

.platform-capabilities {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.capability-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.05);
}

.capability-card__kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #0f766e;
}

.capability-card strong {
  font-size: 22px;
}

.capability-card p {
  margin: 0;
  color: #475569;
  line-height: 1.65;
}

.capability-card__chips,
.endpoint-row__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.capability-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.endpoint-list,
.example-list,
.notes-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.runtime-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.runtime-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px;
  border-radius: 18px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.runtime-item span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.runtime-item strong {
  font-size: 16px;
  line-height: 1.55;
  word-break: break-all;
}

.runtime-item small {
  color: #64748b;
  line-height: 1.5;
}

.request-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px auto;
  gap: 10px;
  margin-bottom: 14px;
}

.request-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.table-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.delivery-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.delivery-detail__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.endpoint-row,
.example-card,
.note-card {
  padding: 14px;
  border-radius: 18px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.endpoint-row__head,
.example-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.endpoint-row__head strong {
  font-size: 15px;
  word-break: break-all;
}

.endpoint-row p,
.note-card p {
  margin: 10px 0 0;
  color: #475569;
  line-height: 1.65;
}

.endpoint-row__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.endpoint-method {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.endpoint-method--get {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

.endpoint-method--post {
  background: rgba(2, 132, 199, 0.12);
  color: #0369a1;
}

.example-card pre {
  margin: 12px 0 0;
  padding: 14px;
  border-radius: 14px;
  background: #0f172a;
  color: #e2e8f0;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
}

.note-card strong {
  font-size: 16px;
}

@media (max-width: 960px) {
  .platform-hero {
    grid-template-columns: 1fr;
  }

  .request-toolbar {
    grid-template-columns: 1fr;
  }

  .delivery-detail__meta {
    grid-template-columns: 1fr;
  }
}
</style>
