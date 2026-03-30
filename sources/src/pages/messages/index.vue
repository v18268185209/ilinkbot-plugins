<template>
  <div class="page">
    <page-header
      kicker="发送"
      title="消息发送台"
      description="选择微信账号与最近消息联系人进行发送测试，联系人列表基于消息聚合结果生成。"
    >
      <template #actions>
        <n-button @click="openTextModal">文本发送</n-button>
        <n-button type="primary" @click="openMediaModal">媒体发送</n-button>
      </template>
    </page-header>

    <n-card title="最近结果">
      <pre>{{ JSON.stringify(lastResult, null, 2) }}</pre>
    </n-card>

    <modal-frame v-model:show="textModalVisible" title="发送文本消息" :height="520">
      <n-form label-placement="top">
        <n-form-item>
          <template #label>
            <field-hint label="微信账号" tip="发送动作必须明确选择一个微信账号。"/>
          </template>
          <n-select
            v-model:value="textForm.wechatAccountId"
            :options="accountOptions"
            filterable
            clearable
            placeholder="请选择微信账号"
          />
        </n-form-item>
        <n-form-item>
          <template #label>
            <field-hint label="目标用户" tip="下拉内容来自该账号已收到或发送过消息的聚合联系人。"/>
          </template>
          <n-select
            v-model:value="textForm.toUserId"
            :options="textPeerOptions"
            :loading="textPeersLoading"
            filterable
            remote
            clearable
            placeholder="请选择目标用户"
            :disabled="!textForm.wechatAccountId"
            @search="handleTextPeerSearch"
          />
        </n-form-item>
        <n-form-item>
          <template #label>
            <field-hint label="文本内容" tip="当前发送逻辑默认要求命中该用户的 context token。"/>
          </template>
          <n-input v-model:value="textForm.text" type="textarea" :autosize="{ minRows: 5, maxRows: 8 }" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="textModalVisible = false">取消</n-button>
        <n-button type="primary" @click="submitText" :disabled="!textForm.wechatAccountId || !textForm.toUserId || !textForm.text">发送文本</n-button>
      </template>
    </modal-frame>

    <modal-frame v-model:show="mediaModalVisible" title="发送媒体消息" :height="560">
      <n-form label-placement="top">
        <n-form-item>
          <template #label>
            <field-hint label="微信账号" tip="媒体消息同样必须指定一个微信账号。"/>
          </template>
          <n-select
            v-model:value="mediaForm.wechatAccountId"
            :options="accountOptions"
            filterable
            clearable
            placeholder="请选择微信账号"
          />
        </n-form-item>
        <n-form-item>
          <template #label>
            <field-hint label="目标用户" tip="下拉内容来自该账号已收到或发送过消息的聚合联系人。"/>
          </template>
          <n-select
            v-model:value="mediaForm.toUserId"
            :options="mediaPeerOptions"
            :loading="mediaPeersLoading"
            filterable
            remote
            clearable
            placeholder="请选择目标用户"
            :disabled="!mediaForm.wechatAccountId"
            @search="handleMediaPeerSearch"
          />
        </n-form-item>
        <n-form-item label="媒体类型">
          <n-select v-model:value="mediaForm.type" :options="mediaTypeOptions" />
        </n-form-item>
        <n-form-item>
          <template #label>
            <field-hint label="文件路径" tip="请输入插件运行环境能够访问到的本地绝对路径。"/>
          </template>
          <n-input v-model:value="mediaForm.filePath" />
        </n-form-item>
        <n-form-item label="附带文本"><n-input v-model:value="mediaForm.text" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" /></n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="mediaModalVisible = false">取消</n-button>
        <n-button type="primary" @click="submitMedia" :disabled="!mediaForm.wechatAccountId || !mediaForm.toUserId || !mediaForm.filePath">发送媒体</n-button>
      </template>
    </modal-frame>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '../../api.js'
