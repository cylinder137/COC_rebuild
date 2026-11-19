import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * API 基址：默认走同源 /api（nginx 反代到后端）。
 * 这样构建产物里不写死域名 —— 官网、cn.* 中继域、App 内都可以复用同一份 dist，
 * 也顺手绕开跨域。需要指向别处（如本地直连 8090）时在 .env 里显式指定。
 */
/**
 * 运行环境判定：App/Capacitor 外壳里页面源是 https://localhost，
 * 此时同源相对路径 /api 会指向壳内部 → 全部接口 404/超时。
 * 因此原生壳一律回落到线上绝对地址；网页端继续用同源。
 */
const PROD_ORIGIN = 'https://collectionofcreations.uk'
const IS_NATIVE_SHELL = (() => {
  try {
    if (typeof window === 'undefined') return false
    const cap = window.Capacitor
    if (cap && typeof cap.isNativePlatform === 'function' && cap.isNativePlatform()) return true
    if (window.location.protocol === 'capacitor:') return true
    const h = window.location.hostname
    return h === 'localhost' || h === '127.0.0.1'
  } catch (e) {
    return false
  }
})()
const ENV_API_BASE = import.meta.env.VITE_API_BASE || ''
const ENV_WS_BASE = import.meta.env.VITE_WS_BASE || ''
export const API_BASE =
  ENV_API_BASE && /^https?:\/\//i.test(ENV_API_BASE)
    ? ENV_API_BASE
    : IS_NATIVE_SHELL
      ? PROD_ORIGIN + '/api/v1'
      : (ENV_API_BASE || '/api/v1')
export const WS_BASE =
  ENV_WS_BASE && /^wss?:\/\//i.test(ENV_WS_BASE)
    ? ENV_WS_BASE
    : IS_NATIVE_SHELL
      ? PROD_ORIGIN.replace('https://', 'wss://')
      : (typeof location !== 'undefined'
        ? `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`
        : '')
export const ZONE = import.meta.env.VITE_ZONE || 'main'
/** 资源/接口的统一源：网页端为空串（同源相对），原生壳为线上绝对源 */
export const ORIGIN = IS_NATIVE_SHELL ? PROD_ORIGIN : ''
export const IS_USER_ZONE = ZONE === 'user'

const TOKEN_KEY = 'coc_token'

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY) || '' } catch (e) { return '' }
}
export function setToken(t) {
  try { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY) } catch (e) {}
}

const http = axios.create({ baseURL: API_BASE, timeout: 20000 })

/**
 * 轻量遥测：把前端错误以 1px 图片请求打到 nginx（只落 access.log 的 query，不写库），
 * 目的：App 里出问题时不靠截图也能从服务器日志看到真实报错。
 */
export function reportClientError(kind, detail) {
  try {
    const d = String(detail == null ? '' : detail).slice(0, 300)
    const url = (ORIGIN || '') + '/__coc_err?k=' + encodeURIComponent(kind) + '&d=' + encodeURIComponent(d) + '&t=' + Date.now()
    const img = new Image()
    img.src = url
  } catch (e) {}
}

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
    if (!res.config?.silent) ElMessage.error(body.message || '哎呀，出了点小状况，再试一次吧～')
    return Promise.reject(Object.assign(new Error(body.message || '请求失败'), { code: body.code, business: true }))
  },
  (err) => {
    const status = err?.response?.status
    const msg = err?.response?.data?.message
    // 登录接口自身返回 401（账号或密码错误）不触发「登录已失效」跳转，
    // 由调用方决定后续动作（如引导去注册）
    if (status === 401 && err.config?.skipAuthRedirect) {
      if (!err.config?.silent) ElMessage.error(msg || '账号或密码错误')
      return Promise.reject(Object.assign(err, { code: 401, http401: true }))
    }
    if (status === 401) {
      setToken('')
      if (!redirecting) {
        redirecting = true
        const base = import.meta.env.BASE_URL || '/'
        const here = window.location.pathname + window.location.search
        const loginPath = `${base}login`.replace(/\/{2,}/g, '/')
        const alreadyLogin = window.location.pathname.startsWith(loginPath)
        ElMessage.warning('登录过期啦，重新登录一下吧～')
        setTimeout(() => {
          redirecting = false
          // 已经在登录页就不要再跳（避免 ?redirect=/login 自我循环）
          window.location.href = alreadyLogin ? loginPath : `${loginPath}?redirect=${encodeURIComponent(here)}`
        }, 400)
      }
      return Promise.reject(Object.assign(err, { code: 401, http401: true }))
    }
    if (!err.config?.silent) {
      if (status === 403) ElMessage.error(msg || '这个门暂时打不开哦～')
      else if (status === 404) ElMessage.error(msg || '找了一圈，没找到你要的东西～')
      else if (err.code === 'ECONNABORTED') ElMessage.error('信号有点慢，等得花儿都谢了～ 稍后再试一次吧')
      else if (!err.response) ElMessage.error('啊哦，网络离家出走啦～ 检查一下信号再试试？')
      else ElMessage.error(msg || '这次没成功，稍后再试试吧～')
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
