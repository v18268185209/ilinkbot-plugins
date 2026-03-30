import axios from 'axios'
import { createDiscreteApi } from 'naive-ui'
import appEnv from '../config/env'

const SUCCESS_CODE = '000000'
const { message } = createDiscreteApi(['message'])

function resolvePayload(data) {
  if (!data || typeof data !== 'object') {
    return data
  }
  if (Object.prototype.hasOwnProperty.call(data, 'result')) {
    return data.result
  }
  if (Object.prototype.hasOwnProperty.call(data, 'data')) {
    return data.data
  }
  return data
}

function resolveErrorMessage(payload, fallback) {
  if (payload && typeof payload === 'object') {
    if (payload.message) {
      return payload.message
    }
    if (payload.msg) {
      return payload.msg
    }
  }
  return fallback
}

function buildError(payload, fallback) {
  const error = new Error(resolveErrorMessage(payload, fallback))
  error.payload = payload
  return error
}

const http = axios.create({
  baseURL: appEnv.apiBaseUrl,
  timeout: appEnv.httpTimeout,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json'
  }
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (!payload || typeof payload !== 'object') {
      return payload
    }
    if (payload.code === SUCCESS_CODE || payload.success === true || payload.failure === false) {
      return resolvePayload(payload)
    }
    const error = buildError(payload, 'Request failed')
    if (!response.config?.silentError) {
      message.error(error.message)
    }
    return Promise.reject(error)
  },
  (error) => {
    const payload = error?.response?.data
    const normalizedError = buildError(payload, error?.message || 'Network request failed')
    if (!error?.config?.silentError) {
      message.error(normalizedError.message)
    }
    return Promise.reject(normalizedError)
  }
)

export function httpGet(url, config = {}) {
  return http.get(url, config)
}

export function httpPost(url, data = {}, config = {}) {
  return http.post(url, data, config)
}

export function httpPut(url, data = {}, config = {}) {
  return http.put(url, data, config)
}

export function httpDelete(url, config = {}) {
  return http.delete(url, config)
}

export function ensureArray(value) {
  return Array.isArray(value) ? value : []
}

export default http
