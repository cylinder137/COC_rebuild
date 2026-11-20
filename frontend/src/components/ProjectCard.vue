<template>
  <router-link :to="`/projects/${p.project_id ?? p.id}`" class="pcard hoverable">
    <div class="thumb" :style="{ borderTopColor: typeColor }">
      <!-- 代码预览模式：只渲染入口源码片段，不运行项目（省用户端算力） -->
      <template v-if="noRun">
        <pre v-if="codePreview" class="code-peek"><code>{{ codePreview }}</code></pre>
        <img v-else-if="cover" :src="cover" :alt="p.title" loading="lazy" @error="imgErr = true" />
        <div v-else class="thumb-ph" :style="{ background: phBg }">
          <span class="ph-txt">{{ (p.title || '?').slice(0, 1) }}</span>
        </div>
        <span v-if="codePreview" class="live-badge code-badge"><i />代码预览</span>
      </template>
      <!-- 实时运行模式（详情页等需要真跑的地方）：进入视野后加载沙箱 iframe -->
      <template v-else>
        <div v-if="live" class="live-frame">
          <iframe
            :src="liveUrl"
            sandbox="allow-scripts"
            referrerpolicy="no-referrer"
            tabindex="-1"
            aria-hidden="true"
          />
        </div>
        <img v-else-if="cover" :src="cover" :alt="p.title" loading="lazy" @error="imgErr = true" />
        <div v-else class="thumb-ph" :style="{ background: phBg }">
          <span class="ph-txt">{{ (p.title || '?').slice(0, 1) }}</span>
        </div>
        <span v-if="live" class="live-badge"><i />实时预览</span>
      </template>
      <span class="type-bar" :style="{ background: typeColor }" />
      <div class="overlay">
        <div class="play"><el-icon :size="18"><VideoPlay /></el-icon></div>
        <span class="xs">{{ noRun ? '查看项目' : '试玩' }}</span>
      </div>
      <el-tag v-if="showStatus" :type="st.type" size="small" class="status-tag" effect="dark">
        {{ st.label }}
      </el-tag>
    </div>

    <div class="body">
      <div class="title ellipsis" :title="p.title">{{ p.title || '未命名作品' }}</div>
      <div class="meta xs dim ellipsis">
        @{{ p.author?.nickname || p.author_nickname || '匿名' }}
        <template v-if="p.published_at || p.created_at"> · {{ fmtTime(p.published_at || p.created_at) }}</template>
      </div>
      <div class="stats xs">
        <span class="stat" title="浏览量"><el-icon><View /></el-icon>{{ compactNum(p.view_count_total ?? p.views) }}</span>
        <span class="stat" title="点赞数"><el-icon><Star /></el-icon>{{ compactNum(p.like_count ?? p.likes) }}</span>
        <span class="stat hide-sm" title="收藏数"><el-icon><Collection /></el-icon>{{ compactNum(p.favorite_count ?? p.favorites) }}</span>
      </div>
      <div v-if="showReason && reason" class="reason xs">
        {{ st.label === '已拒绝' ? '拒绝原因' : '下架原因' }}：{{ reason }}
      </div>
    </div>
  </router-link>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { compactNum, fmtTime, statusOf, TYPE_COLOR, fileUrl, runUrlOf } from '@/utils/format'
import { acquire, release } from '@/utils/liveSlots'
import { useUiStore } from '@/stores/ui'

const props = defineProps({
  p: { type: Object, required: true },
  showStatus: { type: Boolean, default: false },
  showReason: { type: Boolean, default: false },
  /** 代码预览片段（静态文本，不执行）：有值则展示代码缩略图 */
  codePreview: { type: String, default: '' },
  /** true=只做代码预览、不运行项目（列表页省算力） */
  noRun: { type: Boolean, default: false }
})

const ui = useUiStore()
const imgErr = ref(false)
const live = ref(false)
const visible = ref(false)
let io = null
let cardEl = null

/** 名额队列句柄（队列在模块级，全站共享 4 个并发 iframe 名额） */
const handle = {
  live: false,
  queued: false,
  onGrant: () => { live.value = true }
}

function wantsPreview() {
  return acquire(handle)
}
function dropPreview() {
  release(handle)
  live.value = false
}

