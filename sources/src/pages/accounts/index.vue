<template>
  <div class="page">
    <page-header
      kicker="账号"
      title="微信账号管理"
      description="管理多微信账号资源、默认负责人和登录会话，支持成员协作与账号启停。"
    >
      <template #actions>
        <n-button v-permission="'/api/wechathlink/admin/accounts/list'" @click="loadAccounts">刷新</n-button>
        <n-button v-permission="'btn:wechathlink_accounts:loginstart'" @click="openLoginModal()">扫码绑定</n-button>
        <n-button v-permission="'btn:wechathlink_accounts:member'" @click="openMemberModal()">成员授权</n-button>
        <n-button v-permission="'btn:wechathlink_accounts:create'" type="primary" @click="openAccountModal()">新增账号</n-button>
      </template>
    </page-header>

    <n-card title="账号列表">
      <n-data-table :columns="columns" :data="rows" :pagination="false" />
    </n-card>

    <modal-frame v-model:show="detailModalVisible" title="账号详情" :height="680" :width="1080">
      <div v-if="detailAccount" class="account-detail-shell">
        <div class="account-detail-grid">
          <div class="account-detail-card">
            <span>账号编码</span>
            <strong>{{ detailAccount.accountCode || '-' }}</strong>
          </div>
          <div class="account-detail-card">
            <span>账号名称</span>
            <strong>{{ detailAccount.accountName || '-' }}</strong>
          </div>
          <div class="account-detail-card">
            <span>绑定状态</span>
            <strong>{{ 绑定状态文本(detailAccount.bindStatus) }}</strong>
          </div>
          <div class="account-detail-card">
            <span>当前 Runtime</span>
            <strong>{{ detailAccount.currentRuntimeId || '-' }}</strong>
          </div>
          <div class="account-detail-card">
            <span>最近绑定时间</span>
            <strong>{{ formatDateTime(detailAccount.lastBindAt) }}</strong>
          </div>
          <div class="account-detail-card">
            <span>最近轮询时间</span>
            <strong>{{ formatDateTime(detailAccount.lastPollAt) }}</strong>
          </div>
        </div>

        <n-card title="运行实例" size="small">
          <n-data-table :columns="runtimeColumns" :data="detailRuntimes" :pagination="false" />
        </n-card>

        <n-card title="最近绑定会话" size="small">
          <n-data-table :columns="sessionColumns" :data="detailSessions" :pagination="false" />
        </n-card>
      </div>
      <n-empty v-else description="请选择账号查看详情" />
      <template #footer>
        <n-button @click="detailModalVisible = false">关闭</n-button>
      </template>
    </modal-frame>

    <modal-frame v-model:show="accountModalVisible" :title="accountModalTitle" :height="520">
      <n-form label-placement="top">
        <n-form-item>
          <template #label>
            <field-hint label="账号编码" tip="用于唯一标识一个微信接入账号，建议使用稳定且可读的编码。"/>
          </template>
          <n-input v-model:value="form.accountCode" />
        </n-form-item>
        <n-form-item label="账号名称">
          <n-input v-model:value="form.accountName" />
        </n-form-item>
        <n-form-item>
          <template #label>
            <field-hint label="接入地址" tip="iLink 服务地址，扫码登录与消息收发都会走这个地址。"/>
          </template>
          <n-input v-model:value="form.baseUrl" />
        </n-form-item>
        <n-form-item label="登录状态">
          <n-select v-model:value="form.loginStatus" :options="loginStatusOptions" />
        </n-form-item>
        <n-form-item label="轮询状态">
          <n-select v-model:value="form.pollStatus" :options="pollStatusOptions" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="accountModalVisible = false">取消</n-button>
        <n-button v-permission="'btn:wechathlink_accounts:create'" type="primary" @click="submitAccount">保存</n-button>
      </template>
    </modal-frame>

    <modal-frame v-model:show="loginModalVisible" title="扫码绑定机器人" :height="620">
      <n-space vertical size="large">
        <n-alert type="info" title="提示" :bordered="false">
          请先选择一个已创建的微信基础账号，再发起扫码绑定。扫码成功后会为该账号生成新的机器人运行实例并启动轮询。
        </n-alert>
        <n-form label-placement="top">
          <n-form-item label="绑定目标账号">
            <n-select
              v-model:value="loginTargetAccountId"
              :options="accountOptions"
              filterable
              placeholder="请选择要绑定的微信账号"
              @update:value="handleLoginTargetChange"
            />
          </n-form-item>
          <n-form-item>
            <template #label>
            <field-hint label="登录接入地址" tip="可为空，留空时使用系统设置中的默认 iLink 接入地址。"/>
          </template>
            <n-input v-model:value="loginBaseUrl" placeholder="可选，默认使用系统接入地址" />
          </n-form-item>
        </n-form>
        <div v-if="loginSession.sessionCode" class="login-session-card">
          <div>
            <strong>会话编号</strong>
            <p>{{ loginSession.sessionCode }}</p>
          </div>
          <div>
            <strong>状态</strong>
            <p>{{ 登录状态文本(loginSession.status) }}</p>
          </div>
          <div v-if="loginSession.expiredAt">
            <strong>过期时间</strong>
            <p>{{ loginSession.expiredAt }}</p>
          </div>
          <div v-if="loginSession.accountId">
            <strong>已绑定账号编号</strong>
            <p>{{ loginSession.accountId }}</p>
          </div>
          <div class="login-session-card__qr-wrap">
            <img
              v-if="qrCodeSrc && !qrImageError"
              :src="qrCodeSrc"
              alt="qr"
              class="login-session-card__qr"
              @error="qrImageError = true"
            />
            <div v-else class="login-session-card__qr-empty">
              二维码加载失败，请重新创建会话
            </div>
          </div>
        </div>
      </n-space>
      <template #footer>
        <n-button @click="loginModalVisible = false">关闭</n-button>
        <n-button v-permission="'btn:wechathlink_accounts:loginstatus'" @click="refreshLoginStatus" :disabled="!loginSession.sessionCode || loginPolling">刷新状态</n-button>
        <n-button
          v-permission="'btn:wechathlink_accounts:loginstart'"
          type="primary"
          @click="createLoginSession"
          :loading="creatingLoginSession"
          :disabled="!loginTargetAccountId"
        >
          创建会话
        </n-button>
      </template>
    </modal-frame>

    <modal-frame v-model:show="memberModalVisible" title="成员授权" :height="520">
      <n-form label-placement="top">
        <n-form-item label="微信账号">
          <n-select v-model:value="memberForm.wechatAccountId" :options="accountOptions" filterable @update:value="handleMemberAccountChange" />
        </n-form-item>
        <n-form-item>
          <template #label>
            <field-hint label="系统用户编号" tip="输入宿主系统用户编号，用于绑定账号操作权限。"/>
          </template>
          <n-input v-model:value="memberForm.userId" />
        </n-form-item>
        <n-form-item>
          <template #label>
            <field-hint label="角色" tip="负责人可管理全部，运营可操作账号，分析与审计角色仅用于查看。"/>
          </template>
          <n-select v-model:value="memberForm.roleCode" :options="roleOptions" />
        </n-form-item>
        <n-form-item>
          <template #label>
            <field-hint label="权限范围" tip="可在角色默认权限上微调，未选择时会按角色自动填充默认值。"/>
          </template>
          <div class="member-scope-editor">
            <n-select
              v-model:value="memberForm.permissionScopes"
              :options="permissionScopeOptions"
              multiple
              clearable
              placeholder="请选择权限范围"
            />
            <n-button v-permission="'btn:wechathlink_accounts:member'" tertiary @click="applyRoleDefaultScopes(memberForm.roleCode)">套用角色默认权限</n-button>
          </div>
        </n-form-item>
      </n-form>

      <div v-if="memberForm.wechatAccountId" class="member-panel">
        <div class="member-panel__head">
          <strong>当前账号成员</strong>
          <span>{{ memberRows.length }} 人</span>
        </div>
        <n-data-table :columns="memberColumns" :data="memberRows" :pagination="false" size="small" />
      </div>
      <template #footer>
        <n-button @click="memberModalVisible = false">取消</n-button>
        <n-button v-permission="'btn:wechathlink_accounts:member'" type="primary" @click="submitMember">保存成员</n-button>
      </template>
    </modal-frame>
  </div>
