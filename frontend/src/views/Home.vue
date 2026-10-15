<template>
  <div class="page">
    <!-- Hero 搜索区 -->
    <section class="hero">
      <div class="container hero-inner">
        <h1 class="hero-title">找到让你眼前一亮的小造物</h1>
        <div class="hero-search">
          <el-icon class="hs-ico"><Search /></el-icon>
          <input
            v-model="keyword"
            placeholder="搜索作品、作者、标签…"
            @keyup.enter="applySearch"
          />
          <el-button type="primary" round @click="applySearch">搜索</el-button>
        </div>
        <div class="quick">
          <span class="xs dim">快捷：</span>
          <span v-for="q in ['贪吃蛇', '像素画', '红石电路', '迷宫']" :key="q" class="quick-item" @click="quickSearch(q)">
            {{ q }}
          </span>
        </div>
      </div>
    </section>

    <div class="container">
      <!-- 类型入口 -->
      <div class="type-entry">
        <div
          v-for="t in typeEntries"
          :key="t.value"
          class="te-card hoverable"
          :class="{ active: filters.project_type === t.value }"
          @click="pickType(t.value)"
        >
          <span class="te-bar" :style="{ background: t.color }" />
          <div class="te-ico" :style="{ background: t.color + '1f', color: t.color }">
            <el-icon :size="20"><component :is="t.icon" /></el-icon>
          </div>
          <div class="te-txt">
            <div class="te-name">{{ t.label }}</div>
            <div class="xs dim">{{ t.hint }}</div>
          </div>
        </div>
      </div>

      <!-- 主区 -->
      <div class="main-grid">
        <div class="main-col">
          <div class="filter-bar">
            <div class="row center gap12">
              <h2 class="section-title" style="margin:0">今日新鲜</h2>
              <span class="dim xs" v-if="total">共 {{ total }} 件</span>
            </div>
            <div class="row center gap8 wrap">
              <el-select v-model="filters.category_id" placeholder="全部分类" clearable size="small" style="width:130px" @change="reload">
                <el-option v-for="c in categories" :key="c.category_id" :label="c.name" :value="c.category_id" />
              </el-select>
              <el-radio-group v-model="filters.sort" size="small" @change="reload">
                <el-radio-button value="latest">最新</el-radio-button>
                <el-radio-button value="hot">最热</el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <el-alert
            v-if="activeKeyword"
            type="info"
            :closable="true"
            class="mb12"
            @close="clearSearch"
          >
            <template #title>搜索「{{ activeKeyword }}」的结果</template>
          </el-alert>

          <AppState
            :loading="loading && !list.length"
            :error="error"
            :empty="!loading && !error && !list.length"
            empty-text="还没有作品上榜"
            empty-desc="新站冷启动中，成为第一个投稿的人吧。"
            @retry="reload"
          >
            <template #action>
              <router-link to="/upload"><el-button type="primary">去投稿</el-button></router-link>
            </template>
          </AppState>

          <div v-if="list.length" class="grid">
            <ProjectCard v-for="p in list" :key="p.project_id || p.id" :p="p" />
          </div>

          <!-- 加载更多 / 没有更多 -->
          <div v-if="list.length" class="load-more">
            <template v-if="loading">
              <el-icon class="spin"><Loading /></el-icon> <span class="dim sm">加载中…</span>
            </template>
            <template v-else-if="list.length >= total">
              <span class="dim sm">没有更多啦 · 共 {{ total }} 件作品</span>
            </template>
            <el-button v-else @click="loadMore">加载更多</el-button>
          </div>
        </div>

        <!-- 右栏 -->
        <aside class="side-col">
          <div class="card">
            <div class="card-hd">🔥 热门标签</div>
            <div v-if="tags.length" class="tags">
              <el-tag
                v-for="t in tags"
                :key="t.tag_id || t.name"
                class="tag-item"
                effect="plain"
                @click="quickSearch(t.name)"
              >{{ t.name }}</el-tag>
            </div>
            <div v-else class="xs dim">暂无标签（后端未启动或数据为空）</div>
          </div>

          <div class="card">
            <div class="card-hd">▶ 本周热门 Top 10</div>
            <div v-if="hotList.length" class="rank">
              <div v-for="(h, i) in hotList" :key="h.project_id || h.id" class="rank-item" @click="goProject(h)">
                <span class="rank-no" :class="{ top3: i < 3 }">{{ i + 1 }}</span>
                <span class="ellipsis grow">{{ h.title }}</span>
                <span class="xs dim num">{{ compactNum(h.view_count_total ?? h.views) }}</span>
              </div>
            </div>
            <div v-else class="xs dim">暂无数据</div>
          </div>

          <div class="card">
            <div class="card-hd">👥 活跃作者</div>
            <div v-if="authors.length" class="authors">
              <router-link
                v-for="a in authors"
                :key="a.user_id"
                :to="`/users/${a.user_id}`"
                class="author-item"
              >
                <el-avatar :size="30" :src="a.avatar_path">{{ (a.nickname || 'U').slice(0, 1) }}</el-avatar>
                <span class="ellipsis grow sm">{{ a.nickname }}</span>
              </router-link>
            </div>
            <div v-else class="xs dim">暂无数据</div>
          </div>
        </aside>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProjectCard from '@/components/ProjectCard.vue'
