/**
 * 平台判定：区分「浏览器里的官网」和「Capacitor 打的 App 壳」。
 *
 * App 端页面源是 https://localhost（Capacitor 虚拟域名），
 * 所以光看 hostname 就能判定；同时也认 Capacitor 原生桥。
 */
export const IS_APP = (() => {
  try {
    if (typeof window === 'undefined') return false
    if (window.Capacitor?.isNativePlatform?.()) return true
    return window.location.protocol === 'capacitor:' || window.location.hostname === 'localhost'
  } catch (e) {
    return false
  }
})()

/** 触屏 / 窄屏：App 端的双列布局也照这个判断走 */
export const IS_TOUCH = (() => {
  try {
    return window.matchMedia?.('(max-width: 767px)').matches || 'ontouchstart' in window
  } catch (e) {
    return false
  }
})()

/** 构建标记：显示在顶栏品牌名旁边，用来一眼确认装的是哪个包 */
export const APP_BUILD = 'v1.5'
