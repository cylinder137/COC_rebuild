<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">开发者申请</h2>
        <div class="sm dim">通过后用户 status 置为正常，即可用 QQ 号登录投稿</div>
      </div>
      <el-radio-group v-model="status" @change="load">
        <el-radio-button :value="0">待审</el-radio-button>
        <el-radio-button :value="1">已通过</el-radio-button>
        <el-radio-button :value="2">已驳回</el-radio-button>
        <el-radio-button :value="''">全部</el-radio-button>
      </el-radio-group>
    </div>

    <AppState :loading="loading && !list.length" :error="error" :empty="!loading && !error && !list.length"
      empty-text="没有申请记录" empty-desc="当前筛选条件下没有数据。" :skeleton-count="4" @retry="load">
      <el-table :data="list" style="width:100%">
        <el-table-column prop="developer_profile_id" label="ID" width="70" />
        <el-table-column label="QQ 号 / 昵称" min-width="180">
          <template #default="{ row }">
            <div class="mono">{{ row.qq_account || row.user?.qq_account || '—' }}</div>
            <div class="xs dim">{{ row.nickname || row.user?.nickname || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="contact" label="联系方式" min-width="160" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="stOf(row.apply_status).type" effect="dark">{{ stOf(row.apply_status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.applied_at || row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" text :disabled="row.apply_status !== 0" @click="approve(row)">通过</el-button>
            <el-button size="small" type="danger" text :disabled="row.apply_status !== 0" @click="reject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppState>

    <div style="text-align:center;margin-top:16px" v-if="list.length">
      <el-pagination layout="prev, pager, next, total" :total="total" :page-size="size" :current-page="page"
        @current-change="(p) => { page = p; load() }" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppState from '@/components/AppState.vue'
import { adminApi, safe } from '@/api'
import { fmtDateTime, pickList, pickTotal } from '@/utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref(0)
const loading = ref(false)
const error = ref(null)

function stOf(s) {
  return { 0: { label: '待审', type: 'warning' }, 1: { label: '已通过', type: 'success' }, 2: { label: '已驳回', type: 'danger' } }[s]
    || { label: '未知', type: 'info' }
}

async function load() {
  loading.value = true
  error.value = null
  const params = { page: page.value, size: size.value }
  if (status.value !== '') params.status = status.value
  const r = await safe(adminApi.devApplications(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  list.value = pickList(r.data)
  total.value = pickTotal(r.data)
}

async function approve(row) {
  const r = await safe(adminApi.approveDev(row.developer_profile_id || row.user_id))
  if (!r.ok) { ElMessage.error('操作失败'); return }
  ElMessage.success('已通过，用户现在可以登录投稿')
  load()
}
async function reject(row) {
  try {
    const { value } = await ElMessageBox.prompt('驳回理由（会通知申请人）', '驳回申请', {
      inputValidator: (v) => (!!v && v.trim()) || '必须填写理由'
    })
    const r = await safe(adminApi.rejectDev(row.developer_profile_id || row.user_id, { reason: value }))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    ElMessage.success('已驳回'); load()
  } catch (e) {}
}

onMounted(load)
</script>