</template>

<script setup>
import { computed, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { NButton, NTag } from 'naive-ui'
import api from '../../api.js'
import { ensureArray } from '../../utils/http'
import { resolveApiAssetUrl } from '../../config/env'
import PageHeader from '../../components/PageHeader.vue'
import FieldHint from '../../components/FieldHint.vue'
import ModalFrame from '../../components/ModalFrame.vue'
import { hasPermission } from '../../utils/permission'

const form = reactive({
  id: null,
  accountCode: '',
  accountName: '',
  baseUrl: '',
  loginStatus: 'CREATED',
  pollStatus: 'STOPPED'
})
const rows = ref([])
const loginBaseUrl = ref('')
const loginSession = ref({})
const accountModalVisible = ref(false)
const loginModalVisible = ref(false)
const memberModalVisible = ref(false)
const detailModalVisible = ref(false)
const creatingLoginSession = ref(false)
const loginPolling = ref(false)
const qrImageError = ref(false)
const loginTargetAccountId = ref(null)
let loginPollingTimer = null
const memberRows = ref([])
const detailPayload = ref(null)
const memberForm = reactive({
  wechatAccountId: null,
  userId: '',
  roleCode: 'OPERATOR',
  permissionScopes: []
})

const accountModalTitle = computed(() => form.id ? '编辑微信账号' : '新增微信账号')
const qrCodeSrc = computed(() => resolveApiAssetUrl(loginSession.value.qrCodeUrl))
const detailAccount = computed(() => detailPayload.value?.account || null)
const detailRuntimes = computed(() => ensureArray(detailPayload.value?.runtimes))
const detailSessions = computed(() => ensureArray(detailPayload.value?.loginSessions))

const loginStatusOptions = [
  { label: '已创建', value: 'CREATED' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '待扫码', value: 'WAIT_SCAN' }
]

const pollStatusOptions = [
  { label: '已停止', value: 'STOPPED' },
  { label: '运行中', value: 'RUNNING' },
  { label: '异常', value: 'ERROR' }
]

const roleOptions = [
  { label: '负责人', value: 'OWNER' },
  { label: '运营', value: 'OPERATOR' },
  { label: '分析', value: 'ANALYST' },
  { label: '审计', value: 'AUDITOR' }
]

const permissionScopeOptions = [
  { label: '读取', value: 'READ' },
  { label: '操作', value: 'OPERATE' },
  { label: '发送', value: 'SEND' },
  { label: '导出', value: 'EXPORT' },
  { label: '审计', value: 'AUDIT' },
  { label: '媒体查看', value: 'MEDIA' },
  { label: '设置管理', value: 'SETTINGS' }
]

const accountOptions = computed(() => rows.value.map((item) => ({
  label: `${item.accountName} (#${item.id})`,
  value: item.id
})))

const columns = [
  { title: '账号编码', key: 'accountCode' },
  { title: '账号名称', key: 'accountName' },
  { title: '绑定状态', key: 'bindStatus', render: (row) => h(NTag, { type: 绑定状态标签(row.bindStatus), bordered: false }, { default: () => 绑定状态文本(row.bindStatus) }) },
  { title: '当前 Runtime', key: 'currentRuntimeId', render: (row) => row.currentRuntimeId || '-' },
  { title: '最近绑定', key: 'lastBindAt', render: (row) => formatDateTime(row.lastBindAt) },
  { title: '接入地址', key: 'baseUrl' },
  { title: '登录状态', key: 'loginStatus', render: (row) => h(NTag, { type: 'info' }, { default: () => 登录状态文本(row.loginStatus) }) },
  { title: '轮询状态', key: 'pollStatus', render: (row) => h(NTag, { type: row.pollStatus === 'RUNNING' ? 'success' : 'warning' }, { default: () => 轮询状态文本(row.pollStatus) }) },
  {
    title: '操作',
    key: 'actions',
    render: (row) => h(
      'div',
      { style: 'display:flex;gap:8px;flex-wrap:wrap;' },
      [
        hasPermission('/api/wechathlink/admin/accounts/detail')
          ? h(NButton, { size: 'small', tertiary: true, onClick: () => openDetailModal(row) }, { default: () => '详情' })
          : null,
        hasPermission('btn:wechathlink_accounts:create')
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => openAccountModal(row) }, { default: () => '编辑' })
          : null,
        hasPermission('btn:wechathlink_accounts:toggle')
          ? h(NButton, { size: 'small', onClick: () => toggle(row) }, { default: () => (row.status === 1 ? '停用' : '启用') })
          : null,
        hasPermission('btn:wechathlink_accounts:member')
          ? h(NButton, { size: 'small', tertiary: true, onClick: () => openMemberModal(row) }, { default: () => '授权' })
          : null,
        hasPermission('btn:wechathlink_accounts:loginstart')
          ? h(NButton, { size: 'small', tertiary: true, onClick: () => openLoginModal(row) }, { default: () => (row.bindStatus === 'BOUND' ? '重绑' : '绑定') })
          : null
      ].filter(Boolean)
    )
  }
]

