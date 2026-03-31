<template>
  <div class="page page--messages">
    <section class="chat-shell">
      <aside class="chat-sidebar">
        <div class="chat-sidebar__panel">
          <div class="sidebar-stack">
            <div class="sidebar-block">
              <div class="sidebar-block__label-row">
                <label class="field-label">微信账号</label>
                <n-button v-permission="'/api/wechathlink/admin/messages/peers'" size="small" tertiary @click="refreshWorkspace">刷新</n-button>
              </div>
              <n-select
                v-model:value="selectedAccountId"
                :options="accountOptions"
                filterable
                clearable
                placeholder="请选择微信账号"
              />
            </div>

            <div class="sidebar-block">
              <label class="field-label">联系人搜索</label>
              <div class="contact-search">
                <n-input v-model:value="contactKeyword" placeholder="输入联系人ID" />
                <n-button v-permission="'/api/wechathlink/admin/messages/peers'" @click="applyContactSearch">查询</n-button>
              </div>
            </div>

            <div v-if="selectedAccount" class="account-brief">
              <div class="account-brief__title">
                <strong>{{ selectedAccount.accountName || selectedAccount.accountCode }}</strong>
                <span>#{{ selectedAccount.id }}</span>
              </div>
              <div class="account-brief__meta">
                <n-tag size="small" :bordered="false" type="info">{{ selectedAccount.loginStatus || 'CREATED' }}</n-tag>
                <n-tag size="small" :bordered="false" :type="selectedAccount.pollStatus === 'RUNNING' ? 'success' : 'warning'">
                  {{ selectedAccount.pollStatus || 'STOPPED' }}
                </n-tag>
              </div>
            </div>

            <div
              v-if="groupedContacts.length || contactLoading"
              ref="contactListRef"
              class="contact-list"
              @scroll.passive="handleContactListScroll"
            >
              <section
                v-for="group in groupedContacts"
                :key="group.key"
                class="contact-group"
              >
                <div class="contact-group__title">
                  <span>{{ group.label }}</span>
                  <span>{{ group.items.length }}</span>
                </div>

                <button
                  v-for="item in group.items"
                  :key="item.contactId"
                  type="button"
                  :class="[
                    'contact-item',
                    selectedContactId === item.contactId ? 'contact-item--active' : '',
                    item.isUnread ? 'contact-item--unread' : '',
                    item.hasRecentReplyFailure ? 'contact-item--failed' : '',
                    item.hasContextToken ? 'contact-item--replyable' : 'contact-item--blocked'
                  ]"
                  @click="selectContact(item)"
                >
                  <div class="contact-item__main">
                    <div class="contact-item__avatar-wrap">
                      <div class="contact-item__avatar">
                        {{ contactInitial(item.contactId) }}
                      </div>
                      <span v-if="item.isUnread" class="contact-item__avatar-dot"></span>
                    </div>

                    <div class="contact-item__body">
                      <div class="contact-item__top">
                        <strong class="contact-item__name" :title="item.contactId">{{ item.contactId }}</strong>
                        <span>{{ relativeTime(item.lastSeenAt) }}</span>
                      </div>

                      <div class="contact-item__status-row">
                        <span v-if="item.isUnread" class="contact-pill contact-pill--unread">未读</span>
                        <span
                          v-if="item.hasRecentReplyFailure"
                          class="contact-pill contact-pill--failed"
                          :title="item.lastReplyFailureMessage || '最近一次回复失败'"
                        >
                          回复失败
                        </span>
                        <span :class="['contact-pill', item.hasContextToken ? 'contact-pill--ready' : 'contact-pill--muted']">
                          {{ item.hasContextToken ? '可回复' : '需等待新消息' }}
                        </span>
                      </div>

                      <div class="contact-item__preview">{{ item.lastMessagePreview || '暂无摘要' }}</div>

                      <div class="contact-item__meta">
                        <span>{{ directionText(item.lastDirection) }}</span>
                        <span>{{ activityText(item.lastSeenAt) }}</span>
                        <span>总 {{ item.totalCount || 0 }}</span>
                      </div>
                    </div>
                  </div>
                </button>
              </section>

              <div class="contact-list__status">
                <span v-if="contactLoading">正在加载联系人...</span>
                <span v-else-if="contactHasMore">向下滚动加载更多联系人</span>
                <span v-else>已显示全部联系人</span>
              </div>
            </div>
            <div v-else class="contact-list contact-list--empty">
              <n-empty description="当前账号暂无联系人" />
            </div>
          </div>
        </div>
      </aside>

      <section class="chat-main">
        <div class="chat-main__panel">
          <div v-if="selectedAccount && selectedContact" class="chat-main__header">
            <div>
              <div class="chat-main__title">
                <strong>{{ selectedContact.contactId }}</strong>
                <n-tag size="small" :bordered="false" :type="selectedContact.hasContextToken ? 'success' : 'warning'">
                  {{ selectedContact.hasContextToken ? '可回复' : '上下文不足' }}
                </n-tag>
              </div>
              <div class="chat-main__meta">
                <span>账号：{{ selectedAccount.accountName || selectedAccount.accountCode }}</span>
                <span>最近活跃：{{ formatDateTime(selectedContact.lastSeenAt) }}</span>
                <span>联系频次：{{ selectedContact.totalCount || 0 }}</span>
              </div>
            </div>

            <div class="inline-actions">
              <n-select
                v-model:value="conversationEventType"
                :options="conversationEventTypeOptions"
                clearable
                placeholder="消息类型"
                style="width: 180px;"
              />
              <n-button v-permission="'/api/wechathlink/admin/events/list'" @click="applyConversationFilter">筛选</n-button>
            </div>
          </div>

          <div
            v-if="conversationRows.length"
            ref="conversationViewportRef"
            class="chat-main__messages"
            @scroll.passive="handleConversationScroll"
          >
            <div class="conversation-status">
              <span v-if="conversationLoading">正在加载历史消息...</span>
              <span v-else-if="conversationHasMore">向上滚动加载更多历史消息</span>
              <span v-else>已显示全部消息</span>
            </div>
            <article
              v-for="row in conversationRows"
              :key="row.id"
              :class="['message-bubble', row.direction === 'outbound' ? 'message-bubble--outbound' : 'message-bubble--inbound']"
            >
              <div class="message-bubble__meta">
                <span :class="['message-bubble__badge', row.direction === 'outbound' ? 'message-bubble__badge--outbound' : 'message-bubble__badge--inbound']">
                  {{ directionText(row.direction) }}
                </span>
                <span>{{ eventTypeText(row.eventType) }}</span>
                <span>{{ formatDateTime(row.createTime) }}</span>
              </div>

              <div class="message-bubble__content">
                <div v-if="row.bodyText" class="message-bubble__text">{{ row.bodyText }}</div>

                <div v-if="row.mediaPath" class="message-bubble__media">
                  <img
                    v-if="row.eventType === 'image'"
                    :src="eventMediaUrl(row)"
                    alt="message-image"
                    class="message-bubble__image"
                    @click="openPreview(row)"
                  />
                  <audio
                    v-else-if="row.eventType === 'voice'"
                    :src="eventMediaUrl(row)"
                    controls
                    preload="metadata"
                    class="message-bubble__audio"
                  />
                  <video
                    v-else-if="row.eventType === 'video'"
                    :src="eventMediaUrl(row)"
                    controls
                    preload="metadata"
                    class="message-bubble__video"
                  />
                  <div v-else class="message-bubble__file">{{ row.mediaFileName || 'media-file' }}</div>

                  <div class="message-bubble__actions">
                    <n-button v-permission="'btn:wechathlink_messages:media'" size="small" tertiary @click="openPreview(row)">查看</n-button>
                    <a :href="eventMediaUrl(row)" target="_blank" rel="noreferrer" class="message-bubble__link">新窗口</a>
                  </div>
                </div>
              </div>
            </article>
          </div>
          <n-empty v-else description="请选择联系人并加载会话" class="chat-main__empty" />

          <footer class="chat-composer">
            <div v-if="selectedContact" class="composer-target">
              <strong>{{ selectedContact.contactId }}</strong>
              <span>{{ selectedContact.lastMessagePreview || '已选择联系人' }}</span>
            </div>
            <n-alert v-if="selectedContact && !selectedContact.hasContextToken" type="warning" :bordered="false">
              当前联系人没有可用上下文，暂时不能直接回复。请先等待该联系人发送新消息。
            </n-alert>

            <div class="chat-composer__tabs">
              <n-tabs v-model:value="composeTab" type="line" animated>
                <n-tab-pane name="text" tab="文本消息" />
                <n-tab-pane name="media" tab="媒体消息" />
              </n-tabs>
            </div>

            <div v-if="composeTab === 'text'" class="chat-composer__body">
              <div class="phrase-list">
                <button
                  v-for="phrase in commonPhrases"
                  :key="phrase"
                  type="button"
                  class="phrase-chip"
                  @click="appendPhrase(phrase)"
                >
                  {{ phrase }}
                </button>
              </div>

              <n-input
                v-model:value="textDraft"
                type="textarea"
                placeholder="请输入要发送的文本内容"
                :autosize="{ minRows: 5, maxRows: 10 }"
              />

              <div class="chat-composer__footer">
                <span>{{ textDraft.length }} 字</span>
                <n-button
                  v-permission="'btn:wechathlink_messages:sendtext'"
                  type="primary"
                  :loading="sendingText"
                  :disabled="!canSendText"
                  @click="sendText"
                >
                  发送文本
                </n-button>
              </div>
            </div>

            <div v-else class="chat-composer__body">
              <div class="inline-actions">
                <n-select v-model:value="mediaDraft.type" :options="mediaTypeOptions" style="width: 160px;" />
                <input
                  ref="fileInputRef"
                  type="file"
                  class="visually-hidden"
                  @change="handleFileChange"
                />
                <n-button v-permission="'btn:wechathlink_messages:upload'" @click="triggerFilePicker" :loading="uploadingMedia">选择文件</n-button>
              </div>

              <div v-if="uploadedMedia" class="upload-card">
                <div class="upload-card__meta">
                  <strong>{{ uploadedMedia.fileName }}</strong>
                  <span>{{ uploadedMedia.mimeType }}</span>
                  <span>{{ formatFileSize(uploadedMedia.size) }}</span>
                </div>
                <div v-if="uploadPreviewUrl" class="upload-card__preview">
                  <img v-if="uploadPreviewKind === 'image'" :src="uploadPreviewUrl" alt="upload-preview" class="upload-card__image" />
                  <audio v-else-if="uploadPreviewKind === 'voice'" :src="uploadPreviewUrl" controls class="upload-card__audio" />
                  <video v-else-if="uploadPreviewKind === 'video'" :src="uploadPreviewUrl" controls class="upload-card__video" />
                </div>
                <div class="inline-actions">
                  <n-button v-permission="'btn:wechathlink_messages:upload'" size="small" tertiary @click="triggerFilePicker">重新选择</n-button>
                  <n-button v-permission="'btn:wechathlink_messages:upload'" size="small" quaternary @click="clearUploadedMedia">清除</n-button>
                </div>
              </div>
              <n-empty v-else description="请选择要发送的文件" />

              <n-input
                v-model:value="mediaDraft.text"
                type="textarea"
                placeholder="可选，填写媒体附带说明"
                :autosize="{ minRows: 3, maxRows: 6 }"
              />

              <div class="chat-composer__footer">
                <span>{{ uploadedMedia ? '文件已上传' : '未选择文件' }}</span>
                <n-button
                  v-permission="'btn:wechathlink_messages:sendmedia'"
                  type="primary"
                  :loading="sendingMedia"
                  :disabled="!canSendMedia"
                  @click="sendMedia"
                >
                  发送媒体
                </n-button>
              </div>
            </div>
          </footer>
        </div>
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
        <div v-else class="preview-empty">当前消息没有可预览的媒体内容</div>
      </div>
      <template #footer>
        <n-button @click="previewModalVisible = false">关闭</n-button>
      </template>
    </modal-frame>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import api from '../../api.js'
