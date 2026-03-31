<template>
  <div class="page page--events">
    <section class="events-layout">
      <header class="events-overview">
        <div class="events-overview__intro">
          <div class="events-overview__kicker">事件运营</div>
          <strong>事件中心</strong>
          <span>面向企业运营与审计的账号、事件、联系人一体化排查工作台。</span>
        </div>

        <div class="events-overview__actions">
          <n-button v-permission="'/api/wechathlink/admin/events/list'" @click="clearEventFilters">清空筛选</n-button>
          <n-button v-permission="'/api/wechathlink/admin/events/summary'" type="primary" @click="refreshWorkspace">刷新事件</n-button>
        </div>

        <div class="events-kpis">
          <article class="events-kpi">
            <span class="events-kpi__label">当前页账号</span>
            <strong>{{ summaryRows.length }}</strong>
            <small>共 {{ summaryPaginationState.itemCount }} 个可见账号</small>
          </article>
          <article class="events-kpi">
            <span class="events-kpi__label">运行中账号</span>
            <strong>{{ runningAccountCount }}</strong>
            <small>轮询状态为 RUNNING</small>
          </article>
          <article class="events-kpi">
            <span class="events-kpi__label">异常账号</span>
            <strong>{{ abnormalAccountCount }}</strong>
            <small>轮询异常或登录异常</small>
          </article>
          <article class="events-kpi">
            <span class="events-kpi__label">当前页事件量</span>
            <strong>{{ visibleEventCount }}</strong>
            <small>{{ latestVisibleEventAt ? `最近事件 ${formatDateTime(latestVisibleEventAt)}` : '暂无事件' }}</small>
          </article>
        </div>
      </header>

      <section class="events-shell">
        <aside class="events-sidebar">
          <div class="events-panel">
            <div class="events-panel__header">
              <div>
                <strong>账号概览</strong>
                <span>{{ summaryLoading ? '正在刷新账号事件摘要...' : '按账号查看健康度、事件规模与最近活跃时间。' }}</span>
              </div>
            </div>

            <div class="toolbar toolbar--compact">
              <n-input
                v-model:value="summaryKeyword"
                clearable
                placeholder="按微信号 / 名称 / 地址搜索"
                @keyup.enter="applySummarySearch"
              />
              <n-button v-permission="'/api/wechathlink/admin/events/summary'" @click="applySummarySearch">查询</n-button>
            </div>

            <div v-if="summaryRows.length" class="account-list">
              <button
                v-for="row in summaryRows"
                :key="row.wechatAccountId"
                type="button"
                :class="['account-row', selectedAccountId === row.wechatAccountId ? 'account-row--active' : '']"
                @click="selectAccount(row)"
              >
                <div class="account-row__top">
                  <strong class="truncate" :title="accountDisplayName(row)">{{ accountDisplayName(row) }}</strong>
                  <span class="account-row__time">{{ relativeTime(row.lastEventAt) }}</span>
                </div>

                <div class="account-row__sub">
                  <span class="truncate" :title="row.accountCode || ''">{{ row.accountCode || '未绑定微信号' }}</span>
                  <span>#{{ row.wechatAccountId }}</span>
                </div>

                <div class="account-row__chips">
                  <span :class="['status-chip', `status-chip--${pollTone(row.pollStatus)}`]">
                    {{ row.pollStatus || 'STOPPED' }}
                  </span>
                  <span :class="['status-chip', `status-chip--${loginTone(row.loginStatus)}`]">
                    {{ row.loginStatus || 'CREATED' }}
                  </span>
                  <span class="status-chip status-chip--neutral">总 {{ row.totalCount || 0 }}</span>
                </div>

                <div class="account-row__stats">
                  <span>入站 {{ row.inboundCount || 0 }}</span>
                  <span>出站 {{ row.outboundCount || 0 }}</span>
                </div>
              </button>
            </div>
            <n-empty v-else description="暂无账号事件数据" class="panel-empty panel-empty--fill" />

            <div class="panel-pagination">
              <n-pagination
                :page="summaryPaginationState.page"
                :page-size="summaryPaginationState.pageSize"
                :item-count="summaryPaginationState.itemCount"
                :page-sizes="[10, 20, 50]"
                show-size-picker
                @update:page="handleSummaryPageChange"
                @update:page-size="handleSummaryPageSizeChange"
              />
            </div>
          </div>
        </aside>

        <main class="events-stream">
          <div class="events-panel">
            <div class="events-panel__header events-panel__header--stack">
              <div>
                <strong>{{ selectedAccount ? accountDisplayName(selectedAccount) : '事件流' }}</strong>
                <span>
                  {{
                    selectedAccount
                      ? `微信账号 #${selectedAccount.wechatAccountId} 的事件流，支持按联系人、方向和类型排查。`
                      : '请选择账号后查看事件流。'
                  }}
                </span>
              </div>

              <div v-if="selectedAccount" class="events-panel__meta">
                <span :class="['status-chip', `status-chip--${pollTone(selectedAccount.pollStatus)}`]">
                  {{ selectedAccount.pollStatus || 'STOPPED' }}
                </span>
                <span :class="['status-chip', `status-chip--${loginTone(selectedAccount.loginStatus)}`]">
                  {{ selectedAccount.loginStatus || 'CREATED' }}
                </span>
                <span class="status-chip status-chip--neutral">
                  最近事件 {{ formatDateTime(selectedAccount.lastEventAt) }}
                </span>
              </div>
            </div>

            <div class="toolbar toolbar--filters">
              <n-input
                v-model:value="detailFilters.contactId"
                clearable
                placeholder="联系人ID"
                @keyup.enter="applyDetailSearch"
              />
              <n-input
                v-model:value="detailFilters.keyword"
                clearable
                placeholder="关键词 / 文件名 / 用户ID"
                @keyup.enter="applyDetailSearch"
              />
              <n-select
                v-model:value="detailFilters.direction"
                :options="directionOptions"
                clearable
                placeholder="方向"
              />
              <n-select
                v-model:value="detailFilters.eventType"
                :options="eventTypeOptions"
                clearable
                placeholder="事件类型"
              />
              <n-select
                v-model:value="detailFilters.hasMedia"
                :options="hasMediaOptions"
                clearable
                placeholder="媒体筛选"
              />
              <n-date-picker
                v-model:value="detailFilters.dateRange"
                type="daterange"
                clearable
                start-placeholder="开始日期"
                end-placeholder="结束日期"
              />
              <n-button v-permission="'/api/wechathlink/admin/events/list'" @click="applyDetailSearch">查询</n-button>
              <n-button v-permission="'btn:wechathlink_events:export'" :disabled="!selectedAccount" @click="exportCurrentEvents">导出当前结果</n-button>
            </div>

            <div v-if="detailRows.length" class="event-list">
              <button
                v-for="row in detailRows"
                :key="row.id"
                type="button"
                :class="['event-row', selectedEvent?.id === row.id ? 'event-row--active' : '']"
                @click="selectEvent(row)"
              >
                <div class="event-row__top">
                  <div class="event-row__chips">
                    <span :class="['status-chip', `status-chip--${directionTone(row.direction)}`]">
                      {{ directionText(row.direction) }}
                    </span>
                    <span class="status-chip status-chip--type">{{ eventTypeText(row.eventType) }}</span>
                    <span v-if="row.mediaPath" class="status-chip status-chip--media">媒体</span>
                    <span v-if="row.contextToken" class="status-chip status-chip--ready">上下文</span>
                  </div>
                  <span class="event-row__time">{{ formatDateTime(row.createTime) }}</span>
                </div>

                <div class="event-row__route" :title="eventRouteText(row)">
                  {{ eventRouteText(row) }}
                </div>

                <div class="event-row__preview">
                  {{ eventPreview(row) }}
                </div>

                <div class="event-row__footer">
                  <span>ID {{ row.id }}</span>
                  <span v-if="row.mediaMimeType">{{ row.mediaMimeType }}</span>
                  <span v-if="resolveEventPeerId(row)">{{ resolveEventPeerId(row) }}</span>
                </div>
              </button>
            </div>
            <n-empty
              v-else
              :description="detailLoading ? '正在加载事件...' : '当前条件下暂无事件'"
              class="panel-empty panel-empty--fill"
            />

            <div class="panel-pagination">
              <n-pagination
                :page="detailPaginationState.page"
                :page-size="detailPaginationState.pageSize"
                :item-count="detailPaginationState.itemCount"
                :page-sizes="[10, 20, 50]"
                show-size-picker
                @update:page="handleDetailPageChange"
                @update:page-size="handleDetailPageSizeChange"
              />
            </div>
          </div>
        </main>
        <aside class="events-detail">
          <div class="events-panel">
            <div class="events-panel__header">
              <div>
                <strong>事件详情</strong>
                <span>{{ selectedEvent ? `事件 #${selectedEvent.id}` : '从事件流中选择一条记录查看详情。' }}</span>
              </div>

              <div v-if="selectedEvent?.mediaPath" class="events-panel__actions">
                <n-button v-permission="'btn:wechathlink_events:media'" size="small" tertiary @click="openPreview(selectedEvent)">预览媒体</n-button>
              </div>
            </div>

            <template v-if="selectedEvent">
              <n-tabs v-model:value="detailTab" type="line" animated>
                <n-tab-pane name="detail" tab="详情" />
                <n-tab-pane name="media" tab="媒体" />
                <n-tab-pane name="json" tab="原始JSON" />
              </n-tabs>

              <div class="detail-scroll">
                <div v-if="detailTab === 'detail'" class="detail-stack">
                  <section class="detail-card detail-card--grid">
                    <div class="detail-field">
                      <span class="detail-field__label">事件ID</span>
                      <strong>{{ selectedEvent.id }}</strong>
                    </div>
                    <div class="detail-field">
                      <span class="detail-field__label">发生时间</span>
                      <strong>{{ formatDateTime(selectedEvent.createTime) }}</strong>
                    </div>
                    <div class="detail-field">
                      <span class="detail-field__label">方向</span>
                      <strong>{{ directionText(selectedEvent.direction) }}</strong>
                    </div>
                    <div class="detail-field">
                      <span class="detail-field__label">类型</span>
                      <strong>{{ eventTypeText(selectedEvent.eventType) }}</strong>
                    </div>
                    <div class="detail-field">
                      <span class="detail-field__label">发起方</span>
                      <strong class="detail-field__value detail-field__value--wrap">{{ selectedEvent.fromUserId || '-' }}</strong>
                    </div>
                    <div class="detail-field">
                      <span class="detail-field__label">接收方</span>
                      <strong class="detail-field__value detail-field__value--wrap">{{ selectedEvent.toUserId || '-' }}</strong>
                    </div>
                    <div class="detail-field">
                      <span class="detail-field__label">关联联系人</span>
                      <strong class="detail-field__value detail-field__value--wrap">{{ selectedEventContactId || '-' }}</strong>
                    </div>
                    <div class="detail-field">
                      <span class="detail-field__label">消息ID</span>
                      <strong>{{ selectedEvent.messageId || '-' }}</strong>
                    </div>
                    <div class="detail-field detail-field--full">
                      <span class="detail-field__label">上下文 Token</span>
                      <strong class="detail-field__value detail-field__value--wrap" :title="selectedEvent.contextToken || ''">
                        {{ selectedEvent.contextToken || '无上下文 Token' }}
                      </strong>
                    </div>
                  </section>

                  <section class="detail-card">
                    <div class="detail-card__title">消息内容</div>
                    <div v-if="selectedEvent.bodyText" class="detail-text">{{ selectedEvent.bodyText }}</div>
                    <div v-else class="detail-empty">当前事件没有文本内容。</div>
                  </section>
                </div>

                <div v-else-if="detailTab === 'media'" class="detail-stack">
                  <section class="detail-card">
                    <div class="detail-card__title">媒体信息</div>

                    <div class="detail-meta-grid">
                      <div class="detail-field">
                        <span class="detail-field__label">文件名</span>
                        <strong class="detail-field__value detail-field__value--wrap">{{ selectedEvent.mediaFileName || '-' }}</strong>
                      </div>
                      <div class="detail-field">
                        <span class="detail-field__label">MIME</span>
                        <strong class="detail-field__value detail-field__value--wrap">{{ selectedEvent.mediaMimeType || '-' }}</strong>
                      </div>
                      <div class="detail-field detail-field--full">
                        <span class="detail-field__label">媒体路径</span>
                        <strong class="detail-field__value detail-field__value--wrap">{{ selectedEvent.mediaPath || '-' }}</strong>
                      </div>
                    </div>

                    <div v-if="selectedEvent.mediaPath" class="detail-media">
                      <img
                        v-if="selectedEvent.eventType === 'image'"
                        :src="eventMediaUrl(selectedEvent)"
                        alt="event-media"
                        class="detail-media__image"
                        @click="openPreview(selectedEvent)"
                      />
                      <audio
                        v-else-if="selectedEvent.eventType === 'voice'"
                        :src="eventMediaUrl(selectedEvent)"
                        controls
                        class="detail-media__audio"
                      />
                      <video
                        v-else-if="selectedEvent.eventType === 'video'"
                        :src="eventMediaUrl(selectedEvent)"
                        controls
                        class="detail-media__video"
                      />
                      <div v-else class="detail-media__file">{{ selectedEvent.mediaFileName || 'media-file' }}</div>

                      <div class="detail-media__actions">
                        <n-button v-permission="'btn:wechathlink_events:media'" size="small" tertiary @click="openPreview(selectedEvent)">查看</n-button>
                        <a
                          :href="eventMediaUrl(selectedEvent)"
                          target="_blank"
                          rel="noreferrer"
                          class="detail-media__link"
                        >
                          新窗口
                        </a>
                      </div>
                    </div>
                    <div v-else class="detail-empty">当前事件没有媒体附件。</div>
                  </section>
                </div>

                <div v-else class="detail-stack">
                  <section class="detail-card detail-card--json">
                    <pre>{{ formattedRawJson }}</pre>
                  </section>
                </div>

                <section class="detail-card">
                  <div class="detail-card__head">
                    <div>
                      <div class="detail-card__title">联系人上下文</div>
                      <div class="detail-card__desc">同账号联系人集合，可直接过滤到某个联系人事件。</div>
                    </div>

                    <div class="toolbar toolbar--compact">
                      <n-input
                        v-model:value="contactsKeyword"
                        clearable
                        placeholder="搜索联系人ID"
                        @keyup.enter="applyContactsSearch"
                      />
                      <n-button v-permission="'/api/wechathlink/admin/events/contacts'" @click="applyContactsSearch">查询</n-button>
                    </div>
                  </div>

                  <div v-if="contactRows.length" class="contact-rail">
                    <button
                      v-for="item in contactRows"
                      :key="item.contactId"
                      type="button"
                      :class="[
                        'contact-rail__item',
                        detailFilters.contactId === item.contactId || selectedEventContactId === item.contactId
                          ? 'contact-rail__item--highlight'
                          : ''
                      ]"
                      @click="filterByContact(item.contactId)"
                    >
                      <div class="contact-rail__top">
                        <strong class="truncate" :title="item.contactId">{{ item.contactId }}</strong>
                        <span>{{ relativeTime(item.lastSeenAt) }}</span>
                      </div>

                      <div class="contact-rail__preview">{{ item.lastMessagePreview || '暂无摘要' }}</div>

                      <div class="contact-rail__chips">
                        <span class="status-chip status-chip--neutral">{{ directionText(item.lastDirection) }}</span>
                        <span :class="['status-chip', item.hasContextToken ? 'status-chip--ready' : 'status-chip--muted']">
                          {{ item.hasContextToken ? '可回复' : '无上下文' }}
                        </span>
                        <span class="status-chip status-chip--neutral">总 {{ item.totalCount || 0 }}</span>
                      </div>
                    </button>
                  </div>
                  <n-empty
                    v-else
                    :description="contactsLoading ? '正在加载联系人上下文...' : '暂无联系人上下文'"
                    class="panel-empty"
                  />

                  <div class="panel-pagination panel-pagination--compact">
                    <n-pagination
                      :page="contactsPaginationState.page"
                      :page-size="contactsPaginationState.pageSize"
                      :item-count="contactsPaginationState.itemCount"
                      simple
                      @update:page="handleContactsPageChange"
                    />
                  </div>
                </section>
              </div>
            </template>
            <n-empty v-else description="请选择一条事件查看详情" class="panel-empty panel-empty--fill" />
          </div>
        </aside>
      </section>
    </section>

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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api from '../../api.js'
import { ensureArray } from '../../utils/http'
import ModalFrame from '../../components/ModalFrame.vue'

