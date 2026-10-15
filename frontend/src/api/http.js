import axios from 'axios'
import { ElMessage } from 'element-plus'

export const API_BASE =
  import.meta.env.VITE_API_BASE || 'http://127.0.0.1:8090/api/v1'
export const WS_BASE = import.meta.env.VITE_WS_BASE || 'ws://127.0.0.1:8090'
export const ZONE = import.meta.env.VITE_ZONE || 'main'
export const IS_USER_ZONE = ZONE === 'user'

const TOKEN_KEY = 'coc_token'

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY) || '' } catch (e) { return '' }
}
export function setToken(t) {
  try { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY) } catch (e) {}
}

const http = axios.create({ baseURL: API_BASE, timeout: 20000 })

http.interceptors.request.use((cfg) => {
  const t = getToken()
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

// 401 只跳一次，避免并发请求连环跳
let redirecting = false

http.interceptors.response.use(
  (res) => {
    const body = res.data
    // 二进制/非包装响应直接放行
    if (!body || typeof body !== 'object' || !('code' in body)) return body
    if (body.code === 0) return body.data
    // 业务错误：统一提示，但把错误抛给调用方以便显示区块级错误态
    if (!res.config?.silent) ElMessage.error(body.message || '请求失败')
    return Promise.reject(Object.assign(new Error(body.message || '请求失败'), { code: body.code, business: true }))
  },
  (err) => {
    const status = err?.response?.status
    const msg = err?.response?.data?.message
    if (status === 401) {
      setToken('')
      if (!redirecting) {
        redirecting = true
        const here = window.location.pathname + window.location.search
        const alreadyLogin = window.location.pathname.startsWith('/login')
        ElMessage.warning('登录已失效，请重新登录')
        setTimeout(() => {
          redirecting = false
          // 已经在登录页就不要再跳（避免 ?redirect=/login 自我循环）
          window.location.href = alreadyLogin ? '/login' : `/login?redirect=${encodeURIComponent(here)}`
        }, 400)
      }
      return Promise.reject(Object.assign(err, { code: 401, http401: true }))
    }
    if (!err.config?.silent) {
      if (status === 403) ElMessage.error(msg || '没有权限')
      else if (status === 404) ElMessage.error(msg || '资源不存在')
      else if (err.code === 'ECONNABORTED') ElMessage.error('请求超时，请稍后重试')
      else if (!err.response) ElMessage.error('无法连接后端服务（8090 可能未启动）')
      else ElMessage.error(msg || `请求失败（${status}）`)
    }
    return Promise.reject(err)
  }
)

/**
 * 安全请求：失败不抛出，返回 { ok, data, error }。
 * 页面用它做「优雅降级」——后端没起来时显示空状态 + 重试，而不是白屏报错堆栈。
 */
export async function safe(promise, fallback = null) {
  try {
    const data = await promise
    return { ok: true, data, error: null }
  } catch (e) {
    return { ok: false, data: fallback, error: e }
  }
}

export default http
