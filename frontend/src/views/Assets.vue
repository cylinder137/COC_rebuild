<template>
  <div class="page">
    <div class="container">
      <div class="row between center wrap gap12 head">
        <div>
          <h1 class="page-title">素材库</h1>
          <p class="sm dim intro">
            站内公开素材。每条都能一键复制<strong>直链</strong>，直接写进你自己的 HTML：
            <code class="inline">&lt;img src=".../media/12/x.png"&gt;</code>
          </p>
        </div>
        <div class="row gap8">
          <router-link v-if="auth.isLoggedIn" to="/me/materials">
            <el-button><el-icon><Files /></el-icon>&nbsp;我的素材</el-button>
          </router-link>
        </div>
      </div>

      <div class="toolbar row between center wrap gap12">
        <el-radio-group v-model="type" size="small" @change="reload">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="image">图片</el-radio-button>
          <el-radio-button value="gif">动图</el-radio-button>
          <el-radio-button value="video">视频</el-radio-button>
          <el-radio-button value="audio">音频</el-radio-button>
          <el-radio-button value="json">JSON</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="keyword"
          placeholder="搜素材名"
          clearable
          style="max-width:240px"
          @keyup.enter="reload"
          @clear="reload"
        >
          <template #append>
            <el-button @click="reload"><el-icon><Search /></el-icon></el-button>
          </template>
        </el-input>
      </div>

      <AppState
        :loading="loading && !list.length"
        :error="error"
        :empty="!loading && !error && !list.length"
        empty-text="还没有公开素材"
        empty-desc="上传素材时把「公开」打开，就会出现在这里。"
        :skeleton-count="8"
        @retry="reload"
      />

      <div class="grid">
        <article v-for="a in list" :key="a.asset_id" class="card asset-card" @click="openPreview(a)">
          <div class="thumb">
            <img v-if="isImg(a)" :src="urlOf(a)" :alt="a.name" loading="lazy" />
            <video v-else-if="a.asset_type === 'video'" :src="urlOf(a)" preload="metadata" muted playsinline />
            <div v-else class="ph">
              <el-icon :size="30"><component :is="iconOf(a)" /></el-icon>
              <span class="xs">{{ typeLabel(a) }}</span>
            </div>
            <span class="badge">{{ typeLabel(a) }}</span>
          </div>
          <div class="meta">
            <div class="name ellipsis">{{ a.name }}</div>
            <div class="xs dim ellipsis">
              {{ fmtSize(a.file_size) }}<template v-if="a.author_nickname"> · {{ a.author_nickname }}</template>
            </div>
            <div v-if="a.series_name" class="xs dim ellipsis">系列：{{ a.series_name }}</div>
          </div>
          <div class="ops" @click.stop>
            <el-button size="small" text @click="copy(a)">复制链接</el-button>
            <a :href="urlOf(a)" target="_blank" rel="noopener"><el-button size="small" text>新窗口</el-button></a>
          </div>
        </article>
      </div>

      <div v-if="list.length < total" class="more">
        <el-button :loading="loading" @click="loadMore">加载更多（{{ list.length }}/{{ total }}）</el-button>
      </div>
      <div v-else-if="list.length" class="xs dim more">共 {{ total }} 个素材</div>

      <!-- 预览 -->
      <el-dialog v-model="dlg" :title="cur?.name || '素材预览'" width="min(880px, 94vw)" destroy-on-close>
        <div v-if="cur" class="preview">
          <img v-if="isImg(cur)" :src="urlOf(cur)" :alt="cur.name" class="pv-img" />
          <video v-else-if="cur.asset_type === 'video'" :src="urlOf(cur)" controls autoplay class="pv-media" />
          <audio v-else-if="cur.asset_type === 'audio'" :src="urlOf(cur)" controls class="pv-audio" />
          <pre v-else-if="cur.asset_type === 'json'" class="pv-json">{{ jsonText || '加载中…' }}</pre>
          <div v-else class="pv-none xs dim">该类型暂不支持预览，可直接打开直链</div>

          <div class="link-row">
            <el-input :model-value="absUrlOf(cur)" readonly />
            <el-button type="primary" @click="copy(cur)">复制</el-button>
          </div>
          <div class="xs dim">
            引用示例：
            <code class="inline">{{ snippetOf(cur) }}</code>
          </div>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppState from '@/components/AppState.vue'
import { assetApi, safe } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { absoluteUrl, assetTypeOf, fmtSize, mediaUrlOf, pickList, pickTotal } from '@/utils/format'

