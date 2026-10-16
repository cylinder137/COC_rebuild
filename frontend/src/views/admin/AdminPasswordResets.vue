<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">密码找回申请</h2>
        <div class="sm dim">处理方式：核实身份后为用户生成一次性临时密码（24 小时有效，首次登录强制改密，记审计）；核实不通过可驳回（保留记录并通知申请人）</div>
      </div>
      <div class="row center gap12">
        <el-radio-group v-model="status" @change="load">
          <el-radio-button :value="0">待处理</el-radio-button>
          <el-radio-button :value="1">已办结</el-radio-button>
          <el-radio-button :value="2">已驳回</el-radio-button>
          <el-radio-button :value="''">全部</el-radio-button>
        </el-radio-group>
        <el-button @click="load"><el-icon><Refresh /></el-icon>&nbsp;刷新</el-button>
      </div>
    </div>

    <AppState :loading="loading && !list.length" :error="error" :empty="!loading && !error && !list.length"
      empty-text="没有找回申请" empty-desc="用户在 /forgot 页面提交后会出现在这里。" :skeleton-count="4" @retry="load">
      <el-table :data="list" style="width:100%">
        <el-table-column prop="apply_id" label="ID" width="70" />
        <el-table-column label="QQ 号（账号）" min-width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.qq_account }}</span>
            <div class="xs dim">{{ row.nickname || (row.user_id ? ('用户 #' + row.user_id) : '（无对应用户）') }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="contact" label="联系方式" min-width="140" />
        <el-table-column prop="note" label="备注" min-width="180">
          <template #default="{ row }"><span class="sm dim clamp2">{{ row.note || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="stOf(row.status).type" effect="dark">{{ stOf(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理记录" min-width="180">
          <template #default="{ row }">
            <div v-if="row.status === 2" class="sm">驳回：{{ row.reject_reason || '（未填理由）' }}</div>
            <div v-else-if="row.status === 1" class="sm dim">已生成临时密码（24h 有效）</div>
            <div v-else class="sm dim">—</div>
            <div class="xs dim">{{ row.handled_at ? fmtDateTime(row.handled_at) : '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.applied_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text :disabled="row.status !== 0" @click="resetPw(row)">生成临时密码</el-button>
            <el-button size="small" type="danger" text :disabled="row.status !== 0" @click="rejectPw(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppState>

    <div style="text-align:center;margin-top:16px" v-if="list.length">
      <el-pagination layout="prev, pager, next, total" :total="total" :page-size="size" :current-page="page"
        @current-change="(p) => { page = p; load() }" />
    </div>

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
const page = ref(1)
const size = ref(20)
const status = ref(0)
const loading = ref(false)
const error = ref(null)
const dialog = ref(false)
const tempPw = ref('')

// 0 待处理 / 1 已办结（已生成临时密码） / 2 已驳回
const STATUS_MAP = {
  0: { label: '待处理', type: 'warning' },
  1: { label: '已办结', type: 'success' },
  2: { label: '已驳回', type: 'danger' }
}
function stOf(s) { return STATUS_MAP[s] || { label: '未知', type: 'info' } }

async function load() {
  loading.value = true
  error.value = null
  const params = { page: page.value, size: size.value }
  if (status.value !== '') params.status = status.value
  const r = await safe(adminApi.passwordResets(params))
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
    const r = await safe(adminApi.resetPassword(uid, { apply_id: row.apply_id }))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    tempPw.value = r.data?.temp_password || r.data?.password || '（后端未返回明文，请查看响应）'
    dialog.value = true
    load()
  } catch (e) {}
}

async function rejectPw(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      '驳回理由（会通知申请人，可填写「信息不足」「QQ 号与账号归属不符」等）',
      '驳回找回密码申请',
      {
        inputType: 'textarea',
        inputPlaceholder: '信息不足，无法核实身份',
        // 注意：Element Plus 里 inputValidator 返回「字符串」= 校验失败，合法必须返回 true
        inputValidator: (v) => !!((v || '').trim()) || '必须填写驳回理由'
      }
    )
    const r = await safe(adminApi.rejectPasswordReset(row.apply_id, { reason: value.trim() }))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    ElMessage.success('已驳回，申请记录已保留')
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
