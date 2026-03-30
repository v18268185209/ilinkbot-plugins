<template>
  <div class="page">
    <page-header
      kicker="事件"
      title="事件中心"
      description="先按微信账号查看事件汇总，再下钻具体消息列表和联系人集合。"
    >
      <template #actions>
        <n-button type="primary" @click="loadSummary">刷新事件</n-button>
      </template>
    </page-header>

    <div class="toolbar">
      <n-input v-model:value="summaryKeyword" placeholder="按微信号/名称/地址搜索" style="max-width: 280px;" />
      <n-button @click="applySummarySearch">查询</n-button>
    </div>

    <n-card title="微信账号事件汇总">
      <n-data-table :columns="summaryColumns" :data="summaryRows" :pagination="summaryPagination" remote />
    </n-card>

    <modal-frame
      v-model:show="detailModalVisible"
      :title="detailTitle"
      :width="1080"
      :height="680"
    >
      <div class="toolbar">
        <n-select v-model:value="detailFilters.direction" :options="directionOptions" clearable placeholder="方向" style="max-width: 220px;" />
        <n-select v-model:value="detailFilters.eventType" :options="eventTypeOptions" clearable placeholder="事件类型" style="max-width: 220px;" />
        <n-button @click="applyDetailSearch">查询</n-button>
      </div>
      <n-data-table :columns="detailColumns" :data="detailRows" :pagination="detailPagination" remote />
      <template #footer>
        <n-button @click="detailModalVisible = false">关闭</n-button>
      </template>
    </modal-frame>

    <modal-frame
      v-model:show="previewModalVisible"
      :title="previewTitle"
      :width="980"
      :height="720"
    >
      <div class="preview-shell">
        <img
          v-if="previewKind === 'image' && previewSrc"
          :src="previewSrc"
          alt="media-preview"
          class="preview-image"
        />
        <audio
          v-else-if="previewKind === 'voice' && previewSrc"
          :src="previewSrc"
          controls
          class="preview-audio"
        />
        <video
          v-else-if="previewKind === 'video' && previewSrc"
          :src="previewSrc"
          controls
          class="preview-video"
        />
        <a
          v-else-if="previewSrc"
          :href="previewSrc"
          target="_blank"
          rel="noreferrer"
          class="preview-link"
        >
          打开媒体文件
        </a>
        <div v-else class="preview-empty">当前事件没有可预览的媒体内容</div>
      </div>
      <template #footer>
        <n-button @click="previewModalVisible = false">关闭</n-button>
      </template>
    </modal-frame>

    <modal-frame
      v-model:show="contactsModalVisible"
      :title="contactsTitle"
      :width="980"
      :height="640"
    >
      <div class="toolbar">
        <n-input v-model:value="contactsKeyword" placeholder="按联系人ID搜索" style="max-width: 280px;" />
        <n-button @click="applyContactsSearch">查询</n-button>
      </div>
      <n-data-table :columns="contactColumns" :data="contactRows" :pagination="contactsPagination" remote />
      <template #footer>
        <n-button @click="contactsModalVisible = false">关闭</n-button>
      </template>
    </modal-frame>
  </div>
</template>

<script setup>
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton } from 'naive-ui'
import api from '../../api.js'
import { ensureArray } from '../../utils/http'
import PageHeader from '../../components/PageHeader.vue'
import ModalFrame from '../../components/ModalFrame.vue'

const summaryKeyword = ref('')
const summaryRows = ref([])
const summaryPaginationState = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})

const detailModalVisible = ref(false)
const detailRows = ref([])
const detailPaginationState = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})
const detailFilters = reactive({
  direction: null,
  eventType: null
})
const selectedAccount = ref(null)
const previewModalVisible = ref(false)
const previewEvent = ref(null)

const contactsModalVisible = ref(false)
const contactsKeyword = ref('')
const contactRows = ref([])
const contactsPaginationState = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})

const directionOptions = [
  { label: '入站', value: 'inbound' },
  { label: '出站', value: 'outbound' }
]

const eventTypeOptions = [
  { label: '文本', value: 'text' },
  { label: '图片', value: 'image' },
  { label: '文件', value: 'file' },
  { label: '视频', value: 'video' },
  { label: '语音', value: 'voice' }
]

const summaryColumns = [
  { title: '微信账号ID', key: 'wechatAccountId', width: 120 },
  { title: '微信号', key: 'accountCode', width: 220 },
  { title: '账号名称', key: 'accountName', width: 220 },
  { title: '事件总数', key: 'totalCount', width: 120 },
  { title: '出站数量', key: 'outboundCount', width: 120 },
  { title: '入站数量', key: 'inboundCount', width: 120 },
  { title: '最近事件时间', key: 'lastEventAt', width: 180, render: (row) => formatDateTime(row.lastEventAt) },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    render: (row) => h(
      'div',
      { style: 'display:flex;gap:8px;flex-wrap:wrap;' },
      [
        h(NButton, { size: 'small', onClick: () => openDetailModal(row) }, { default: () => '详情' }),
        h(NButton, { size: 'small', tertiary: true, onClick: () => openContactsModal(row) }, { default: () => '联系人' })
      ]
    )
  }
]

const detailColumns = [
  { title: '方向', key: 'direction', width: 100 },
  { title: '类型', key: 'eventType', width: 120 },
  { title: '发送方', key: 'fromUserId', width: 180 },
  { title: '接收方', key: 'toUserId', width: 180 },
  { title: '正文', key: 'bodyText', minWidth: 240 },
  { title: '媒体文件', key: 'mediaFileName', width: 200 },
  {
    title: '预览',
    key: 'preview',
    width: 100,
    render: (row) => row.mediaPath && ['image', 'voice', 'video', 'file'].includes(row.eventType)
      ? h(NButton, { size: 'small', tertiary: true, onClick: () => openPreview(row) }, { default: () => '查看' })
      : '-'
  },
  { title: '时间', key: 'createTime', width: 180, render: (row) => formatDateTime(row.createTime) }
]