const EVENT_FILTER_STORAGE_KEY = 'wechathlink:events:filters'

const summaryKeyword = ref('')
const summaryRows = ref([])
const summaryLoading = ref(false)
const summaryPaginationState = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})
let summaryRequestSeq = 0

const selectedAccountId = ref(null)

const detailRows = ref([])
const detailLoading = ref(false)
const detailPaginationState = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})
const detailFilters = reactive({
  contactId: '',
  keyword: '',
  direction: null,
  eventType: null,
  hasMedia: null,
  dateRange: null
})
const selectedEvent = ref(null)
const detailTab = ref('detail')
let detailRequestSeq = 0

const contactsKeyword = ref('')
const contactRows = ref([])
const contactsLoading = ref(false)
const contactsPaginationState = reactive({
  page: 1,
  pageSize: 8,
  itemCount: 0
})
let contactsRequestSeq = 0

const previewModalVisible = ref(false)
const previewEvent = ref(null)

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

const hasMediaOptions = [
  { label: '仅含媒体', value: 1 },
  { label: '仅无媒体', value: 0 }
]

const selectedAccount = computed(() => summaryRows.value.find((item) => item.wechatAccountId === selectedAccountId.value) || null)
const runningAccountCount = computed(() => summaryRows.value.filter((item) => `${item.pollStatus || ''}`.toUpperCase() === 'RUNNING').length)
const abnormalAccountCount = computed(() => summaryRows.value.filter((item) => isAbnormalAccount(item)).length)
const visibleEventCount = computed(() => summaryRows.value.reduce((total, item) => total + Number(item.totalCount || 0), 0))
const latestVisibleEventAt = computed(() => {
  const timestamps = summaryRows.value.map((item) => toTimestamp(item.lastEventAt)).filter((value) => value > 0)
  if (!timestamps.length) {
    return ''
  }
  return new Date(Math.max(...timestamps)).toISOString()
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

const selectedEventContactId = computed(() => resolveEventPeerId(selectedEvent.value))
const formattedRawJson = computed(() => {
  const rawJson = selectedEvent.value?.rawJson
  if (!rawJson) {
    return '当前事件没有原始 JSON'
  }
  try {
    return JSON.stringify(JSON.parse(rawJson), null, 2)
  } catch (error) {
    return rawJson
  }
})

async function loadSummary() {
  const requestSeq = ++summaryRequestSeq
  summaryLoading.value = true
  try {
    const payload = await api.listEventAccountSummaries({
      keyword: summaryKeyword.value,
      pageNum: summaryPaginationState.page,
      pageSize: summaryPaginationState.pageSize
    })
    if (requestSeq !== summaryRequestSeq) {
      return
    }
    summaryRows.value = ensureArray(payload?.list)
    summaryPaginationState.itemCount = Number(payload?.total || 0)
    if (!summaryRows.value.length) {
      clearSelection()
      return
    }
    const matched = summaryRows.value.find((item) => item.wechatAccountId === selectedAccountId.value)
    await selectAccount(matched || summaryRows.value[0], { silentReset: true })
  } finally {
    if (requestSeq === summaryRequestSeq) {
      summaryLoading.value = false
    }
  }
}

async function loadDetailEvents() {
  if (!selectedAccount.value?.wechatAccountId) {
    detailRows.value = []
    detailPaginationState.itemCount = 0
    selectedEvent.value = null
    return
  }
  const requestSeq = ++detailRequestSeq
  detailLoading.value = true
  try {
    const payload = await api.listEvents({
      wechatAccountId: selectedAccount.value.wechatAccountId,
      contactId: detailFilters.contactId,
      keyword: detailFilters.keyword,
      direction: detailFilters.direction,
      eventType: detailFilters.eventType,
      hasMedia: detailFilters.hasMedia,
      dateFrom: formatDateParam(detailFilters.dateRange?.[0]),
      dateTo: formatDateParam(detailFilters.dateRange?.[1]),
      pageNum: detailPaginationState.page,
      pageSize: detailPaginationState.pageSize
    })
    if (requestSeq !== detailRequestSeq) {
      return
    }
    detailRows.value = ensureArray(payload?.list)
    detailPaginationState.itemCount = Number(payload?.total || 0)
    if (!detailRows.value.length) {
      selectedEvent.value = null
      return
    }
    const matched = detailRows.value.find((item) => item.id === selectedEvent.value?.id)
    selectedEvent.value = matched || detailRows.value[0]
  } finally {
    if (requestSeq === detailRequestSeq) {
      detailLoading.value = false
    }
  }
}

async function loadContacts() {
  if (!selectedAccount.value?.wechatAccountId) {
    contactRows.value = []
    contactsPaginationState.itemCount = 0
    return
  }
  const requestSeq = ++contactsRequestSeq
  contactsLoading.value = true
  try {
    const payload = await api.listEventContacts({
      wechatAccountId: selectedAccount.value.wechatAccountId,
      keyword: contactsKeyword.value,
      pageNum: contactsPaginationState.page,
      pageSize: contactsPaginationState.pageSize
    })
    if (requestSeq !== contactsRequestSeq) {
      return
    }
    contactRows.value = ensureArray(payload?.list)
    contactsPaginationState.itemCount = Number(payload?.total || 0)
  } finally {
    if (requestSeq === contactsRequestSeq) {
      contactsLoading.value = false
    }
  }
}

async function refreshWorkspace() {
  persistEventFilterState()
  await loadSummary()
}

async function selectAccount(row, options = {}) {
  if (!row?.wechatAccountId) {
    return
  }
  const changed = selectedAccountId.value !== row.wechatAccountId
  selectedAccountId.value = row.wechatAccountId
  if (changed && !options.silentReset) {
    detailFilters.contactId = ''
    detailFilters.keyword = ''
    contactsKeyword.value = ''
    detailTab.value = 'detail'
    detailFilters.hasMedia = null
    detailFilters.dateRange = null
    detailFilters.direction = null
    detailFilters.eventType = null
    detailPaginationState.page = 1
    contactsPaginationState.page = 1
  }
  persistEventFilterState()
  await Promise.all([loadDetailEvents(), loadContacts()])
}

function selectEvent(row) {
  selectedEvent.value = row
  detailTab.value = 'detail'
}

function clearSelection() {
  selectedAccountId.value = null
  detailRows.value = []
  contactRows.value = []
  selectedEvent.value = null
  summaryPaginationState.itemCount = 0
  detailPaginationState.itemCount = 0
  contactsPaginationState.itemCount = 0
}
async function applySummarySearch() {
  summaryPaginationState.page = 1
  await loadSummary()
}

async function applyDetailSearch() {
  detailPaginationState.page = 1
  persistEventFilterState()
  await loadDetailEvents()
}

async function applyContactsSearch() {
  contactsPaginationState.page = 1
  await loadContacts()
}

async function clearEventFilters() {
  summaryKeyword.value = ''
  detailFilters.contactId = ''
  detailFilters.keyword = ''
  detailFilters.direction = null
  detailFilters.eventType = null
  detailFilters.hasMedia = null
  detailFilters.dateRange = null
  contactsKeyword.value = ''
  summaryPaginationState.page = 1
  detailPaginationState.page = 1
  contactsPaginationState.page = 1
  persistEventFilterState()
  await loadSummary()
}

async function filterByContact(contactId) {
  detailFilters.contactId = contactId || ''
  detailPaginationState.page = 1
  persistEventFilterState()
  await loadDetailEvents()
}

function exportCurrentEvents() {
  if (!selectedAccount.value?.wechatAccountId) {
    return
  }
  persistEventFilterState()
  const url = api.getEventExportUrl({
    wechatAccountId: selectedAccount.value.wechatAccountId,
    contactId: detailFilters.contactId,
    keyword: detailFilters.keyword,
    direction: detailFilters.direction,
    eventType: detailFilters.eventType,
    hasMedia: detailFilters.hasMedia,
    dateFrom: formatDateParam(detailFilters.dateRange?.[0]),
    dateTo: formatDateParam(detailFilters.dateRange?.[1])
  })
  window.open(url, '_blank', 'noopener,noreferrer')
}

function openPreview(row) {
  previewEvent.value = row
  previewModalVisible.value = true
}

function eventMediaUrl(row) {
  if (!row?.id || !row?.mediaPath) {
    return ''
  }
  return api.getEventMediaUrl(row.id)
}

function handleSummaryPageChange(page) {
  summaryPaginationState.page = page
  loadSummary()
}

function handleSummaryPageSizeChange(pageSize) {
  summaryPaginationState.pageSize = pageSize
  summaryPaginationState.page = 1
  loadSummary()
}

function handleDetailPageChange(page) {
  detailPaginationState.page = page
  loadDetailEvents()
}

function handleDetailPageSizeChange(pageSize) {
  detailPaginationState.pageSize = pageSize
  detailPaginationState.page = 1
  loadDetailEvents()
}

function handleContactsPageChange(page) {
  contactsPaginationState.page = page
  loadContacts()
}

function accountDisplayName(row) {
  if (!row) {
    return '-'
  }
  return row.accountName || row.accountCode || `账号 #${row.wechatAccountId}`
}

function isAbnormalAccount(row) {
  const pollStatus = `${row?.pollStatus || ''}`.toUpperCase()
  const loginStatus = `${row?.loginStatus || ''}`.toUpperCase()
  return pollStatus === 'ERROR' || loginStatus === 'ERROR'
}

function pollTone(value) {
  const normalized = `${value || ''}`.trim().toUpperCase()
  if (normalized === 'RUNNING') {
    return 'success'
  }
  if (normalized === 'ERROR') {
    return 'danger'
  }
  return 'warning'
}

function loginTone(value) {
  const normalized = `${value || ''}`.trim().toUpperCase()
  if (normalized === 'CONFIRMED' || normalized === 'LOGGED_IN') {
    return 'success'
  }
  if (normalized === 'ERROR' || normalized === 'FAILED') {
    return 'danger'
  }
  return 'neutral'
}

function directionTone(value) {
  return `${value || ''}`.trim().toLowerCase() === 'outbound' ? 'info' : 'success'
}

function directionText(value) {
  const map = {
    inbound: '入站',
    outbound: '出站'
  }
  return map[value] || value || '-'
}

function eventTypeText(value) {
  const map = {
    text: '文本',
    image: '图片',
    file: '文件',
    video: '视频',
    voice: '语音'
  }
  return map[value] || value || '-'
}

function eventRouteText(row) {
  const from = row?.fromUserId || '-'
  const to = row?.toUserId || '-'
  return `${from} -> ${to}`
}

function eventPreview(row) {
  if (!row) {
    return '-'
  }
  if (row.bodyText) {
    return row.bodyText
  }
  if (row.mediaFileName) {
    return row.mediaFileName
  }
  return `${eventTypeText(row.eventType)}事件`
}

function resolveEventPeerId(row) {
  if (!row) {
    return ''
  }
  const accountCode = `${selectedAccount.value?.accountCode || ''}`.trim()
  const from = `${row.fromUserId || ''}`.trim()
  const to = `${row.toUserId || ''}`.trim()
  if (accountCode) {
    if (from && from !== accountCode && to === accountCode) {
      return from
    }
    if (to && to !== accountCode && from === accountCode) {
      return to
    }
  }
  if (`${row.direction || ''}`.trim().toLowerCase() === 'outbound') {
    return to || from
  }
  return from || to
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

function relativeTime(value) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const diff = Date.now() - date.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < hour) {
    return `${Math.max(1, Math.floor(diff / minute))} 分钟前`
  }
  if (diff < day) {
    return `${Math.max(1, Math.floor(diff / hour))} 小时前`
  }
  if (diff < 7 * day) {
    return `${Math.max(1, Math.floor(diff / day))} 天前`
  }
  return formatDateTime(value)
}

function toTimestamp(value) {
  if (!value) {
    return 0
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

function formatDateParam(value) {
  if (!value) {
    return ''
  }
  const date = new Date(Number(value))
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

function persistEventFilterState() {
  if (typeof window === 'undefined' || !window.localStorage) {
    return
  }
  window.localStorage.setItem(EVENT_FILTER_STORAGE_KEY, JSON.stringify({
    selectedAccountId: selectedAccountId.value,
    summaryKeyword: summaryKeyword.value,
    detailFilters: {
      contactId: detailFilters.contactId,
      keyword: detailFilters.keyword,
      direction: detailFilters.direction,
      eventType: detailFilters.eventType,
      hasMedia: detailFilters.hasMedia,
      dateRange: Array.isArray(detailFilters.dateRange) ? detailFilters.dateRange : null
    }
  }))
}

function restoreEventFilterState() {
  if (typeof window === 'undefined' || !window.localStorage) {
    return
  }
  const raw = window.localStorage.getItem(EVENT_FILTER_STORAGE_KEY)
  if (!raw) {
    return
  }
  try {
    const parsed = JSON.parse(raw)
    selectedAccountId.value = parsed?.selectedAccountId || null
    summaryKeyword.value = parsed?.summaryKeyword || ''
    detailFilters.contactId = parsed?.detailFilters?.contactId || ''
    detailFilters.keyword = parsed?.detailFilters?.keyword || ''
    detailFilters.direction = parsed?.detailFilters?.direction || null
    detailFilters.eventType = parsed?.detailFilters?.eventType || null
    detailFilters.hasMedia = parsed?.detailFilters?.hasMedia ?? null
    detailFilters.dateRange = Array.isArray(parsed?.detailFilters?.dateRange) ? parsed.detailFilters.dateRange : null
  } catch (error) {
  }
}

onMounted(() => {
  restoreEventFilterState()
  loadSummary()
})
</script>

<style scoped>
.page--events {
  height: calc(100vh - 112px);
  overflow: hidden;
}

.events-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.events-overview {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) auto;
  gap: 16px;
  padding: 18px 20px;
  border-radius: 22px;
  background:
    radial-gradient(circle at top left, rgba(15, 118, 110, 0.14), transparent 34%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.96));
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
}

.events-overview__intro {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.events-overview__kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #0f766e;
  text-transform: uppercase;
}

.events-overview__intro strong {
  font-size: 28px;
  line-height: 1.2;
  color: #0f172a;
}

.events-overview__intro span {
  color: #475569;
  font-size: 14px;
}

.events-overview__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  align-items: flex-start;
}

.events-kpis {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.events-kpi {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.events-kpi__label {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.events-kpi strong {
  font-size: 28px;
  line-height: 1.1;
  color: #0f172a;
}

.events-kpi small {
  color: #64748b;
  font-size: 12px;
}

.events-shell {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr) 380px;
  gap: 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.events-sidebar,
.events-stream,
.events-detail {
  min-width: 0;
  min-height: 0;
}

.events-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  min-height: 0;
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
}

.events-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.events-panel__header--stack {
  flex-direction: column;
}

.events-panel__header strong {
  display: block;
  color: #0f172a;
  font-size: 18px;
}

.events-panel__header span {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.events-panel__meta,
.events-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.toolbar--compact {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
}

.toolbar--filters {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1.4fr) 140px 160px 140px minmax(0, 1.2fr) auto auto;
  gap: 10px;
}

.account-list,
.event-list,
.detail-scroll,
.contact-rail {
  min-height: 0;
  overflow: auto;
  padding-right: 6px;
}

.account-list,
.event-list,
.contact-rail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-scroll {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-empty {
  border-radius: 18px;
  border: 1px dashed rgba(148, 163, 184, 0.22);
  background: rgba(248, 250, 252, 0.86);
}

.panel-empty--fill {
  flex: 1;
}

.panel-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.panel-pagination--compact {
  padding-top: 10px;
}

.truncate {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-row,
.event-row,
.contact-rail__item {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 16px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: transform 120ms ease, box-shadow 120ms ease, border-color 120ms ease, background 120ms ease;
}

.account-row:hover,
.event-row:hover,
.contact-rail__item:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}
.account-row {
  padding: 14px;
}

.account-row--active {
  border-color: rgba(15, 118, 110, 0.36);
  background: linear-gradient(180deg, #f0fdfa 0%, #ffffff 100%);
}

.account-row__top,
.account-row__sub,
.account-row__stats,
.event-row__top,
.event-row__footer,
.contact-rail__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.account-row__top strong,
.contact-rail__top strong {
  color: #0f172a;
}

.account-row__time,
.account-row__sub,
.account-row__stats,
.event-row__footer,
.contact-rail__top span {
  color: #64748b;
  font-size: 12px;
}

.account-row__sub,
.account-row__stats {
  margin-top: 8px;
}

.account-row__chips,
.event-row__chips,
.contact-rail__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-chip--success {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

.status-chip--info {
  background: rgba(14, 116, 144, 0.12);
  color: #0e7490;
}

.status-chip--warning {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

.status-chip--danger {
  background: rgba(220, 38, 38, 0.12);
  color: #b91c1c;
}

.status-chip--neutral {
  background: rgba(100, 116, 139, 0.12);
  color: #475569;
}

.status-chip--type {
  background: rgba(79, 70, 229, 0.1);
  color: #4338ca;
}

.status-chip--media {
  background: rgba(168, 85, 247, 0.12);
  color: #7e22ce;
}

.status-chip--ready {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

.status-chip--muted {
  background: rgba(148, 163, 184, 0.18);
  color: #475569;
}

.event-row {
  padding: 14px 16px;
}

.event-row--active {
  border-color: rgba(14, 116, 144, 0.34);
  background: linear-gradient(180deg, #f0f9ff 0%, #ffffff 100%);
}

.event-row__route {
  margin-top: 10px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-row__preview {
  margin-top: 8px;
  color: #334155;
  font-size: 13px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.event-row__time {
  color: #64748b;
  font-size: 12px;
  flex-shrink: 0;
}

.event-row__footer {
  margin-top: 10px;
  flex-wrap: wrap;
}

.detail-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-card {
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.detail-card--grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-card--json {
  padding: 0;
  overflow: hidden;
}

.detail-card__title {
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.detail-card__desc {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.detail-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.detail-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-field--full {
  grid-column: 1 / -1;
}
.detail-field__label {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.detail-field__value {
  color: #0f172a;
}

.detail-field__value--wrap {
  word-break: break-all;
}

.detail-text {
  margin-top: 10px;
  color: #0f172a;
  line-height: 1.75;
  white-space: pre-wrap;
}

.detail-empty {
  margin-top: 10px;
  color: #64748b;
  font-size: 13px;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.detail-media {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 14px;
}

.detail-media__image,
.detail-media__video {
  max-width: 100%;
  max-height: 260px;
  border-radius: 14px;
  background: #00000008;
}

.detail-media__image {
  cursor: pointer;
}

.detail-media__audio {
  width: 100%;
}

.detail-media__file {
  font-weight: 700;
  color: #0f172a;
}

.detail-media__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-media__link {
  color: #0f766e;
  text-decoration: none;
  font-size: 13px;
  font-weight: 700;
}

.detail-card--json pre {
  margin: 0;
  padding: 16px;
  max-height: 320px;
  overflow: auto;
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.65;
}

.contact-rail__item {
  padding: 12px 14px;
}

.contact-rail__item--highlight {
  border-color: rgba(15, 118, 110, 0.34);
  background: linear-gradient(180deg, #f0fdfa 0%, #ffffff 100%);
}

.contact-rail__preview {
  margin-top: 8px;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

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

@media (max-width: 1440px) {
  .events-shell {
    grid-template-columns: 300px minmax(0, 1fr) 340px;
  }
}

@media (max-width: 1200px) {
  .events-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .events-shell {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .events-detail {
    grid-column: 1 / -1;
  }
}

@media (max-width: 900px) {
  .page--events {
    height: auto;
    overflow: visible;
  }

  .events-layout,
  .events-shell {
    height: auto;
    overflow: visible;
  }

  .events-overview {
    grid-template-columns: 1fr;
  }

  .events-overview__actions {
    justify-content: flex-start;
  }

  .events-kpis,
  .events-shell,
  .detail-card--grid,
  .detail-meta-grid,
  .toolbar--filters,
  .toolbar--compact {
    grid-template-columns: 1fr;
  }

  .events-panel {
    height: auto;
  }

  .account-list,
  .event-list,
  .detail-scroll,
  .contact-rail {
    max-height: none;
  }

  .detail-card__head {
    flex-direction: column;
  }
}
</style>
