<template>
  <div class="auth-page">
    <div class="auth-card">
      <router-link to="/" class="back">← 返回首页</router-link>

      <div class="brand-row">
        <span class="logo-tile">造</span>
        <div>
          <div class="brand-title">登录造物集</div>
          <div class="xs dim">开发者 / 管理员统一入口</div>
        </div>
      </div>

      <el-alert v-if="reason" :title="reason" type="warning" :closable="false" class="mb16" show-icon />

      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="QQ 号（账号）">
          <el-input v-model="form.qq_account" placeholder="请输入注册时使用的 QQ 号" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>

      <div class="links">
        <router-link to="/dev-apply">申请成为开发者</router-link>
        <span class="dim">·</span>
        <router-link to="/forgot">忘记密码？联系管理员</router-link>
      </div>

      <p class="note xs dim">
        访客无需登录即可浏览与试玩作品；评论、点赞、收藏、举报、投稿等互动需要登录。
      </p>
    </div>

    <!-- 临时密码首登强制改密 -->
    <el-dialog v-model="mustChange" title="首次登录：请修改密码" width="420px" :close-on-click-modal="false" :show-close="false">
      <p class="sm muted">你正在使用管理员生成的临时密码（有效期 24 小时）。为账号安全，请立即设置新密码。</p>
      <el-form label-position="top" style="margin-top:12px">
        <el-form-item label="新密码">
          <el-input v-model="pw.new_password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="pwLoading" @click="doChangePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { authApi, safe } from '@/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = reactive({ qq_account: '', password: '' })
const loading = ref(false)
const mustChange = ref(false)
const pwLoading = ref(false)
const pw = reactive({ new_password: '' })
const reason = computed(() => route.query.reason || '')

async function submit() {
  if (!form.qq_account || !form.password) { ElMessage.warning('请填写 QQ 号与密码'); return }
  loading.value = true
  try {
    const data = await auth.login(form.qq_account.trim(), form.password)
    ElMessage.success(`欢迎回来，${auth.displayName}`)
    if (data?.must_change_password || auth.mustChangePassword) {
      mustChange.value = true
      return
    }
    goLanding()
  } catch (e) {
    // axios 封装已统一提示（审核中 / 已注销 / 密码错误）
  } finally {
    loading.value = false
  }
}

/** 按 user.type 决定落地页：2 → 我的作品/回跳；3 → 管理后台 */
function goLanding() {
  const redirect = route.query.redirect
  if (redirect) { router.replace(String(redirect)); return }
  router.replace(auth.landingPath)
}

async function doChangePassword() {
  if (!pw.new_password || pw.new_password.length < 6) { ElMessage.warning('新密码至少 6 位'); return }
  pwLoading.value = true
  const r = await safe(authApi.changePassword({ old_password: form.password, new_password: pw.new_password }))
  pwLoading.value = false
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '修改失败，请重试'); return }
  ElMessage.success('密码已修改')
  mustChange.value = false
  goLanding()
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  padding: 40px 20px;
  background:
    radial-gradient(700px 300px at 50% 0%, rgba(79, 110, 247, .18), transparent 70%),
    var(--c-bg-page);
}
.auth-card {
  width: 100%; max-width: 400px; background: var(--c-bg-card);
  border: 1px solid var(--c-border); border-radius: var(--r-lg);
  padding: 28px; box-shadow: var(--sh-3);
}
.back { font-size: var(--fs-sm); color: var(--c-text-3); }
.back:hover { color: var(--c-primary); }
.brand-row { display: flex; align-items: center; gap: 12px; margin: 18px 0 22px; }
.brand-title { font-size: 19px; font-weight: 700; }
.links { margin-top: 16px; font-size: var(--fs-sm); display: flex; gap: 8px; justify-content: center; }
.note { margin: 18px 0 0; line-height: 18px; }
.mb16 { margin-bottom: 16px; }
</style>
