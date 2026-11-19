import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElIcons from '@element-plus/icons-vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import './styles/tokens.css'
import './styles/global.css'

import App from './App.vue'
import router from './router'
import { IS_APP, APP_BUILD } from './utils/platform'

/* App 内打个标记：原生层已经做了安全区让位，CSS 侧不再重复补 */
if (IS_APP && typeof document !== 'undefined') {
  document.documentElement.classList.add('is-native')
  /* 固定渲染宽度：禁止双指缩放把页面拉到屏幕外（横向滚动由 global.css 的
     html.is-native 规则整体禁用，内容按设备宽度贴合渲染） */
  const vp = document.querySelector('meta[name="viewport"]')
  if (vp) {
    vp.setAttribute(
      'content',
      'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover'
    )
  }
}

/* 构建版本号挂到 window：App 顶栏按需求不再显示标题，
   需要确认装的是哪个包时在 WebView 控制台看 window.__COC_BUILD */
if (typeof window !== 'undefined') {
  window.__COC_BUILD = APP_BUILD
  // 原生壳里把未捕获异常也打到服务器日志，便于无截图排查
  if (IS_APP) {
    window.addEventListener('error', (e) => {
      try { import('./api/http').then(m => m.reportClientError('window', (e.message || '') + ' @' + (e.filename || '') + ':' + (e.lineno || 0))) } catch (x) {}
    })
    window.addEventListener('unhandledrejection', (e) => {
      const r = e.reason
      const msg = r && (r.stack || r.message || r)
      try { import('./api/http').then(m => m.reportClientError('promise', String(msg))) } catch (x) {}
    })
  }
}

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

for (const [name, comp] of Object.entries(ElIcons)) {
  app.component(name, comp)
}

// 全局兜底：任何未捕获的渲染错误都不要白屏
app.config.errorHandler = (err) => {
  console.error('[coc] render error:', err)
}

app.mount('#app')
