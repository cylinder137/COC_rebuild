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
