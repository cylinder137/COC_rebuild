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
        <div class="card-hd">
          <span>全站浏览 / 评论趋势</span>
          <el-radio-group v-model="range" size="small" @change="loadTrend">
            <el-radio-button :value="2">2 天</el-radio-button>
            <el-radio-button :value="7">7 天</el-radio-button>
            <el-radio-button :value="15">15 天</el-radio-button>
            <el-radio-button :value="30">30 天</el-radio-button>
          </el-radio-group>
        </div>
        <AppState
          :loading="trendLoading"
          :error="trendError"
          :empty="!trendLoading && !trendError && !points.length"
          empty-text="暂无趋势数据"
          empty-desc="作品获得浏览与评论后，这里会显示折线图。"
          :skeleton-count="1"
          @retry="loadTrend"
        >
          <template #skeleton>
            <div class="sk" style="height:300px;width:100%" />
          </template>
        </AppState>
        <div v-show="!trendLoading && !trendError && points.length" ref="chartEl" class="chart" />
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
import { computed, onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import AppState from '@/components/AppState.vue'
import { adminApi, safe } from '@/api'
import { compactNum, pickList } from '@/utils/format'
import { useThemeStore } from '@/stores/ui'

const theme = useThemeStore()

const raw = ref({})
const loading = ref(false)
const error = ref(null)

// ---- 趋势折线 ----
const range = ref(7)
const points = ref([])
const trendLoading = ref(false)
const trendError = ref(null)
const chartEl = ref(null)
let chart = null

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

async function loadTrend() {
  trendLoading.value = true
  trendError.value = null
  const r = await safe(adminApi.platformTrend(range.value))
  trendLoading.value = false
  if (!r.ok) { trendError.value = r.error || true; points.value = []; return }
  points.value = pickList(r.data)
  await nextTick()
  renderChart()
}

function renderChart() {
  if (!chartEl.value) return
  if (!points.value.length) return
  if (!chart) chart = echarts.init(chartEl.value)
  const dark = theme.isDark
  const textColor = dark ? '#9BA3B4' : '#5F6672'
  const axisLine = dark ? '#262B3A' : '#E8EAF0'
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { data: ['浏览量', '评论量'], textStyle: { color: textColor }, right: 10, top: 0 },
    grid: { left: 42, right: 20, top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: points.value.map((p) => p.date),
      axisLine: { lineStyle: { color: axisLine } },
      axisLabel: { color: textColor, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: axisLine, type: 'dashed' } },
      axisLabel: { color: textColor, fontSize: 11 }
    },
    series: [
      {
        name: '浏览量', type: 'line', smooth: true, showSymbol: false,
        data: points.value.map((p) => Number(p.views || 0)),
        lineStyle: { width: 2.5, color: dark ? '#6C86FF' : '#4F6EF7' },
        itemStyle: { color: dark ? '#6C86FF' : '#4F6EF7' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(79,110,247,.35)' },
            { offset: 1, color: 'rgba(79,110,247,0)' }
          ])
        }
      },
      {
        name: '评论量', type: 'line', smooth: true, showSymbol: false,
        data: points.value.map((p) => Number(p.comments || 0)),
        lineStyle: { width: 2.5, color: dark ? '#34D399' : '#10B981' },
        itemStyle: { color: dark ? '#34D399' : '#10B981' }
      }
    ]
  }, true)
  chart.resize()
}

function onResize() { chart?.resize() }

watch(() => theme.theme, () => renderChart())

onMounted(() => {
  window.addEventListener('resize', onResize)
  load()
  loadTrend()
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 16px; }
.kpi-num { font-size: 24px; font-weight: 700; margin-top: 6px; }
.raw { background: var(--c-bg-sunken); padding: 12px; border-radius: var(--r-md); max-height: 260px; overflow: auto; }
.chart { width: 100%; height: 320px; }
</style>
