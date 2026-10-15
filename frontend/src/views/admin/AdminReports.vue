<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">举报处理</h2>
        <div class="sm dim">处理结果会通知举报人并为其发放正反馈积分</div>
      </div>
      <el-radio-group v-model="status" @change="load">
        <el-radio-button :value="0">待处理</el-radio-button>
        <el-radio-button :value="1">已处理</el-radio-button>
        <el-radio-button :value="2">已驳回</el-radio-button>
        <el-radio-button :value="''">全部</el-radio-button>
      </el-radio-group>
    </div>

    <AppState :loading="loading && !list.length" :error="error" :empty="!loading && !error && !list.length"
      empty-text="没有举报记录" empty-desc="当前筛选条件下没有数据。" :skeleton-count="4" @retry="load">
      <el-table :data="list" style="width:100%">
        <el-table-column prop="report_id" label="ID" width="70" />
        <el-table-column label="对象" min-width="200">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.target_type }}</el-tag>
            <span class="sm" style="margin-left:6px">#{{ row.target_id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原因" min-width="160">
          <template #default="{ row }">
            <div class="sm">{{ reasonLabel(row.reason_type) }}</div>
            <div class="xs dim clamp2">{{ row.detail || '（无补充）' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="举报人" width="120">
          <template #default="{ row }">{{ row.reporter?.nickname || row.reporter_id }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="stOf(row.status).type" effect="dark">{{ stOf(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmtDateTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" text :disabled="row.status !== 0" @click="handle(row, 'resolve')">受理并加分</el-button>
            <el-button size="small" type="danger" text :disabled="row.status !== 0" @click="handle(row, 'reject')">驳回</el-button>
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
import { REASON_TYPES, REPORT_STATUS, fmtDateTime, pickList, pickTotal } from '@/utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref(0)
const loading = ref(false)
const error = ref(null)

function reasonLabel(v) { return REASON_TYPES.find((r) => r.value === v)?.label || v || '未填写' }
function stOf(s) { return REPORT_STATUS[s] || { label: '未知', type: 'info' } }

async function load() {
  loading.value = true
  error.value = null
  const params = { page: page.value, size: size.value }
  if (status.value !== '') params.status = status.value
  const r = await safe(adminApi.reports(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  list.value = pickList(r.data)
  total.value = pickTotal(r.data)
}

async function handle(row, action) {
  try {
    const { value } = await ElMessageBox.prompt(
      action === 'resolve' ? '处理结果（会通知举报人并加分）' : '驳回理由（会通知举报人）',
      action === 'resolve' ? '受理举报' : '驳回举报',
      { inputValidator: (v) => (!!v && v.trim()) || '必须填写内容' }
    )
    const r = await safe(adminApi.handleReport(row.report_id, { action, result: value }))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    ElMessage.success(action === 'resolve' ? '已受理，举报人已获积分' : '已驳回')
    load()
  } catch (e) {}
}

onMounted(load)
</script>
