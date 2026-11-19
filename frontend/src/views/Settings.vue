<template>
  <div class="page">
    <div class="container narrow">
      <h1 class="pg-title">设置</h1>
      <p class="pg-sub sm dim">外观与偏好只保存在本机浏览器；账号安全相关会同步到服务器。</p>

      <!-- ============ 外观 ============ -->
      <section class="card sec">
        <div class="sec-hd">
          <el-icon><Brush /></el-icon><span>外观</span>
        </div>

        <div class="row-item">
          <div class="ri-label">
            <div class="ri-t">主题模式</div>
            <div class="ri-d xs dim">跟随系统时会自动跟随手机 / 电脑的深浅色</div>
          </div>
          <el-radio-group :model-value="theme.theme" size="small" @change="onMode">
            <el-radio-button value="dark">深色</el-radio-button>
            <el-radio-button value="light">浅色</el-radio-button>
            <el-radio-button value="auto">跟随系统</el-radio-button>
          </el-radio-group>
        </div>

        <div class="row-item">
          <div class="ri-label">
            <div class="ri-t">主题色调</div>
            <div class="ri-d xs dim">影响按钮、链接、渐变与图表高亮</div>
          </div>
          <div class="swatches">
            <button
              v-for="a in ACCENTS"
              :key="a.id"
              class="sw"
              :class="{ on: theme.accent === a.id }"
              :title="a.name"
              :aria-label="a.name"
              :style="{ background: `linear-gradient(135deg, ${a.primary}, ${a.accent2})` }"
              @click="theme.setAccent(a.id)"
            >
              <el-icon v-if="theme.accent === a.id"><Check /></el-icon>
            </button>
          </div>
        </div>
      </section>

      <!-- ============ 浏览偏好 ============ -->
      <section class="card sec">
        <div class="sec-hd">
          <el-icon><View /></el-icon><span>浏览偏好</span>
        </div>
        <div class="row-item">
          <div class="ri-label">
            <div class="ri-t">自动加载 HTML 作品预览</div>
            <div class="ri-d xs dim">
              开启后，列表页滚到哪就把作品真实跑起来（同时最多 4 个）；关掉可省流量、老机型更流畅
            </div>
          </div>
          <el-switch :model-value="ui.autoLoad" @change="onAutoLoad" />
        </div>
      </section>

      <!-- ============ 账号安全 ============ -->
      <section class="card sec">
        <div class="sec-hd">
          <el-icon><Lock /></el-icon><span>账号安全</span>
        </div>
        <div class="acc-info sm">
          登录账号：<b class="mono">{{ auth.user?.qq_account || '-' }}</b>
          <span class="dim"> · {{ auth.isAdmin ? '管理员' : '开发者' }}</span>
        </div>

        <el-form ref="pwdRef" :model="pwd" :rules="pwdRules" label-width="92px" class="pwd-form" @submit.prevent>
          <el-form-item label="原密码" prop="old">
            <el-input v-model="pwd.old" type="password" show-password autocomplete="current-password" placeholder="当前登录密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="next">
            <el-input v-model="pwd.next" type="password" show-password autocomplete="new-password" placeholder="6-64 位，不能与原密码相同" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="again">
            <el-input v-model="pwd.again" type="password" show-password autocomplete="new-password" placeholder="再输入一次" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="pwdLoading" @click="submitPwd">修改密码</el-button>
            <span class="xs dim pwd-tip">改完自动换发登录凭证，其它设备会被登出</span>
          </el-form-item>
        </el-form>
      </section>

      <!-- ============ 危险操作 ============ -->
      <section class="card sec danger">
        <div class="sec-hd">
          <el-icon><WarningFilled /></el-icon><span>危险操作</span>
        </div>
        <div class="row-item">
          <div class="ri-label">
            <div class="ri-t">注销账号</div>
            <div class="ri-d xs dim">
              注销后立即退出登录且无法再登录；<b>已发布作品不会被删除</b>，作者名统一显示为「用户已注销」
            </div>
          </div>
          <el-button type="danger" plain @click="cancelVisible = true">注销账号</el-button>
        </div>
      </section>
    </div>

    <!-- 注销确认 -->
    <el-dialog v-model="cancelVisible" title="确认注销账号" width="420px" :close-on-click-modal="false">
      <div class="warn-box xs">
        此操作不可撤销。作品保留但作者名会回退为「用户已注销」，你的积分、关注关系一并失效。
      </div>
      <el-form :model="cancelForm" label-width="86px" class="cancel-form">
        <el-form-item label="登录密码">
          <el-input v-model="cancelForm.password" type="password" show-password placeholder="校验身份用" />
        </el-form-item>
        <el-form-item label="注销原因">
          <el-input v-model="cancelForm.reason" maxlength="100" placeholder="选填，帮助平台改进" />
        </el-form-item>
        <el-form-item label="确认输入">
          <el-input v-model="cancelForm.confirm" placeholder="请输入「注销」两个字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelVisible = false">再想想</el-button>
        <el-button type="danger" :loading="cancelLoading" @click="submitCancel">确认注销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { setToken } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore, useUiStore, ACCENTS } from '@/stores/ui'

