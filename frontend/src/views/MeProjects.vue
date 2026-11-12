<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">我的作品</h2>
        <div class="sm dim">状态徽标一目了然：待审 / 已发布 / 已下架 / 已拒绝</div>
      </div>
      <router-link to="/upload"><el-button type="primary"><el-icon><Upload /></el-icon>&nbsp;新建投稿</el-button></router-link>
    </div>

    <el-radio-group v-model="status" class="mb16" @change="load">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button value="0">草稿</el-radio-button>
      <el-radio-button value="1">待审</el-radio-button>
      <el-radio-button value="2">已发布</el-radio-button>
      <el-radio-button value="3">已下架</el-radio-button>
      <el-radio-button value="4">已拒绝</el-radio-button>
    </el-radio-group>

    <AppState
      :loading="loading && !list.length"
      :error="error"
      :empty="!loading && !error && !list.length"
      empty-text="还没有作品"
      empty-desc="投稿你的第一个小造物，Scratch / 结构文件免审直发。"
      :skeleton-count="6"
      @retry="load"
    >
      <template #action>
        <router-link to="/upload"><el-button type="primary">去投稿</el-button></router-link>
      </template>
    </AppState>

    <div v-if="list.length" class="grid">
      <div v-for="p in list" :key="p.project_id || p.id" class="card">
        <ProjectCard :p="p" :show-status="true" :show-reason="true" />
        <div class="ops">
          <router-link :to="`/projects/${p.project_id || p.id}`">
            <el-button size="small" text>查看</el-button>
          </router-link>
          <el-button v-if="p.status === 2" size="small" text @click="goDashboard(p)">看数据</el-button>
          <el-button size="small" text @click="openCover(p)">封面</el-button>
          <!-- 草稿补提交：刷新或离开上传页后也能发布，避免卡在 status=0 -->
          <el-button
            v-if="p.status === 0"
            size="small"
            text
            type="primary"
            :loading="submittingId === (p.project_id || p.id)"
            @click="submitDraft(p)"
          >
            提交审核
          </el-button>
          <el-button v-if="isRejectedOrDown(p)" size="small" text type="primary" @click="resubmit(p)">
            重新编辑投稿
          </el-button>
        </div>
      </div>
    </div>

    <div v-if="list.length >= total && list.length" class="xs dim" style="text-align:center;padding:20px">
      共 {{ total }} 件作品
    </div>

    <!-- 更换封面（作者本人） -->
    <el-dialog v-model="coverVisible" title="设置项目封面" width="520px">
      <p class="xs dim" style="margin-top:0">
        正在编辑《{{ coverTarget?.title }}》。封面会用在首页卡片、作品详情页与分享图。
      </p>
      <CoverUploader
        v-if="coverTarget"
        :project-id="coverTarget.project_id || coverTarget.id"
        :cover-path="coverTarget.cover_path"
        @updated="onCoverUpdated"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppState from '@/components/AppState.vue'
import ProjectCard from '@/components/ProjectCard.vue'
import CoverUploader from '@/components/CoverUploader.vue'
import { uploadApi, safe } from '@/api'
import { pickList, pickTotal } from '@/utils/format'

const router = useRouter()
const list = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref(null)
const status = ref('')
const coverVisible = ref(false)
const coverTarget = ref(null)
const submittingId = ref(null)

/** 打开某作品的封面设置弹窗 */
function openCover(p) {
  coverTarget.value = { ...p }
  coverVisible.value = true
}
/** 封面变更后就地更新列表里的那张卡，避免整页重刷 */
function onCoverUpdated(d) {
  const id = coverTarget.value?.project_id || coverTarget.value?.id
  const hit = list.value.find((x) => (x.project_id || x.id) === id)
  if (hit) hit.cover_path = d?.cover_path || ''
  if (coverTarget.value) coverTarget.value.cover_path = d?.cover_path || ''
}

function isRejectedOrDown(p) { return p.status === 3 || p.status === 4 }

async function load() {
  loading.value = true
  error.value = null
  const params = { page: 1, size: 50 }
  if (status.value !== '') params.status = status.value
  const r = await safe(uploadApi.myProjects(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  list.value = pickList(r.data)
  total.value = pickTotal(r.data)
}

function goDashboard(p) { router.push({ path: '/me/dashboard', query: { projectId: p.project_id || p.id } }) }
function resubmit(p) {
  ElMessage.info('编辑功能：MVP 阶段请新建投稿（后端 PUT /projects/{id} 已就绪，前端编辑页二期补）')
  router.push('/upload')
}

/** 草稿(0) 直接补提交：html 走人工审核(→1)，scratch/structure 直接发布(→2) */
async function submitDraft(p) {
  const id = p.project_id || p.id
  submittingId.value = id
  const r = await safe(uploadApi.submit(id))
  submittingId.value = null
  if (!r.ok) {
    ElMessage.error(r.error?.response?.data?.message || '提交失败，请稍后再试')
    return
  }
  const st = r.data?.status
  if (st === 2) ElMessage.success('已发布，刷新首页即可看到')
  else ElMessage.success('已提交审核，管理员通过后出现在首页')
  load()
}

onMounted(load)
</script>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.card { padding: 12px; }
.ops { display: flex; gap: 4px; margin-top: 10px; justify-content: flex-end; }
.mb16 { margin-bottom: 16px; }
</style>