const runtimeColumns = [
  { title: 'Runtime ID', key: 'id', width: 100 },
  { title: '状态', key: 'runtimeStatus', width: 100, render: (row) => h(NTag, { bordered: false, type: runtimeTagType(row.runtimeStatus) }, { default: () => row.runtimeStatus || '-' }) },
  { title: '是否当前', key: 'isActive', width: 90, render: (row) => (Number(row.isActive) === 1 ? '是' : '否') },
  { title: '接入地址', key: 'baseUrl', minWidth: 200 },
  { title: '最后在线', key: 'lastOnlineAt', width: 180, render: (row) => formatDateTime(row.lastOnlineAt) },
  { title: '最后心跳', key: 'lastHeartbeatAt', width: 180, render: (row) => formatDateTime(row.lastHeartbeatAt) }
]

const sessionColumns = [
  { title: '会话编号', key: 'sessionCode', minWidth: 220 },
  { title: '状态', key: 'sessionStatus', width: 120 },
  { title: '绑定 Runtime', key: 'confirmedRuntimeId', width: 120, render: (row) => row.confirmedRuntimeId || '-' },
  { title: '过期时间', key: 'expiredAt', width: 180, render: (row) => formatDateTime(row.expiredAt) },
  { title: '接入地址', key: 'baseUrl', minWidth: 220 }
]

