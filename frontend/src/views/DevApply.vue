<template>
  <div class="page">
    <div class="container narrow">
      <h1 class="title">申请成为开发者</h1>
      <p class="muted sm mb24">
        开发者可以用 QQ 号作为账号登录，投稿自己的作品（HTML / Scratch / MC 结构）。
        Scratch 与结构文件免审直发，HTML 需要管理员人工审核。
      </p>

      <div class="card">
        <el-form :model="form" label-position="top">
          <el-form-item label="QQ 号（将作为你的登录账号）">
            <el-input v-model="form.qq_account" placeholder="例如 3254328344" size="large" />
          </el-form-item>
          <el-form-item label="设置密码">
            <el-input v-model="form.password" type="password" show-password size="large" placeholder="至少 6 位，请牢记" />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" size="large" placeholder="展示在作品与评论中" maxlength="20" show-word-limit />
          </el-form-item>
          <el-form-item label="联系方式">
            <el-input v-model="form.contact" size="large" placeholder="邮箱 / 微信 / QQ 均可，仅管理员可见" />
          </el-form-item>

          <el-form-item>
            <el-checkbox v-model="agreed">
              我已阅读并同意
              <a href="javascript:;" @click.prevent="showAgreement = true">《开源协议与投稿规范》</a>
            </el-checkbox>
          </el-form-item>

          <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">
            提交申请
          </el-button>
        </el-form>

        <el-alert
          v-if="done"
          style="margin-top:16px"
          type="success"
          :closable="false"
          show-icon
          title="申请已提交"
          description="管理员审核通过后即可登录投稿。审核结果会通过你留下的联系方式通知。"
        />
      </div>

      <p class="xs dim" style="margin-top:16px">
        已有账号？<router-link to="/login">去登录</router-link> · 忘记密码？<router-link to="/forgot">联系管理员</router-link>
      </p>
    </div>

    <!-- 开源协议弹窗 -->
    <el-dialog v-model="showAgreement" title="开源协议与投稿规范" width="640px">
      <div class="agreement">
        <p><b>1. 授权范围</b>：投稿即表示你授权造物集在本平台及关联沙箱域名内展示、运行与分发你提交的产物；源码仅对已登录的开发者提供下载。</p>
        <p><b>2. 原创与合规</b>：请勿上传含有恶意脚本、外链窃取、违法违规内容的作品。HTML 类型会经管理员人工审核，审核时管理员只查看服务器存储路径下的文件，不会在系统内运行未过审内容。</p>
        <p><b>3. 审核与下架</b>：HTML 需人工审核；Scratch 与 MC 结构文件为纯数据/平台打包产物，免审直发。平台有权对违规作品执行下架或删除，并保留操作审计日志。</p>
        <p><b>4. 数据与隐私</b>：平台仅按天/小时记录浏览量计数，不采集访客明细。账号密码使用 BCrypt 单向加密，管理员不可见你的明文密码。</p>
        <p><b>5. 密码找回</b>：如忘记密码，请联系管理员 QQ <b>3254328344</b>，核实身份后由管理员生成一次性临时密码（24 小时有效，首次登录必须改密）。</p>
        <p><b>6. 责任</b>：你应对上传内容负责。因侵权或违规造成的后果由上传者承担。</p>
      </div>
      <template #footer>
        <el-button @click="showAgreement = false">关闭</el-button>
        <el-button type="primary" @click="agreeFromDialog">我已阅读并同意</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi, safe } from '@/api'

const form = reactive({ qq_account: '', password: '', nickname: '', contact: '' })
const agreed = ref(false)
const loading = ref(false)
const done = ref(false)
const showAgreement = ref(false)

function agreeFromDialog() { agreed.value = true; showAgreement.value = false }

async function submit() {
  if (!form.qq_account || !form.password || !form.nickname) { ElMessage.warning('请填写 QQ 号、密码与昵称'); return }
  if (!agreed.value) { ElMessage.warning('请先阅读并同意开源协议'); return }
  loading.value = true
  const r = await safe(authApi.devApply({ ...form }))
  loading.value = false
  if (!r.ok) {
    ElMessage.error(r.error?.response?.data?.message || '提交失败，请稍后重试（后端 8090 可能未启动）')
    return
  }
  done.value = true
  ElMessage.success('申请已提交，请等待管理员审核')
}
</script>

<style scoped>
.narrow { max-width: 560px; }
.title { font-size: var(--fs-h1); font-weight: 700; margin: 0 0 8px; }
.mb24 { margin-bottom: 24px; }
.agreement { max-height: 52vh; overflow-y: auto; font-size: var(--fs-sm); line-height: 26px; color: var(--c-text-2); }
.agreement b { color: var(--c-text-1); }
</style>