const type = computed(() => props.p.project_type || props.p.type || 'html')
const typeColor = computed(() => TYPE_COLOR[type.value] || 'var(--c-primary)')
const cover = computed(() => (imgErr.value ? '' : fileUrl(props.p.cover_path || props.p.cover || props.p.thumb)))
const phBg = computed(() => `linear-gradient(135deg, ${typeColor.value}22, ${typeColor.value}08)`)
const st = computed(() => statusOf(props.p.status))
const reason = computed(() => props.p.down_reason || props.p.reject_reason || props.p.delete_reason)
const pid = computed(() => props.p.project_id ?? props.p.id)
const liveUrl = computed(() => runUrlOf(pid.value))

/** 只有已发布 + 可运行类型才尝试实时预览；设置页关掉「自动加载」时一律不加载 */
const canLive = computed(() => {
  if (props.noRun) return false
  if (!ui.autoLoad) return false
  if (!pid.value) return false
  if (props.p.status !== undefined && props.p.status !== 2) return false
  return ['html', 'scratch'].includes(type.value)
})

onMounted(() => {
  cardEl = getCurrentInstance()?.proxy?.$el || null
  if (!cardEl) return
  io = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        visible.value = e.isIntersecting
        if (e.isIntersecting && canLive.value) wantsPreview()
        else dropPreview()
      }
    },
    { threshold: 0.5, rootMargin: '80px' }
  )
  io.observe(cardEl)
})

// 设置页开关实时生效：关掉立刻卸载 iframe，打开且卡片在视野内则重新加载
watch(canLive, (v) => {
  if (!v) dropPreview()
  else if (visible.value) wantsPreview()
})

onBeforeUnmount(() => {
  io?.disconnect()
  io = null
  dropPreview()
})
</script>

<style scoped>
.pcard {
  display: block; background: var(--c-bg-card); border: 1px solid var(--c-border);
  border-radius: var(--r-lg); overflow: hidden; color: var(--c-text-1);
}
.pcard:hover { color: var(--c-text-1); }
.thumb {
  position: relative; padding-top: 66%; background: var(--c-bg-sunken);
  border-top: 3px solid transparent; overflow: hidden;
}
.thumb img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.thumb-ph {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
}
.ph-txt { font-size: 34px; font-weight: 700; color: var(--c-text-3); opacity: .5; }

/* 代码预览层：静态文本，pointer-events:none 保证点击卡片仍跳转项目主页 */
.code-peek {
  position: absolute; inset: 0; margin: 0; padding: 10px 12px;
  overflow: hidden; white-space: pre; word-break: normal;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px; line-height: 1.5; tab-size: 2;
  color: #cdd7e6; background: #0e1420;
  -webkit-mask-image: linear-gradient(180deg, #000 0%, #000 68%, transparent 100%);
  mask-image: linear-gradient(180deg, #000 0%, #000 68%, transparent 100%);
  pointer-events: none;
}
.code-badge { background: rgba(30, 41, 59, .82); }
.code-badge i { background: #60a5fa; }
/* 实时预览层：pointer-events:none 保证点击卡片仍跳转项目主页 */
.live-frame { position: absolute; inset: 0; }
.live-frame iframe {
  width: 100%; height: 100%; border: 0; display: block;
  pointer-events: none; background: #fff;
}
.live-badge {
  position: absolute; left: 8px; top: 8px; z-index: 2;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 7px; border-radius: var(--r-pill);
  background: rgba(0, 0, 0, .55); color: #fff; font-size: 11px; line-height: 1.6;
  pointer-events: none;
}
.live-badge i { width: 6px; height: 6px; border-radius: 50%; background: #34d399; }
.overlay {
  position: absolute; inset: 0; display: flex; flex-direction: column; gap: 6px;
  align-items: center; justify-content: center; color: #fff;
  background: rgba(0, 0, 0, .45); opacity: 0; transition: opacity var(--dur-fast) var(--ease-out);
  /* 纯装饰层：不能吞掉点击（P1「组件不可选中」防御修复） */
  pointer-events: none;
}
.pcard:hover .overlay { opacity: 1; }
.play {
  width: 40px; height: 40px; border-radius: var(--r-pill); background: rgba(255, 255, 255, .9);
  color: #1a1d24; display: flex; align-items: center; justify-content: center;
}
.status-tag { position: absolute; top: 8px; right: 8px; }
.body { padding: 12px; }
.title { font-size: var(--fs-body); font-weight: 600; margin-bottom: 4px; }
.meta { margin-bottom: 8px; }
.stats { display: flex; gap: 12px; color: var(--c-text-2); }
.stat { display: inline-flex; align-items: center; gap: 3px; font-variant-numeric: tabular-nums; }
.reason {
  margin-top: 8px; padding: 6px 8px; border-radius: var(--r-sm);
  background: rgba(239, 68, 68, .1); color: var(--c-danger);
}
</style>
