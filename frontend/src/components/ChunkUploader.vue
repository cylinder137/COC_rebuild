<template>
  <div class="uploader">
    <!-- 拖拽/选择区 -->
    <div
      v-if="!file"
      class="drop"
      :class="{ over: over }"
      @dragover.prevent="over = true"
      @dragleave.prevent="over = false"
      @drop.prevent="onDrop"
      @click="pick"
    >
      <el-icon :size="34" class="dim"><UploadFilled /></el-icon>
      <div class="drop-title">点击选择文件，或拖拽到此处</div>
      <div class="xs dim">支持 .html / .zip / .sb2 / .sb3 / .mcstructure；单文件建议 &lt; 90MB 分片上限，大文件自动分片并支持断点续传</div>
      <input ref="inputRef" type="file" hidden @change="onPick" />
    </div>

    <template v-else>
      <!-- 大文件提示 -->
      <el-alert
        v-if="totalParts > 20"
        type="warning"
        :closable="false"
        show-icon
        class="mb12"
        :title="`${file.name}（${fmtSize(file.size)}）将分为 ${totalParts} 片上传，可断点续传`"
      />
      <el-alert
        v-if="resumedTip"
        type="info"
        :closable="false"
        show-icon
        class="mb12"
        title="检测到未完成的上传，已为你恢复"
        :description="`已续传 ${uploadedSet.size}/${totalParts} 片`"
      />

      <div class="file-card">
        <div class="fc-head">
          <el-icon class="dim"><Document /></el-icon>
          <span class="fc-name ellipsis">{{ file.name }}</span>
          <span class="xs dim num">{{ fmtSize(file.size) }}</span>
          <span class="fc-status" :class="statusClass">{{ statusText }}</span>
          <span class="grow" />
          <el-button v-if="state === 'uploading'" size="small" @click="paused = true">暂停</el-button>
          <el-button v-if="state === 'paused'" size="small" type="primary" @click="resume">继续</el-button>
          <el-button size="small" text @click="reset">取消</el-button>
        </div>

        <div class="fc-progress">
          <el-progress
            :percentage="percent"
            :stroke-width="8"
            :status="state === 'done' ? 'success' : state === 'error' ? 'exception' : undefined"
          />
          <div class="xs dim" style="margin-top:6px">
            已传 {{ uploadedSet.size }} / {{ totalParts }} 片 · {{ Math.floor(percent) }}%
            <template v-if="instant"> · <span class="ok">秒传命中，服务端已存在相同文件</span></template>
          </div>
        </div>

        <!-- 分片网格 -->
        <div class="chunk-grid" role="list">
          <span
            v-for="i in gridParts"
            :key="i"
            class="chunk"
            :class="chunkClass(i)"
            :title="`第 ${i} 片 / 共 ${totalParts} 片`"
            role="listitem"
          >{{ i > 200 ? '' : i }}</span>
          <span v-if="totalParts > 200" class="xs dim">（分片较多，已收起为条纹进度）</span>
        </div>

        <div v-if="failedSet.size" class="failed-row">
          <span class="xs" style="color: var(--c-danger)">失败 {{ failedSet.size }} 片：[{{ [...failedSet].join(', ') }}]</span>
          <el-button size="small" @click="retryFailed">重试失败分片</el-button>
        </div>
      </div>

      <div class="row gap12 center" style="margin-top:12px">
        <el-button v-if="state === 'done'" type="success" disabled>
          <el-icon><CircleCheck /></el-icon>&nbsp;文件已上传完成
        </el-button>
        <el-button v-else-if="state === 'error'" type="primary" @click="startUpload(true)">重新上传</el-button>
        <span class="xs dim">
          上传会话 ID：{{ sessionId || '—' }}
          <template v-if="storagePath"> · 服务器路径：<span class="mono">{{ storagePath }}</span></template>
        </span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadApi, safe } from '@/api'
import { fmtSize } from '@/utils/format'

const props = defineProps({
  /** 分片大小（字节），默认 5MB；后端 init 会回传自己的 chunk_size 并覆盖 */
  chunkSize: { type: Number, default: 5 * 1024 * 1024 }
})
const emit = defineEmits(['completed', 'progress', 'reset'])