const memberColumns = [
  { title: '用户ID', key: 'userId', width: 100 },
  {
    title: '角色',
    key: 'roleCode',
    width: 100,
    render: (row) => h(NTag, { bordered: false, type: roleTagType(row.roleCode) }, { default: () => roleLabel(row.roleCode) })
  },
  {
    title: '权限范围',
    key: 'permissionScope',
    minWidth: 320,
    render: (row) => h(
      'div',
      { style: 'display:flex;gap:6px;flex-wrap:wrap;' },
      normalizePermissionScopes(row.permissionScope || row.permissionScopes).map((scope) =>
        h(NTag, { size: 'small', bordered: false, type: 'info' }, { default: () => permissionScopeLabel(scope) })
      )
    )
  },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row) => hasPermission('btn:wechathlink_accounts:member')
      ? h(NButton, { size: 'small', tertiary: true, onClick: () => editMember(row) }, { default: () => '编辑' })
      : null
  }
]

async function loadAccounts() {
  const payload = await api.listAccounts()
  rows.value = ensureArray(payload?.list)
}

function roleLabel(value) {
  const map = {
    OWNER: '负责人',
    OPERATOR: '运营',
    ANALYST: '分析',
    AUDITOR: '审计'
  }
  return map[value] || value || '-'
}

