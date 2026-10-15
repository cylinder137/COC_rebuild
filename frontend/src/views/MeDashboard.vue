<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">数据看板</h2>
        <div class="sm dim">浏览量 / 评论量折线（后端按天·小时表动态计算）</div>
      </div>
      <div class="row center gap8">
        <el-select v-model="projectId" placeholder="选择作品" style="width:220px" @change="loadStats">
          <el-option v-for="p in projects" :key="p.project_id || p.id" :label="p.title" :value="p.project_id || p.id" />
        </el-select>
        <el-radio-group v-model="range" @change="loadStats">
          <el-radio-button :value="2">2 天</el-radio-button>
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="15">15 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- KPI -->
    <div class="kpi-row">
      <div class="card kpi">
        <div class="xs dim">总浏览量</div>
        <div class="kpi-num num">{{ compactNum(sumViews) }}</div>
      </div>
      <div class="card kpi">
        <div class="xs dim">区间浏览量</div>
        <div class="kpi-num num">{{ compactNum(rangeViews) }}</div>
      </div>
      <div class="card kpi">
        <div class="xs dim">区间评论量</div>
        <div class="kpi-num num">{{ compactNum(rangeComments) }}</div>
      </div>
      <div class="card kpi">
        <div class="xs dim">数据点</div>
        <div class="kpi-num num">{{ points.length }}</div>
      </div>
    </div>

    <div class="card" style="margin-top:16px">
      <div class="card-hd">
        <span>趋势（range = {{ range }}）</span>
        <span class="xs dim">{{ range === 2 ? '按小时聚合' : '按天聚合' }}</span>
      </div>
      <AppState
        :loading="loading"
        :error="error"
        :empty="!loading && !error && !points.length"
        empty-text="暂无统计数据"
        empty-desc="作品获得浏览与评论后，这里会显示折线图。"
        :skeleton-count="1"
        @retry="loadStats"
      >
        <template #skeleton>
          <div class="sk" style="height:300px;width:100%" />
        </template>
      </AppState>
      <div v-show="!loading && !error && points.length" ref="chartEl" class="chart" />
    </div>

    <div class="card" style="margin-top:16px">
      <div class="card-hd">数据明细</div>
      <el-table :data="points" size="small" max-height="320">
        <el-table-column prop="date" label="日期 / 小时" min-width="160" />
        <el-table-column prop="views" label="浏览量" width="120" sortable />
        <el-table-column prop="comments" label="评论量" width="120" sortable />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import AppState from '@/components/AppState.vue'
import { projectApi, uploadApi, safe } from '@/api'
import { compactNum, pickList } from '@/utils/format'
import { useThemeStore } from '@/stores/ui'

const route = useRoute()
const theme = useThemeStore()

const projects = ref([])
const projectId = ref(route.query.projectId ? Number(route.query.projectId) : null)
const range = ref(Number(route.query.range) || 7)
const points = ref([])
const loading = ref(false)
const error = ref(null)
const chartEl = ref(null)
let chart = null

const rangeViews = computed(() => points.value.reduce((s, p) => s + Number(p.views || 0), 0))
const rangeComments = computed(() => points.value.reduce((s, p) => s + Number(p.comments || 0), 0))
const sumViews = computed(() => {
  const p = projects.value.find((x) => (x.project_id || x.id) === projectId.value)
  return p?.view_count_total ?? rangeViews.value
})

async function loadProjects() {
  const r = await safe(uploadApi.myProjects({ page: 1, size: 50 }))
  if (r.ok) {
    projects.value = pickList(r.data)
    if (!projectId.value && projects.value.length) {
      projectId.value = projects.value[0].project_id || projects.value[0].id
    }
  }
  await loadStats()
}

async function loadStats() {
  if (!projectId.value) { points.value = []; return }
  loading.value = true
  error.value = null
  const r = await safe(projectApi.stats(projectId.value, range.value))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; points.value = []; return }
  points.value = Array.isArray(r.data) ? r.data : pickList(r.data)
  await nextTick()
  renderChart()
}

function renderChart() {
  if (!chartEl.value) return
  // v-show 隐藏时 DOM 宽度为 0，echarts 会算出空画布 → 无数据时先不初始化
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

onMounted(async () => {
  window.addEventListener('resize', onResize)
  await loadProjects()
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.kpi { padding: 16px; }
.kpi-num { font-size: 26px; font-weight: 700; margin-top: 6px; }
.chart { width: 100%; height: 320px; }
.mb16 { margin-bottom: 16px; }
@media (max-width: 991px) { .kpi-row { grid-template-columns: repeat(2, 1fr); } }
</style>
