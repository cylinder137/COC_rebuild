/* 项目类型 / 状态 / 数字格式化 —— 全站共用口径 */

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
  return type === 'scratch' || type === 'structure' || type === 'mcstructure'
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
  const base = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:8090/api/v1'
  try { return new URL(base).origin } catch (e) { return '' }
})()

export function fileUrl(p) {
  if (!p) return ''
  if (/^https?:\/\//i.test(p) || p.startsWith('data:')) return p
  return API_ORIGIN + (p.startsWith('/') ? p : '/' + p)
}

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
