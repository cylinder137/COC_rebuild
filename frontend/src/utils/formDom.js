/**
 * 输入框取值兜底（Android WebView 专用补丁）
 *
 * 现象：App 里中文输入法打字，输入框确实显示出了字，但 v-model 始终是空值。
 * 表现就是「填完标题点保存，提示请填写标题」，按钮一直是灰的，请求根本没发出去。
 *
 * 原因（两条叠加）：
 *   1. html/body 上的 -webkit-user-select:none 会让 Android WebView 把 input 判成
 *      「可聚焦但非编辑」，input 事件不上报；
 *   2. Element Plus 的 el-input 用 isComposing 标记过滤输入事件，WebView 里
 *      compositionend 偶发不触发，标记永远停在 true，之后所有 input 全被吞掉。
 *
 * 对策：绕开组件内部状态，直接在原生 <input>/<textarea> 上挂事件监听，
 * 把 DOM 里的真实值回填到响应式模型；提交前再整体拉一次，确保「看到什么就提交什么」。
 *
 * 用法：
 *   <div data-field="title"><el-input v-model="form.title" /></div>
 *   const detach = attachFieldSync(['title'], (name, value) => { form[name] = value })
 *   onBeforeUnmount(detach)
 *   pullFields(form, ['title', 'summary'])   // 提交前兜底
 */

const FIELD_ATTR = 'data-field'
const INPUT_SELECTOR = 'input:not([type="hidden"]):not([type="file"]), textarea'

/** 取字段容器里的原生控件（隐藏/不可见时返回 null）
 *  注意：Element Plus 的 el-input 会把非 class/style 的 attrs 透传到内部的
 *  <input>/<textarea> 上，所以 data-field 可能落在控件本身而不是外层容器，两种都要认。 */
export function fieldInput(name, root) {
  if (typeof document === 'undefined') return null
  const scope = root || document
  const box = scope.querySelector(`[${FIELD_ATTR}="${name}"]`)
  if (!box) return null
  if (box.matches(INPUT_SELECTOR)) return box
  return box.querySelector(INPUT_SELECTOR)
}

function visible(el) {
  if (!el) return false
  if (el.offsetParent === null && getComputedStyle(el).position !== 'fixed') return false
  return !el.disabled && !el.readOnly
}

/** 读单个字段的 DOM 实际值；控件不存在返回 null（用于区分「空字符串」与「读不到」） */
export function readDomField(name, root) {
  const el = fieldInput(name, root)
  if (!el) return null
  return String(el.value == null ? '' : el.value)
}

/**
 * 把 DOM 值回填进模型。
 * @param {object} model  响应式对象
 * @param {string} name   字段名（同时是 data-field 名）
 * @param {object} opts   { trim?: boolean, force?: boolean }
 *        force=true 时即使 DOM 为空也覆盖模型（提交前用，保证以用户所见为准）
 * @returns {boolean} 是否发生了覆盖
 */
export function pullField(model, name, opts = {}) {
  const { trim = false, force = false } = opts
  const el = fieldInput(name)
  if (!el) return false
  if (!force && !visible(el)) return false
  let v = String(el.value == null ? '' : el.value)
  if (trim) v = v.trim()
  // 未要求强制覆盖时，只补「模型丢了值」的情况，避免打断组件自身正在进行的输入
  if (!force && model[name] === v) return false
  if (!force && model[name] && v) {
    // 两边都有值但不同：以 DOM 为准（用户看到的是 DOM），除非模型更长且刚由代码写入
    if (String(model[name]).length > v.length && String(model[name]).startsWith(v)) return false
  }
  if (model[name] === v) return false
  model[name] = v
  return true
}

/** 批量回填，返回被修正过的字段名数组（便于遥测与调试） */
export function pullFields(model, names, opts = {}) {
  const fixed = []
  for (const n of names) {
    try {
      if (pullField(model, n, opts)) fixed.push(n)
    } catch (e) { /* 单字段失败不影响其它字段 */ }
  }
  return fixed
}

/**
 * 在原生控件上挂监听，绕过组件内部的 isComposing 状态。
 * @param {string[]} names            字段名列表
 * @param {(name:string, value:string)=>void} apply 回填函数
 * @returns {() => void} 解绑函数
 */
export function attachFieldSync(names, apply) {
  if (typeof document === 'undefined' || typeof apply !== 'function') return () => {}
  const cleanups = []

  for (const name of names) {
    const el = fieldInput(name)
    if (!el) continue

    // 输入中不写模型：写回会触发组件重设 input.value，打断中文输入法组词
    const onInput = (e) => {
      if (e && e.isComposing) return
      apply(name, String(el.value == null ? '' : el.value))
    }
    // 组词结束：等一拍让原生值落定再回填（部分 WebView 的 compositionend 早于最终 input）
    const onCompositionEnd = () => {
      setTimeout(() => {
        if (document.body.contains(el)) apply(name, String(el.value == null ? '' : el.value))
      }, 0)
    }
    const onBlur = () => apply(name, String(el.value == null ? '' : el.value))

    el.addEventListener('input', onInput)
    el.addEventListener('compositionend', onCompositionEnd)
    el.addEventListener('change', onBlur)
    el.addEventListener('blur', onBlur)

    cleanups.push(() => {
      el.removeEventListener('input', onInput)
      el.removeEventListener('compositionend', onCompositionEnd)
      el.removeEventListener('change', onBlur)
      el.removeEventListener('blur', onBlur)
    })
  }

  return () => cleanups.forEach((fn) => { try { fn() } catch (e) {} })
}

/** 聚焦某字段（校验失败时把光标送过去，手机上还会自动弹键盘） */
export function focusField(name) {
  const el = fieldInput(name)
  if (!el) return
  try { el.focus({ preventScroll: false }) } catch (e) { try { el.focus() } catch (x) {} }
}