import { ensureArray } from '../../utils/http'
import ModalFrame from '../../components/ModalFrame.vue'

const BOTTOM_STICKY_THRESHOLD = 72

const commonPhrases = [
  '您好，已收到您的消息。',
  '请稍等，我正在为您核实。',
  '感谢您的反馈，我们会尽快处理。',
  '如果方便，请补充更多细节。'
]

const accounts = ref([])
const selectedAccountId = ref(null)
const contactKeyword = ref('')
const contactListRef = ref(null)
const contactRows = ref([])
const contactPagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0
})
const contactLoading = ref(false)
let contactRequestSeq = 0
const selectedContactId = ref('')
const selectedContact = ref(null)

const conversationEventType = ref(null)
const conversationViewportRef = ref(null)
const conversationRowsRaw = ref([])
const conversationPagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0
})
const conversationLoading = ref(false)
let conversationRequestSeq = 0

const composeTab = ref('text')
const textDraft = ref('')
const mediaDraft = reactive({
  type: 'image',
  text: ''
})
const fileInputRef = ref(null)
const uploadedMedia = ref(null)
const uploadPreviewUrl = ref('')
const uploadingMedia = ref(false)
const sendingText = ref(false)
const sendingMedia = ref(false)

const lastResult = ref({})
const previewModalVisible = ref(false)
const previewEvent = ref(null)
const contactUiStateStore = reactive({})
const conversationStateStore = reactive({})

