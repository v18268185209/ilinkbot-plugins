import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiBaseUrl = env.VITE_API_BASE_URL || '/api'
  const proxyTarget = `${env.VITE_PROXY_TARGET || ''}`.trim()
  const devPort = Number(env.VITE_DEV_PORT || 5173)

  return {
    plugins: [vue()],
    base: env.VITE_PUBLIC_BASE || './',
    server: {
      host: '0.0.0.0',
      port: Number.isFinite(devPort) ? devPort : 5173,
      strictPort: true,
      proxy: proxyTarget
        ? {
            [apiBaseUrl]: {
              target: proxyTarget,
              changeOrigin: true
            }
          }
        : undefined
    }
  }
})