import AppState from '@/components/AppState.vue'
import { categoryApi, projectApi, safe } from '@/api'
import { compactNum, pickList, pickTotal, TYPE_COLOR } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 20
const list = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref(null)
const categories = ref([])
const tags = ref([])
const hotList = ref([])
const authors = ref([])
const keyword = ref(route.query.keyword || '')
const activeKeyword = ref(route.query.keyword || '')

const filters = reactive({
  category_id: route.query.category_id || '',
  project_type: route.query.project_type || '',
  sort: route.query.sort || 'latest'
})

const typeEntries = [
  { value: 'scratch', label: 'Scratch', hint: '积木编程', color: TYPE_COLOR.scratch, icon: 'Cpu' },
  { value: 'html', label: 'HTML', hint: '网页小游戏', color: TYPE_COLOR.html, icon: 'Monitor' },
  { value: 'structure', label: '结构', hint: 'MC 结构文件', color: TYPE_COLOR.structure, icon: 'Box' },
  { value: '', label: '全部作品', hint: '所有已发布', color: 'var(--c-accent)', icon: 'Grid' }
]

async function fetchPage(reset = false) {
  if (reset) { page.value = 1; list.value = [] }
  loading.value = true
  error.value = null
  const params = { page: page.value, size: PAGE_SIZE, sort: filters.sort }
  if (filters.category_id) params.category_id = filters.category_id
  if (filters.project_type) params.project_type = filters.project_type
  if (activeKeyword.value) params.keyword = activeKeyword.value

  const r = await safe(projectApi.list(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  const items = pickList(r.data)
  list.value = reset ? items : list.value.concat(items)
  total.value = pickTotal(r.data)
}

function loadMore() { page.value += 1; fetchPage(false) }
function reload() {
  syncQuery()
  fetchPage(true)
}

function syncQuery() {
  const q = {}
  if (filters.sort !== 'latest') q.sort = filters.sort
  if (filters.category_id) q.category_id = filters.category_id
  if (filters.project_type) q.project_type = filters.project_type
  if (activeKeyword.value) q.keyword = activeKeyword.value
  router.replace({ path: '/', query: q })
}

function pickType(v) {
  filters.project_type = filters.project_type === v ? '' : v
  reload()
}
function applySearch() {
  activeKeyword.value = keyword.value.trim()
  reload()
}
function quickSearch(q) {
  keyword.value = q
  activeKeyword.value = q
  reload()
}
function clearSearch() {
  keyword.value = ''
  activeKeyword.value = ''
  reload()
}
function goProject(p) { router.push(`/projects/${p.project_id || p.id}`) }

/** 右栏是低优先级区块：失败就静默隐藏，不打扰阅读 */
async function fetchSide() {
  const [cat, hot] = await Promise.all([
    safe(categoryApi.list()),
    safe(projectApi.list({ page: 1, size: 10, sort: 'hot' }))
  ])
  if (cat.ok && Array.isArray(cat.data)) categories.value = cat.data
  if (hot.ok) {
    hotList.value = pickList(hot.data).slice(0, 10)
    const map = new Map()
    for (const p of pickList(hot.data)) {
      const a = p.author
      if (a?.user_id && !map.has(a.user_id)) map.set(a.user_id, a)
    }
    authors.value = [...map.values()].slice(0, 6)
  }
}

onMounted(() => {
  fetchPage(true)
  fetchSide()
})
</script>

<style scoped>
.hero {
  padding: 40px 0 32px; margin-bottom: 8px;
  background:
    radial-gradient(900px 220px at 50% -40px, rgba(79, 110, 247, .18), transparent 70%),
    var(--c-bg-page);
}
.hero-inner { max-width: 720px; text-align: center; }
.hero-title { font-size: 30px; font-weight: 700; margin: 0 0 18px; letter-spacing: .01em; }
.hero-search {
  display: flex; align-items: center; gap: 8px; height: 52px; padding: 0 8px 0 16px;
  background: var(--c-bg-card); border: 1px solid var(--c-border-strong);
  border-radius: var(--r-pill); box-shadow: var(--sh-3);
}
.hs-ico { color: var(--c-text-3); }
.hero-search input {
  flex: 1; border: none; outline: none; background: transparent;
  color: var(--c-text-1); font-size: var(--fs-body-l);
}
.quick { margin-top: 14px; display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }
.quick-item { font-size: var(--fs-sm); color: var(--c-text-2); cursor: pointer; }
.quick-item:hover { color: var(--c-primary); }

.type-entry { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 28px; }
.te-card {
  position: relative; display: flex; align-items: center; gap: 12px; padding: 16px;
  background: var(--c-bg-card); border: 1px solid var(--c-border); border-radius: var(--r-lg); cursor: pointer;
}
.te-card.active { border-color: var(--c-primary); }
.te-bar { position: absolute; left: 0; top: 14px; bottom: 14px; width: 4px; border-radius: 2px; }
.te-ico { width: 40px; height: 40px; border-radius: var(--r-md); display: flex; align-items: center; justify-content: center; }
.te-name { font-weight: 600; }

.main-grid { display: grid; grid-template-columns: 1fr 320px; gap: 24px; }
.filter-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.load-more { text-align: center; padding: 24px 0; display: flex; align-items: center; justify-content: center; gap: 8px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.mb12 { margin-bottom: 12px; }

.side-col { display: flex; flex-direction: column; gap: 16px; }
.tags { display: flex; flex-wrap: wrap; gap: 8px; }
.tag-item { cursor: pointer; }
.rank { display: flex; flex-direction: column; gap: 10px; }
.rank-item { display: flex; align-items: center; gap: 10px; font-size: var(--fs-sm); cursor: pointer; }
.rank-item:hover { color: var(--c-primary); }
.rank-no {
  width: 18px; text-align: center; font-weight: 700; color: var(--c-text-3);
  font-variant-numeric: tabular-nums;
}
.rank-no.top3 { color: var(--c-danger); }
.authors { display: flex; flex-direction: column; gap: 10px; }
.author-item { display: flex; align-items: center; gap: 10px; color: var(--c-text-1); }

@media (max-width: 1199px) {
  .main-grid { grid-template-columns: 1fr; }
  .side-col { flex-direction: row; overflow-x: auto; }
  .side-col .card { min-width: 260px; }
}
@media (max-width: 767px) {
  .type-entry { grid-template-columns: repeat(2, 1fr); }
  .hero { padding: 24px 0 20px; }
  .hero-title { font-size: 22px; }
}
</style>
