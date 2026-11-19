<template>
  <div class="asset-uploader">
    <!-- 选择区 -->
    <div
      v-if="!file"
      class="drop"
      :class="{ over }"
      @dragover.prevent="over = true"
      @dragleave.prevent="over = false"
      @drop.prevent="onDrop"
      @click="pick"
    >
      <el-icon :size="32" class="dim"><UploadFilled /></el-icon>
      <div class="drop-title">点击选择，或拖拽文件到此处</div>
      <div class="xs dim">
        图片 png/jpg/webp · 动图 gif · 视频 mp4/webm · 音频 mp3/wav · 数据 json（单个 ≤300MB，大文件自动分片续传）
      </div>
      <input ref="inputRef" type="file" hidden @change="onPick" />
    </div>

    <template v-else>
      <div class="file-card">
        <div class="fc-head">
          <el-icon class="dim"><Document /></el-icon>
          <span class="fc-name ellipsis">{{ file.name }}</span>
          <span class="xs dim">{{ fmtSize(file.size) }}</span>
          <span class="grow" />
          <el-button size="small" text :disabled="busy" @click="reset">换一个</el-button>
        </div>

        <div class="form-row">
          <el-input v-model="meta.name" maxlength="120" placeholder="素材名称（默认用文件名）" data-field="asset_name" />
        </div>
        <div class="form-row">
          <el-select v-model="meta.series_id" clearable placeholder="归到哪个素材系列（可不选）" style="width:100%">
            <el-option v-for="s in series" :key="s.series_id" :label="s.name" :value="s.series_id" />
          </el-select>
        </div>
        <div class="form-row row gap10 center wrap">
          <el-input
            v-model="meta.new_series_name"
            placeholder="或新建系列（填名字即可）"
            maxlength="80"
            style="max-width:260px"
          />
          <div class="row gap6 center sm">
            <span class="dim">公开</span>
            <el-switch v-model="meta.is_public" size="small" />
          </div>
        </div>

        <el-progress
          :percentage="percent"
          :stroke-width="8"
          :status="state === 'done' ? 'success' : state === 'error' ? 'exception' : undefined"
        />
        <div class="xs dim" style="margin-top:6px">
          {{ statusText }}<template v-if="totalParts"> · {{ doneParts }}/{{ totalParts }} 片</template>
        </div>

        <div class="row gap12 center" style="margin-top:14px">
          <el-button type="primary" :loading="busy" :disabled="state === 'done'" @click="start">
            {{ state === 'error' ? '重新上传' : '开始上传' }}
          </el-button>
          <span v-if="state === 'done'" class="sm ok">已入库，可在下方列表里复制直链</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { assetApi, uploadApi, safe } from '@/api'
import { fmtSize } from '@/utils/format'

const props = defineProps({
  /** 可选：预设归入的素材系列 */
  defaultSeriesId: { type: [Number, String], default: null },
  /** 可选：父级已加载的素材系列列表 */
  series: { type: Array, default: () => [] }
})
const emit = defineEmits(['done'])

const inputRef = ref(null)
const file = ref(null)
const over = ref(false)
const busy = ref(false)
const percent = ref(0)
const totalParts = ref(0)
const doneParts = ref(0)
const state = ref('idle') // idle | uploading | done | error
const meta = reactive({
  name: '',
  series_id: props.defaultSeriesId || null,
  new_series_name: '',
  is_public: true
})

const statusText = computed(() => ({
  idle: '待上传',
  uploading: '上传中…',
  done: '上传完成',
  error: '上传失败'
}[state.value]))

const CHUNK = 5 * 1024 * 1024

function pick() { inputRef.value?.click() }
function onPick(e) {
  const f = e.target.files?.[0]
  if (f) accept(f)
  e.target.value = ''
}
function onDrop(e) {
  over.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) accept(f)
}

