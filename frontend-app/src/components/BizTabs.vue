<template>
  <section class="biz">
    <div class="biz-hd">
      <div class="row center gap8">
        <h2 class="biz-title">造物集的业务</h2>
        <span class="biz-pill">多站一体</span>
      </div>
      <span class="dim xs">除创作社区外，同一套服务器上还在跑这些站点</span>
    </div>

    <div class="biz-tabs" role="tablist">
      <button
        v-for="b in businesses"
        :key="b.key"
        class="biz-tab"
        :class="{ active: active === b.key }"
        role="tab"
        :aria-selected="active === b.key"
        @click="active = b.key"
      >
        <el-icon :size="16"><component :is="b.icon" /></el-icon>
        <span>{{ b.name }}</span>
        <span v-if="b.tag" class="biz-tag" :class="'t-' + b.key">{{ b.tag }}</span>
      </button>
    </div>

    <div class="biz-panel">
      <div class="biz-ico" :style="{ background: cur.color + '1f', color: cur.color }">
        <el-icon :size="24"><component :is="cur.icon" /></el-icon>
      </div>
      <div class="biz-body">
        <div class="biz-name">{{ cur.name }}</div>
        <p class="biz-desc">{{ cur.desc }}</p>
        <div class="biz-host xs dim">{{ cur.host }}</div>
      </div>
      <div class="biz-action">
        <el-button v-if="cur.external" type="primary" round @click="open(cur.url)">前往访问 ↗</el-button>
        <router-link v-else to="/"><el-button round>就在当前页</el-button></router-link>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'

/**
 * 造物集的“其他业务”栏目：
 * 这里只放「同域名的兄弟站点」链接，纯前端静态数据（不动后端）。
 * 新增业务只要往 businesses 里加一条即可。
 */
const businesses = [
  {
    key: 'coc',
    name: '造物集创作社区',
    tag: '主营',
    icon: 'Grid',
    color: '#4f6ef7',
    desc: '本站。Scratch / HTML / 结构文件的创作分享社区：作品展示、开发者认证、投稿审核、数据看板。',
    host: 'collectionofcreations.uk',
    url: '/',
    external: false
  },
  {
    key: 'tutor',
    name: '造物集家教平台',
    tag: '在线',
    icon: 'Reading',
    color: '#10b981',
    desc: '大连私人家教信息平台：教师入驻与认证、家长需求发布、订单撮合、管理员审核与数据统计。',
    host: 'tutor.collectionofcreations.uk',
    url: 'https://tutor.collectionofcreations.uk',
    external: true
  },
  {
    key: 'ws',
    name: 'WebSocket 跨端同步',
    tag: '实验',
    icon: 'Connection',
    color: '#a855f7',
    desc: '实时协作学习项目：手机 / 电脑同时拖动同一个圆，演示 WebSocket 广播、断线重连与状态同步。',
    host: 'websocket.collectionofcreations.uk',
    url: 'https://websocket.collectionofcreations.uk',
    external: true
  }
]

const active = ref('coc')
const cur = computed(() => businesses.find((b) => b.key === active.value) || businesses[0])

function open(url) {
  window.open(url, '_blank', 'noopener')
}
</script>

<style scoped>
.biz { margin: 0 0 28px; }
.biz-hd { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.biz-title { font-size: var(--fs-h3, 18px); font-weight: 700; margin: 0; }
.biz-pill {
  font-size: 11px; padding: 2px 8px; border-radius: var(--r-pill);
  background: var(--c-primary-weak); color: var(--c-primary);
  border: 1px solid var(--c-primary-border);
}
.biz-tabs {
  display: flex; gap: 8px; flex-wrap: wrap;
  background: var(--c-bg-sunken); border: 1px solid var(--c-border);
  border-radius: var(--r-pill); padding: 6px; margin-bottom: 12px;
}
.biz-tab {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 16px; border: none; border-radius: var(--r-pill);
  background: transparent; color: var(--c-text-2); font-size: var(--fs-sm);
  cursor: pointer; transition: background var(--dur-fast, .15s), color var(--dur-fast, .15s);
  font-family: inherit;
}
.biz-tab:hover { color: var(--c-primary); }
.biz-tab.active { background: var(--c-bg-card); color: var(--c-primary); box-shadow: var(--sh-2, 0 1px 3px rgba(0, 0, 0, .12)); }
.biz-tag {
  font-size: 10px; padding: 1px 6px; border-radius: var(--r-pill);
  background: var(--c-bg-sunken); color: var(--c-text-3); border: 1px solid var(--c-border);
}
.biz-tab.active .biz-tag { color: var(--c-primary); border-color: var(--c-primary-border); }

.biz-panel {
  display: flex; align-items: center; gap: 16px; padding: 20px;
  background: var(--c-bg-card); border: 1px solid var(--c-border);
  border-radius: var(--r-lg); flex-wrap: wrap;
}
.biz-ico {
  width: 48px; height: 48px; border-radius: var(--r-md);
  display: flex; align-items: center; justify-content: center; flex: 0 0 auto;
}
.biz-body { flex: 1 1 320px; min-width: 220px; }
.biz-name { font-weight: 600; margin-bottom: 4px; }
.biz-desc { margin: 0 0 6px; color: var(--c-text-2); font-size: var(--fs-sm); line-height: 1.65; }
.biz-host { font-family: var(--font-mono, monospace); }
.biz-action { flex: 0 0 auto; }

@media (max-width: 767px) {
  .biz-tabs { border-radius: var(--r-lg); }
  .biz-tab { padding: 8px 12px; font-size: var(--fs-xs); }
  .biz-panel { padding: 16px; }
  .biz-action { width: 100%; }
  .biz-action :deep(.el-button) { width: 100%; }
}
</style>
