<template>
  <div class="page">
    <page-header
      kicker="审计"
      title="审计中心"
      description="查看关键操作的审计记录，支持按账号、操作人、动作类型和结果状态快速检索。"
    >
      <template #actions>
        <n-button v-permission="'/api/wechathlink/admin/audit/list'" @click="refreshAuditLogs">刷新审计</n-button>
      </template>
    </page-header>

    <section class="audit-shell">
      <div class="audit-toolbar">
        <n-select
          v-model:value="filters.wechatAccountId"
          :options="accountOptions"
          clearable
          filterable
          placeholder="微信账号"
        />
        <n-input v-model:value="filters.operatorUserId" clearable placeholder="操作人ID" />
        <n-select
          v-model:value="filters.actionType"
          :options="actionTypeOptions"
          clearable
          placeholder="动作类型"
        />
        <n-select
          v-model:value="filters.resultStatus"
          :options="resultStatusOptions"
          clearable
          placeholder="结果状态"
        />
        <n-button v-permission="'/api/wechathlink/admin/audit/list'" @click="applySearch">查询</n-button>
      </div>

      <n-card title="审计记录">
        <n-data-table :columns="columns" :data="rows" :pagination="pagination" remote />
      </n-card>
    </section>

    <modal-frame
      v-model:show="detailModalVisible"
      title="审计详情"
      :width="960"
      :height="720"
    >
      <div v-if="detailRecord" class="audit-detail">
        <section class="audit-detail__meta">
          <div class="audit-detail__item">
            <span>记录ID</span>
            <strong>{{ detailRecord.id }}</strong>
          </div>
          <div class="audit-detail__item">
            <span>账号ID</span>
            <strong>{{ detailRecord.wechatAccountId || '-' }}</strong>
          </div>
          <div class="audit-detail__item">
            <span>操作人</span>
            <strong>{{ detailRecord.operatorUserName || detailRecord.operatorUserId || '-' }}</strong>
          </div>
          <div class="audit-detail__item">
            <span>动作</span>
            <strong>{{ detailRecord.actionType }}</strong>
          </div>
          <div class="audit-detail__item">
            <span>资源</span>
            <strong>{{ detailRecord.resourceType }} / {{ detailRecord.resourceId || '-' }}</strong>
          </div>
          <div class="audit-detail__item">
            <span>结果</span>
            <strong>{{ detailRecord.resultStatus }}</strong>
          </div>
          <div class="audit-detail__item audit-detail__item--full">
            <span>摘要</span>
            <strong>{{ detailRecord.summary }}</strong>
          </div>
        </section>

        <section class="audit-detail__json">
          <pre>{{ formattedDetailJson }}</pre>
        </section>
      </div>
      <n-empty v-else description="请选择一条审计记录" />
      <template #footer>
        <n-button @click="detailModalVisible = false">关闭</n-button>
      </template>
    </modal-frame>
  </div>
</template>

<script setup>
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NTag } from 'naive-ui'
import api from '../../api.js'
import { ensureArray } from '../../utils/http'
import PageHeader from '../../components/PageHeader.vue'
import ModalFrame from '../../components/ModalFrame.vue'
import { hasPermission } from '../../utils/permission'

const filters = reactive({
  wechatAccountId: null,
  operatorUserId: '',
  actionType: null,
  resultStatus: null
})

const rows = ref([])
const accounts = ref([])
const detailRecord = ref(null)
const detailModalVisible = ref(false)
const paginationState = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})

const actionTypeOptions = [
  { label: '账号创建', value: 'ACCOUNT_CREATE' },
  { label: '账号更新', value: 'ACCOUNT_UPDATE' },
  { label: '账号启停', value: 'ACCOUNT_TOGGLE' },
  { label: '成员授权创建', value: 'ACCOUNT_MEMBER_CREATE' },
  { label: '成员授权更新', value: 'ACCOUNT_MEMBER_UPDATE' },
  { label: '文本发送', value: 'MESSAGE_SEND_TEXT' },
  { label: '媒体发送', value: 'MESSAGE_SEND_MEDIA' },
  { label: '设置保存', value: 'SETTINGS_SAVE' },
  { label: '媒体查看', value: 'EVENT_MEDIA_VIEW' }
]

