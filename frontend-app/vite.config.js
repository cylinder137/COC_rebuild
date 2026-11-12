import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 两种运行模式：
//   npm run dev       -> mode=development, 端口 8085（主站）
//   npm run dev:user  -> mode=user,        端口 8086（用户内容区）
// 同一套代码，用 VITE_ZONE 区分区域（main / user），页面按 zone 做轻微差异（不搞两套代码）。
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
    },
    server: {
      host: '127.0.0.1',
      port: mode === 'user' ? 8086 : 8085,
      strictPort: true,
      proxy: {
        // 开发期可选：把 /api 代理到 8090（默认不启用，前端直连 VITE_API_BASE）
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://127.0.0.1:8090',
          changeOrigin: true,
          configure: () => {}
        }
      }
    },
    build: {
      outDir: mode === 'user' ? 'dist-user' : 'dist',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          manualChunks: {
            vendor: ['vue', 'vue-router', 'pinia', 'axios'],
            element: ['element-plus', '@element-plus/icons-vue'],
            charts: ['echarts']
          }
        }
      }
    }
  }
})
