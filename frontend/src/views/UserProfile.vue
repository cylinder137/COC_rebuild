<template>
  <div class="page">
    <div class="container">
      <AppState :loading="loading && !user" :error="error" :empty="!loading && !error && !user" empty-text="用户不存在" @retry="load">
        <template v-if="user">
          <!-- 头部 -->
          <div class="card head">
            <el-avatar :size="72" :src="user.avatar_path">{{ (user.nickname || 'U').slice(0, 1) }}</el-avatar>
            <div class="grow">
              <div class="row center gap8 wrap">
                <h1 class="u-name">{{ user.nickname || '匿名用户' }}</h1>
                <el-tag v-if="user.type === 2 || user.type === 3" size="small" effect="plain" type="primary">开发者</el-tag>
                <el-tag v-if="user.status === 3" size="small" effect="plain" type="info">用户已注销</el-tag>
              </div>
              <div class="sm dim">
                <span class="num">{{ followers }}</span> 关注者 ·
                <span class="num">{{ user.project_count ?? published.length }}</span> 件作品
              </div>
            </div>
            <div class="acts">
              <el-button v-if="!isSelf" :loading="chatting" @click="openChat">
                <el-icon><ChatDotRound /></el-icon>&nbsp;私聊
              </el-button>
              <el-button
                v-if="!isSelf"
                :type="following ? 'default' : 'primary'"
                @click="toggleFollow"
              >{{ following ? '已关注' : '+ 关注' }}</el-button>
              <template v-else>
                <el-button @click="openPwd">
                  <el-icon><Lock /></el-icon>&nbsp;修改密码
                </el-button>
                <router-link to="/me/projects"><el-button>我的作品</el-button></router-link>
              </template>
            </div>
          </div>

          <!-- 修改密码（只有看自己的主页才有入口） -->
          <el-dialog v-model="pwdVisible" title="修改密码" width="420px" :close-on-click-modal="false">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="改完会自动换上新的登录凭证，其它设备上的登录态将失效，需要重新登录。"
              style="margin-bottom:12px"
            />
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="原密码">
                <el-input v-model="pwd.old_password" type="password" show-password placeholder="当前使用的密码" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="pwd.new_password" type="password" show-password placeholder="6-64 位" />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input v-model="pwd.confirm" type="password" show-password placeholder="再输一遍新密码" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="pwdVisible = false">取消</el-button>
              <el-button type="primary" :loading="pwdLoading" @click="submitPwd">确认修改</el-button>
            </template>
          </el-dialog>

          <!-- 作品列表 -->
          <h2 class="section-title" style="margin:28px 0 16px">作品</h2>
          <AppState
            :loading="loading"
            :error="error"
            :empty="!loading && !error && !published.length"
            empty-text="还没有公开作品"
            empty-desc="作者还没有发布任何作品。"
            :skeleton-count="6"
            @retry="load"
          >
            <div class="grid">
              <ProjectCard v-for="p in published" :key="p.project_id || p.id" :p="p" />
            </div>
          </AppState>
        </template>
      </AppState>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppState from '@/components/AppState.vue'
import ProjectCard from '@/components/ProjectCard.vue'
import { userApi, interactApi, imApi, authApi, safe, setToken } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { pickList } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const user = ref(null)
const published = ref([])
const followers = ref(0)
const following = ref(false)
const chatting = ref(false)
const loading = ref(false)
const error = ref(null)

/* ---------- 修改密码（仅本人主页可见） ---------- */
const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwd = reactive({ old_password: '', new_password: '', confirm: '' })

function openPwd() {
  pwd.old_password = ''
  pwd.new_password = ''
  pwd.confirm = ''
  pwdVisible.value = true
}

async function submitPwd() {
  const { old_password: oldPwd, new_password: newPwd, confirm } = pwd
  if (!oldPwd) { ElMessage.warning('请填写原密码'); return }
  if (!newPwd || newPwd.length < 6 || newPwd.length > 64) { ElMessage.warning('新密码长度需为 6-64 位'); return }
  if (newPwd === oldPwd) { ElMessage.warning('新密码不能与原密码相同'); return }
  if (newPwd !== confirm) { ElMessage.warning('两次输入的新密码不一致'); return }
  pwdLoading.value = true
  const r = await safe(authApi.changePassword({ old_password: oldPwd, new_password: newPwd }))
  pwdLoading.value = false
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '修改失败，请重试'); return }
  // 后端改密会 token_version+1（所有旧凭证立刻失效），同时返回新 token —— 必须马上换上
  if (r.data?.token) {
    setToken(r.data.token)
    auth.token = r.data.token
    auth.mustChangePassword = false
  }
  pwdVisible.value = false
  ElMessage.success('密码已修改，其它设备需重新登录')
}

const isSelf = computed(() => auth.user?.user_id && auth.user.user_id === Number(route.params.id))

async function load() {
  const uid = route.params.id
  loading.value = true
  error.value = null
  const r = await safe(userApi.profile(uid))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  user.value = r.data?.user || r.data
  published.value = pickList(r.data?.projects || r.data?.list || [])
  followers.value = r.data?.followers ?? r.data?.follower_count ?? 0
  const fs = await safe(userApi.followStatus(uid))
  if (fs.ok && fs.data) {
    following.value = !!fs.data.following
    followers.value = fs.data.followers ?? followers.value
  }
}

async function toggleFollow() {
  if (!auth.isLoggedIn) { ui.promptLogin('关注需要登录', route.fullPath); return }
  const r = await safe(interactApi.follow(route.params.id))
  if (!r.ok) { ElMessage.error('操作失败，请稍后重试'); return }
  if (r.data) { following.value = !!r.data.following; followers.value = r.data.followers ?? followers.value }
  else following.value = !following.value
  ElMessage.success(following.value ? '已关注' : '已取消关注')
}

/** 私聊：打开/复用与该用户的单聊会话，然后跳到消息页并选中它 */
async function openChat() {
  if (isSelf.value) return
  if (!auth.isLoggedIn) { ui.promptLogin('私聊需要登录', route.fullPath); return }
  if (user.value?.status === 3) { ElMessage.warning('该用户已注销，无法私聊'); return }
  chatting.value = true
  const r = await safe(imApi.openDirect({ user_id: Number(route.params.id) }))
  chatting.value = false
  if (!r.ok) { ElMessage.error(r.error?.message || '打开会话失败，请稍后重试'); return }
  router.push({ name: 'messages', query: { session: r.data?.im_session_id } })
}

watch(() => route.params.id, load)
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 20px; padding: 24px; flex-wrap: wrap; }
.acts { display: flex; gap: 10px; align-items: center; }
.u-name { font-size: var(--fs-h1); font-weight: 700; margin: 0; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
</style>
