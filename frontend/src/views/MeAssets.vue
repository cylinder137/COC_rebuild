<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:16px">
      <div>
        <h2 class="section-title" style="margin:0">我的素材</h2>
        <div class="sm dim">
          上传后复制<strong>直链</strong>即可在自己的 HTML 里引用；打开「公开」会出现在素材库，其他人也能看到。
        </div>
      </div>
      <div class="row gap8">
        <el-button type="primary" @click="openUpload">
          <el-icon><UploadFilled /></el-icon>&nbsp;上传素材
        </el-button>
      </div>
    </div>

    <!-- 系列筛选 -->
    <div class="row gap8 wrap center series-bar">
      <el-tag
        :type="filter.series_id === '' ? 'primary' : 'info'"
        :effect="filter.series_id === '' ? 'dark' : 'plain'"
        round
        @click="pickSeries('')"
      >
        全部 {{ total }}
      </el-tag>
      <el-tag
        :type="filter.series_id === 'none' ? 'primary' : 'info'"
        :effect="filter.series_id === 'none' ? 'dark' : 'plain'"
        round
        @click="pickSeries('none')"
      >
        未归系列
      </el-tag>
      <el-tag
        v-for="s in series"
        :key="s.series_id"
        :type="filter.series_id === s.series_id ? 'primary' : 'info'"
        :effect="filter.series_id === s.series_id ? 'dark' : 'plain'"
        round
        closable
        @click="pickSeries(s.series_id)"
        @close="removeSeries(s)"
      >
        {{ s.name }} {{ s.item_count }}
        <span v-if="s.is_public" class="pub-dot" title="系列公开">●</span>
      </el-tag>
      <el-button size="small" text @click="openSeriesDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新建系列
      </el-button>
      <el-select v-model="filter.asset_type" size="small" style="width:130px" clearable placeholder="全部类型" @change="reload">
        <el-option label="图片" value="image" />
        <el-option label="动图" value="gif" />
        <el-option label="视频" value="video" />
        <el-option label="音频" value="audio" />
        <el-option label="JSON" value="json" />
      </el-select>
    </div>

    <AppState
      :loading="loading && !list.length"
      :error="error"
      :empty="!loading && !error && !list.length"
      empty-text="还没有素材"
      empty-desc="图片、动图、视频、音频、JSON 都可以传；大文件自动分片续传。"
      :skeleton-count="8"
      @retry="reload"
    >
      <template #action>
        <el-button type="primary" @click="openUpload">上传第一个素材</el-button>
      </template>
    </AppState>

    <div class="grid">
      <article v-for="a in list" :key="a.asset_id" class="card asset-card">
        <div class="thumb" @click="openPreview(a)">
          <img v-if="isImg(a)" :src="urlOf(a)" :alt="a.name" loading="lazy" />
          <video v-else-if="a.asset_type === 'video'" :src="urlOf(a)" preload="metadata" muted playsinline />
          <div v-else class="ph">
            <el-icon :size="28"><component :is="iconOf(a)" /></el-icon>
            <span class="xs">{{ typeLabel(a) }}</span>
          </div>
          <span class="badge">{{ typeLabel(a) }}</span>
        </div>
        <div class="meta">
          <div class="name ellipsis" :title="a.name">{{ a.name }}</div>
          <div class="xs dim ellipsis">{{ fmtSize(a.file_size) }} · {{ a.file_name }}</div>
          <div class="xs dim ellipsis">{{ a.series_name ? '系列：' + a.series_name : '未归系列' }}</div>
        </div>
        <div class="ops">
          <el-switch
            :model-value="!!a.is_public"
            size="small"
            inline-prompt
            active-text="公开"
            inactive-text="私有"
            @change="(v) => togglePublic(a, v)"
          />
          <span class="grow" />
          <el-button size="small" text @click="copy(a)">复制链接</el-button>
          <el-dropdown trigger="click" @command="(c) => onCmd(c, a)">
            <el-button size="small" text>更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="preview">预览</el-dropdown-item>
                <el-dropdown-item command="series">归入系列</el-dropdown-item>
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
                <el-dropdown-item command="open">新窗口打开</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </article>
    </div>

    <div v-if="list.length < total" class="more">
      <el-button :loading="loading" @click="loadMore">加载更多（{{ list.length }}/{{ total }}）</el-button>
    </div>

    <!-- 上传 -->
    <el-dialog v-model="uploadVisible" title="上传素材" width="min(620px, 94vw)" destroy-on-close>
      <AssetUploader :series="series" :default-series-id="filter.series_id" @done="onUploaded" />
    </el-dialog>

    <!-- 预览 -->
    <el-dialog v-model="dlg" :title="cur?.name || '素材预览'" width="min(880px, 94vw)" destroy-on-close>
      <div v-if="cur" class="preview">
        <img v-if="isImg(cur)" :src="urlOf(cur)" :alt="cur.name" class="pv-img" />
        <video v-else-if="cur.asset_type === 'video'" :src="urlOf(cur)" controls autoplay class="pv-media" />
        <audio v-else-if="cur.asset_type === 'audio'" :src="urlOf(cur)" controls class="pv-audio" />
        <pre v-else-if="cur.asset_type === 'json'" class="pv-json">{{ jsonText || '加载中…' }}</pre>
        <div v-else class="pv-none xs dim">该类型暂不支持预览</div>
        <div class="link-row">
          <el-input :model-value="absUrlOf(cur)" readonly />
          <el-button type="primary" @click="copy(cur)">复制</el-button>
        </div>
        <div class="xs dim">引用示例：<code class="inline">{{ snippetOf(cur) }}</code></div>
      </div>
    </el-dialog>

    <!-- 系列新建/编辑 -->
    <el-dialog v-model="seriesDlg" :title="seriesForm.series_id ? '编辑素材系列' : '新建素材系列'" width="min(460px, 92vw)">
      <el-form label-width="72px">
        <el-form-item label="名称">
          <el-input v-model="seriesForm.name" maxlength="80" placeholder="例如：物理演示素材" data-field="series_name" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="seriesForm.intro" maxlength="255" type="textarea" :rows="2" placeholder="这个系列放什么" />
        </el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="seriesForm.is_public" />
          <span class="xs dim" style="margin-left:8px">公开后别人能在你的主页看到这个系列</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="seriesDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSeries">保存</el-button>
      </template>
    </el-dialog>

    <!-- 归入系列 -->
    <el-dialog v-model="moveDlg" title="归入素材系列" width="min(420px, 92vw)">
      <el-select v-model="moveTo" clearable placeholder="不归入任何系列" style="width:100%">
        <el-option v-for="s in series" :key="s.series_id" :label="s.name" :value="s.series_id" />
      </el-select>
      <template #footer>
        <el-button @click="moveDlg = false">取消</el-button>
        <el-button type="primary" @click="doMove">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppState from '@/components/AppState.vue'