const mediaTypeOptions = [
  { label: '图片', value: 'image' },
  { label: '视频', value: 'video' },
  { label: '文件', value: 'file' },
  { label: '语音', value: 'voice' }
]

const conversationEventTypeOptions = [
  { label: '全部类型', value: null },
  ...mediaTypeOptions,
  { label: '文本', value: 'text' }
]

const accountOptions = computed(() => accounts.value.map((item) => ({
  label: `${item.accountName} (#${item.id})`,
  value: item.id
})))

const selectedAccount = computed(() => accounts.value.find((item) => item.id === selectedAccountId.value) || null)
const contactHasMore = computed(() => contactRows.value.length < contactPagination.itemCount)
const conversationRows = computed(() => [...conversationRowsRaw.value].reverse())
const conversationHasMore = computed(() => conversationRowsRaw.value.length < conversationPagination.itemCount)
const groupedContacts = computed(() => {
  const groups = [
    { key: 'today', label: '今天活跃', items: [] },
    { key: 'week', label: '近 7 天', items: [] },
    { key: 'older', label: '更早之前', items: [] }
  ]
  for (const item of contactRows.value) {
    const bucket = groupContact(item.lastSeenAt)
    if (bucket === 'today') {
      groups[0].items.push(item)
    } else if (bucket === 'week') {
      groups[1].items.push(item)
    } else {
      groups[2].items.push(item)
    }
  }
  return groups.filter((group) => group.items.length)
})
const uploadPreviewKind = computed(() => uploadedMedia.value?.detectedType || mediaDraft.type)
const previewTitle = computed(() => previewEvent.value?.mediaFileName || '媒体预览')
const previewKind = computed(() => previewEvent.value?.eventType || '')
const previewSrc = computed(() => previewEvent.value?.id ? api.getEventMediaUrl(previewEvent.value.id) : '')

