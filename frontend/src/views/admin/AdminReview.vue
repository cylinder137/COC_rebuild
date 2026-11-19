<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">审核队列</h2>
        <div class="sm dim">默认只显示需人工审核的 HTML / zip 待审项；Scratch 与结构文件免审不进队列。驳回 = 直接删除该作品及其服务器代码文件（不保留审核记录）</div>
      </div>
      <div class="row center gap12">
        <el-checkbox v-model="showAuto" @change="load">显示免审类型</el-checkbox>
        <el-button @click="load"><el-icon><Refresh /></el-icon>&nbsp;刷新</el-button>
      </div>
    </div>

    <AppState :loading="loading && !list.length" :error="error" :empty="!loading && !error && !list.length"
      empty-text="队列为空" empty-desc="当前没有待审核的作品。" :skeleton-count="5" @retry="load">
      <el-table :data="list" style="width:100%">
        <el-table-column label="作品" min-width="240">
          <template #default="{ row }">
            <div class="row center gap8">
              <span class="tbar" :style="{ background: typeColor(row.project_type) }" />
              <router-link :to="`/projects/${row.project_id}`" class="pname ellipsis">{{ row.title }}</router-link>
            </div>
            <div class="xs dim">@{{ row.author?.nickname || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.project_type }}</el-tag>
            <el-tag size="small" :type="row.audit_mode === 'auto' ? 'success' : 'warning'" effect="plain" style="margin-left:4px">
              {{ row.audit_mode === 'auto' ? '免审' : '待审' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusOf(row.status).type" effect="dark">{{ statusOf(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="openCode(row)">查看/编辑代码</el-button>
            <el-button size="small" text @click="viewStorage(row)">存储路径</el-button>
            <el-button v-if="row.status !== 2" size="small" type="success" text @click="approve(row)">通过</el-button>
            <el-button v-if="row.status !== 2" size="small" type="danger" text @click="reject(row)">驳回并删除</el-button>
            <el-button v-if="row.status === 2" size="small" type="danger" text @click="down(row)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppState>

    <!-- 源码查看 / 在线编辑 -->
    <el-dialog v-model="codeVisible" :title="`源码 · ${codeRow?.title || ''}`" width="86%" top="6vh"
               destroy-on-close @closed="resetCode">
      <div v-loading="codeLoading">
        <div class="row center gap12 wrap" style="margin-bottom:12px">
          <el-select v-model="activeRel" placeholder="选择文件" style="width:320px" @change="selectFile">
            <el-option v-for="f in files" :key="f.rel" :value="f.rel"
              :label="`${f.file_name}（${fmtSize(f.size)}）`" />
          </el-select>
          <el-tag v-if="currentFile" size="small" :type="currentFile.editable ? 'success' : 'info'" effect="plain">
            {{ currentFile.editable ? '可编辑文本' : '只读 / 二进制' }}
          </el-tag>
          <span class="xs dim" v-if="currentFile">.{{ currentFile.ext || '—' }} · {{ fmtSize(currentFile.size) }}</span>
          <span class="grow" />
          <span class="xs dim">改动只写入服务器源码目录；已打包产物不会自动重建。</span>
        </div>
        <el-input
          v-model="content"
          type="textarea"
          :rows="22"
          :disabled="!currentFile || !currentFile.editable"
          class="code-area"
          placeholder="该文件无可读文本内容"
          spellcheck="false"
          @input="markDirty"
        />
      </div>
      <template #footer>
        <el-button @click="codeVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!currentFile?.editable || !dirty || saving" :loading="saving"
          @click="saveCode">保存修改</el-button>
      </template>
    </el-dialog>

    <div style="text-align:center;margin-top:16px" v-if="list.length">
      <el-pagination
        layout="prev, pager, next, total"
        :total="total" :page-size="size" :current-page="page"
        @current-change="(p) => { page = p; load() }"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppState from '@/components/AppState.vue'
import { adminApi, safe } from '@/api'
import { TYPE_COLOR, fmtDateTime, fmtSize, pickList, pickTotal, statusOf } from '@/utils/format'

const router = useRouter()
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const error = ref(null)
const showAuto = ref(false)

// ---- 源码查看 / 编辑 ----
const codeVisible = ref(false)
const codeRow = ref(null)
const files = ref([])
const activeRel = ref('')
const content = ref('')
const codeLoading = ref(false)
const saving = ref(false)
const dirty = ref(false)

const currentFile = computed(() => files.value.find((f) => f.rel === activeRel.value) || null)

async function openCode(row) {
  codeRow.value = row
  codeVisible.value = true
  files.value = []
  activeRel.value = ''
  content.value = ''
  dirty.value = false
  codeLoading.value = true
  const r = await safe(adminApi.sourceFiles(row.project_id))
  codeLoading.value = false
  if (!r.ok) { ElMessage.error('源码加载失败'); return }
  files.value = pickList(r.data?.files)
  const first = files.value[0]
  if (first) {
    activeRel.value = first.rel
    selectFile(first.rel)
  }
}

function selectFile(rel) {
  const f = files.value.find((x) => x.rel === rel)
  if (!f) return
  activeRel.value = rel
  content.value = f.content ?? ''
  dirty.value = false
}

function markDirty() { dirty.value = true }

async function saveCode() {
  const f = currentFile.value
  if (!f || !f.editable) return
  saving.value = true
  const r = await safe(adminApi.saveSource(codeRow.value.project_id, { rel: f.rel, content: content.value }))
  saving.value = false
  if (!r.ok) { ElMessage.error('保存失败'); return }
  f.content = content.value
  f.size = r.data?.size ?? f.size
  dirty.value = false
  ElMessage.success('源码已保存')
}

function resetCode() {
  codeRow.value = null
  files.value = []
  activeRel.value = ''
  content.value = ''
  dirty.value = false
}

function typeColor(t) { return TYPE_COLOR[t] || 'var(--c-primary)' }

async function load() {
  loading.value = true
  error.value = null
  const params = { page: page.value, size: size.value, status: 1 }
  if (!showAuto.value) params.audit_mode = 'manual'
  const r = await safe(adminApi.projects(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  list.value = pickList(r.data)
  total.value = pickTotal(r.data)
}

async function approve(row) {
  const vid = row.latest_version_id || row.current_version_id || row.project_version_id
  if (!vid) { ElMessage.warning('缺少版本 ID（后端未返回 latest_version_id），请到项目管理页操作'); return }
  const r = await safe(adminApi.approve(vid, {}))
  if (!r.ok) { ElMessage.error('操作失败'); return }
  ElMessage.success('已通过审核，作品已公开')
  load()
}

/** 驳回 = 直接删除被驳回的作品（含服务器上的代码文件），不保留审核记录 */
async function reject(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      '驳回原因（会通知作者）。注意：驳回将直接删除该作品及服务器上的代码文件，不可恢复。',
      '驳回并删除作品',
      {
        inputType: 'textarea',
        inputPlaceholder: '例如：含站外跳转 / 存在安全风险 / 不符合投稿规范',
        inputValidator: (v) => !!((v || '').trim()) || '必须填写原因',
        confirmButtonText: '驳回并删除',
        confirmButtonClass: 'el-button--danger'
      }
    )
    const r = await safe(adminApi.removeProject(row.project_id, { reason: value.trim() }))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    ElMessage.success(r.data?.storage_removed === false ? '已驳回并删除（代码目录原本不存在）' : '已驳回并删除')
    load()
  } catch (e) {}
}

async function down(row) {
  try {
    const { value } = await ElMessageBox.prompt('请填写下架原因（代码保留在服务器）', '下架作品', {
      inputValidator: (v) => !!((v || '').trim()) || '必须填写原因'
    })
    const r = await safe(adminApi.down(row.project_id, { reason: value }))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    ElMessage.success('已下架')
    load()
  } catch (e) {}
}

function viewStorage(row) { router.push(`/admin/projects/${row.project_id}/storage`) }

onMounted(load)
</script>

<style scoped>
.tbar { display: inline-block; width: 4px; height: 18px; border-radius: 2px; }
.pname { font-weight: 600; color: var(--c-text-1); }
.code-area :deep(.el-textarea__inner) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre;
  background: var(--c-bg-sunken);
}
</style>
