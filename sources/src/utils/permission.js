const STORAGE_KEY = {
  USER_INFO: 'userInfo',
  TOKEN: 'token',
  BTN_COMPONENTS_PERMS: 'btnConPomentsPerms',
  PERMISSIONS: 'permissions'
}

const SUPER_TYPES = new Set(['super', 'supper'])

function normalize(value) {
  return `${value || ''}`.trim()
}

function parseJson(raw, fallback) {
  if (!raw) {
    return fallback
  }
  try {
    const data = JSON.parse(raw)
    return data ?? fallback
  } catch (_error) {
    return fallback
  }
}

function readUserInfo() {
  const userInfo = parseJson(localStorage.getItem(STORAGE_KEY.USER_INFO), null)
  if (userInfo) {
    return userInfo
  }
  return parseJson(localStorage.getItem('user'), null)
}

function resolveUserCacheTag() {
  const user = readUserInfo()
  if (user?.id !== undefined && user?.id !== null) {
    return `${user.id}`
  }
  if (user?.account) {
    return `${user.account}`
  }
  if (user?.account?.id !== undefined && user?.account?.id !== null) {
    return `${user.account.id}`
  }
  if (user?.account?.account) {
    return `${user.account.account}`
  }
  return ''
}

function toCode(item) {
  if (!item) {
    return ''
  }
  if (typeof item === 'string') {
    return normalize(item)
  }
  if (typeof item === 'object') {
    return normalize(item.permCode || item.code || item.router || item.permission || '')
  }
  return ''
}

function readCodes(storageKey) {
  const userTag = resolveUserCacheTag()
  const scopedKey = userTag ? `${storageKey}:${userTag}` : ''
  const raw = (scopedKey && localStorage.getItem(scopedKey)) || localStorage.getItem(storageKey)
  const list = parseJson(raw, [])
  if (!Array.isArray(list)) {
    return []
  }
  return list.map(toCode).filter(Boolean)
}

function wildcardMatch(pattern, target) {
  const p = normalize(pattern)
  const t = normalize(target)
  if (!p || !t) {
    return false
  }
  if (!p.includes('*')) {
    return p.toLowerCase() === t.toLowerCase()
  }
  const escaped = p.replace(/[.+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`^${escaped.replace(/\*/g, '.*')}$`, 'i')
  return regex.test(t)
}

export function isSuperUser() {
  const user = readUserInfo()
  const accountType = normalize(user?.accountType || user?.account?.accountType).toLowerCase()
  return SUPER_TYPES.has(accountType)
}

export function getPermissionCodes() {
  const codeSet = new Set([
    ...readCodes(STORAGE_KEY.BTN_COMPONENTS_PERMS),
    ...readCodes(STORAGE_KEY.PERMISSIONS)
  ])
  return Array.from(codeSet)
}

export function hasToken() {
  return normalize(localStorage.getItem(STORAGE_KEY.TOKEN)).length > 0
}

export function isStandalonePermissionMode() {
  return !hasToken() && getPermissionCodes().length === 0
}

export function hasPermission(input) {
  if (isStandalonePermissionMode()) {
    return true
  }
  if (isSuperUser()) {
    return true
  }
  const code = normalize(input)
  if (!code) {
    return true
  }
  const permissions = getPermissionCodes().map((item) => normalize(item))
  if (permissions.length === 0) {
    return false
  }
  if (permissions.some((item) => item === '*')) {
    return true
  }
  return permissions.some((item) => wildcardMatch(item, code))
}

export function hasAnyPermission(codes) {
  if (isStandalonePermissionMode()) {
    return true
  }
  if (isSuperUser()) {
    return true
  }
  if (!Array.isArray(codes) || codes.length === 0) {
    return true
  }
  return codes.some((code) => hasPermission(code))
}

export function resolvePermissionVisible(permission) {
  if (permission === undefined || permission === null || permission === '') {
    return true
  }
  if (typeof permission === 'string') {
    return hasPermission(permission)
  }
  if (Array.isArray(permission)) {
    return permission.length === 0 ? true : hasAnyPermission(permission)
  }
  if (typeof permission === 'object') {
    const anyOf = Array.isArray(permission.anyOf)
      ? permission.anyOf
      : (normalize(permission.anyOf) ? [permission.anyOf] : [])
    if (anyOf.length > 0) {
      return hasAnyPermission(anyOf)
    }

    const allOf = Array.isArray(permission.allOf)
      ? permission.allOf
      : (normalize(permission.allOf) ? [permission.allOf] : [])
    if (allOf.length > 0) {
      return allOf.every((code) => hasPermission(code))
    }

    const singleCode = permission.code || permission.perm || permission.permission
    if (Array.isArray(singleCode)) {
      return singleCode.length === 0 ? true : hasAnyPermission(singleCode)
    }
    if (normalize(singleCode)) {
      return hasPermission(singleCode)
    }

    return true
  }
  if (typeof permission === 'boolean') {
    return permission
  }
  return Boolean(permission)
}

export default {
  getPermissionCodes,
  hasToken,
  isStandalonePermissionMode,
  hasPermission,
  hasAnyPermission,
  isSuperUser,
  resolvePermissionVisible
}
