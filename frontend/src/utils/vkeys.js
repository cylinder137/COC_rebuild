/**
 * 虚拟按键（Virtual Pad）字段表与本地方案存取
 *
 * 字段命名规则（用户可见的就是这些字符串）：
 *   space / enter / ctrl / shift      固定别名
 *   a ~ z                             字母键（小写）
 *   0 ~ 9                             数字键
 *   up / down / left / right / tab / esc / alt   方向与功能键（附加，方便做菜单/暂停）
 *
 * 说明：iframe 是 sandbox（无 allow-same-origin），父页面无法 dispatchEvent，
 * 只能把字段解析成 {key, code, keyCode} 后 postMessage 给页面内的桥（coc-key-bridge.js）。
 */

export const VK_STORAGE_KEY = 'coc_vpad_v1'

const ALIASES = {
  space: { label: '空格', key: ' ', code: 'Space', keyCode: 32 },
  enter: { label: '回车', key: 'Enter', code: 'Enter', keyCode: 13 },
  ctrl: { label: 'Ctrl', key: 'Control', code: 'ControlLeft', keyCode: 17 },
  shift: { label: 'Shift', key: 'Shift', code: 'ShiftLeft', keyCode: 16 },
  alt: { label: 'Alt', key: 'Alt', code: 'AltLeft', keyCode: 18 },
  tab: { label: 'Tab', key: 'Tab', code: 'Tab', keyCode: 9 },
  esc: { label: 'Esc', key: 'Escape', code: 'Escape', keyCode: 27 },
  up: { label: '↑', key: 'ArrowUp', code: 'ArrowUp', keyCode: 38 },
  down: { label: '↓', key: 'ArrowDown', code: 'ArrowDown', keyCode: 40 },
  left: { label: '←', key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37 },
  right: { label: '→', key: 'ArrowRight', code: 'ArrowRight', keyCode: 39 }
}

/** 用户输入的别名容错：空格/回车/大写/control 之类 */
const ALIAS_INPUT = {
  ' ': 'space',
  '空格': 'space',
  '空格键': 'space',
  '回车': 'enter',
  '回车键': 'enter',
  'return': 'enter',
  'control': 'ctrl',
  'ctrl键': 'ctrl',
  'shift键': 'shift',
  'esc键': 'esc',
  'escape': 'esc',
  'alt键': 'alt',
  'tab键': 'tab',
  '上': 'up', '↑': 'up',
  '下': 'down', '↓': 'down',
  '左': 'left', '←': 'left',
  '右': 'right', '→': 'right'
}

/** 把用户输入规范化成字段名；不合法返回 '' */
export function normalizeField(raw) {
  let s = String(raw == null ? '' : raw).trim().toLowerCase()
  if (!s) return ''
  if (ALIAS_INPUT[s]) s = ALIAS_INPUT[s]
  if (ALIASES[s]) return s
  if (/^[a-z]$/.test(s)) return s
  if (/^[0-9]$/.test(s)) return s
  return ''
}

/** 字段 → 键盘事件参数；非法返回 null */
export function resolveKey(field) {
  const f = normalizeField(field)
  if (!f) return null
  if (ALIASES[f]) return { field: f, ...ALIASES[f] }
  if (/^[a-z]$/.test(f)) {
    const code = f.charCodeAt(0)
    return { field: f, label: f.toUpperCase(), key: f, code: 'Key' + f.toUpperCase(), keyCode: code - 32 }
  }
  // 数字键（Digit 系列，不区分小键盘）
  const code = f.charCodeAt(0)
  return { field: f, label: f, key: f, code: 'Digit' + f, keyCode: code }
}

/** 字段的显示名 */
export function labelOf(field) {
  const k = resolveKey(field)
  return k ? k.label : String(field || '')
}

/** 两个摇杆：左 = WASD，右 = 方向键。两套按键互不重叠，可同时操作 */
export const JOY_DEFS = {
  joyA: {
    id: 'joyA',
    name: '左摇杆',
    desc: 'WASD',
    dirs: { up: 'w', down: 's', left: 'a', right: 'd' }
  },
  joyB: {
    id: 'joyB',
    name: '右摇杆',
    desc: '方向键',
    dirs: { up: 'up', down: 'down', left: 'left', right: 'right' }
  }
}

/** 新建按键时的快捷候选 */
export const QUICK_FIELDS = ['space', 'enter', 'ctrl', 'shift', 'z', 'x', 'c', '1', '2', '3']

export function defaultScheme() {
  return {
    v: 1,
    // 是否上次处于「显示虚拟按键」状态
    on: false,
    joyA: { show: true, opacity: 0.55, size: 132, x: 18, y: 72 },
    joyB: { show: false, opacity: 0.55, size: 132, x: 82, y: 72 },
    buttons: []
  }
}

function num(v, def, min, max) {
  const n = Number(v)
  if (!Number.isFinite(n)) return def
  return Math.min(max, Math.max(min, n))
}

/** 读取本地方案：逐字段兜底，避免旧版本数据缺字段把界面弄崩 */
export function loadScheme() {
  const base = defaultScheme()
  let raw = null
  try { raw = JSON.parse(localStorage.getItem(VK_STORAGE_KEY) || 'null') } catch (e) { raw = null }
  if (!raw || typeof raw !== 'object') return base

  const joy = (src, def) => ({
    show: typeof src?.show === 'boolean' ? src.show : def.show,
    opacity: num(src?.opacity, def.opacity, 0.15, 1),
    size: num(src?.size, def.size, 56, 200),
    x: num(src?.x, def.x, 0, 100),
    y: num(src?.y, def.y, 0, 100)
  })

  const buttons = Array.isArray(raw.buttons)
    ? raw.buttons
        .filter((b) => b && normalizeField(b.field))
        .slice(0, 12)
        .map((b, i) => ({
          id: String(b.id || 'k' + i + '_' + Math.random().toString(36).slice(2, 6)),
          field: normalizeField(b.field),
          label: b.label ? String(b.label).slice(0, 6) : labelOf(b.field),
          opacity: num(b.opacity, 0.6, 0.15, 1),
          size: num(b.size, 64, 36, 160),
          x: num(b.x, 66 + (i % 3) * 12, 0, 100),
          y: num(b.y, 66 + Math.floor(i / 3) * 16, 0, 100)
        }))
    : []

  return {
    v: 1,
    on: !!raw.on,
    joyA: joy(raw.joyA, base.joyA),
    joyB: joy(raw.joyB, base.joyB),
    buttons
  }
}

export function saveScheme(scheme) {
  try {
    localStorage.setItem(VK_STORAGE_KEY, JSON.stringify(scheme))
    return true
  } catch (e) {
    return false
  }
}
