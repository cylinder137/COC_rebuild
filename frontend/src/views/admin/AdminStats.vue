<template>
  <div>
    <h2 class="section-title">平台统计</h2>
    <AppState :loading="loading" :error="error" :empty="false" @retry="load">
      <div class="kpi-row">
        <div class="card kpi" v-for="k in kpis" :key="k.label">
          <div class="xs dim">{{ k.label }}</div>
          <div class="kpi-num num">{{ compactNum(k.value) }}</div>
        </div>
      </div>
      <div class="card" style="margin-top:16px">
        <div class="card-hd">待办入口</div>
        <div class="row gap12 wrap">
          <router-link to="/admin/review"><el-button type="warning" plain>审核队列</el-button></router-link>
          <router-link to="/admin/reports"><el-button type="danger" plain>举报处理</el-button></router-link>
          <router-link to="/admin/developers"><el-button plain>开发者申请</el-button></router-link>
          <router-link to="/admin/password-resets"><el-button plain>找回申请</el-button></router-link>
          <router-link to="/admin/comments"><el-button plain>评论管理</el-button></router-link>
        </div>
      </div>
      <div class="card" style="margin-top:16px">
        <div class="card-hd">原始数据（后端 /admin/stats/platform）</div>
        <pre class="mono xs raw">{{ JSON.stringify(raw, null, 2) }}</pre>
      </div>
    </AppState>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppState from '@/components/AppState.vue'
import { adminApi, safe } from '@/api'
import { compactNum } from '@/utils/format'

const raw = ref({})
const loading = ref(false)
const error = ref(null)

const kpis = computed(() => {
  const d = raw.value || {}
  // 字段以后端 /admin/stats/platform 实际返回为准（users_total/... 为真名，
  // 其余别名仅作兼容兜底）。后端没返回的指标不要凭空造，避免展示假 0。
  return [
    { label: '用户总数', value: d.users_total ?? d.user_count ?? d.users ?? 0 },
    { label: '开发者数', value: d.developers ?? 0 },
    { label: '项目总数', value: d.projects_total ?? d.project_count ?? d.projects ?? 0 },
    { label: '已发布项目', value: d.projects_published ?? 0 },
    { label: '待审数量', value: d.projects_pending ?? d.pending_count ?? d.pending ?? 0 },
    { label: '今日浏览量', value: d.views_today ?? d.today_views ?? 0 },
    { label: '待处理举报', value: d.reports_pending ?? d.report_pending ?? d.pending_reports ?? 0 },
    { label: '待处理开发者申请', value: d.dev_applies_pending ?? 0 }
  ]
})

async function load() {
  loading.value = true
  error.value = null
  const r = await safe(adminApi.platformStats())
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  raw.value = r.data || {}
}
onMounted(load)
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 16px; }
.kpi-num { font-size: 24px; font-weight: 700; margin-top: 6px; }
.raw { background: var(--c-bg-sunken); padding: 12px; border-radius: var(--r-md); max-height: 260px; overflow: auto; }
</style>
