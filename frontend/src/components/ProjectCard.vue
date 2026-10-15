<template>
  <router-link :to="`/projects/${p.project_id ?? p.id}`" class="pcard hoverable">
    <div class="thumb" :style="{ borderTopColor: typeColor }">
      <img v-if="cover" :src="cover" :alt="p.title" loading="lazy" @error="imgErr = true" />
      <div v-else class="thumb-ph" :style="{ background: phBg }">
        <span class="ph-txt">{{ (p.title || '?').slice(0, 1) }}</span>
      </div>
      <span class="type-bar" :style="{ background: typeColor }" />
      <div class="overlay">
        <div class="play"><el-icon :size="18"><VideoPlay /></el-icon></div>
        <span class="xs">试玩</span>
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
import { computed, ref } from 'vue'
import { compactNum, fmtTime, statusOf, TYPE_COLOR, TYPE_LABEL, fileUrl } from '@/utils/format'

const props = defineProps({
  p: { type: Object, required: true },
  showStatus: { type: Boolean, default: false },
  showReason: { type: Boolean, default: false }
})

const imgErr = ref(false)
const type = computed(() => props.p.project_type || props.p.type || 'html')
const typeColor = computed(() => TYPE_COLOR[type.value] || 'var(--c-primary)')
const cover = computed(() => (imgErr.value ? '' : fileUrl(props.p.cover_path || props.p.cover || props.p.thumb)))
const phBg = computed(() => `linear-gradient(135deg, ${typeColor.value}22, ${typeColor.value}08)`)
const st = computed(() => statusOf(props.p.status))
const reason = computed(() => props.p.down_reason || props.p.reject_reason || props.p.delete_reason)
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
.overlay {
  position: absolute; inset: 0; display: flex; flex-direction: column; gap: 6px;
  align-items: center; justify-content: center; color: #fff;
  background: rgba(0, 0, 0, .45); opacity: 0; transition: opacity var(--dur-fast) var(--ease-out);
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