import { ensureArray } from '../../utils/http'
import PageHeader from '../../components/PageHeader.vue'
import FieldHint from '../../components/FieldHint.vue'
import ModalFrame from '../../components/ModalFrame.vue'

const lastResult = ref({})
const textModalVisible = ref(false)
const mediaModalVisible = ref(false)
const accounts = ref([])
const textPeers = ref([])
const mediaPeers = ref([])
const textPeersLoading = ref(false)
const mediaPeersLoading = ref(false)
const textPeerKeyword = ref('')
const mediaPeerKeyword = ref('')

const textForm = reactive({
  wechatAccountId: null,
  toUserId: '',
  text: ''
})

const mediaForm = reactive({
  wechatAccountId: null,
  toUserId: '',
  type: 'image',
  filePath: '',
  text: ''
})

const mediaTypeOptions = [
  { label: '图片', value: 'image' },
  { label: '视频', value: 'video' },
  { label: '文件', value: 'file' },
  { label: '语音', value: 'voice' }
]

const accountOptions = computed(() => accounts.value.map((item) => ({
  label: `${item.accountName} (#${item.id})`,
  value: item.id
})))

const textPeerOptions = computed(() => textPeers.value.map((item) => ({
  label: item.label || item.peerUserId,
  value: item.peerUserId
})))

const mediaPeerOptions = computed(() => mediaPeers.value.map((item) => ({
  label: item.label || item.peerUserId,
  value: item.peerUserId
})))

async function loadAccounts() {
  const payload = await api.listAccounts()
  accounts.value = ensureArray(payload?.list)
}

async function loadTextPeers(keyword = textPeerKeyword.value) {
  textForm.toUserId = ''
  textPeers.value = []
  if (!textForm.wechatAccountId) {
    return
  }
  textPeersLoading.value = true
  try {
    const payload = await api.listMessagePeers(textForm.wechatAccountId, keyword)
    textPeers.value = ensureArray(payload?.list)
  } finally {
    textPeersLoading.value = false
  }
}

async function loadMediaPeers(keyword = mediaPeerKeyword.value) {
  mediaForm.toUserId = ''
  mediaPeers.value = []
  if (!mediaForm.wechatAccountId) {
    return
  }
  mediaPeersLoading.value = true
  try {
    const payload = await api.listMessagePeers(mediaForm.wechatAccountId, keyword)
    mediaPeers.value = ensureArray(payload?.list)
  } finally {
    mediaPeersLoading.value = false
  }
}

function handleTextPeerSearch(value) {
  textPeerKeyword.value = value || ''
  loadTextPeers(textPeerKeyword.value)
}

function handleMediaPeerSearch(value) {
  mediaPeerKeyword.value = value || ''
  loadMediaPeers(mediaPeerKeyword.value)
}

function openTextModal() {
  textModalVisible.value = true
}

function openMediaModal() {
  mediaModalVisible.value = true
}

async function submitText() {
  lastResult.value = await api.sendTextMessage({
    wechatAccountId: Number(textForm.wechatAccountId),
    toUserId: textForm.toUserId,
    text: textForm.text
  })
  await loadTextPeers(textPeerKeyword.value)
  textModalVisible.value = false
}

async function submitMedia() {
  lastResult.value = await api.sendMediaMessage({
    wechatAccountId: Number(mediaForm.wechatAccountId),
    toUserId: mediaForm.toUserId,
    type: mediaForm.type,
    filePath: mediaForm.filePath,
    text: mediaForm.text
  })
  await loadMediaPeers(mediaPeerKeyword.value)
  mediaModalVisible.value = false
}

watch(() => textForm.wechatAccountId, () => {
  textPeerKeyword.value = ''
  loadTextPeers('')
})
watch(() => mediaForm.wechatAccountId, () => {
  mediaPeerKeyword.value = ''
  loadMediaPeers('')
})

onMounted(loadAccounts)
</script>
