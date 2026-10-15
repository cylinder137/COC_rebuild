<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">项目管理</h2>
        <div class="sm dim">下架 / 恢复 / 删除（物理删含服务器留存代码） / 查看存储路径</div>
      </div>
      <div class="row center gap8 wrap">
        <el-select v-model="status" placeholder="全部状态" clearable style="width:140px" @change="load">
          <el-option label="草稿" :value="0" />
          <el-option label="待审" :value="1" />
          <el-option label="已发布" :value="2" />
          <el-option label="已下架" :value="3" />
          <el-option label="已拒绝" :value="4" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜索标题/作者" style="width:180px" clearable @keyup.enter="load" />
        <el-checkbox v-model="includeDeleted" @change="load">含已删除</el-checkbox>
        <el-button @click="load">查询</el-button>
      </div>
    </div>

    <AppState :loading="loading && !list.length" :error="error" :empty="!loading && !error && !list.length"
      empty-text="没有匹配的项目" empty-desc="换个筛选条件试试。" :skeleton-count="5" @retry="load">
      <el-table :data="list" style="width:100%">
        <el-table-column prop="project_id" label="ID" width="80" />
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">
            <div class="ellipsis" style="font-weight:600">{{ row.title }}</div>
            <div class="xs dim">@{{ row.author?.nickname || row.author_nickname || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.project_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusOf(row.status).type" effect="dark">{{ statusOf(row.status).label }}</el-tag>
            <el-tag v-if="row.is_deleted" size="small" type="info" effect="plain" style="margin-left:4px">已删除</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="浏览 / 赞" width="120">
          <template #default="{ row }">
            <span class="num xs">{{ compactNum(row.view_count_total) }} / {{ compactNum(row.like_count) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmtDateTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="$router.push(`/admin/projects/${row.project_id}/storage`)">存储路径</el-button>
            <el-button size="small" text @click="$router.push(`/projects/${row.project_id}`)">前台</el-button>
            <el-button v-if="row.status === 3" size="small" type="success" text @click="restore(row)">恢复</el-button>
            <el-button v-else size="small" type="warning" text @click="down(row)">下架</el-button>
            <el-button size="small" type="danger" text @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppState>

    <div style="text-align:center;margin-top:16px" v-if="list.length">
      <el-pagination layout="prev, pager, next, total" :total="total" :page-size="size" :current-page="page"
        @current-change="(p) => { page = p; load() }" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppState from '@/components/AppState.vue'
import { adminApi, safe } from '@/api'
import { compactNum, fmtDateTime, pickList, pickTotal, statusOf } from '@/utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref('')
const keyword = ref('')
const includeDeleted = ref(false)
const loading = ref(false)
const error = ref(null)

async function load() {
  loading.value = true
  error.value = null
  const params = { page: page.value, size: size.value }
  if (status.value !== '' && status.value !== null) params.status = status.value
  if (keyword.value) params.keyword = keyword.value
  if (includeDeleted.value) params.include_deleted = true
  const r = await safe(adminApi.projects(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  list.value = pickList(r.data)
  total.value = pickTotal(r.data)
}

async function down(row) {
  try {
    const { value } = await ElMessageBox.prompt('下架原因（代码保留）', '下架', {
      inputValidator: (v) => (!!v && v.trim()) || '必须填写原因'
    })
    const r = await safe(adminApi.down(row.project_id, { reason: value }))
    if (!r.ok) { ElMessage.error('操作失败'); return }
    ElMessage.success('已下架'); load()
  } catch (e) {}
}
async function restore(row) {
  const r = await safe(adminApi.restore(row.project_id))
  if (!r.ok) { ElMessage.error('操作失败'); return }
  ElMessage.success('已恢复'); load()
}
async function remove(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      `删除会物理删除服务器留存代码且不可恢复！请输入作品标题「${row.title}」确认`,
      '危险操作',
      { inputValidator: (v) => v === row.title || '标题不匹配', confirmButtonClass: 'el-button--danger' }
    )
    const r = await safe(adminApi.removeProject(row.project_id, { reason: '管理员删除', confirm_title: value }))
    if (!r.ok) { ElMessage.error('删除失败'); return }
    ElMessage.success('已删除'); load()
  } catch (e) {}
}

onMounted(load)
</script>
