import { defineStore } from 'pinia'

const THEME_KEY = 'coc_theme'        // dark | light | auto
const ACCENT_KEY = 'coc_accent'      // 配色 id
const AUTOLOAD_KEY = 'coc_autoload'  // '1' | '0'：卡片是否自动加载 HTML 实时预览

/** 可选主题色（主色 / 强调色 / 渐变收尾色）
 *  primary 用于深色模式；浅色模式用 primaryLight（更深一档）——浅底上用深色模式的亮蓝，
 *  按钮会发白发灰、白字对比度不足（五期测试反馈：投稿向导按钮明暗不一致）。 */
export const ACCENTS = [
  { id: 'indigo',  name: '靛蓝', primary: '#6c86ff', primaryLight: '#4f6ef7', accent: '#7b5cf0', accent2: '#a855f7' },
  { id: 'violet',  name: '紫罗兰', primary: '#a855f7', primaryLight: '#8b3fd9', accent: '#8b5cf6', accent2: '#d946ef' },
  { id: 'cyan',    name: '青蓝', primary: '#22b8cf', primaryLight: '#0b8fa3', accent: '#3bc9db', accent2: '#4dabf7' },
  { id: 'emerald', name: '翡翠', primary: '#2fbf71', primaryLight: '#128a4d', accent: '#20c997', accent2: '#12b886' },
  { id: 'amber',   name: '琥珀', primary: '#f59f00', primaryLight: '#b45309', accent: '#f76707', accent2: '#e8590c' },
  { id: 'rose',    name: '玫红', primary: '#f0506e', primaryLight: '#d92d50', accent: '#e64980', accent2: '#c2255c' },
  { id: 'slate',   name: '石墨', primary: '#7c8a9c', primaryLight: '#5c6b7a', accent: '#5c6b7a', accent2: '#4a5568' }
]

function sysPrefersLight() {
  try { return window.matchMedia('(prefers-color-scheme: light)').matches } catch (e) { return false }
}

function initialTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    if (saved === 'dark' || saved === 'light' || saved === 'auto') return saved
  } catch (e) {}
  return 'auto'
}

function initialAccent() {
  try {
    const saved = localStorage.getItem(ACCENT_KEY)
    if (ACCENTS.some((a) => a.id === saved)) return saved
  } catch (e) {}
  return 'indigo'
}

function initialAutoLoad() {
  try {
    return localStorage.getItem(AUTOLOAD_KEY) !== '0'
  } catch (e) { return true }
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    theme: initialTheme(),   // dark | light | auto
    accent: initialAccent()
  }),
  getters: {
    /** 实际生效的深色/浅色 */
    resolved: (s) => (s.theme === 'auto' ? (sysPrefersLight() ? 'light' : 'dark') : s.theme),
    isDark() { return this.resolved === 'dark' },
    accentDef: (s) => ACCENTS.find((a) => a.id === s.accent) || ACCENTS[0]
  },
  actions: {
    apply() {
      const root = document.documentElement
      const mode = this.resolved
      root.setAttribute('data-theme', mode)
      root.classList.toggle('dark', mode === 'dark')

      const a = this.accentDef
      // 浅色底上必须用深一档的主色，否则白字按钮对比度不足（见 ACCENTS 注释）
      const primary = mode === 'light' ? (a.primaryLight || a.primary) : a.primary
      root.style.setProperty('--c-primary', primary)
      root.style.setProperty('--c-accent', a.accent)
      root.style.setProperty('--c-accent-2', a.accent2)

      try {
        localStorage.setItem(THEME_KEY, this.theme)
        localStorage.setItem(ACCENT_KEY, this.accent)
      } catch (e) {}
    },
    setTheme(t) {
      this.theme = ['dark', 'light', 'auto'].includes(t) ? t : 'auto'
      this.apply()
    },
    setAccent(id) {
      if (ACCENTS.some((a) => a.id === id)) this.accent = id
      this.apply()
    },
    /** 顶栏按钮：浅色 ↔ 深色（不再在 auto 上打转，避免点一次没反应） */
    toggle() {
      this.theme = this.resolved === 'dark' ? 'light' : 'dark'
      this.apply()
    }
  }
})

// 全局会话区标记（管理后台深色顶栏等）
export const useUiStore = defineStore('ui', {
  state: () => ({
    sidebarCollapsed: (() => {
      try { return localStorage.getItem('coc_sidebar') === '1' } catch (e) { return false }
    })(),
    unreadTotal: 0,
    /** 卡片是否自动加载 HTML 实时预览（设置页可关，弱网/省流友好） */
    autoLoad: initialAutoLoad(),
    /** 登录引导弹窗（访客点互动 → 弹这个，而不是静默失败） */
    loginPrompt: { visible: false, reason: '', redirect: '' }
  }),
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      try { localStorage.setItem('coc_sidebar', this.sidebarCollapsed ? '1' : '0') } catch (e) {}
    },
    setAutoLoad(v) {
      this.autoLoad = !!v
      try { localStorage.setItem(AUTOLOAD_KEY, this.autoLoad ? '1' : '0') } catch (e) {}
    },
    promptLogin(reason, redirect) {
      this.loginPrompt = { visible: true, reason: reason || '该操作需要登录', redirect: redirect || '' }
    },
    closeLoginPrompt() { this.loginPrompt.visible = false }
  }
})