const canReply = computed(() => !!selectedAccount.value && !!selectedContact.value && selectedContact.value.hasContextToken)
const canSendText = computed(() => canReply.value && !!textDraft.value.trim())
const canSendMedia = computed(() => canReply.value && !!uploadedMedia.value?.filePath)

async function loadAccounts() {
  const payload = await api.listAccounts()
  accounts.value = ensureArray(payload?.list)
  if (selectedAccountId.value && !accounts.value.some((item) => item.id === selectedAccountId.value)) {
    selectedAccountId.value = null
  }
  if (!selectedAccountId.value && accounts.value.length) {
    selectedAccountId.value = accounts.value[0].id
  }
}

async function loadContacts(options = {}) {
  const reset = options.reset !== false
  const previousContactId = selectedContactId.value
  if (reset) {
    contactRows.value = []
    contactPagination.itemCount = 0
    contactPagination.page = 1
  }
  if (!selectedAccountId.value) {
    selectedContactId.value = ''
    selectedContact.value = null
    conversationRowsRaw.value = []
    conversationPagination.itemCount = 0
    conversationPagination.page = 1
    return
  }
  if (contactLoading.value && !reset) {
    return
  }
  const requestSeq = ++contactRequestSeq
  const pageToLoad = reset ? 1 : contactPagination.page
  contactLoading.value = true
  const payload = await api.listMessagePeers({
    wechatAccountId: selectedAccountId.value,
    keyword: contactKeyword.value,
    pageNum: pageToLoad,
    pageSize: contactPagination.pageSize
  }).finally(() => {
    if (requestSeq === contactRequestSeq) {
      contactLoading.value = false
    }
  })
  if (requestSeq !== contactRequestSeq) {
    return
  }
  const incomingRows = sanitizeContacts(ensureArray(payload?.list))
  const mergedRows = reset ? incomingRows : mergeContactRows(contactRows.value, incomingRows)
  const activeContact = mergedRows.find((item) => item.contactId === selectedContactId.value)
  if (activeContact) {
    markContactRead(activeContact, { refresh: false })
  }
  contactRows.value = mergedRows.map((item) => decorateContact(item))
  contactPagination.itemCount = Number(payload?.total || 0)
  contactPagination.page = pageToLoad + 1
  if (!contactRows.value.length) {
    if (!selectedContactId.value) {
      conversationRowsRaw.value = []
      conversationPagination.itemCount = 0
      conversationPagination.page = 1
    }
    return
  }
  const matched = contactRows.value.find((item) => item.contactId === selectedContactId.value)
  if (matched) {
    selectedContact.value = matched
  }
  if (!selectedContactId.value) {
    await selectContact(contactRows.value[0])
    await nextTick()
    return
  }
  if (reset && !matched && selectedContact.value?.contactId !== selectedContactId.value) {
    await selectContact(contactRows.value[0])
    return
  }
  if (reset && selectedContactId.value === previousContactId && options.refreshConversation !== false) {
    const viewportMetrics = captureViewportMetrics(conversationViewportRef.value)
    await loadConversation({
      reset: true,
      stickToBottom: Boolean(options.forceStickToBottom) || !viewportMetrics || viewportMetrics.isNearBottom,
      preserveViewport: Boolean(options.preserveViewport) || Boolean(viewportMetrics && !viewportMetrics.isNearBottom)
    })
  }
}

