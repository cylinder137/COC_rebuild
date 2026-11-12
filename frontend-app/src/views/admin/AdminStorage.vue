<template>
  <div>
    <el-page-header @back="$router.back()" style="margin-bottom:16px">
      <template #content>
        <span style="font-weight:600">项目存储路径</span>
      </template>
    </el-page-header>

    <el-alert type="warning" :closable="false" show-icon class="mb16">
      <template #title>安全声明（系统内不会运行任何未过审作品）</template>
      <template #default>
        以下路径仅供管理员在服务器上人工审核使用；请勿在浏览器中打开未过审文件。每次查看都会写入审计日志（action = view_storage_path）。
      </template>
    </el-alert>

    <AppState :loading="loading" :error="error" :empty="!loading && !error && !data" empty-text="没有存储信息"
      empty-desc="后端 /admin/projects/{id}/storage 可能尚未实现。" @retry="load">
      <div v-if="data">
        <div class="card mb16">
          <div class="row center gap12 wrap">
            <span class="mono dim">项目 #{{ id }}</span>
            <el-tag size="small" effect="plain">{{ data.project_type || data.type || '—' }}</el-tag>
            <el-tag size="small" :type="isUnapproved ? 'danger' : 'success'" effect="dark">
              {{ isUnapproved ? '⚠ 未过审' : '已发布 / 免审' }}
            </el-tag>
            <span class="grow" />
            <el-button size="small" @click="exportCsv">导出 CSV</el-button>
          </div>
        </div>

        <div class="card mb16">
          <div class="card-hd">存储位置（可复制）</div>
          <div v-for="row in paths" :key="row.label" class="path-row">
            <span class="dim path-label">{{ row.label }}</span>
            <span class="mono path-val">{{ row.value || '—' }}</span>
            <el-button size="small" text :disabled="!row.value" @click="copy(row.value)">
              <el-icon><CopyDocument /></el-icon>&nbsp;复制路径
            </el-button>
            <el-tag v-if="row.warn" size="small" type="danger" effect="plain">⚠ 未过审</el-tag>
          </div>
        </div>

        <div class="card">
          <div class="card-hd">文件清单（只读）</div>
          <el-table :data="files" size="small" max-height="360">
            <el-table-column prop="file_name" label="名称" min-width="180" />
            <el-table-column label="大小" width="110">
              <template #default="{ row }">{{ fmtSize(row.file_size) }}</template>
            </el-table-column>
            <el-table-column prop="file_type" label="类型" width="90" />
            <el-table-column label="sha256" min-width="240">
              <template #default="{ row }"><span class="mono xs">{{ row.sha256 || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="打包" width="110">
              <template #default="{ row }">{{ buildText(row.build_status) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!files.length" class="xs dim" style="padding:12px 0">暂无文件记录</div>
        </div>
      </div>
    </AppState>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppState from '@/components/AppState.vue'
import { adminApi, safe } from '@/api'
import { fmtSize, pickList } from '@/utils/format'

const route = useRoute()
const id = route.params.id
const data = ref(null)
const loading = ref(false)
const error = ref(null)

const isUnapproved = computed(() => {
  const s = data.value?.status
  return s === 0 || s === 1 || s === 4
})
const paths = computed(() => {
  const d = data.value || {}
  return [
    { label: '上传暂存', value: d.upload_path || d.raw_path, warn: isUnapproved.value },
    { label: '解包目录', value: d.unzip_path || d.extract_path, warn: isUnapproved.value },
    { label: '发布目录', value: d.publish_path || d.www_path || '', warn: false },
    { label: '源包文件', value: d.source_path || d.storage_path, warn: isUnapproved.value }
  ]
})
const files = computed(() => pickList(data.value?.files || data.value?.file_list || []))

function buildText(s) {
  return { 0: '无需', 1: '待打包', 2: '成功', 3: '失败' }[s] ?? '—'
}
function copy(v) {
  navigator.clipboard?.writeText(v).then(
    () => ElMessage.success('路径已复制'),
    () => ElMessage.warning('复制失败，请手动选择复制')
  )
}
function exportCsv() {
  if (!files.value.length) { ElMessage.warning('没有文件可导出'); return }
  const head = 'file_name,file_size,file_type,sha256\n'
  const body = files.value.map((f) => `${f.file_name},${f.file_size},${f.file_type},${f.sha256 || ''}`).join('\n')
  const blob = new Blob([head + body], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `project-${id}-files.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

async function load() {
  loading.value = true
  error.value = null
  const r = await safe(adminApi.storage(id))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  data.value = r.data || {}
}
onMounted(load)
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.path-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px dashed var(--c-border); }
.path-row:last-child { border-bottom: none; }
.path-label { min-width: 76px; font-size: var(--fs-sm); }
.path-val { flex: 1; word-break: break-all; font-size: var(--fs-sm); }
</style>
