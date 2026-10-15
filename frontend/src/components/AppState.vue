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
      <div class="state-title">加载失败</div>
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
  if (e.http401 || e.code === 401) return '登录状态已失效，请重新登录后重试。'
  if (!e.response) return '无法连接后端服务（127.0.0.1:8090 可能尚未启动）。请稍后重试。'
  return e.response?.data?.message || e.message || `请求失败（HTTP ${e.response?.status || '未知'}）`
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
