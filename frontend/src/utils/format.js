/* 项目类型 / 状态 / 数字格式化 —— 全站共用口径 */
import { ORIGIN, SITE_ORIGIN } from '@/api/http'

export const TYPE_LABEL = { html: 'HTML', scratch: 'Scratch', structure: 'MC 结构', mcstructure: 'MC 结构' }

export const TYPE_COLOR = {
  html: 'var(--c-type-html)',
  scratch: 'var(--c-type-scratch)',
  structure: 'var(--c-type-structure)',
  mcstructure: 'var(--c-type-structure)'
}

export const TYPE_ICON = { html: 'Monitor', scratch: 'Cpu', structure: 'Box' }

/** 项目状态：0 草稿 / 1 待审 / 2 已发布 / 3 已下架 / 4 已拒绝 */
export const STATUS_MAP = {
  0: { label: '草稿', type: 'info' },
  1: { label: '待审', type: 'warning' },
  2: { label: '已发布', type: 'success' },
  3: { label: '已下架', type: 'danger' },
  4: { label: '已拒绝', type: 'danger' }
}

export function statusOf(s) {
  return STATUS_MAP[s] || { label: `未知(${s})`, type: 'info' }
}

export function auditModeOf(type) {
  return type === 'html' ? 'manual' : 'auto'
}

export function isAutoApproved(type) {
  return type === 'scratch'
}

/* ---------------- 素材（五期） ----------------
   素材类型只有 5 种：图片 / 动图(gif) / 视频 / 音频 / JSON。
   直链由后端给出（/media/{id}/{file}），nginx 直接静态输出，
   可以直接写进用户自己的 HTML：<img src="https://站点/media/12/a.png"> */
export const ASSET_TYPE = {
  image: { label: '图片', icon: 'Picture', color: 'var(--c-type-html)' },
  gif: { label: '动图', icon: 'PictureFilled', color: 'var(--c-type-scratch)' },
  video: { label: '视频', icon: 'VideoCamera', color: '#f97316' },
  audio: { label: '音频', icon: 'Headset', color: '#22c55e' },
  json: { label: 'JSON', icon: 'Document', color: '#38bdf8' }
}

export function assetTypeOf(t) {
  return ASSET_TYPE[t] || { label: '文件', icon: 'Document', color: 'var(--c-text-3)' }
}

/** 素材直链（**绝对地址**）。
 * 后端只回相对路径 `/media/{id}/{file}`；但这个地址的用途是「复制给用户，写进他自己的 HTML」，
 * 所以必须是线上唯一站点源。曾经用 window.location.origin 拼：
 *   App 壳内 → https://localhost/media/14/x.png（壳里根本没有这个文件，图裂 + 链接作废）
 *   本地调试 → http://127.0.0.2:8084/media/... （只有本机能打开）
 * 五期反馈「素材系统使用的链接全是 localhost」就是这个原因。 */
export function mediaUrlOf(a) {
  if (!a) return ''
  const raw = a.url
    ? a.url
    : (a.asset_id && a.file_name
      ? `/media/${a.asset_id}/${a.file_name.split('/').map(encodeURIComponent).join('/')}`
      : '')
  return absoluteUrl(raw)
}

/** 相对路径 → 线上绝对地址；顺带把本机源（localhost / 127.0.0.1）的历史链接改写到线上 */
export function absoluteUrl(p) {
  if (!p) return ''
  const s = String(p)
  if (s.startsWith('data:') || s.startsWith('blob:')) return s
  if (!/^https?:\/\//i.test(s)) return SITE_ORIGIN + (s.startsWith('/') ? s : '/' + s)
  try {
    const u = new URL(s)
    if (/^(localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1\])$/i.test(u.hostname)) {
      return SITE_ORIGIN + u.pathname + u.search + u.hash
    }
    return s
  } catch (e) {
    return s
  }
}

