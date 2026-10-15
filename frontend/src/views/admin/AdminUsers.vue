<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">账号管理</h2>
        <div class="sm dim">重置密码（一次性临时密码） · 注销账号（软删 status=3，作品保留、作者名显示「用户已注销」）</div>
      </div>
      <el-button @click="load"><el-icon><Refresh /></el-icon>&nbsp;刷新</el-button>
    </div>

    <AppState :loading="loading && !list.length" :error="error" :empty="!loading && !error && !list.length"
      empty-text="没有用户数据" empty-desc="后端 /admin/users 可能尚未实现（契约里未列该 GET 接口）。" :skeleton-count="4" @retry="load">
      <el-table :data="list" style="width:100%">
        <el-table-column prop="user_id" label="ID" width="70" />
        <el-table-column label="QQ / 昵称" min-width="180">
          <template #default="{ row }">
            <div class="mono">{{ row.qq_account }}</div>
            <div class="xs dim">{{ row.nickname || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ { 2: '开发者', 3: '管理员' }[row.type] || '用户' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)" effect="dark">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.last_login_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="resetPw(row)">重置密码</el-button>
            <el-button size="small" type="danger" text :disabled="row.status === 3" @click="cancel(row)">注销账号</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppState>

    <el-dialog v-model="dialog" title="一次性临时密码" width="420px">
      <el-alert type="warning" :closable="false" show-icon title="24 小时有效，用户首次登录必须改密" />
      <div class="temp-pw mono">{{ tempPw }}</div>
      <template #footer>
        <el-button @click="copyTemp">复制密码</el-button>
        <el-button type="primary" @click="dialog = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppState from '@/components/AppState.vue'
import http, { safe } from '@/api/http'
import { adminApi, safe as safeApi } from '@/api'
import { fmtDateTime, pickList, pickTotal } from '@/utils/format'

const list = ref([])
const loading = ref(false)
const error = ref(null)
const dialog = ref(false)
const tempPw = ref('')

function statusLabel(s) {
  return { 0: '待审', 1: '正常', 2: '禁用', 3: '已注销' }[s] || '未知'
}
function statusType(s) {
  return { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }[s] || 'info'
}

async function load() {
  loading.value = true
  error.value = null
  // 契约未定义 /admin/users 列表接口，这里两个候选路径都试一下，取到就用
  let r = await safeApi(http.get('/admin/users', { params: { page: 1, size: 50 }, silent: true }))
  if (!r.ok) r = await safeApi(http.get('/admin/developers', { params: { page: 1, size: 50 }, silent: true }))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  list.value = pickList(r.data)
}

async function resetPw(row) {
  try {
    await ElMessageBox.confirm(`为 QQ ${row.qq_account} 生成一次性临时密码？`, '重置密码', { type: 'warning' })
    const r = await safeApi(adminApi.resetPassword(row.user_id))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    tempPw.value = r.data?.temp_password || r.data?.password || '（后端未返回明文）'
    dialog.value = true
    load()
  } catch (e) {}
}

async function cancel(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      `注销后该用户无法登录（作品保留，作者名显示「用户已注销」）。请输入理由：`,
      '注销账号',
      { inputValidator: (v) => (!!v && v.trim()) || '必须填写理由', confirmButtonClass: 'el-button--danger' }
    )
    const r = await safeApi(adminApi.cancelUser(row.user_id, { reason: value }))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    ElMessage.success('账号已注销'); load()
  } catch (e) {}
}

function copyTemp() {
  navigator.clipboard?.writeText(tempPw.value).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.warning('复制失败')
  )
}

onMounted(load)
</script>

<style scoped>
.temp-pw {
  margin-top: 14px; padding: 14px; text-align: center; font-size: 20px; font-weight: 700;
  letter-spacing: .08em; background: var(--c-bg-sunken); border-radius: var(--r-md);
  border: 1px dashed var(--c-primary-border); color: var(--c-primary);
}
</style>