import AssetUploader from '@/components/AssetUploader.vue'
import { assetApi, seriesApi, safe } from '@/api'
import { absoluteUrl, assetTypeOf, fmtSize, mediaUrlOf, pickList, pickTotal } from '@/utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref(null)
const series = ref([])
const filter = reactive({ series_id: '', asset_type: '' })
const uploadVisible = ref(false)
const dlg = ref(false)
const cur = ref(null)
const jsonText = ref('')
const seriesDlg = ref(false)
const saving = ref(false)
const seriesForm = reactive({ series_id: null, name: '', intro: '', is_public: false })
const moveDlg = ref(false)
const moveTo = ref(null)
const moveTarget = ref(null)
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

async function loadSeries() {
  const r = await safe(seriesApi.mine('asset'))
  if (r.ok) series.value = Array.isArray(r.data) ? r.data : []
  else series.value = []
}

async function fetchPage(reset) {
  if (reset) { page.value = 1; list.value = [] }
  loading.value = true
  error.value = null
  const params = { page: page.value, size: SIZE }
  if (filter.asset_type) params.asset_type = filter.asset_type
  if (filter.series_id === 'none') params.no_series = 1
  else if (filter.series_id !== '') params.series_id = filter.series_id
  const r = await safe(assetApi.mine(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  const items = pickList(r.data)
  const tot = pickTotal(r.data)
  list.value = reset ? items : list.value.concat(items)
  total.value = tot
}

function reload() { fetchPage(true) }
function loadMore() { page.value += 1; fetchPage(false) }
function pickSeries(id) { filter.series_id = id; reload() }

function openUpload() { uploadVisible.value = true }
function onUploaded() {
  uploadVisible.value = false
  loadSeries()
  reload()
}

async function togglePublic(a, val) {
  const r = await safe(assetApi.update(a.asset_id, { is_public: val ? 1 : 0 }))
  if (!r.ok) { ElMessage.error('修改失败'); return }
  a.is_public = val ? 1 : 0
  ElMessage.success(val ? '已公开，其他人可在素材库看到' : '已设为私有，仅自己可见')
}

async function copy(a) {
  const text = absUrlOf(a)
  try {
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text)
    else {
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
    ElMessage.warning('复制失败，请手动复制：' + text)
  }
}

function openPreview(a) {
  cur.value = a
  dlg.value = true
  jsonText.value = ''
  if (a.asset_type === 'json') {
    fetch(absUrlOf(a), { cache: 'no-cache' })
      .then((r) => r.text())
      .then((t) => { try { jsonText.value = JSON.stringify(JSON.parse(t), null, 2) } catch (e) { jsonText.value = t.slice(0, 20000) } })
      .catch((e) => { jsonText.value = '读取失败：' + (e?.message || e) })
  }
}

function onCmd(cmd, a) {
  if (cmd === 'preview') openPreview(a)
  else if (cmd === 'open') window.open(absUrlOf(a), '_blank', 'noopener')
  else if (cmd === 'series') { moveTarget.value = a; moveTo.value = a.series_id || null; moveDlg.value = true }
  else if (cmd === 'rename') rename(a)
  else if (cmd === 'delete') remove(a)
}

async function rename(a) {
  try {
    const { value } = await ElMessageBox.prompt('新的素材名称', '重命名', {
      inputValue: a.name,
      inputValidator: (v) => (!!v && v.trim().length > 0) || '名称不能为空'
    })
    const r = await safe(assetApi.update(a.asset_id, { name: value.trim() }))
    if (!r.ok) { ElMessage.error('改名失败'); return }
    a.name = value.trim()
    ElMessage.success('已改名')
  } catch (e) { /* 取消 */ }
}

async function remove(a) {
  try {
    await ElMessageBox.confirm(
      `确定删除《${a.name}》吗？\n已引用该直链的 HTML 不会立刻断链（文件保留在服务器），但列表里不再显示。`,
      '删除素材',
      { type: 'warning' }
    )
  } catch (e) { return }
  const r = await safe(assetApi.remove(a.asset_id))
  if (!r.ok) { ElMessage.error('删除失败'); return }
  ElMessage.success('已删除')
  loadSeries()
  reload()
}

async function doMove() {
  const a = moveTarget.value
  if (!a) return
  const r = await safe(assetApi.update(a.asset_id, { series_id: moveTo.value || null }))
  if (!r.ok) { ElMessage.error('操作失败'); return }
  moveDlg.value = false
  const hit = series.value.find((s) => s.series_id === moveTo.value)
  a.series_id = moveTo.value || null
  a.series_name = hit?.name || ''
  ElMessage.success('已更新系列')
  loadSeries()
}

function openSeriesDialog(s) {
  seriesForm.series_id = s?.series_id || null
  seriesForm.name = s?.name || ''
  seriesForm.intro = s?.intro || ''
  seriesForm.is_public = !!s?.is_public
  seriesDlg.value = true
}

async function saveSeries() {
  const name = (seriesForm.name || '').trim()
  if (!name) { ElMessage.warning('请填写系列名称'); return }
  saving.value = true
  const payload = { kind: 'asset', name, intro: seriesForm.intro || '', is_public: seriesForm.is_public ? 1 : 0 }
  const r = seriesForm.series_id
    ? await safe(seriesApi.update(seriesForm.series_id, payload))
    : await safe(seriesApi.create(payload))
  saving.value = false
  if (!r.ok) {
    ElMessage.error(r.error?.response?.data?.message || '保存失败')
    return
  }
  seriesDlg.value = false
  ElMessage.success('已保存')
  loadSeries()
}

async function removeSeries(s) {
  try {
    await ElMessageBox.confirm(
      `删除系列《${s.name}》？系列里的素材不会被删除，只会变成「未归系列」。`,
      '删除素材系列',
      { type: 'warning' }
    )
  } catch (e) { return }
  const r = await safe(seriesApi.remove(s.series_id, 'asset'))
  if (!r.ok) { ElMessage.error('删除失败'); return }
  if (filter.series_id === s.series_id) filter.series_id = ''
  ElMessage.success('系列已删除')
  loadSeries()
  reload()
}

onMounted(async () => {
  await loadSeries()
  await fetchPage(true)
})
</script>

<style scoped>
.series-bar { margin-bottom: 16px; }
.series-bar .el-tag { cursor: pointer; }
.pub-dot { font-size: 8px; color: var(--c-success); margin-left: 2px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
.asset-card { padding: 10px; }
.thumb {
  position: relative; aspect-ratio: 4 / 3; border-radius: var(--r-md); overflow: hidden;
  background: var(--c-bg-sunken); display: flex; align-items: center; justify-content: center; cursor: zoom-in;
}
.thumb img, .thumb video { width: 100%; height: 100%; object-fit: cover; display: block; }
.ph { display: flex; flex-direction: column; align-items: center; gap: 6px; color: var(--c-text-3); }
.badge {
  position: absolute; left: 8px; top: 8px; font-size: var(--fs-xs);
  background: rgba(0, 0, 0, .55); color: #fff; padding: 1px 7px; border-radius: var(--r-pill);
}
.meta { padding: 8px 2px 4px; }
.name { font-weight: 600; font-size: var(--fs-sm); }
.ops { display: flex; align-items: center; gap: 4px; }
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
.inline { background: var(--c-bg-sunken); padding: 1px 5px; border-radius: 4px; }
</style>
