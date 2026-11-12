/**
 * 卡片实时预览的全局名额队列。
 *
 * 必须放在模块级：`<script setup>` 里的变量是每个组件实例各一份，
 * 写在组件内会导致「每个卡片都以为自己有 4 个名额」，等于没有限制。
 */
const MAX_LIVE = 4
let used = 0
const waiting = new Set()

/**
 * 申请一个预览名额。
 * @param {{ live:boolean, queued:boolean }} handle 调用方句柄（需可写 live/queued）
 * @param {() => void} onGrant 拿到名额时执行（通常是挂上 iframe）
 */
export function acquire(handle, onGrant) {
  if (handle.live) return
  if (used < MAX_LIVE) {
    used++
    handle.live = true
    ;(onGrant || handle.onGrant)?.()
    return
  }
  if (!handle.queued) {
    handle.queued = true
    waiting.add(handle)
  }
}

/** 归还名额，并把名额直接转交给排队中的下一个，不再走一轮「先减后加」 */
export function release(handle) {
  if (handle.live) {
    handle.live = false
    used = Math.max(0, used - 1)
    const next = waiting.values().next().value
    if (next) {
      waiting.delete(next)
      next.queued = false
      used++
      next.live = true
      next.onGrant?.()
    }
    return
  }
  if (handle.queued) {
    handle.queued = false
    waiting.delete(handle)
  }
}