async function loadConversation(options = {}) {
  const reset = Boolean(options.reset)
  const restoreScrollTop = Number.isFinite(options.restoreScrollTop) ? options.restoreScrollTop : null
  const viewport = conversationViewportRef.value
  const previousMetrics = captureViewportMetrics(viewport)
  if (reset) {
    conversationRowsRaw.value = []
    conversationPagination.itemCount = 0
    conversationPagination.page = 1
  }
  if (!selectedAccountId.value || !selectedContactId.value) {
    return
  }
  if (conversationLoading.value && !reset) {
    return
  }
  const requestSeq = ++conversationRequestSeq
  const pageToLoad = conversationPagination.page
  const previousHeight = reset || !viewport ? 0 : viewport.scrollHeight
  const previousTop = reset || !viewport ? 0 : viewport.scrollTop
  conversationLoading.value = true
  try {
    const payload = await api.listEvents({
      wechatAccountId: selectedAccountId.value,
      contactId: selectedContactId.value,
      eventType: conversationEventType.value,
      pageNum: pageToLoad,
      pageSize: conversationPagination.pageSize
    })
    if (requestSeq !== conversationRequestSeq) {
      return
    }
    const rows = ensureArray(payload?.list)
    conversationRowsRaw.value = reset ? rows : [...conversationRowsRaw.value, ...rows]
    conversationPagination.itemCount = Number(payload?.total || 0)
    conversationPagination.page = pageToLoad + 1
    await nextTick()
    const nextViewport = conversationViewportRef.value
    if (nextViewport) {
      if (reset) {
        if (restoreScrollTop !== null) {
          nextViewport.scrollTop = clampScrollTop(nextViewport, restoreScrollTop)
        } else if (options.stickToBottom || !previousMetrics || previousMetrics.isNearBottom) {
          nextViewport.scrollTop = nextViewport.scrollHeight
        } else if (options.preserveViewport && previousMetrics) {
          const nextTop = nextViewport.scrollHeight - nextViewport.clientHeight - previousMetrics.distanceFromBottom
          nextViewport.scrollTop = clampScrollTop(nextViewport, nextTop)
        } else if (previousMetrics) {
          nextViewport.scrollTop = clampScrollTop(nextViewport, previousMetrics.scrollTop)
        }
      } else {
        nextViewport.scrollTop = previousTop + (nextViewport.scrollHeight - previousHeight)
      }
    }
    saveConversationState()
  } finally {
    if (requestSeq === conversationRequestSeq) {
      conversationLoading.value = false
    }
  }
}

async function selectContact(contact) {
  if (!contact) {
    return
  }
  const nextContact = decorateContact(contact)
  const sameContact = selectedContactId.value === nextContact.contactId
  if (!sameContact) {
    saveConversationState()
  }
  selectedContactId.value = nextContact.contactId
  selectedContact.value = nextContact
  markContactRead(nextContact)
  if (sameContact && conversationRowsRaw.value.length) {
    await nextTick()
    return
  }
  await nextTick()
  const restored = await restoreConversationState(nextContact)
  if (!restored) {
    await loadConversation({ reset: true, stickToBottom: true })
  }
}

async function applyContactSearch() {
  await loadContacts({ reset: true, refreshConversation: false })
}

async function applyConversationFilter() {
  const viewportMetrics = captureViewportMetrics(conversationViewportRef.value)
  await loadConversation({
    reset: true,
    stickToBottom: !viewportMetrics || viewportMetrics.isNearBottom,
    preserveViewport: Boolean(viewportMetrics && !viewportMetrics.isNearBottom)
  })
}

function appendPhrase(phrase) {
  if (!phrase) {
    return
  }
  textDraft.value = textDraft.value ? `${textDraft.value}\n${phrase}` : phrase
}

function triggerFilePicker() {
  fileInputRef.value?.click()
}

function revokeUploadPreview() {
  if (uploadPreviewUrl.value) {
    URL.revokeObjectURL(uploadPreviewUrl.value)
    uploadPreviewUrl.value = ''
  }
}

