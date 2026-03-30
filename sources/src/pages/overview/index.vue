<template>
  <div class="page">
    <page-header
      kicker="总览"
      title="微信接入总览工作台"
      description="统一观察多微信账号接入状态、轮询健康度、入站/出站事件数量与近期异常日志。"
    >
      <template #actions>
        <n-button type="primary" @click="loadSummary">刷新数据</n-button>
      </template>
    </page-header>

    <section class="metric-grid">
      <article v-for="item in metrics" :key="item.key" class="metric-card">
        <span class="metric-label">{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <span class="metric-note">{{ item.note }}</span>
      </article>
    </section>

    <section class="panel-grid">
      <n-card title="最近账号">
        <n-data-table :columns="accountColumns" :data="recentAccounts" :pagination="false" />
      </n-card>
      <n-card title="最近日志">
        <n-data-table :columns="logColumns" :data="recentLogs" :pagination="false" />
      </n-card>
    </section>
  </div>
</template>

<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { NTag } from 'naive-ui'
import api from '../../api.js'
import { ensureArray } from '../../utils/http'
import PageHeader from '../../components/PageHeader.vue'

const summary = ref({})

const metrics = computed(() => [
  { key: 'account', label: '微信账号数', value: summary.value.accountCount || 0, note: '当前可访问的微信账号资源' },
  { key: 'enabled', label: '启用账号数', value: summary.value.enabledAccountCount || 0, note: '当前处于启用状态的账号' },
  { key: 'polling', label: '轮询运行数', value: summary.value.pollingAccountCount || 0, note: '当前运行中的轮询任务数' },
  { key: 'inbound', label: '今日入站事件', value: summary.value.inboundTodayCount || 0, note: '今日接收到的消息事件数' },
  { key: 'outbound', label: '今日出站事件', value: summary.value.outboundTodayCount || 0, note: '今日发送记录数' },
  { key: 'error', label: '错误日志数', value: summary.value.errorCount || 0, note: '最近错误日志数量' }
])

const recentAccounts = computed(() => ensureArray(summary.value.recentAccounts))
const recentLogs = computed(() => ensureArray(summary.value.recentLogs))

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

async function loadSummary() {
  summary.value = await api.dashboardSummary()
}

onMounted(loadSummary)
</script>