function accept(f) {
  const ext = (f.name.split('.').pop() || '').toLowerCase()
  const ALLOW = ['png', 'jpg', 'jpeg', 'webp', 'bmp', 'avif', 'ico', 'gif', 'mp4', 'webm', 'mov', 'm4v',
    'mp3', 'wav', 'ogg', 'm4a', 'aac', 'flac', 'json']
  if (!ALLOW.includes(ext)) {
    ElMessage.warning('不支持该格式：.' + ext)
    return
  }
  if (f.size > 300 * 1024 * 1024) {
    ElMessage.warning('单个素材不能超过 300MB')
    return
  }
  file.value = f
  if (!meta.name) meta.name = f.name.replace(/\.[^.]+$/, '')
  state.value = 'idle'
  percent.value = 0
}

function reset() {
  if (busy.value) return
  file.value = null
  state.value = 'idle'
  percent.value = 0
  totalParts.value = 0
  doneParts.value = 0
}

async function sha256(f) {
  try {
    if (!window.crypto?.subtle || f.size > 256 * 1024 * 1024) return ''
    const buf = await f.arrayBuffer()
    const h = await crypto.subtle.digest('SHA-256', buf)
    return [...new Uint8Array(h)].map((b) => b.toString(16).padStart(2, '0')).join('')
  } catch (e) { return '' }
}

/** 上传主流程：init → parts（串行 + 重试）→ complete */
async function start() {
  if (!file.value || busy.value) return
  busy.value = true
  state.value = 'uploading'
  percent.value = 0
  try {
    const hash = await sha256(file.value)
    const init = await safe(assetApi.initUpload({
      file_name: file.value.name,
      file_size: file.value.size,
      sha256: hash || undefined,
      chunk_size: CHUNK
    }))
    if (!init.ok) {
      fail(init.error?.response?.data?.message || '初始化上传失败')
      return
    }
    const d = init.data || {}
    const sid = d.upload_session_id
    const size = d.chunk_size || CHUNK
    totalParts.value = d.total_chunks || Math.ceil(file.value.size / size)
    doneParts.value = 0

    for (let i = 1; i <= totalParts.value; i++) {
      const part = file.value.slice((i - 1) * size, Math.min(i * size, file.value.size))
      const form = new FormData()
      form.append('part_number', String(i))
      form.append('chunk', part, `${file.value.name}.part${i}`)
      let ok = false
      for (let attempt = 0; attempt < 3 && !ok; attempt++) {
        const r = await safe(uploadApi.putPart(sid, form))
        ok = r.ok
        if (!ok) await new Promise((res) => setTimeout(res, 1500 * (attempt + 1)))
      }
      if (!ok) {
        fail(`第 ${i} 片上传失败（已重试 3 次），可点「重新上传」续传`)
        return
      }
      doneParts.value = i
      percent.value = Math.round((i / totalParts.value) * 100)
    }

    const done = await safe(assetApi.completeUpload(sid, {
      name: meta.name || undefined,
      series_id: meta.series_id || undefined,
      new_series_name: meta.new_series_name || undefined,
      is_public: meta.is_public ? 1 : 0
    }))
    if (!done.ok) {
      fail(done.error?.response?.data?.message || '登记素材失败')
      return
    }
    state.value = 'done'
    percent.value = 100
    ElMessage.success('素材已上传，可复制直链使用')
    emit('done', done.data)
    // 留一次提示后自动复位，方便连续上传
    setTimeout(() => {
      if (state.value === 'done') reset()
    }, 1200)
  } catch (e) {
    fail('上传过程出错：' + (e?.message || e))
  } finally {
    busy.value = false
  }
}

function fail(msg) {
  state.value = 'error'
  ElMessage.error(msg)
}

defineExpose({ start, reset })
</script>

<style scoped>
.drop {
  border: 1px dashed var(--c-border-strong); border-radius: var(--r-lg);
  padding: 30px 18px; text-align: center; cursor: pointer; background: var(--c-bg-sunken);
  transition: all var(--dur-fast) var(--ease-out);
}
.drop.over { border-color: var(--c-primary); background: var(--c-primary-weak); }
.drop-title { font-weight: 600; margin: 8px 0 4px; }
.file-card { border: 1px solid var(--c-border); border-radius: var(--r-lg); padding: 14px; background: var(--c-bg-card); }
.fc-head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fc-name { font-weight: 600; max-width: 260px; }
.form-row { margin-bottom: 10px; }
.ok { color: var(--c-success); }
</style>
