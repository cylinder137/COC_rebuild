import { defineStore } from 'pinia'

const KEY = 'coc_theme'

function initial() {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved) return saved
    return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
  } catch (e) { return 'dark' }
}

export const useThemeStore = defineStore('theme', {
  state: () => ({ theme: initial() }),
  getters: { isDark: (s) => s.theme === 'dark' },
  actions: {
    apply() {
      const root = document.documentElement
      root.setAttribute('data-theme', this.theme)
      // Element Plus 深色类
      root.classList.toggle('dark', this.theme === 'dark')
      try { localStorage.setItem(KEY, this.theme) } catch (e) {}
    },
    toggle() {
      this.theme = this.theme === 'dark' ? 'light' : 'dark'
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
    /** 登录引导弹窗（访客点互动 → 弹这个，而不是静默失败） */
    loginPrompt: { visible: false, reason: '', redirect: '' }
  }),
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      try { localStorage.setItem('coc_sidebar', this.sidebarCollapsed ? '1' : '0') } catch (e) {}
    },
    promptLogin(reason, redirect) {
      this.loginPrompt = { visible: true, reason: reason || '该操作需要登录', redirect: redirect || '' }
    },
    closeLoginPrompt() { this.loginPrompt.visible = false }
  }
})
