function normalizeBaseUrl(value, fallback) {
  const text = `${value || fallback || ''}`.trim()
  if (!text) {
    return fallback
  }
  return text.endsWith('/') && text.length > 1 ? text.slice(0, -1) : text
}

function isAbsoluteUrl(value) {
  return /^https?:\/\//i.test(`${value || ''}`.trim())
}

function toNumber(value, fallback) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

export const appEnv = Object.freeze({
  mode: import.meta.env.MODE,
  isDev: import.meta.env.DEV,
  isProd: import.meta.env.PROD,
  title: import.meta.env.VITE_APP_TITLE || 'wechat-hlink-plugins-ui',
  apiBaseUrl: normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL, '/api'),
  publicBase: import.meta.env.VITE_PUBLIC_BASE || import.meta.env.BASE_URL || '/',
  routerBase: import.meta.env.VITE_ROUTER_BASE || '/',
  httpTimeout: toNumber(import.meta.env.VITE_HTTP_TIMEOUT, 30000)
})

export function resolveApiAssetUrl(value) {
  const text = `${value || ''}`.trim()
  if (!text) {
    return ''
  }
  if (isAbsoluteUrl(text) || text.startsWith('data:') || text.startsWith('blob:')) {
    return text
  }
  if (!isAbsoluteUrl(appEnv.apiBaseUrl)) {
    return text
  }
  const apiUrl = new URL(appEnv.apiBaseUrl)
  if (text.startsWith('/')) {
    return new URL(text, apiUrl.origin).toString()
  }
  return new URL(text, `${apiUrl.origin}/`).toString()
}

export default appEnv
