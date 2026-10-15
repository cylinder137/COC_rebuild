<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">审核队列</h2>
        <div class="sm dim">默认只显示需人工审核的 HTML / zip 待审项；Scratch 与结构文件免审不进队列</div>
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
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="viewStorage(row)">存储路径</el-button>
            <el-button v-if="row.status !== 2" size="small" type="success" text @click="approve(row)">通过</el-button>
            <el-button v-if="row.status !== 2" size="small" type="danger" text @click="reject(row)">驳回</el-button>
            <el-button v-if="row.status === 2" size="small" type="danger" text @click="down(row)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppState>

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
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppState from '@/components/AppState.vue'
import { adminApi, safe } from '@/api'
import { TYPE_COLOR, fmtDateTime, pickList, pickTotal, statusOf } from '@/utils/format'

const router = useRouter()
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const error = ref(null)
const showAuto = ref(false)

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
  const vid = row.current_version_id || row.project_version_id
  if (!vid) { ElMessage.warning('缺少版本 ID（后端详情未返回 project_version_id），请到项目管理页操作'); return }
  const r = await safe(adminApi.approve(vid, {}))
  if (!r.ok) { ElMessage.error('操作失败'); return }
  ElMessage.success('已通过审核，作品已公开')
  load()
}

async function reject(row) {
  const vid = row.current_version_id || row.project_version_id
  if (!vid) { ElMessage.warning('缺少版本 ID'); return }
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因（会通知作者）', '驳回作品', {
      inputValidator: (v) => (!!v && v.trim()) || '必须填写原因'
    })
    const r = await safe(adminApi.reject(vid, { reason: value }))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    ElMessage.success('已驳回')
    load()
  } catch (e) {}
}

async function down(row) {
  try {
    const { value } = await ElMessageBox.prompt('请填写下架原因（代码保留在服务器）', '下架作品', {
      inputValidator: (v) => (!!v && v.trim()) || '必须填写原因'
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
</style>
