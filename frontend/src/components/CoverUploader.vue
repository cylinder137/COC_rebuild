<template>
  <div class="cover-up">
    <div
      class="drop"
      :class="{ over: dragOver, busy: uploading }"
      @click="pick"
      @dragover.prevent="dragOver = true"
      @dragleave.prevent="dragOver = false"
      @drop.prevent="onDrop"
    >
      <template v-if="preview">
        <img class="pv" :src="preview" alt="封面预览" />
        <div class="mask">
          <span>{{ uploading ? '上传中…' : '点击更换封面' }}</span>
        </div>
      </template>
      <template v-else>
        <el-icon :size="30"><Picture /></el-icon>
        <div class="t">点击或拖拽图片到这里上传封面</div>
        <div class="d xs dim">支持 jpg / png / webp / gif，不超过 5 MB，建议 16:9（如 800×450）</div>
      </template>
    </div>

    <div class="bar">
      <span class="xs dim">{{ hint }}</span>
      <el-button v-if="hasCover" size="small" text type="danger" :disabled="uploading" @click="remove">
        移除封面
      </el-button>
    </div>

    <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="onPick" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadApi, safe } from '@/api'
import { fileUrl } from '@/utils/format'

const props = defineProps({
  /** 目标项目 id（草稿也要先建好项目才有 id） */
  projectId: { type: [Number, String], default: null },
  /** 当前封面相对路径，如 covers/cover-01.png */
  coverPath: { type: String, default: '' },
  /** 是否允许移除 */
  removable: { type: Boolean, default: true }
})
const emit = defineEmits(['updated'])

const MAX = 5 * 1024 * 1024
const OK_EXT = ['jpg', 'jpeg', 'png', 'webp', 'gif']

const fileInput = ref(null)
const dragOver = ref(false)
const uploading = ref(false)
const localUrl = ref('')      // 刚选中的本地预览
const current = ref(props.coverPath || '')

watch(() => props.coverPath, (v) => { current.value = v || '' })

const preview = computed(() => localUrl.value || (current.value ? fileUrl(current.value) : ''))
const hasCover = computed(() => !!current.value)
const hint = computed(() => {
  if (uploading.value) return '正在上传封面，请稍候…'
  if (!props.projectId) return '请先完成上一步（保存草稿）后再上传封面'
  return hasCover.value ? '封面已保存，再次上传会替换旧图' : '还没有封面，卡片会显示默认底图'
})

function pick() {
  if (uploading.value) return
  if (!props.projectId) { ElMessage.warning('请先保存草稿，再上传封面'); return }
  fileInput.value?.click()
}
function onDrop(e) {
  dragOver.value = false
  if (!props.projectId) { ElMessage.warning('请先保存草稿，再上传封面'); return }
  const f = e.dataTransfer?.files?.[0]
  if (f) upload(f)
}
function onPick(e) {
  const f = e.target.files?.[0]
  if (f) upload(f)
  e.target.value = ''
}

async function upload(file) {
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!OK_EXT.includes(ext)) { ElMessage.warning('封面只支持 jpg / png / webp / gif'); return }
  if (file.size > MAX) { ElMessage.warning('封面不能超过 5 MB'); return }
  clearLocal()
  localUrl.value = URL.createObjectURL(file)
  uploading.value = true
  const fd = new FormData()
  fd.append('file', file)
  const r = await safe(uploadApi.uploadCover(props.projectId, fd))
  uploading.value = false
  if (!r.ok) {
    clearLocal()
    ElMessage.error(r.error?.response?.data?.message || '封面上传失败，请重试')
    return
  }
  current.value = r.data?.cover_path || ''
  clearLocal()
  ElMessage.success('封面已更新')
  emit('updated', r.data)
}

async function remove() {
  try {
    await ElMessageBox.confirm('移除后作品卡片会恢复默认底图，确定移除封面吗？', '移除封面', {
      type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消'
    })
  } catch (e) { return }
  const r = await safe(uploadApi.removeCover(props.projectId))
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '移除失败，请重试'); return }
  current.value = ''
  clearLocal()
  ElMessage.success('封面已移除')
  emit('updated', r.data)
}

function clearLocal() {
  if (localUrl.value) { URL.revokeObjectURL(localUrl.value); localUrl.value = '' }
}
onBeforeUnmount(clearLocal)
</script>

<style scoped>
.drop {
  position: relative; border: 1px dashed var(--c-border-strong); border-radius: var(--r-lg);
  background: var(--c-bg-sunken); cursor: pointer; overflow: hidden;
  aspect-ratio: 16 / 9; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 8px; text-align: center; padding: 16px; transition: all var(--dur-fast) var(--ease-out);
}
.drop:hover { border-color: var(--c-primary); }
.drop.over { border-color: var(--c-primary); background: var(--c-primary-weak); }
.drop.busy { cursor: progress; }
.drop .pv { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.drop .mask {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  background: rgba(0, 0, 0, .45); color: #fff; font-weight: 600; opacity: 0; transition: opacity var(--dur-fast);
}
.drop:hover .mask { opacity: 1; }
.drop .t { font-weight: 600; }
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 8px; }
</style>
