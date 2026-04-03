export const SHELL_MODE_STANDALONE = 'standalone'
export const SHELL_MODE_PLUGIN = 'plugin'

export const routeMetaConfig = Object.freeze({
  overview: {
    key: 'overview',
    path: '/overview',
    alias: '/dashboard/plugins/wechathlink/overview',
    permission: '/dashboard/plugins/wechathlink/overview',
    labels: {
      standalone: '首页',
      plugin: '概览'
    },
    kicker: {
      standalone: 'Workspace',
      plugin: '总览'
    },
    description: '查看账号在线状态、窗口压力、近期异常和工作入口。',
    standaloneSection: 'workspace',
    standaloneOrder: 10,
    pluginArea: 'primary',
    pluginOrder: 10
  },
  accounts: {
    key: 'accounts',
    path: '/accounts',
    alias: '/dashboard/plugins/wechathlink/accounts',
    permission: '/dashboard/plugins/wechathlink/accounts',
    labels: {
      standalone: '接入',
      plugin: '接入'
    },
    kicker: {
      standalone: 'Access',
      plugin: '接入'
    },
    description: '管理微信账号、绑定 Runtime、扫码接入和成员授权。',
    standaloneSection: 'operations',
    standaloneOrder: 20,
    pluginArea: 'primary',
    pluginOrder: 20
  },
  messages: {
    key: 'messages',
    path: '/messages',
    alias: '/dashboard/plugins/wechathlink/messages',
    permission: '/dashboard/plugins/wechathlink/messages',
    labels: {
      standalone: '会话',
      plugin: '会话'
    },
    kicker: {
      standalone: 'Conversation',
      plugin: '会话'
    },
    description: '围绕联系人处理会话、回复窗口、文本与媒体发送。',
    standaloneSection: 'operations',
    standaloneOrder: 30,
    pluginArea: 'primary',
    pluginOrder: 30
  },
  events: {
    key: 'events',
    path: '/events',
    alias: '/dashboard/plugins/wechathlink/events',
    permission: '/dashboard/plugins/wechathlink/events',
    labels: {
      standalone: '投递追踪',
      plugin: '追踪'
    },
    kicker: {
      standalone: 'Tracing',
      plugin: '追踪'
    },
    description: '按账号、联系人和事件类型追踪入站、出站、媒体与链路状态。',
    standaloneSection: 'operations',
    standaloneOrder: 40,
    pluginArea: 'primary',
    pluginOrder: 40
  },
  audits: {
    key: 'audits',
    path: '/audits',
    alias: '/dashboard/plugins/wechathlink/audits',
    permission: '/dashboard/plugins/wechathlink/audits',
    labels: {
      standalone: '审计治理',
      plugin: '审计'
    },
    kicker: {
      standalone: 'Governance',
      plugin: '审计'
    },
    description: '查看操作留痕、失败记录和账号级治理行为。',
    standaloneSection: 'governance',
    standaloneOrder: 50,
    pluginArea: 'utility',
    pluginOrder: 50
  },
  platform: {
    key: 'platform',
    path: '/platform',
    alias: '/dashboard/plugins/wechathlink/platform',
    permission: '/dashboard/plugins/wechathlink/platform',
    labels: {
      standalone: '开放平台',
      plugin: '开放'
    },
    kicker: {
      standalone: 'Open',
      plugin: '开放'
    },
    description: '查看开放接口、健康探针、示例请求与对外接入注意项。',
    standaloneSection: 'governance',
    standaloneOrder: 55,
    pluginArea: 'utility',
    pluginOrder: 55
  },
  settings: {
    key: 'settings',
    path: '/settings',
    alias: '/dashboard/plugins/wechathlink/settings',
    permission: '/dashboard/plugins/wechathlink/settings',
    labels: {
      standalone: '系统配置',
      plugin: '配置'
    },
    kicker: {
      standalone: 'Config',
      plugin: '配置'
    },
    description: '管理运行模式、轮询参数、媒体目录和回调地址。',
    standaloneSection: 'governance',
    standaloneOrder: 60,
    pluginArea: 'utility',
    pluginOrder: 60
  }
})

export const standaloneSections = Object.freeze([
  {
    key: 'workspace',
    label: '工作台',
    description: '先看状态，再进入业务处理。'
  },
  {
    key: 'operations',
    label: '业务链路',
    description: '接入、会话与追踪按操作顺序组织。'
  },
  {
    key: 'governance',
    label: '治理配置',
    description: '收敛审计与系统级配置。'
  }
])

function isEmbeddedPluginContext() {
  try {
    return window.self !== window.top
  } catch (_error) {
    return true
  }
}

export function resolveShellMode(appEnv, pathname = window.location.pathname) {
  const explicit = `${appEnv?.shellMode || ''}`.trim().toLowerCase()
  if (explicit === SHELL_MODE_STANDALONE || explicit === SHELL_MODE_PLUGIN) {
    return explicit
  }
  if (isEmbeddedPluginContext()) {
    return SHELL_MODE_PLUGIN
  }
  return `${pathname || ''}`.startsWith('/dashboard/plugins/wechathlink')
    ? SHELL_MODE_PLUGIN
    : SHELL_MODE_STANDALONE
}

export function resolveNavLabel(item, mode) {
  return item?.labels?.[mode] || item?.labels?.standalone || item?.key || ''
}

export function resolveNavKicker(item, mode) {
  return item?.kicker?.[mode] || item?.kicker?.standalone || ''
}

export function sortByOrder(a, b, field) {
  return Number(a?.[field] || 0) - Number(b?.[field] || 0)
}