const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()
const ui = useUiStore()

onMounted(() => {
  theme.apply()
  if (!auth.loaded) auth.fetchMe()
})

function onMode(v) { theme.setTheme(v) }
function onAutoLoad(v) {
  ui.setAutoLoad(v)
  ElMessage.success(v ? '已开启自动预览' : '已关闭自动预览')
}

/* ---------------- 修改密码 ---------------- */
const pwdRef = ref(null)
const pwdLoading = ref(false)
const pwd = reactive({ old: '', next: '', again: '' })
const pwdRules = {
  old: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  next: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度须为 6-64 位', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        if (v && v === pwd.old) cb(new Error('新密码不能与原密码相同'))
        else cb()
      },
      trigger: 'blur'
    }
  ],
  again: [
    { required: true, message: '请再输入一次', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        if (v !== pwd.next) cb(new Error('两次输入不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

async function submitPwd() {
  const ok = await pwdRef.value?.validate().catch(() => false)
  if (!ok) return
  pwdLoading.value = true
  try {
    const data = await authApi.changePassword({ old_password: pwd.old, new_password: pwd.next })
    // 改密后旧 token 立即失效，必须换上新凭证，否则刷新就被踢
    if (data?.token) {
      auth.token = data.token
      setToken(data.token)
      auth.mustChangePassword = false
    }
    pwd.old = pwd.next = pwd.again = ''
    pwdRef.value?.clearValidate()
    ElMessage.success('密码已修改，其它设备的登录已失效')
  } catch (e) {
    ElMessage.error(e?.message || '修改失败')
  } finally {
    pwdLoading.value = false
  }
}

/* ---------------- 注销账号 ---------------- */
const cancelVisible = ref(false)
const cancelLoading = ref(false)
const cancelForm = reactive({ password: '', reason: '', confirm: '' })

async function submitCancel() {
  if (!cancelForm.password) { ElMessage.warning('请输入登录密码'); return }
  if (cancelForm.confirm.trim() !== '注销') { ElMessage.warning('请准确输入「注销」两个字'); return }
  cancelLoading.value = true
  try {
    await authApi.cancelAccount({ password: cancelForm.password, reason: cancelForm.reason })
    ElMessage.success('账号已注销')
    cancelVisible.value = false
    setTimeout(() => auth.logout(true), 800)
  } catch (e) {
    ElMessage.error(e?.message || '注销失败')
  } finally {
    cancelLoading.value = false
  }
}
</script>

<style scoped>
.narrow { max-width: 760px; }
.pg-title { font-size: var(--fs-h1); font-weight: 700; margin: 0 0 4px; }
.pg-sub { margin: 0 0 18px; }

.sec { margin-bottom: 16px; }
.sec.danger { border-color: color-mix(in srgb, var(--c-danger) 40%, var(--c-border)); }
.sec-hd {
  display: flex; align-items: center; gap: 8px;
  font-size: var(--fs-h3); font-weight: 600; margin-bottom: 6px;
}
.row-item {
  display: flex; align-items: center; justify-content: space-between; gap: 20px;
  padding: 14px 0; border-top: 1px solid var(--c-border);
  flex-wrap: wrap;
}
.row-item:first-of-type { border-top: none; }
.ri-t { font-weight: 600; }
.ri-d { margin-top: 3px; max-width: 460px; line-height: 1.6; }

.swatches { display: flex; gap: 10px; flex-wrap: wrap; }
.sw {
  width: 30px; height: 30px; border-radius: var(--r-pill); border: 2px solid transparent;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  color: #fff; box-shadow: var(--sh-1); transition: transform var(--dur-fast) var(--ease-out);
}
.sw:hover { transform: translateY(-2px); }
.sw.on { border-color: var(--c-text-1); box-shadow: var(--sh-brand); }

.acc-info { padding: 8px 12px; border-radius: var(--r-md); background: var(--c-bg-sunken); margin: 10px 0 6px; }
.pwd-form { margin-top: 10px; max-width: 520px; }
.pwd-tip { margin-left: 12px; }

.warn-box {
  padding: 10px 12px; border-radius: var(--r-md); margin-bottom: 14px; line-height: 1.7;
  background: color-mix(in srgb, var(--c-danger) 12%, transparent);
  color: var(--c-danger);
}
.cancel-form { margin-top: 4px; }

@media (max-width: 767px) {
  .row-item { flex-direction: column; align-items: flex-start; gap: 10px; }
  .pwd-form { max-width: 100%; }
}
</style>