const inputRef = ref(null)
const file = ref(null)
const over = ref(false)
const sessionId = ref('')
const uploadedSet = ref(new Set())
const failedSet = ref(new Set())
const totalParts = ref(0)
const partSize = ref(props.chunkSize)
const currentPart = ref(0)
const state = ref('idle')      // idle | uploading | paused | done | error
const paused = ref(false)
const instant = ref(false)
const resumedTip = ref(false)
const storagePath = ref('')
const projectFileId = ref(null)

const percent = computed(() => (totalParts.value ? (uploadedSet.value.size / totalParts.value) * 100 : 0))
const gridParts = computed(() => Math.min(totalParts.value, 200))
const statusText = computed(() => ({
  idle: '待上传', uploading: '上传中', paused: '已暂停', done: '已完成', error: '上传失败'
}[state.value]))
const statusClass = computed(() => ({ ok: state.value === 'done', warn: state.value === 'paused', err: state.value === 'error' }))

function chunkClass(i) {
  if (uploadedSet.value.has(i)) return 'done'
  if (failedSet.value.has(i)) return 'fail'
  if (i === currentPart.value && state.value === 'uploading') return 'active'
  return 'wait'
}

function pick() { inputRef.value?.click() }
function onPick(e) {
  const f = e.target.files?.[0]
  if (f) { file.value = f; startUpload() }
}
function onDrop(e) {
  over.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) { file.value = f; startUpload() }
}

async function sha256(fileObj) {
  try {
    if (!window.crypto?.subtle || fileObj.size > 512 * 1024 * 1024) return null
    const buf = await fileObj.arrayBuffer()
    const h = await crypto.subtle.digest('SHA-256', buf)
    return [...new Uint8Array(h)].map((b) => b.toString(16).padStart(2, '0')).join('')
  } catch (e) { return null }
}

function reset() {
  ElMessageBox.confirm('已上传的分片将被丢弃，确定取消吗？', '取消上传', { type: 'warning' })
    .then(() => {
      file.value = null
      sessionId.value = ''
      uploadedSet.value = new Set()
      failedSet.value = new Set()
      totalParts.value = 0
      state.value = 'idle'
      instant.value = false
      resumedTip.value = false
      storagePath.value = ''
      projectFileId.value = null
      emit('reset')
    })
    .catch(() => {})
}

/** 1) init（含秒传 + 续传信息） */
async function startUpload(isRetry) {
  if (!file.value) return
  state.value = 'uploading'
  paused.value = false
  failedSet.value = new Set()
  if (!isRetry) {
    const hash = await sha256(file.value)
    const r = await safe(uploadApi.init({
      file_name: file.value.name,
      file_size: file.value.size,
      sha256: hash || '',
      chunk_size: props.chunkSize
    }))
    if (!r.ok) {
      state.value = 'error'
      ElMessage.error('初始化上传失败（后端 8090 可能未启动）')
      return
    }
    const d = r.data || {}
    sessionId.value = d.upload_session_id
    partSize.value = d.chunk_size || props.chunkSize
    totalParts.value = d.total_chunks || Math.ceil(file.value.size / partSize.value)
    const already = d.uploaded_chunks || []
    uploadedSet.value = new Set(already)
    instant.value = !!d.instant
    if (already.length) resumedTip.value = true
    if (d.project_file_id) projectFileId.value = d.project_file_id
    if (instant.value) {
      uploadedSet.value = new Set(Array.from({ length: totalParts.value }, (_, i) => i + 1))
      await complete()
      return
    }
  }
  await uploadMissing()
}