function roleTagType(value) {
  if (value === 'OWNER') {
    return 'success'
  }
  if (value === 'OPERATOR') {
    return 'info'
  }
  if (value === 'AUDITOR') {
    return 'warning'
  }
  return 'default'
}

function permissionScopeLabel(value) {
  const map = {
    READ: '读取',
    OPERATE: '操作',
    SEND: '发送',
    EXPORT: '导出',
    AUDIT: '审计',
    MEDIA: '媒体查看',
    SETTINGS: '设置管理',
    FULL: '全部权限'
  }
  return map[value] || value || '-'
}

function defaultPermissionScopes(roleCode) {
  const map = {
    OWNER: ['FULL'],
    OPERATOR: ['READ', 'OPERATE', 'SEND', 'EXPORT', 'MEDIA'],
    ANALYST: ['READ', 'EXPORT', 'MEDIA'],
    AUDITOR: ['READ', 'EXPORT', 'MEDIA', 'AUDIT']
  }
  return [...(map[roleCode] || ['READ'])]
}

function normalizePermissionScopes(value) {
  if (Array.isArray(value)) {
    return [...new Set(value.filter(Boolean).map((item) => `${item}`.trim().toUpperCase()))]
  }
  const text = `${value || ''}`
    .replace(/\[|\]|"|'/g, ' ')
    .trim()
  if (!text) {
    return []
  }
  return [...new Set(text.split(/[,\s;]+/).filter(Boolean).map((item) => item.trim().toUpperCase()))]
}

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

function 绑定状态文本(value) {
  const map = {
    UNBOUND: '未绑定',
    BOUND: '已绑定',
    EXPIRING_SOON: '即将失效',
    REAUTH_REQUIRED: '需重绑',
    DISABLED: '已停用'
  }
  return map[value] || value || '-'
}

function 绑定状态标签(value) {
  if (value === 'BOUND') {
    return 'success'
  }
  if (value === 'EXPIRING_SOON') {
    return 'warning'
  }
  if (value === 'REAUTH_REQUIRED') {
    return 'error'
  }
  return 'default'
}

function runtimeTagType(value) {
  if (value === 'ONLINE') {
    return 'success'
  }
  if (value === 'OFFLINE') {
    return 'warning'
  }
  if (value === 'EXPIRED') {
    return 'error'
  }
  return 'default'
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

function resetAccountForm() {
  form.id = null
  form.accountCode = ''
  form.accountName = ''
  form.baseUrl = ''
  form.loginStatus = 'CREATED'
  form.pollStatus = 'STOPPED'
}

function openAccountModal(row = null) {
  if (!row) {
    resetAccountForm()
  } else {
    form.id = row.id
    form.accountCode = row.accountCode || ''
    form.accountName = row.accountName || ''
    form.baseUrl = row.baseUrl || ''
    form.loginStatus = row.loginStatus || 'CREATED'
    form.pollStatus = row.pollStatus || 'STOPPED'
  }
  accountModalVisible.value = true
}

async function submitAccount() {
  await api.saveAccount(form)
  accountModalVisible.value = false
  await loadAccounts()
}

async function toggle(row) {
  await api.toggleAccount(row.id, row.status === 1 ? 0 : 1)
  await loadAccounts()
}

async function openDetailModal(row) {
  detailPayload.value = await api.getAccountDetail(row.id)
  detailModalVisible.value = true
}

function openLoginModal(row = null) {
  qrImageError.value = false
  loginTargetAccountId.value = row?.id || null
  loginBaseUrl.value = row?.baseUrl || ''
  loginSession.value = {}
  loginModalVisible.value = true
}

function handleLoginTargetChange(value) {
  loginTargetAccountId.value = value
  const target = rows.value.find((item) => item.id === value)
  if (target && target.baseUrl) {
    loginBaseUrl.value = target.baseUrl
  }
}

async function createLoginSession() {
  if (!loginTargetAccountId.value) {
    return
  }
  creatingLoginSession.value = true
  qrImageError.value = false
  try {
    loginSession.value = await api.startLoginSession({
      wechatAccountId: loginTargetAccountId.value,
      baseUrl: loginBaseUrl.value
    })
  } finally {
    creatingLoginSession.value = false
  }
}

async function refreshLoginStatus(options = {}) {
  if (!loginSession.value.sessionCode) {
    return
  }
  loginSession.value = await api.getLoginStatus(loginSession.value.sessionCode, options.silent ? { silentError: true } : {})
  if (loginSession.value.accountId) {
    await loadAccounts()
  }
}

function openMemberModal(row = null) {
  memberForm.wechatAccountId = row?.id || null
  memberForm.userId = ''
  memberForm.roleCode = 'OPERATOR'
  memberForm.permissionScopes = defaultPermissionScopes(memberForm.roleCode)
  memberRows.value = []
  memberModalVisible.value = true
  if (memberForm.wechatAccountId) {
    loadMemberDetails(memberForm.wechatAccountId)
  }
}

async function loadMemberDetails(accountId) {
  if (!accountId) {
    memberRows.value = []
    return
  }
  const payload = await api.getAccountDetail(accountId)
  memberRows.value = ensureArray(payload?.members)
}

function handleMemberAccountChange(value) {
  memberForm.wechatAccountId = value
  loadMemberDetails(value)
}

function applyRoleDefaultScopes(roleCode) {
  memberForm.permissionScopes = defaultPermissionScopes(roleCode)
}

function editMember(row) {
  memberForm.userId = `${row.userId || ''}`
  memberForm.roleCode = row.roleCode || 'OPERATOR'
  memberForm.permissionScopes = normalizePermissionScopes(row.permissionScope || row.permissionScopes)
}

async function submitMember() {
  await api.saveAccountMember({
    wechatAccountId: memberForm.wechatAccountId,
    userId: Number(memberForm.userId),
    roleCode: memberForm.roleCode,
    permissionScope: normalizePermissionScopes(memberForm.permissionScopes).join(',')
  })
  await loadMemberDetails(memberForm.wechatAccountId)
  await loadAccounts()
}

function stopLoginPolling() {
  if (loginPollingTimer) {
    clearInterval(loginPollingTimer)
    loginPollingTimer = null
  }
}

function shouldPollLoginStatus() {
  return loginModalVisible.value
    && !!loginSession.value.sessionCode
    && ['WAIT_SCAN', 'SCANNED', 'WAIT_CONFIRM', 'CONFIRMING'].includes(loginSession.value.status)
    && !loginSession.value.accountId
}

function syncLoginPolling() {
  stopLoginPolling()
  if (!shouldPollLoginStatus()) {
    return
  }
  loginPollingTimer = window.setInterval(async () => {
    if (loginPolling.value) {
      return
    }
    loginPolling.value = true
    try {
      await refreshLoginStatus({ silent: true })
    } finally {
      loginPolling.value = false
    }
  }, 3000)
}

watch(
  () => [loginModalVisible.value, loginSession.value.sessionCode, loginSession.value.status, loginSession.value.accountId],
  () => {
    syncLoginPolling()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  stopLoginPolling()
})

onMounted(loadAccounts)
</script>

<style scoped>
.login-session-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.login-session-card strong {
  display: inline-block;
  margin-bottom: 4px;
}

.login-session-card p {
  margin: 0;
  color: #475569;
}

.login-session-card__qr {
  width: 220px;
  height: 220px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: #fff;
}

.login-session-card__qr-wrap {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.login-session-card__qr-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 220px;
  height: 220px;
  padding: 16px;
  border-radius: 16px;
  border: 1px dashed rgba(148, 163, 184, 0.4);
  background: #fff;
  color: #64748b;
  text-align: center;
  line-height: 1.6;
}

.member-scope-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.member-panel {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.member-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #475569;
  font-size: 13px;
}
</style>
