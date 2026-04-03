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

  startLoginSession(payload = {}, config = {}) {
    if (typeof payload === 'string') {
      return httpPost(`${adminPrefix}/login/start`, compactParams({ baseUrl: payload }), config)
    }
    return httpPost(`${adminPrefix}/login/start`, compactParams(payload), config)
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

  listEventDispatches(params = {}) {
    return httpGet(`${adminPrefix}/events/dispatches`, {
      params: compactParams(params)
    })
  },

  listEventMediaAssets(params = {}) {
    return httpGet(`${adminPrefix}/events/media-assets`, {
      params: compactParams(params)
    })
  },

  getEventMediaUrl(eventId) {
    return resolveApiAssetUrl(`/api${adminPrefix}/events/media?eventId=${encodeURIComponent(eventId)}`)
  },

  getEventExportUrl(params = {}) {
    const query = new URLSearchParams(compactParams(params))
    return resolveApiAssetUrl(`/api${adminPrefix}/events/export${query.toString() ? `?${query.toString()}` : ''}`)
  },

  uploadMessageFile(file) {
    const formData = new FormData()
    formData.append('file', file)
    return httpPost(`${adminPrefix}/messages/upload`, formData)
  },

  listMessagePeers(params = {}) {
    return httpGet(`${adminPrefix}/messages/peers`, {
      params: compactParams(params)
    })
  },

  sendTextMessage(data) {
    return httpPost(`${adminPrefix}/messages/send-text`, data)
  },

  sendMediaMessage(data) {
    return httpPost(`${adminPrefix}/messages/send-media`, data)
  },

  retryDispatch(data) {
    return httpPost(`${adminPrefix}/messages/retry-dispatch`, data)
  },

  getSettings() {
    return httpGet(`${adminPrefix}/settings/get`)
  },

  saveSettings(data) {
    return httpPost(`${adminPrefix}/settings/save`, data)
  },

  listAuditLogs(params = {}) {
    return httpGet(`${adminPrefix}/audit/list`, {
      params: compactParams(params)
    })
  },

  getPlatformSummary() {
    return httpGet(`${adminPrefix}/platform/summary`)
  },

  listPlatformRequests(params = {}) {
    return httpGet(`${adminPrefix}/platform/requests`, {
      params: compactParams(params)
    })
  },

  listPlatformDeliveries(params = {}) {
    return httpGet(`${adminPrefix}/platform/deliveries`, {
      params: compactParams(params)
    })
  },

  getPlatformDeliveryDetail(id) {
    return httpGet(`${adminPrefix}/platform/deliveries/detail`, {
      params: compactParams({ id })
    })
  },

  retryPlatformDelivery(data) {
    return httpPost(`${adminPrefix}/platform/deliveries/retry`, data)
  },

  getAuditDetail(id) {
    return httpGet(`${adminPrefix}/audit/detail`, {
      params: compactParams({ id })
    })
  }
}

export default api
