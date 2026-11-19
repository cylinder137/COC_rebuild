<template>
  <div class="page">
    <div class="container narrow">
      <h1 class="dl-title">下载造物集 App</h1>
      <p class="muted sm intro">
        造物集 Android 客户端。下面按发布时间从新到旧列出已发布版本，按需选择下载；
        不确定选哪个时，直接下载最新版即可。
      </p>

      <!-- 最新版 -->
      <div class="card latest">
        <div class="latest-main">
          <div class="ver-big mono">v{{ latest.version }}</div>
          <div class="xs dim">
            {{ latest.date }} 发布 · {{ fmtSize(latest.size) }}
            <template v-if="latest.note"> · {{ latest.note }}</template>
          </div>
        </div>
        <a :href="downloadUrl(latest)" download>
          <el-button type="primary" size="large">
            <el-icon class="btn-ico"><Download /></el-icon>下载最新版
          </el-button>
        </a>
      </div>

      <h2 class="section-title hist-title">历史版本</h2>
      <div class="card list">
        <div v-for="r in history" :key="r.version" class="release-row">
          <div class="release-info">
            <div class="release-meta">
              <span class="ver mono">v{{ r.version }}</span>
              <span class="xs dim">{{ r.date }} · {{ fmtSize(r.size) }}</span>
            </div>
            <div v-if="r.note" class="xs dim note">{{ r.note }}</div>
          </div>
          <a :href="downloadUrl(r)" download>
            <el-button size="small" round>
              <el-icon class="btn-ico"><Download /></el-icon>下载
            </el-button>
          </a>
        </div>
      </div>

      <p class="xs dim tip">
        安装提示：Android 升级安装要求 versionCode 不低于已装版本，正常覆盖安装即可保留账号与本地数据；
        若系统提示无法安装，可先卸载旧版再装（卸载会清除 App 本地缓存，账号数据不受影响）。
        下载或安装遇到问题，可通过「联系管理员」反馈。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fileUrl, fmtSize } from '@/utils/format'

/**
 * 历史版本清单（兜底 + 历史列表）。
 * 最新版优先从服务器 /downloads/version.json 读取（nginx 已单独放行），
 * 发版脚本更新该文件后本页自动显示新版本；下面的数组只需在需要保留旧版时维护。
 * size 为安装包字节数，仅用于展示。
 */
const RELEASES = [
  { version: '2.0.6', file: 'coc-app-2.0.6.apk', date: '2026-11-19', size: 3864440, note: '素材库替代 MC 结构板块；项目/素材系列；素材直链一键复制' },
  { version: '2.0.5', file: 'coc-app-2.0.5.apk', date: '2026-11-19', size: 3864431, note: '手机端全屏虚拟按键：双摇杆 + 自定义按键' },
  { version: '2.0.4', file: 'coc-app-2.0.4.apk', date: '2026-11-19', size: 3855581, note: '手机端默认手机版视图，可切电脑版；全屏自动横屏' },
  { version: '2.0.3', file: 'coc-app-2.0.3.apk', date: '2026-11-15', size: 3853343, note: '修复 App 端输入框失效导致的「草稿建不出来」' },
  { version: '2.0.2', file: 'coc-app-2.0.2.apk', date: '2026-11-15', size: 3851337, note: '' },
  { version: '2.0.1', file: 'coc-app-2.0.1.apk', date: '2026-11-15', size: 3850338, note: '' },
  { version: '2.0', file: 'coc-app-2.0.apk', date: '2026-11-15', size: 3850339, note: '修复 App 内接口地址错误（1.9 全功能失效的根因）' },
  { version: '1.9', file: 'coc-app-1.9.apk', date: '2026-11-12', size: 3849917, note: '' },
  { version: '1.8', file: 'coc-app-1.8.apk', date: '2026-11-04', size: 3843631, note: '' },
  { version: '1.7', file: 'coc-app-1.7.apk', date: '2026-10-25', size: 3842847, note: '' },
  { version: '1.6', file: 'coc-app-1.6.apk', date: '2026-10-25', size: 3842856, note: '' },
  { version: '1.5', file: 'coc-app-1.5.apk', date: '2026-10-25', size: 3842837, note: '' },
  { version: '1.4', file: 'coc-app-1.4-rollback.apk', date: '2026-10-19', size: 3840532, note: '1.4 回滚版' },
  { version: '1.2', file: 'coc-app-1.2.apk', date: '2026-10-19', size: 3840189, note: '' },
  { version: '1.1', file: 'coc-app-1.1.apk', date: '2026-10-19', size: 3839527, note: '' },
  { version: '1.0', file: 'coc-app-1.0.apk', date: '2026-10-19', size: 3838132, note: '' }
]

/** 服务器发布信息（version.json），取不到时用静态清单首项兜底 */
const remote = ref(null)
const latest = computed(() => remote.value || RELEASES[0])
const history = computed(() => RELEASES.filter((r) => r.version !== latest.value.version))

onMounted(async () => {
  try {
    const res = await fetch(fileUrl('/downloads/version.json'), { cache: 'no-cache' })
    if (!res.ok) return
    const d = await res.json()
    if (!d?.versionName || !d?.fileName) return
    remote.value = {
      version: String(d.versionName),
      file: String(d.fileName),
      date: String(d.builtAt || d.updatedAt || '').slice(0, 10),
      size: Number(d.size || 0),
      note: d.notes || ''
    }
  } catch (e) {
    /* 取不到就用静态兜底，不影响页面可用 */
  }
})

/** 走 fileUrl：网页端同源相对路径，Capacitor 壳内自动补线上绝对源 */
function downloadUrl(r) {
  return fileUrl(`/downloads/${r.file}`)
}
</script>

<style scoped>
.narrow { max-width: 720px; }
.dl-title { font-size: var(--fs-h1); font-weight: 700; margin: 0 0 8px; }
.intro { margin: 0 0 20px; }
.latest {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  flex-wrap: wrap;
  border-color: var(--c-primary-border);
}
.latest-main { min-width: 0; }
.ver-big { font-size: 26px; font-weight: 700; color: var(--c-primary); line-height: 1.2; margin-bottom: 4px; }
.hist-title { margin: 28px 0 12px; }
.list { padding: 4px 16px; }
.release-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 14px 0; border-bottom: 1px solid var(--c-border);
}
.release-row:last-child { border-bottom: 0; }
.release-info { min-width: 0; }
.release-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ver { font-weight: 600; }
.note { margin-top: 4px; }
.btn-ico { margin-right: 4px; }
.tip { margin-top: 16px; line-height: 18px; }
</style>
