<template>
  <!-- 统一状态组件：加载骨架 / 空状态 / 错误态（带重试） -->
  <div v-if="loading" class="state-wrap">
    <slot name="skeleton">
      <div class="sk-grid">
        <div v-for="i in skeletonCount" :key="i" class="sk-card">
          <div class="sk sk-thumb" />
          <div class="sk sk-line" style="width: 70%" />
          <div class="sk sk-line" style="width: 40%" />
        </div>
      </div>
    </slot>
  </div>

  <div v-else-if="error" class="state-wrap">
    <div class="state-box">
      <div class="state-ico error"><el-icon :size="30"><WarningFilled /></el-icon></div>
      <div class="state-title">哎呀，卡住了</div>
      <div class="state-desc">{{ errorText }}</div>
      <div class="row gap12 center" style="margin-top:16px">
        <el-button type="primary" @click="$emit('retry')">
          <el-icon><Refresh /></el-icon>&nbsp;重试
        </el-button>
        <router-link to="/"><el-button>返回首页</el-button></router-link>
      </div>
    </div>
  </div>

  <div v-else-if="empty" class="state-wrap">
    <div class="state-box">
      <div class="state-ico"><el-icon :size="30"><Box /></el-icon></div>
      <div class="state-title">{{ emptyText }}</div>
      <div class="state-desc">{{ emptyDesc }}</div>
      <div v-if="$slots.action" style="margin-top:16px"><slot name="action" /></div>
    </div>
  </div>

  <slot v-else />
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  loading: Boolean,
  error: [Object, String, Boolean],
  empty: Boolean,
  emptyText: { type: String, default: '这里还空着' },
  emptyDesc: { type: String, default: '' },
  skeletonCount: { type: Number, default: 8 }
})
defineEmits(['retry'])

const errorText = computed(() => {
  if (typeof props.error === 'string') return props.error
  const e = props.error
  if (!e) return ''
  if (e.http401 || e.code === 401) return '登录过期啦，重新登录一下再回来吧～'
  if (!e.response) return '啊哦，网络离家出走啦～ 检查一下信号，我们再试一次？'
  const status = e.response?.status
  if (status >= 500) return '服务器打了个盹儿，稍等一会儿再试试～'
  return e.response?.data?.message || e.message || '这条内容暂时没加载出来，再试一次吧～'
})
</script>

<style scoped>
.state-wrap { width: 100%; }
.sk-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.sk-card { background: var(--c-bg-card); border: 1px solid var(--c-border); border-radius: var(--r-lg); padding: 12px; }
.sk-thumb { width: 100%; padding-top: 66%; margin-bottom: 12px; border-radius: var(--r-md); }
.sk-line { height: 12px; margin-bottom: 8px; }
.state-box {
  text-align: center; padding: 48px 20px; border: 1px dashed var(--c-border);
  border-radius: var(--r-lg); background: var(--c-bg-subtle);
}
.state-ico {
  width: 56px; height: 56px; margin: 0 auto 12px; border-radius: var(--r-pill);
  display: flex; align-items: center; justify-content: center;
  background: var(--c-bg-sunken); color: var(--c-text-3);
}
.state-ico.error { background: rgba(239, 68, 68, .12); color: var(--c-danger); }
.state-title { font-size: var(--fs-h3); font-weight: 600; margin-bottom: 6px; }
.state-desc { color: var(--c-text-2); font-size: var(--fs-sm); max-width: 520px; margin: 0 auto; }
</style>
