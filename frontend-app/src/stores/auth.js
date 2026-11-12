import { defineStore } from 'pinia'
import { authApi, safe } from '@/api'
import { getToken, setToken } from '@/api/http'

const USER_KEY = 'coc_user'

function readCached() {
  try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null') } catch (e) { return null }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: readCached(),      // {user_id,qq_account,nickname,type,avatar_path}
    mustChangePassword: false,
    loaded: false
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.user?.type === 3,
    isDeveloper: (s) => s.user?.type === 2 || s.user?.type === 3,
    /** 访客 = 没有 token。任何互动都要先登录 */
    isGuest: (s) => !s.token,
    displayName: (s) => s.user?.nickname || s.user?.qq_account || '访客',
    /** 登录后落地页：type 2 → 首页/我的作品；type 3 → 管理后台 */
    landingPath: (s) => (s.user?.type === 3 ? '/admin' : '/me/projects')
  },
  actions: {
    persist() {
      try {
        this.user ? localStorage.setItem(USER_KEY, JSON.stringify(this.user))
                  : localStorage.removeItem(USER_KEY)
      } catch (e) {}
    },
    async login(qq_account, password) {
      const data = await authApi.login({ qq_account, password })
      this.token = data.token
      setToken(data.token)
      this.user = data.user
      this.mustChangePassword = !!data.must_change_password
      this.persist()
      this.loaded = true
      return data
    },
    async fetchMe() {
      if (!this.token) { this.loaded = true; return null }
      const r = await safe(authApi.me())
      if (r.ok && r.data) {
        this.user = { ...(this.user || {}), ...r.data }
        this.persist()
      } else if (r.error?.http401) {
        this.logout(false)
      }
      this.loaded = true
      return this.user
    },
    logout(redirect = true) {
      this.token = ''
      this.user = null
      this.mustChangePassword = false
      setToken('')
      this.persist()
      if (redirect) window.location.href = '/'
    },
    /** 未登录时统一走这里：弹登录引导（不静默失败） */
    requireLogin(router, reason = '该操作需要登录') {
      if (this.isLoggedIn) return true
      const here = router.currentRoute.value.fullPath
      router.push({ path: '/login', query: { redirect: here, reason } })
      return false
    }
  }
})