/** 12345 -> 1.2万；带 tooltip 用 fullNumber */
export function compactNum(n) {
  const v = Number(n || 0)
  if (v < 10000) return String(v)
  return (v / 10000).toFixed(1).replace(/\.0$/, '') + '万'
}

export function fmtSize(bytes) {
  const b = Number(bytes || 0)
  if (!b) return '—'
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  if (b < 1024 ** 3) return (b / 1024 ** 2).toFixed(1) + ' MB'
  return (b / 1024 ** 3).toFixed(2) + ' GB'
}

export function fmtTime(t) {
  if (!t) return '—'
  const d = new Date(typeof t === 'number' ? t : String(t).replace(' ', 'T'))
  if (isNaN(d.getTime())) return String(t)
  const diff = Date.now() - d.getTime()
  const min = 60 * 1000, hour = 60 * min, day = 24 * hour
  if (diff < min) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / min)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function fmtDateTime(t) {
  if (!t) return '—'
  const d = new Date(typeof t === 'number' ? t : String(t).replace(' ', 'T'))
  if (isNaN(d.getTime())) return String(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 后端可能给绝对/相对 URL；统一成可直接放 <img>/<iframe> 的地址 */
const API_ORIGIN = (() => {
  // 与 api/http.js 保持同一口径：原生壳(Capacitor)里页面源是 https://localhost，
  // 所有相对路径都会打到壳内部 → 封面/头像/沙箱 iframe 全部失效，必须改成线上绝对源。
  if (ORIGIN) return ORIGIN
  // 生产构建 VITE_API_BASE=/api/v1 是**相对**基址 → 与页面同源，这里必须返回空串，
  // 让 fileUrl 生成相对路径（/downloads/x.apk、/avatars/x.png 由 nginx 直出）。
  // ⚠️ 曾经写成 new URL(base, 'http://127.0.0.1:8090').origin：相对基址下这个「解析基准」
  // 会被当成真实源返回，产物里就写死了 http://127.0.0.1:8090 —— 远程设备上封面/头像全裂、
  // 下载链接打到后端 8090 的 /downloads/* 变成 {"code":404,...}。
  const base = import.meta.env.VITE_API_BASE || ''
  if (!/^https?:\/\//i.test(base)) return ''
  try { return new URL(base).origin } catch (e) { return '' }
})()

export function fileUrl(p) {
  if (!p) return ''
  if (/^https?:\/\//i.test(p) || p.startsWith('data:')) return p
  return API_ORIGIN + (p.startsWith('/') ? p : '/' + p)
}

/**
 * 沙箱运行地址。
 * 必须是「绝对地址」：App 端页面源是 https://localhost（Capacitor 虚拟域名），
 * 相对路径会打到 App 自己的本地服务器，拿回应用自身的 index.html，表现为白屏。
 */
export function runUrlOf(projectId) {
  if (!projectId) return ''
  return `${API_ORIGIN}/api/v1/run/${projectId}/`
}

export { API_ORIGIN }

/** 从任意后端字段里抠出列表（兼容 list / records / items / data 包装） */
export function pickList(res) {
  if (!res) return []
  if (Array.isArray(res)) return res
  return res.list || res.records || res.items || res.data || []
}
export function pickTotal(res) {
  if (!res) return 0
  if (Array.isArray(res)) return res.length
  return res.total ?? pickList(res).length
}

export const REASON_TYPES = [
  { value: 'illegal', label: '违法违规内容' },
  { value: 'porn', label: '色情低俗' },
  { value: 'violence', label: '暴力血腥' },
  { value: 'malicious', label: '恶意代码 / 挂马' },
  { value: 'infringe', label: '侵权盗用' },
  { value: 'spam', label: '垃圾广告' },
  { value: 'other', label: '其他' }
]

export const REPORT_STATUS = {
  0: { label: '待处理', type: 'warning' },
  1: { label: '已处理', type: 'success' },
  2: { label: '已驳回', type: 'info' }
}
