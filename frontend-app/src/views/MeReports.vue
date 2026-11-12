<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">我的举报</h2>
        <div class="sm dim">处理进度一目了然；有效举报会获得正反馈积分</div>
      </div>
      <el-button @click="markRead" :disabled="!unread"><el-icon><Check /></el-icon>&nbsp;标记处理结果已读</el-button>
    </div>

    <!-- 正反馈积分卡 -->
    <div class="reward-card">
      <div>
        <div class="xs" style="opacity:.85">正反馈积分</div>
        <div class="reward-num num">{{ rewardPoints }}</div>
        <div class="xs" style="opacity:.85;margin-top:4px">举报属实并处理成功后，管理员会发放积分奖励，感谢你维护社区秩序。</div>
      </div>
      <el-icon :size="56" style="opacity:.35"><Medal /></el-icon>
    </div>

    <AppState
      :loading="loading && !list.length"
      :error="error"
      :empty="!loading && !error && !list.length"
      empty-text="还没有举报记录"
      empty-desc="在项目详情或评论处点击「举报」即可提交。"
      :skeleton-count="4"
      @retry="load"
    >
      <div class="list">
        <div v-for="r in list" :key="r.report_id" class="card item">
          <div class="row between center wrap gap8">
            <div class="row center gap8">
              <el-tag :type="stOf(r.status).type" size="small" effect="dark">{{ stOf(r.status).label }}</el-tag>
              <span class="sm ellipsis" style="max-width:420px">{{ targetText(r) }}</span>
            </div>
            <span class="xs dim">{{ fmtDateTime(r.created_at) }}</span>
          </div>
          <div v-if="r.handle_result" class="result sm">
            <el-icon><ChatLineSquare /></el-icon>
            处理结果：{{ r.handle_result }}
          </div>
        </div>
      </div>
    </AppState>

    <div v-if="list.length < total" style="text-align:center;margin-top:16px">
      <el-button text @click="loadMore">加载更多（{{ list.length }}/{{ total }}）</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppState from '@/components/AppState.vue'
import { interactApi, safe } from '@/api'
import { REPORT_STATUS, fmtDateTime, pickList, pickTotal } from '@/utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref(null)
const rewardPoints = ref(0)
const unread = ref(0)

function stOf(s) { return REPORT_STATUS[s] || { label: '未知', type: 'info' } }
function targetText(r) {
  const t = r.target || {}
  const typeLabel = { project: '项目', comment: '评论', user: '用户' }[r.target_type || t.type] || '对象'
  return `${typeLabel}：${t.title || t.content || t.nickname || r.target_id || '—'}`
}

async function load(reset = true) {
  if (reset) { page.value = 1; list.value = [] }
  loading.value = true
  error.value = null
  const r = await safe(interactApi.myReports({ page: page.value, size: 20 }))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  const items = pickList(r.data)
  list.value = reset ? items : list.value.concat(items)
  total.value = pickTotal(r.data)
  rewardPoints.value = r.data?.reward_points ?? r.data?.points ?? 0
  unread.value = r.data?.unread ?? r.data?.unread_count ?? 0
}
function loadMore() { page.value += 1; load(false) }

async function markRead() {
  const r = await safe(interactApi.readReports())
  if (!r.ok) { ElMessage.error('操作失败'); return }
  unread.value = 0
  ElMessage.success('已标记为已读')
}

onMounted(() => load(true))
</script>

<style scoped>
.reward-card {
  display: flex; align-items: center; justify-content: space-between;
  padding: 22px 24px; border-radius: var(--r-lg); margin-bottom: 20px;
  background: var(--c-gradient-brand); color: #fff; box-shadow: var(--sh-brand);
}
.reward-num { font-size: 34px; font-weight: 700; line-height: 1.1; }
.list { display: flex; flex-direction: column; gap: 12px; }
.item { padding: 14px 16px; }
.result {
  margin-top: 10px; padding: 8px 12px; border-radius: var(--r-md);
  background: var(--c-bg-sunken); color: var(--c-text-2);
  display: flex; align-items: center; gap: 8px;
}
</style>