/** 2) parts：串行补传缺失/失败分片 */
async function uploadMissing() {
  if (!file.value || !sessionId.value) return
  state.value = 'uploading'
  paused.value = false
  for (let i = 1; i <= totalParts.value; i++) {
    if (paused.value) { state.value = 'paused'; return }
    if (uploadedSet.value.has(i)) continue
    currentPart.value = i
    const ok = await putPartWithRetry(i)
    if (!ok) {
      failedSet.value = new Set([...failedSet.value, i])
      state.value = 'error'
      ElMessage.error(`第 ${i} 片上传失败，已重试 3 次。可点「重试失败分片」继续（已传分片会保留）`)
      return
    }
    uploadedSet.value = new Set([...uploadedSet.value, i])
    failedSet.value = new Set([...failedSet.value].filter((x) => x !== i))
    emit('progress', { uploaded: uploadedSet.value.size, total: totalParts.value })
    await sleep(100)  // 分片间隔，避免抖动
  }
  await complete()
}

async function putPartWithRetry(partNumber) {
  for (let attempt = 0; attempt < 3; attempt++) {
    const start = (partNumber - 1) * partSize.value
    const blob = file.value.slice(start, Math.min(start + partSize.value, file.value.size))
    const form = new FormData()
    form.append('part_number', String(partNumber))
    form.append('chunk', blob, `${file.value.name}.part${partNumber}`)
    const r = await safe(uploadApi.putPart(sessionId.value, form))
    if (r.ok) return true
    await sleep(2000 * Math.pow(2, attempt))  // 2s / 4s / 8s 退避
    if (paused.value) return false
  }
  return false
}

function resume() { uploadMissing() }

async function retryFailed() {
  await uploadMissing()
}

/** 3) complete：校验 sha256 → 落盘 → 建 project_file */
async function complete() {
  const r = await safe(uploadApi.complete(sessionId.value, {}))
  if (!r.ok) {
    state.value = 'error'
    ElMessage.error('合并分片失败，请重试')
    return
  }
  state.value = 'done'
  storagePath.value = r.data?.storage_path || ''
  projectFileId.value = r.data?.project_file_id || projectFileId.value
  ElMessage.success('文件上传完成')
  emit('completed', {
    project_file_id: r.data?.project_file_id,
    storage_path: r.data?.storage_path,
    build_task_id: r.data?.build_task_id
  })
}

function sleep(ms) { return new Promise((res) => setTimeout(res, ms)) }

// 关闭页签提醒
function beforeUnload(e) {
  if (state.value === 'uploading') { e.preventDefault(); e.returnValue = '' }
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<style scoped>
.drop {
  border: 1px dashed var(--c-border-strong); border-radius: var(--r-lg);
  padding: 36px 20px; text-align: center; cursor: pointer; background: var(--c-bg-sunken);
  transition: all var(--dur-fast) var(--ease-out);
}
.drop.over { border-color: var(--c-primary); background: var(--c-primary-weak); }
.drop-title { font-weight: 600; margin: 10px 0 6px; }
.mb12 { margin-bottom: 12px; }

.file-card { border: 1px solid var(--c-border); border-radius: var(--r-lg); padding: 16px; background: var(--c-bg-card); }
.fc-head { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.fc-name { font-weight: 600; max-width: 320px; }
.fc-status { font-size: var(--fs-xs); padding: 2px 8px; border-radius: var(--r-pill); background: var(--c-bg-sunken); color: var(--c-text-2); }
.fc-status.ok { background: rgba(34, 197, 94, .15); color: var(--c-success); }
.fc-status.warn { background: rgba(245, 158, 11, .15); color: var(--c-warning); }
.fc-status.err { background: rgba(239, 68, 68, .15); color: var(--c-danger); }

.chunk-grid { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 14px; }
.chunk {
  width: 22px; height: 22px; border-radius: var(--r-xs); font-size: 10px;
  display: flex; align-items: center; justify-content: center;
  background: var(--c-bg-sunken); border: 1px solid var(--c-border); color: var(--c-text-3);
}
.chunk.done { background: var(--c-success); border-color: var(--c-success); color: #fff; }
.chunk.fail { background: var(--c-danger); border-color: var(--c-danger); color: #fff; }
.chunk.active { background: rgba(79, 110, 247, .4); border-color: var(--c-primary); color: #fff; animation: pulse 1.2s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { opacity: .6; } 50% { opacity: 1; } }
.failed-row { margin-top: 12px; display: flex; align-items: center; gap: 12px; }
.ok { color: var(--c-success); }
</style>