const resultStatusOptions = [
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILURE' }
]

const accountOptions = computed(() => accounts.value.map((item) => ({
  label: `${item.accountName} (#${item.id})`,
  value: item.id
})))

const formattedDetailJson = computed(() => {
  const raw = detailRecord.value?.detailJson
  if (!raw) {
    return '{}'
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch (error) {
    return raw
  }
})

const columns = [
  { title: '时间', key: 'createTime', width: 180, render: (row) => formatDateTime(row.createTime) },
  { title: '账号ID', key: 'wechatAccountId', width: 100, render: (row) => row.wechatAccountId || '-' },
  { title: '操作人', key: 'operatorUserName', width: 140, render: (row) => row.operatorUserName || row.operatorUserId || '-' },
  { title: '动作', key: 'actionType', width: 180 },
  { title: '资源', key: 'resourceType', width: 160, render: (row) => `${row.resourceType}${row.resourceId ? ` / ${row.resourceId}` : ''}` },
  {
    title: '结果',
    key: 'resultStatus',
    width: 100,
    render: (row) => h(NTag, { type: row.resultStatus === 'SUCCESS' ? 'success' : 'error', bordered: false }, { default: () => row.resultStatus })
  },
  { title: '摘要', key: 'summary', minWidth: 320 },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row) => hasPermission('btn:wechathlink_audits:detail')
      ? h(NButton, { size: 'small', tertiary: true, onClick: () => openDetail(row) }, { default: () => '详情' })
      : null
  }
]

const pagination = computed(() => ({
  page: paginationState.page,
  pageSize: paginationState.pageSize,
  itemCount: paginationState.itemCount,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page) => {
    paginationState.page = page
    loadAuditLogs()
  },
  onUpdatePageSize: (pageSize) => {
    paginationState.pageSize = pageSize
    paginationState.page = 1
    loadAuditLogs()
  }
}))

async function loadAccounts() {
  const payload = await api.listAccounts()
  accounts.value = ensureArray(payload?.list)
}

async function loadAuditLogs() {
  const payload = await api.listAuditLogs({
    wechatAccountId: filters.wechatAccountId,
    operatorUserId: filters.operatorUserId ? Number(filters.operatorUserId) : null,
    actionType: filters.actionType,
    resultStatus: filters.resultStatus,
    pageNum: paginationState.page,
    pageSize: paginationState.pageSize
  })
  rows.value = ensureArray(payload?.list)
  paginationState.itemCount = Number(payload?.total || 0)
}

async function refreshAuditLogs() {
  await loadAccounts()
  await loadAuditLogs()
}

async function applySearch() {
  paginationState.page = 1
  await loadAuditLogs()
}

async function openDetail(row) {
  detailRecord.value = await api.getAuditDetail(row.id)
  detailModalVisible.value = true
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

onMounted(refreshAuditLogs)
</script>

<style scoped>
.audit-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.audit-toolbar {
  display: grid;
  grid-template-columns: 220px 180px 200px 160px auto;
  gap: 12px;
  align-items: center;
}

.audit-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.audit-detail__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.audit-detail__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.audit-detail__item span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.audit-detail__item strong {
  color: #0f172a;
  word-break: break-all;
}

.audit-detail__item--full {
  grid-column: 1 / -1;
}

.audit-detail__json {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.audit-detail__json pre {
  margin: 0;
  padding: 16px;
  max-height: 360px;
  overflow: auto;
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.65;
}

@media (max-width: 1200px) {
  .audit-toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .audit-toolbar,
  .audit-detail__meta {
    grid-template-columns: 1fr;
  }
}
</style>