const auth = useAuthStore()
const list = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref(null)
const type = ref('')
const keyword = ref('')
const dlg = ref(false)
const cur = ref(null)
const jsonText = ref('')
const SIZE = 24

const isImg = (a) => a.asset_type === 'image' || a.asset_type === 'gif'
const urlOf = (a) => mediaUrlOf(a)
const absUrlOf = (a) => absoluteUrl(mediaUrlOf(a))
const typeLabel = (a) => assetTypeOf(a.asset_type).label
const iconOf = (a) => assetTypeOf(a.asset_type).icon

function snippetOf(a) {
  const u = absUrlOf(a)
  if (a.asset_type === 'video') return `<video src="${u}" controls></video>`
  if (a.asset_type === 'audio') return `<audio src="${u}" controls></audio>`
  if (a.asset_type === 'json') return `fetch("${u}")`
  return `<img src="${u}">`
}

async function fetchPage(reset) {
  if (reset) { page.value = 1; list.value = [] }
  loading.value = true
  error.value = null
  const params = { page: page.value, size: SIZE }
  if (type.value) params.asset_type = type.value
  if (keyword.value.trim()) params.keyword = keyword.value.trim()
  const r = await safe(assetApi.list(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  const items = pickList(r.data)
  list.value = reset ? items : list.value.concat(items)
  total.value = pickTotal(r.data)
}

function reload() { fetchPage(true) }
function loadMore() { page.value += 1; fetchPage(false) }

async function openPreview(a) {
  cur.value = a
  dlg.value = true
  jsonText.value = ''
  if (a.asset_type === 'json') {
    try {
      const res = await fetch(absUrlOf(a), { cache: 'no-cache' })
      const text = await res.text()
      try { jsonText.value = JSON.stringify(JSON.parse(text), null, 2) } catch (e) { jsonText.value = text.slice(0, 20000) }
    } catch (e) {
      jsonText.value = '读取失败：' + (e?.message || e)
    }
  }
}

async function copy(a) {
  const text = absUrlOf(a)
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success('直链已复制：' + text)
  } catch (e) {
    ElMessage.warning('复制失败，请手动选中链接：' + text)
  }
}

onMounted(reload)
</script>

<style scoped>
.head { margin-bottom: 14px; }
.page-title { font-size: 24px; margin: 0 0 6px; }
.intro { margin: 0; max-width: 720px; }
.inline { background: var(--c-bg-sunken); padding: 1px 5px; border-radius: 4px; font-size: .92em; }
.toolbar { margin-bottom: 16px; }

.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 14px; }
.asset-card { padding: 10px; cursor: pointer; transition: transform var(--dur-fast) var(--ease-out), border-color var(--dur-fast); }
.asset-card:hover { transform: translateY(-2px); border-color: var(--c-border-strong); }
.thumb {
  position: relative; aspect-ratio: 4 / 3; border-radius: var(--r-md); overflow: hidden;
  background: var(--c-bg-sunken); display: flex; align-items: center; justify-content: center;
}
.thumb img, .thumb video { width: 100%; height: 100%; object-fit: cover; display: block; }
.ph { display: flex; flex-direction: column; align-items: center; gap: 6px; color: var(--c-text-3); }
.badge {
  position: absolute; left: 8px; top: 8px; font-size: var(--fs-xs);
  background: rgba(0, 0, 0, .55); color: #fff; padding: 1px 7px; border-radius: var(--r-pill);
}
.meta { padding: 8px 2px 4px; }
.name { font-weight: 600; font-size: var(--fs-sm); }
.ops { display: flex; justify-content: flex-end; gap: 2px; }
.more { text-align: center; padding: 18px 0; }

.preview { display: flex; flex-direction: column; gap: 12px; }
.pv-img { max-width: 100%; max-height: 62vh; object-fit: contain; border-radius: var(--r-md); align-self: center; }
.pv-media { width: 100%; max-height: 62vh; border-radius: var(--r-md); background: #000; }
.pv-audio { width: 100%; }
.pv-json {
  max-height: 52vh; overflow: auto; margin: 0; padding: 12px; border-radius: var(--r-md);
  background: var(--c-bg-sunken); font-size: 12px; line-height: 1.5; white-space: pre-wrap; word-break: break-all;
}
.pv-none { padding: 24px; text-align: center; }
.link-row { display: flex; gap: 8px; align-items: center; }
</style>