const contactColumns = [
  { title: '联系人ID', key: 'contactId', minWidth: 260 },
  { title: '作为发送方次数', key: 'senderCount', width: 140 },
  { title: '作为接收方次数', key: 'receiverCount', width: 140 },
  { title: '总次数', key: 'totalCount', width: 100 },
  { title: '最近出现时间', key: 'lastSeenAt', width: 180, render: (row) => formatDateTime(row.lastSeenAt) }
]

const summaryPagination = computed(() => ({
  page: summaryPaginationState.page,
  pageSize: summaryPaginationState.pageSize,
  itemCount: summaryPaginationState.itemCount,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page) => {
    summaryPaginationState.page = page
    loadSummary()
  },
  onUpdatePageSize: (pageSize) => {
    summaryPaginationState.pageSize = pageSize
    summaryPaginationState.page = 1
    loadSummary()
  }
}))

const detailPagination = computed(() => ({
  page: detailPaginationState.page,
  pageSize: detailPaginationState.pageSize,
  itemCount: detailPaginationState.itemCount,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page) => {
    detailPaginationState.page = page
    loadDetailEvents()
  },
  onUpdatePageSize: (pageSize) => {
    detailPaginationState.pageSize = pageSize
    detailPaginationState.page = 1
    loadDetailEvents()
  }
}))

const contactsPagination = computed(() => ({
  page: contactsPaginationState.page,
  pageSize: contactsPaginationState.pageSize,
  itemCount: contactsPaginationState.itemCount,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page) => {
    contactsPaginationState.page = page
    loadContacts()
  },
  onUpdatePageSize: (pageSize) => {
    contactsPaginationState.pageSize = pageSize
    contactsPaginationState.page = 1
    loadContacts()
  }
}))

const detailTitle = computed(() => {
  if (!selectedAccount.value) {
    return '事件详情'
  }
  return `事件详情 - ${selectedAccount.value.accountName || selectedAccount.value.accountCode || selectedAccount.value.wechatAccountId}`
})

const contactsTitle = computed(() => {
  if (!selectedAccount.value) {
    return '联系人列表'
  }
  return `联系人列表 - ${selectedAccount.value.accountName || selectedAccount.value.accountCode || selectedAccount.value.wechatAccountId}`
})

const previewTitle = computed(() => {
  if (!previewEvent.value) {
    return '媒体预览'
  }
  return `媒体预览 - ${previewEvent.value.mediaFileName || previewEvent.value.eventType || 'event'}`
})

const previewKind = computed(() => previewEvent.value?.eventType || '')
const previewSrc = computed(() => {
  if (!previewEvent.value?.id || !previewEvent.value?.mediaPath) {
    return ''
  }
  return api.getEventMediaUrl(previewEvent.value.id)
})

async function loadSummary() {
  const payload = await api.listEventAccountSummaries({
    keyword: summaryKeyword.value,
    pageNum: summaryPaginationState.page,
    pageSize: summaryPaginationState.pageSize
  })
  summaryRows.value = ensureArray(payload?.list)
  summaryPaginationState.itemCount = Number(payload?.total || 0)
}

async function loadDetailEvents() {
  if (!selectedAccount.value?.wechatAccountId) {
    return
  }
  const payload = await api.listEvents({
    wechatAccountId: selectedAccount.value.wechatAccountId,
    direction: detailFilters.direction,
    eventType: detailFilters.eventType,
    pageNum: detailPaginationState.page,
    pageSize: detailPaginationState.pageSize
  })
  detailRows.value = ensureArray(payload?.list)
  detailPaginationState.itemCount = Number(payload?.total || 0)
}

async function loadContacts() {
  if (!selectedAccount.value?.wechatAccountId) {
    return
  }
  const payload = await api.listEventContacts({
    wechatAccountId: selectedAccount.value.wechatAccountId,
    keyword: contactsKeyword.value,
    pageNum: contactsPaginationState.page,
    pageSize: contactsPaginationState.pageSize
  })
  contactRows.value = ensureArray(payload?.list)
  contactsPaginationState.itemCount = Number(payload?.total || 0)
}

function applySummarySearch() {
  summaryPaginationState.page = 1
  loadSummary()
}

function applyDetailSearch() {
  detailPaginationState.page = 1
  loadDetailEvents()
}

function applyContactsSearch() {
  contactsPaginationState.page = 1
  loadContacts()
}

function openDetailModal(row) {
  selectedAccount.value = row
  detailFilters.direction = null
  detailFilters.eventType = null
  detailPaginationState.page = 1
  detailModalVisible.value = true
  loadDetailEvents()
}

function openContactsModal(row) {
  selectedAccount.value = row
  contactsKeyword.value = ''
  contactsPaginationState.page = 1
  contactsModalVisible.value = true
  loadContacts()
}

function openPreview(row) {
  previewEvent.value = row
  previewModalVisible.value = true
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

onMounted(loadSummary)
</script>

<style scoped>
.preview-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  background: #0f172a08;
  border-radius: 16px;
}

.preview-image,
.preview-video {
  max-width: 100%;
  max-height: 560px;
  border-radius: 12px;
}

.preview-audio {
  width: min(100%, 520px);
}

.preview-link {
  color: #0f766e;
  text-decoration: none;
  font-weight: 700;
}

.preview-empty {
  color: #64748b;
}
</style>
