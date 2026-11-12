<template>
  <div class="c-item">
    <el-avatar :size="36" :src="c.user?.avatar_path">
      {{ (c.user?.nickname || 'U').slice(0, 1) }}
    </el-avatar>
    <div class="c-body">
      <div class="c-head">
        <router-link v-if="c.user?.user_id" :to="`/users/${c.user.user_id}`" class="c-name">
          {{ c.user?.nickname || '匿名用户' }}
        </router-link>
        <span v-else class="c-name">{{ c.user?.nickname || '匿名用户' }}</span>
        <el-tag v-if="c.is_author" size="small" effect="plain" type="primary">作者</el-tag>
        <span class="xs dim">{{ fmtTime(c.created_at) }}</span>
        <span class="grow" />
        <span class="c-act" @click="$emit('like', c)">
          <el-icon><component :is="c.liked ? 'StarFilled' : 'Star'" /></el-icon>
          <span class="num">{{ c.like_count || 0 }}</span>
        </span>
        <span class="c-act" @click="$emit('reply', c)">回复</span>
        <span class="c-act" @click="$emit('report', c)">举报</span>
        <el-dropdown v-if="isAdmin" trigger="click">
          <span class="c-act"><el-icon><MoreFilled /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="$emit('remove', c)">删除评论（管理员）</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div class="c-content">{{ c.content }}</div>

      <!-- 楼中楼（一层） -->
      <div v-if="replies.length" class="c-replies">
        <div v-for="r in replies" :key="r.comment_id" class="c-reply">
          <el-avatar :size="24" :src="r.user?.avatar_path">
            {{ (r.user?.nickname || 'U').slice(0, 1) }}
          </el-avatar>
          <div class="grow">
            <div class="c-head">
              <span class="c-name sm">{{ r.user?.nickname || '匿名用户' }}</span>
              <el-tag v-if="r.is_author" size="small" effect="plain" type="primary">作者</el-tag>
              <span class="xs dim">{{ fmtTime(r.created_at) }}</span>
            </div>
            <div class="sm">{{ r.content }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { fmtTime } from '@/utils/format'

const props = defineProps({
  c: { type: Object, required: true },
  isAdmin: { type: Boolean, default: false }
})
defineEmits(['reply', 'like', 'report', 'remove'])

const replies = computed(() => props.c.replies || props.c.children || [])
</script>

<style scoped>
.c-item { display: flex; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--c-border); }
.c-item:last-child { border-bottom: none; }
.c-body { flex: 1; min-width: 0; }
.c-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.c-name { font-size: var(--fs-sm); font-weight: 600; color: var(--c-text-1); }
.c-act {
  font-size: var(--fs-xs); color: var(--c-text-3); cursor: pointer;
  display: inline-flex; align-items: center; gap: 3px;
}
.c-act:hover { color: var(--c-primary); }
.c-content { margin: 6px 0; line-height: 24px; word-break: break-word; }
.c-replies {
  margin-top: 8px; padding-left: 12px; border-left: 2px solid var(--c-border);
  display: flex; flex-direction: column; gap: 10px;
}
.c-reply { display: flex; gap: 8px; }
</style>
