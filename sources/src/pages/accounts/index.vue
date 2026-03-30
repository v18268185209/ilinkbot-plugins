<template>
  <div class="page">
    <page-header
      kicker="账号"
      title="微信账号管理"
      description="管理多微信账号资源、默认负责人和登录会话，支持成员协作与账号启停。"
    >
      <template #actions>
        <n-button @click="loadAccounts">刷新</n-button>
        <n-button @click="openLoginModal">扫码接入</n-button>
        <n-button @click="openMemberModal()">成员授权</n-button>
        <n-button type="primary" @click="openAccountModal()">新增账号</n-button>
      </template>
    </page-header>

    <n-card title="账号列表">
      <n-data-table :columns="columns" :data="rows" :pagination="false" />
    </n-card>

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
        <n-button type="primary" @click="submitAccount">保存</n-button>
      </template>
    </modal-frame>

    <modal-frame v-model:show="loginModalVisible" title="扫码登录接入" :height="560">
      <n-space vertical size="large">
        <n-alert type="info" title="提示" :bordered="false">
          创建登录会话后会生成二维码，扫码确认后刷新状态即可自动生成微信账号并启动轮询。
        </n-alert>
        <n-form label-placement="top">
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
        <n-button @click="refreshLoginStatus" :disabled="!loginSession.sessionCode || loginPolling">刷新状态</n-button>
        <n-button type="primary" @click="createLoginSession" :loading="creatingLoginSession">创建会话</n-button>
      </template>
    </modal-frame>

    <modal-frame v-model:show="memberModalVisible" title="成员授权" :height="520">
      <n-form label-placement="top">
        <n-form-item label="微信账号">
          <n-select v-model:value="memberForm.wechatAccountId" :options="accountOptions" filterable />
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
      </n-form>
      <template #footer>
        <n-button @click="memberModalVisible = false">取消</n-button>
        <n-button type="primary" @click="submitMember">保存成员</n-button>
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
const creatingLoginSession = ref(false)
const loginPolling = ref(false)
const qrImageError = ref(false)
let loginPollingTimer = null
const memberForm = reactive({
  wechatAccountId: null,
  userId: '',
  roleCode: 'OPERATOR'
})

const accountModalTitle = computed(() => form.id ? '编辑微信账号' : '新增微信账号')
const qrCodeSrc = computed(() => resolveApiAssetUrl(loginSession.value.qrCodeUrl))

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

const accountOptions = computed(() => rows.value.map((item) => ({
  label: `${item.accountName} (#${item.id})`,
  value: item.id
})))

const columns = [
  { title: '账号编码', key: 'accountCode' },
  { title: '账号名称', key: 'accountName' },
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
        h(NButton, { size: 'small', quaternary: true, onClick: () => openAccountModal(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', onClick: () => toggle(row) }, { default: () => (row.status === 1 ? '停用' : '启用') }),
        h(NButton, { size: 'small', tertiary: true, onClick: () => openMemberModal(row) }, { default: () => '授权' })
      ]
    )
  }
]

async function loadAccounts() {
  const payload = await api.listAccounts()
  rows.value = ensureArray(payload?.list)
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

function openLoginModal() {
  qrImageError.value = false
  loginModalVisible.value = true
}

async function createLoginSession() {
  creatingLoginSession.value = true
  qrImageError.value = false
  try {
    loginSession.value = await api.startLoginSession(loginBaseUrl.value)
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
  memberModalVisible.value = true
}

async function submitMember() {
  await api.saveAccountMember({
    wechatAccountId: memberForm.wechatAccountId,
    userId: Number(memberForm.userId),
    roleCode: memberForm.roleCode
  })
  memberModalVisible.value = false
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
</style>
