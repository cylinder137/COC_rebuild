<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">密码找回申请</h2>
        <div class="sm dim">处理方式：核实身份后为用户生成一次性临时密码（24 小时有效，首次登录强制改密，记审计）</div>
      </div>
      <el-button @click="load"><el-icon><Refresh /></el-icon>&nbsp;刷新</el-button>
    </div>

    <AppState :loading="loading && !list.length" :error="error" :empty="!loading && !error && !list.length"
      empty-text="没有找回申请" empty-desc="用户在 /forgot 页面提交后会出现在这里。" :skeleton-count="4" @retry="load">
      <el-table :data="list" style="width:100%">
        <el-table-column prop="apply_id" label="ID" width="70" />
        <el-table-column label="QQ 号（账号）" min-width="140">
          <template #default="{ row }"><span class="mono">{{ row.qq_account }}</span></template>
        </el-table-column>
        <el-table-column prop="contact" label="联系方式" min-width="150" />
        <el-table-column prop="note" label="备注" min-width="200">
          <template #default="{ row }"><span class="sm dim clamp2">{{ row.note || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 0 ? 'warning' : 'success'" effect="dark">
              {{ row.status === 0 ? '待处理' : '已办结' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.applied_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="resetPw(row)">生成临时密码</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppState>

    <el-dialog v-model="dialog" title="一次性临时密码" width="420px">
      <el-alert type="warning" :closable="false" show-icon title="请通过安全渠道发送给用户" description="该密码 24 小时内有效，用户首次登录必须修改密码。" />
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
import { adminApi, safe } from '@/api'
import { fmtDateTime, pickList, pickTotal } from '@/utils/format'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref(null)
const dialog = ref(false)
const tempPw = ref('')

async function load() {
  loading.value = true
  error.value = null
  const r = await safe(adminApi.passwordResets({ page: 1, size: 50 }))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  list.value = pickList(r.data)
  total.value = pickTotal(r.data)
}

async function resetPw(row) {
  const uid = row.user_id || row.user?.user_id
  if (!uid) { ElMessage.warning('该申请未关联用户 ID，请先在账号管理中按 QQ 号定位用户'); return }
  try {
    await ElMessageBox.confirm(`将为 QQ ${row.qq_account} 生成一次性临时密码，确认继续？`, '生成临时密码', { type: 'warning' })
    const r = await safe(adminApi.resetPassword(uid))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    tempPw.value = r.data?.temp_password || r.data?.password || '（后端未返回明文，请查看响应）'
    dialog.value = true
    load()
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