function clearUploadedMedia() {
  uploadedMedia.value = null
  revokeUploadPreview()
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

async function handleFileChange(event) {
  const file = event?.target?.files?.[0]
  if (!file) {
    return
  }
  uploadingMedia.value = true
  revokeUploadPreview()
  try {
    uploadPreviewUrl.value = URL.createObjectURL(file)
    const payload = await api.uploadMessageFile(file)
    uploadedMedia.value = payload
    if (payload?.detectedType) {
      mediaDraft.type = payload.detectedType
    }
  } finally {
    uploadingMedia.value = false
  }
}

async function sendText() {
  if (!canSendText.value) {
    return
  }
  sendingText.value = true
  try {
    lastResult.value = await api.sendTextMessage({
      wechatAccountId: Number(selectedAccountId.value),
      toUserId: selectedContactId.value,
      text: textDraft.value.trim()
    })
    clearReplyFailure(selectedContact.value, { refresh: false })
    textDraft.value = ''
    await loadConversation({ reset: true, stickToBottom: true })
    await loadContacts({ refreshConversation: false })
  } catch (error) {
    markReplyFailure(selectedContact.value, error)
    throw error
  } finally {
    sendingText.value = false
  }
}

async function sendMedia() {
  if (!canSendMedia.value) {
    return
  }
  sendingMedia.value = true
  try {
    lastResult.value = await api.sendMediaMessage({
      wechatAccountId: Number(selectedAccountId.value),
      toUserId: selectedContactId.value,
      type: mediaDraft.type,
      filePath: uploadedMedia.value.filePath,
      text: mediaDraft.text
    })
    clearReplyFailure(selectedContact.value, { refresh: false })
    mediaDraft.text = ''
    clearUploadedMedia()
    await loadConversation({ reset: true, stickToBottom: true })
    await loadContacts({ refreshConversation: false })
  } catch (error) {
    markReplyFailure(selectedContact.value, error)
    throw error
  } finally {
    sendingMedia.value = false
  }
}

async function refreshWorkspace() {
  const viewportMetrics = captureViewportMetrics(conversationViewportRef.value)
  await loadAccounts()
  await loadContacts({
    forceStickToBottom: !viewportMetrics || viewportMetrics.isNearBottom,
    preserveViewport: Boolean(viewportMetrics && !viewportMetrics.isNearBottom)
  })
}

function openPreview(row) {
  previewEvent.value = row
  previewModalVisible.value = true
}

function eventMediaUrl(row) {
  return row?.id ? api.getEventMediaUrl(row.id) : ''
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

function formatFileSize(size) {
  const value = Number(size || 0)
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`
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

function activityText(value) {
  const bucket = groupContact(value)
  if (bucket === 'today') {
    return '活跃'
  }
  if (bucket === 'week') {
    return '近期'
  }
  return '较早'
}

function groupContact(value) {
  if (!value) {
    return 'older'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return 'older'
  }
  const diff = Date.now() - date.getTime()
  const day = 24 * 60 * 60 * 1000
  if (diff < day) {
    return 'today'
  }
  if (diff < 7 * day) {
    return 'week'
  }
  return 'older'
}

function contactInitial(contactId) {
  const text = `${contactId || ''}`.trim()
  if (!text) {
    return '?'
  }
  return text.slice(0, 2).toUpperCase()
}

function handleConversationScroll(event) {
  const target = event?.target
  if (!target) {
    return
  }
  saveConversationScrollTop()
  if (conversationLoading.value || !conversationHasMore.value) {
    return
  }
  if (target.scrollTop <= 40) {
    loadConversation()
  }
}

function handleContactListScroll(event) {
  const target = event?.target
  if (!target || contactLoading.value || !contactHasMore.value) {
    return
  }
  const distanceToBottom = target.scrollHeight - target.clientHeight - target.scrollTop
  if (distanceToBottom <= 48) {
    loadContacts({ reset: false, refreshConversation: false })
  }
}

function sanitizeContacts(rows) {
  if (!selectedAccount.value) {
    return rows
  }
  return rows.filter((item) => {
    const contactId = `${item?.contactId || ''}`.trim()
    if (!contactId) {
      return false
    }
    if (selectedAccount.value.accountCode && contactId === `${selectedAccount.value.accountCode}`.trim()) {
      return false
    }
    return true
  })
}

function mergeContactRows(existingRows, incomingRows) {
  if (!existingRows.length) {
    return incomingRows
  }
  const merged = [...existingRows]
  const seen = new Set(existingRows.map((item) => item.contactId))
  for (const item of incomingRows) {
    if (item?.contactId && !seen.has(item.contactId)) {
      seen.add(item.contactId)
      merged.push(item)
    }
  }
  return merged
}

function buildContactStateKey(accountId, contactId) {
  return `${accountId || 'na'}::${contactId || ''}`
}

function buildConversationStateKey(accountId = selectedAccountId.value, contactId = selectedContactId.value, eventType = conversationEventType.value) {
  return `${accountId || 'na'}::${contactId || ''}::${eventType || 'all'}`
}

function ensureContactUiState(accountId, contactId) {
  const key = buildContactStateKey(accountId, contactId)
  if (!contactUiStateStore[key]) {
    contactUiStateStore[key] = {
      lastReadAt: '',
      lastReplyFailure: null
    }
  }
  return contactUiStateStore[key]
}

function toTimestamp(value) {
  if (!value) {
    return 0
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

function normalizeDirection(value) {
  return `${value || ''}`.trim().toLowerCase()
}

function decorateContact(item) {
  if (!item?.contactId) {
    return item
  }
  const accountId = item.wechatAccountId ?? selectedAccountId.value
  const state = ensureContactUiState(accountId, item.contactId)
  const lastSeenAt = toTimestamp(item.lastSeenAt || item.lastMessageAt)
  const lastReadAt = toTimestamp(state.lastReadAt)
  const isCurrentContact = accountId === selectedAccountId.value && item.contactId === selectedContactId.value
  return {
    ...item,
    isUnread: !isCurrentContact && normalizeDirection(item.lastDirection) === 'inbound' && !!lastSeenAt && (!lastReadAt || lastSeenAt > lastReadAt),
    hasRecentReplyFailure: Boolean(state.lastReplyFailure),
    lastReplyFailureMessage: state.lastReplyFailure?.message || ''
  }
}

function refreshContactRows() {
  contactRows.value = contactRows.value.map((item) => decorateContact(item))
  if (!selectedContactId.value) {
    return
  }
  const matched = contactRows.value.find((item) => item.contactId === selectedContactId.value)
  if (matched) {
    selectedContact.value = matched
  }
}

function markContactRead(contact, options = {}) {
  if (!contact?.contactId) {
    return
  }
  const accountId = contact.wechatAccountId ?? selectedAccountId.value
  if (!accountId) {
    return
  }
  const state = ensureContactUiState(accountId, contact.contactId)
  state.lastReadAt = contact.lastSeenAt || contact.lastMessageAt || new Date().toISOString()
  if (options.refresh !== false) {
    refreshContactRows()
  }
}

function markReplyFailure(contact, error) {
  if (!contact?.contactId) {
    return
  }
  const accountId = contact.wechatAccountId ?? selectedAccountId.value
  if (!accountId) {
    return
  }
  const state = ensureContactUiState(accountId, contact.contactId)
  state.lastReplyFailure = {
    at: new Date().toISOString(),
    message: error?.message || '最近一次回复失败'
  }
  refreshContactRows()
}

function clearReplyFailure(contact, options = {}) {
  if (!contact?.contactId) {
    return
  }
  const accountId = contact.wechatAccountId ?? selectedAccountId.value
  if (!accountId) {
    return
  }
  const state = ensureContactUiState(accountId, contact.contactId)
  state.lastReplyFailure = null
  if (options.refresh !== false) {
    refreshContactRows()
  }
}

function captureViewportMetrics(viewport) {
  if (!viewport) {
    return null
  }
  const distanceFromBottom = Math.max(0, viewport.scrollHeight - viewport.clientHeight - viewport.scrollTop)
  return {
    scrollTop: viewport.scrollTop,
    distanceFromBottom,
    isNearBottom: distanceFromBottom <= BOTTOM_STICKY_THRESHOLD
  }
}

function clampScrollTop(viewport, value) {
  if (!viewport) {
    return 0
  }
  const maxTop = Math.max(0, viewport.scrollHeight - viewport.clientHeight)
  return Math.min(Math.max(0, Number(value || 0)), maxTop)
}

function saveConversationState() {
  if (!selectedAccountId.value || !selectedContactId.value) {
    return
  }
  const key = buildConversationStateKey()
  const viewport = conversationViewportRef.value
  conversationStateStore[key] = {
    loaded: true,
    rows: [...conversationRowsRaw.value],
    page: conversationPagination.page,
    itemCount: conversationPagination.itemCount,
    scrollTop: viewport ? viewport.scrollTop : conversationStateStore[key]?.scrollTop || 0
  }
}

function saveConversationScrollTop() {
  if (!selectedAccountId.value || !selectedContactId.value) {
    return
  }
  const key = buildConversationStateKey()
  const cached = conversationStateStore[key]
  if (!cached) {
    return
  }
  const viewport = conversationViewportRef.value
  conversationStateStore[key] = {
    ...cached,
    scrollTop: viewport ? viewport.scrollTop : cached.scrollTop || 0
  }
}

async function restoreConversationState(contact) {
  const accountId = contact?.wechatAccountId ?? selectedAccountId.value
  const contactId = contact?.contactId
  if (!accountId || !contactId) {
    return false
  }
  const cached = conversationStateStore[buildConversationStateKey(accountId, contactId, conversationEventType.value)]
  if (!cached?.loaded) {
    return false
  }
  conversationRowsRaw.value = ensureArray(cached.rows)
  conversationPagination.page = Number(cached.page || 1)
  conversationPagination.itemCount = Number(cached.itemCount || 0)
  await nextTick()
  const viewport = conversationViewportRef.value
  if (viewport) {
    viewport.scrollTop = clampScrollTop(viewport, cached.scrollTop)
  }
  return true
}

watch(selectedAccountId, () => {
  saveConversationState()
  contactKeyword.value = ''
  selectedContactId.value = ''
  selectedContact.value = null
  contactPagination.page = 1
  contactRows.value = []
  conversationRowsRaw.value = []
  conversationPagination.itemCount = 0
  conversationPagination.page = 1
  loadContacts()
})

onBeforeUnmount(() => {
  saveConversationState()
  revokeUploadPreview()
})

onMounted(loadAccounts)
</script>

<style scoped>
.page--messages {
  height: calc(100vh - 112px);
  overflow: hidden;
}

.chat-shell {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.chat-sidebar,
.chat-main {
  min-width: 0;
  min-height: 0;
}

.chat-sidebar__panel,
.chat-main__panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
}

.sidebar-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.sidebar-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sidebar-block__label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.field-label {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.contact-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.inline-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.account-brief {
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(14, 116, 144, 0.08));
  border: 1px solid rgba(15, 118, 110, 0.12);
}

.account-brief__title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.account-brief__meta {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.contact-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 6px;
}

.contact-list--empty {
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  border: 1px dashed rgba(148, 163, 184, 0.24);
  background: rgba(248, 250, 252, 0.82);
}

.contact-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.contact-group__title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.contact-list__status {
  display: flex;
  justify-content: center;
  padding: 2px 0 4px;
  color: #64748b;
  font-size: 12px;
}

.contact-item {
  width: 100%;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 16px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: transform 120ms ease, box-shadow 120ms ease, border-color 120ms ease, background 120ms ease;
}

.contact-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.contact-item--active {
  border-color: rgba(15, 118, 110, 0.4);
  background: #f0fdfa;
  box-shadow: 0 12px 28px rgba(15, 118, 110, 0.08);
}

.contact-item--replyable:not(.contact-item--active) {
  box-shadow: inset 3px 0 0 rgba(15, 118, 110, 0.16);
}

.contact-item--blocked:not(.contact-item--active) {
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.contact-item--unread {
  border-color: rgba(220, 38, 38, 0.24);
}

.contact-item--failed {
  border-color: rgba(234, 88, 12, 0.32);
}

.contact-item__main {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.contact-item__avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.contact-item__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: linear-gradient(135deg, #0f766e, #0e7490);
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

.contact-item__avatar-dot {
  position: absolute;
  top: -1px;
  right: -1px;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  border: 2px solid #ffffff;
  background: #dc2626;
}

.contact-item__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.contact-item__top,
.contact-item__meta,
.contact-item__status-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
}

.contact-item__top {
  justify-content: space-between;
}

.contact-item__name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
}

.contact-item__top span,
.contact-item__meta {
  font-size: 12px;
  color: #64748b;
}

.contact-item__top span:last-child {
  flex-shrink: 0;
}

.contact-pill {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.contact-pill--unread {
  background: rgba(220, 38, 38, 0.1);
  color: #b91c1c;
}

.contact-pill--failed {
  background: rgba(234, 88, 12, 0.1);
  color: #c2410c;
}

.contact-pill--ready {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

.contact-pill--muted {
  background: rgba(100, 116, 139, 0.12);
  color: #475569;
}

.contact-item__preview {
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.chat-main__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  flex-shrink: 0;
}

.chat-main__title {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.chat-main__meta {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  flex-wrap: wrap;
}

.chat-main__messages {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding-right: 6px;
}

.conversation-status {
  position: sticky;
  top: 0;
  z-index: 1;
  align-self: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.94);
  border: 1px solid rgba(148, 163, 184, 0.18);
  color: #64748b;
  font-size: 12px;
}

.chat-main__empty {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-bubble {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-bubble--outbound {
  align-items: flex-end;
}

.message-bubble--inbound {
  align-items: flex-start;
}

.message-bubble__meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #64748b;
}

.message-bubble__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 54px;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-weight: 700;
}

.message-bubble__badge--inbound {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

.message-bubble__badge--outbound {
  background: rgba(14, 116, 144, 0.12);
  color: #0e7490;
}

.message-bubble__content {
  width: min(100%, 720px);
  padding: 14px 16px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.message-bubble--outbound .message-bubble__content {
  background: #ecfeff;
}

.message-bubble__text {
  white-space: pre-wrap;
  line-height: 1.75;
  color: #0f172a;
}

.message-bubble__media {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message-bubble__image,
.message-bubble__video {
  max-width: 100%;
  max-height: 280px;
  border-radius: 14px;
  background: #00000008;
}

.message-bubble__image {
  cursor: pointer;
}

.message-bubble__audio {
  width: min(100%, 420px);
}

.message-bubble__file {
  font-weight: 600;
  color: #0f172a;
}

.message-bubble__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.message-bubble__link {
  color: #0f766e;
  text-decoration: none;
  font-size: 13px;
  font-weight: 700;
}

.chat-composer {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
  flex-shrink: 0;
}

.composer-target {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.phrase-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.phrase-chip {
  border: 1px solid rgba(15, 118, 110, 0.18);
  background: #f0fdfa;
  color: #0f766e;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}

.chat-composer__tabs {
  margin-top: -6px;
}

.chat-composer__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chat-composer__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  color: #64748b;
  font-size: 12px;
}

.upload-card {
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.upload-card__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
}

.upload-card__preview {
  margin-bottom: 12px;
}

.upload-card__image,
.upload-card__video {
  max-width: 100%;
  max-height: 220px;
  border-radius: 12px;
}

.upload-card__audio {
  width: 100%;
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

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}

@media (max-width: 1280px) {
  .chat-shell {
    grid-template-columns: 280px minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .page--messages {
    height: auto;
    overflow: visible;
  }

  .chat-shell {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .chat-sidebar,
  .chat-main {
    min-height: 420px;
  }

  .chat-main__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .chat-main__panel,
  .chat-sidebar__panel {
    height: auto;
  }
}
</style>
