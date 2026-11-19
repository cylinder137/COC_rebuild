<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">Bug 反馈</h2>
        <div class="sm dim">用户在设置页提交的问题反馈，按提交时间倒序</div>
      </div>
      <el-radio-group v-model="status" @change="load">
        <el-radio-button :value="0">未受理</el-radio-button>
        <el-radio-button :value="1">已处理</el-radio-button>
        <el-radio-button :value="''">全部</el-radio-button>
      </el-radio-group>
    </div>

    <AppState :loading="loading && !list.length" :error="error" :empty="!loading && !error && !list.length"
      empty-text="没有反馈记录" empty-desc="当前筛选条件下没有数据。" :skeleton-count="4" @retry="load">
      <el-table :data="list" style="width:100%">
        <el-table-column prop="bug_id" label="ID" width="70" />
        <el-table-column label="用户" min-width="150">
          <template #default="{ row }">
            <div class="sm">{{ row.user_nickname || row.qq_account || row.user_id }}</div>
            <div class="xs dim mono">{{ row.qq_account || ('#' + row.user_id) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="反馈内容" min-width="320">
          <template #default="{ row }">
            <div class="bug-content sm">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="stOf(row.status).type" effect="dark">{{ stOf(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ fmtDateTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" text :disabled="row.status !== 0" @click="handle(row)">
              标记已处理
            </el-button>
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

const BUG_STATUS = {
  0: { label: '未受理', type: 'warning' },
  1: { label: '已处理', type: 'success' }
}

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref(0)
const loading = ref(false)
const error = ref(null)

function stOf(s) { return BUG_STATUS[s] || { label: '未知', type: 'info' } }

async function load() {
  loading.value = true
  error.value = null
  const params = { page: page.value, size: size.value }
  if (status.value !== '') params.status = status.value
  const r = await safe(adminApi.bugFeedbacks(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  list.value = pickList(r.data)
  total.value = pickTotal(r.data)
}

async function handle(row) {
  try {
    await ElMessageBox.confirm(`确认把反馈 #${row.bug_id} 标记为已处理？`, 'Bug 反馈', { type: 'info' })
  } catch (e) { return }
  const r = await safe(adminApi.handleBugFeedback(row.bug_id))
  if (!r.ok) { ElMessage.error('操作失败，请稍后重试'); return }
  ElMessage.success('已标记为已处理')
  load()
}

onMounted(load)
</script>

<style scoped>
.bug-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}
</style>
