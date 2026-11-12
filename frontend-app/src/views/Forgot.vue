<template>
  <div class="page">
    <div class="container narrow">
      <h1 class="title">密码找回 · 联系管理员</h1>

      <!-- 醒目展示管理员 QQ -->
      <div class="admin-card">
        <div class="ac-left">
          <div class="ac-label">管理员 QQ</div>
          <div class="ac-qq mono">3254328344</div>
          <div class="xs dim">密码使用 BCrypt 单向加密，平台无法直接告知原密码；请联系管理员核实身份后生成一次性临时密码。</div>
        </div>
        <el-button type="primary" size="large" @click="copyQQ">
          <el-icon><CopyDocument /></el-icon>&nbsp;复制 QQ 号
        </el-button>
      </div>

      <div class="card" style="margin-top:20px">
        <div class="card-hd">提交找回申请</div>
        <p class="sm muted" style="margin-top:0">
          提交后管理员会在后台看到你的申请，并生成一次性临时密码（24 小时有效，首次登录必须修改密码，全程记审计）。
        </p>
        <el-form :model="form" label-position="top">
          <el-form-item label="QQ 号（账号）">
            <el-input v-model="form.qq_account" size="large" placeholder="注册时使用的 QQ 号" />
          </el-form-item>
          <el-form-item label="联系方式">
            <el-input v-model="form.contact" size="large" placeholder="方便管理员联系你的邮箱 / 微信 / 备选 QQ" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="例如：注册昵称是「像素阿明」，可以补充能证明身份的信息" />
          </el-form-item>
          <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">
            提交找回申请
          </el-button>
        </el-form>

        <el-alert
          v-if="applyId"
          style="margin-top:16px"
          type="success"
          :closable="false"
          show-icon
          :title="`申请已提交，编号 #${applyId}`"
          description="请同时通过 QQ 联系管理员 3254328344 以便尽快处理。"
        />
      </div>

      <el-steps :active="2" align-center class="steps" simple>
        <el-step title="提交申请" />
        <el-step title="管理员核实" />
        <el-step title="获取临时密码" />
      </el-steps>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi, safe } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const form = reactive({ qq_account: '', contact: '', note: '' })
const loading = ref(false)
const applyId = ref(null)

async function copyQQ() {
  try {
    await navigator.clipboard.writeText('3254328344')
    ElMessage.success('管理员 QQ 已复制：3254328344')
  } catch (e) {
    ElMessage.info('管理员 QQ：3254328344')
  }
}

async function submit() {
  if (!form.qq_account || !form.contact) { ElMessage.warning('请填写 QQ 号与联系方式'); return }
  loading.value = true
  const r = await safe(authApi.forgot({ ...form }))
  loading.value = false
  if (!r.ok) {
    ElMessage.error(r.error?.response?.data?.message || '提交失败（后端 8090 可能未启动），也可直接联系管理员 QQ 3254328344')
    return
  }
  applyId.value = r.data?.apply_id || '—'
  ElMessage.success('找回申请已提交')
}
</script>

<style scoped>
.narrow { max-width: 640px; }
.title { font-size: var(--fs-h1); font-weight: 700; margin: 0 0 20px; }
.admin-card {
  display: flex; align-items: center; gap: 20px; padding: 22px;
  border-radius: var(--r-lg); border: 1px solid var(--c-primary-border);
  background: linear-gradient(135deg, rgba(79, 110, 247, .16), rgba(168, 85, 247, .10));
  flex-wrap: wrap;
}
.ac-left { flex: 1; min-width: 240px; }
.ac-label { font-size: var(--fs-sm); color: var(--c-text-2); }
.ac-qq { font-size: 30px; font-weight: 700; letter-spacing: .04em; color: var(--c-primary); margin: 4px 0 6px; }
.steps { margin-top: 28px; }
</style>
