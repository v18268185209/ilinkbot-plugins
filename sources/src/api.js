import { httpGet, httpPost } from './utils/http'
import { resolveApiAssetUrl } from './config/env'

const adminPrefix = '/wechathlink/admin'

function compactParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== null && value !== undefined && value !== '')
  )
}

const api = {
  dashboardSummary() {
    return httpGet(`${adminPrefix}/dashboard/summary`)
  },

  listEventAccountSummaries(params = {}) {
    return httpGet(`${adminPrefix}/events/summary`, {
      params: compactParams(params)
    })
  },

  listAccounts(keyword) {
    return httpGet(`${adminPrefix}/accounts/list`, {
      params: compactParams({ keyword })
    })
  },

  getAccountDetail(id) {
    return httpGet(`${adminPrefix}/accounts/detail`, {
      params: compactParams({ id })
    })
  },

  saveAccount(data) {
    return httpPost(`${adminPrefix}/accounts/save`, data)
  },

  toggleAccount(id, status) {
    return httpPost(`${adminPrefix}/accounts/toggle`, { id, status })
  },

  saveAccountMember(data) {
    return httpPost(`${adminPrefix}/accounts/member/save`, data)
  },

  startLoginSession(baseUrl, config = {}) {
    return httpPost(`${adminPrefix}/login/start`, compactParams({ baseUrl }), config)
  },

  getLoginStatus(sessionCode, config = {}) {
    return httpGet(`${adminPrefix}/login/status`, {
      ...config,
      params: compactParams({ sessionCode })
    })
  },

  listEvents(params = {}) {
    return httpGet(`${adminPrefix}/events/list`, {
      params: compactParams(params)
    })
  },

  listEventContacts(params = {}) {
    return httpGet(`${adminPrefix}/events/contacts`, {
      params: compactParams(params)
    })
  },

  getEventMediaUrl(eventId) {
    return resolveApiAssetUrl(`/api${adminPrefix}/events/media?eventId=${encodeURIComponent(eventId)}`)
  },

  listMessagePeers(wechatAccountId, keyword) {
    return httpGet(`${adminPrefix}/messages/peers`, {
      params: compactParams({ wechatAccountId, keyword })
    })
  },

  sendTextMessage(data) {
    return httpPost(`${adminPrefix}/messages/send-text`, data)
  },

  sendMediaMessage(data) {
    return httpPost(`${adminPrefix}/messages/send-media`, data)
  },

  getSettings() {
    return httpGet(`${adminPrefix}/settings/get`)
  },

  saveSettings(data) {
    return httpPost(`${adminPrefix}/settings/save`, data)
  }
}

export default api
