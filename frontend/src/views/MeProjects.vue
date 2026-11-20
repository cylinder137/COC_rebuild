<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">我的作品</h2>
        <div class="sm dim">状态徽标一目了然：待审 / 已发布 / 已下架 / 已拒绝</div>
      </div>
      <div class="row gap8">
        <el-button @click="openSeriesDialog()"><el-icon><Plus /></el-icon>&nbsp;新建系列</el-button>
        <el-button v-if="series.length" text @click="manageVisible = true">系列管理</el-button>
        <router-link to="/upload"><el-button type="primary"><el-icon><Upload /></el-icon>&nbsp;新建投稿</el-button></router-link>
      </div>
    </div>

    <!-- 项目系列筛选：同一系列的作品成体系管理（如「物理演示程序系列」） -->
    <div v-if="series.length" class="row gap8 wrap center mb16">
      <el-tag
        :type="seriesFilter === '' ? 'primary' : 'info'"
        :effect="seriesFilter === '' ? 'dark' : 'plain'"
        round
        @click="pickSeries('')"
      >全部系列</el-tag>
      <el-tag
        :type="seriesFilter === 'none' ? 'primary' : 'info'"
        :effect="seriesFilter === 'none' ? 'dark' : 'plain'"
        round
        @click="pickSeries('none')"
      >未归系列</el-tag>
      <el-tag
        v-for="s in series"
        :key="s.series_id"
        :type="seriesFilter === s.series_id ? 'primary' : 'info'"
        :effect="seriesFilter === s.series_id ? 'dark' : 'plain'"
        round
        @click="pickSeries(s.series_id)"
      >
        {{ s.name }} {{ s.item_count }}<span v-if="s.is_public" class="pub-dot">●</span>
      </el-tag>
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
      empty-desc="投稿你的第一个小造物，Scratch 免审直发；素材可在「我的素材」上传。"
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
        <div v-if="p.series_name" class="xs dim series-tag">系列：{{ p.series_name }}</div>
        <div class="ops" role="group" aria-label="作品操作">
          <router-link :to="`/projects/${p.project_id || p.id}`">
            <el-button size="small" text class="op-btn" title="查看作品"><el-icon><View /></el-icon></el-button>
          </router-link>
          <el-button v-if="p.status === 2" size="small" text class="op-btn" title="看数据" @click="goDashboard(p)"><el-icon><DataAnalysis /></el-icon></el-button>
          <el-button size="small" text class="op-btn" title="编辑标题/简介/描述" @click="openEdit(p)"><el-icon><EditPen /></el-icon></el-button>
          <el-button size="small" text class="op-btn" title="编辑源码" @click="openCode(p)"><el-icon><Document /></el-icon></el-button>
          <el-button size="small" text class="op-btn" title="加入系列" @click="openSeriesFor(p)"><el-icon><Collection /></el-icon></el-button>
          <el-button size="small" text class="op-btn" title="设置封面" @click="openCover(p)"><el-icon><Picture /></el-icon></el-button>
          <!-- 草稿补提交：刷新或离开上传页后也能发布，避免卡在 status=0 -->
          <el-button
            v-if="p.status === 0"
            size="small"
            text
            class="op-btn"
            type="primary"
            title="提交审核"
            :loading="submittingId === (p.project_id || p.id)"
            @click="submitDraft(p)"
          ><el-icon><Promotion /></el-icon></el-button>
          <!-- 已发布 → 下架；已下架 → 恢复 -->
          <el-button v-if="p.status === 2" size="small" text class="op-btn" type="warning" title="下架" @click="downProject(p)"><el-icon><ArrowDown /></el-icon></el-button>
          <el-button v-if="p.status === 3" size="small" text class="op-btn" type="success" title="恢复上架" @click="restoreProject(p)"><el-icon><Refresh /></el-icon></el-button>
          <el-button
            size="small" text class="op-btn" type="danger" title="删除"
            :loading="deletingId === (p.project_id || p.id)"
            @click="deleteProject(p)"
          ><el-icon><Delete /></el-icon></el-button>
        </div>
      </div>
    </div>

    <div v-if="list.length >= total && list.length" class="xs dim" style="text-align:center;padding:20px">
      共 {{ total }} 件作品
    </div>

    <!-- 新建 / 编辑项目系列 -->
    <el-dialog v-model="seriesDlg" :title="seriesForm.series_id ? '编辑项目系列' : '新建项目系列'" width="min(460px, 92vw)">
      <el-form label-width="72px">
        <el-form-item label="名称">
          <el-input v-model="seriesForm.name" maxlength="80" placeholder="例如：物理演示程序系列" data-field="p_series_name" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="seriesForm.intro" maxlength="255" type="textarea" :rows="2" placeholder="这个系列收录什么" />
        </el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="seriesForm.is_public" />
          <span class="xs dim" style="margin-left:8px">公开后别人能在你的主页看到这个系列</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="seriesDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSeries">保存</el-button>
      </template>
    </el-dialog>

    <!-- 系列管理 -->
    <el-dialog v-model="manageVisible" title="项目系列" width="min(560px, 94vw)">
      <div v-for="s in series" :key="s.series_id" class="mng-row">
        <div class="grow">
          <div class="sm">{{ s.name }} <span class="xs dim">（{{ s.item_count }} 个作品{{ s.is_public ? ' · 公开' : '' }}）</span></div>
          <div v-if="s.intro" class="xs dim">{{ s.intro }}</div>
        </div>
        <el-switch
          :model-value="!!s.is_public"
          size="small"
          inline-prompt
          active-text="公开"
          inactive-text="私有"
          @change="(v) => toggleSeriesPublic(s, v)"
        />
        <el-button size="small" text @click="openSeriesDialog(s)">改名</el-button>
        <el-button size="small" text type="danger" @click="removeSeries(s)">删除</el-button>
      </div>
      <div v-if="!series.length" class="xs dim" style="padding:12px 0">还没有项目系列</div>
    </el-dialog>

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

    <!-- 编辑作品元信息（标题 / 简介 / 描述） -->
    <el-dialog v-model="editVisible" title="编辑作品信息" width="min(560px, 94vw)">
      <el-form label-width="64px" v-if="editTarget">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" maxlength="100" show-word-limit data-field="me_title" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editForm.summary" maxlength="200" type="textarea" :rows="2" placeholder="一句话介绍你的作品" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" maxlength="2000" type="textarea" :rows="5" placeholder="详细说明、玩法、更新记录等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 在线编辑源码（作者本人，文本文件） -->
    <el-dialog v-model="codeVisible" :title="`编辑源码 · ${codeTarget?.title || ''}`" width="min(820px, 96vw)" top="4vh">
      <div v-if="codeLoading" class="xs dim" style="padding:16px 0">正在读取源码…</div>
      <template v-else-if="codeFiles.length">
        <div class="code-layout">
          <ul class="code-files">
            <li
              v-for="f in codeFiles"
              :key="f.rel"
              :class="{ sel: codeCur === f.rel, dis: !f.editable }"
              @click="f.editable && pickCodeFile(f)"
            >
              <span class="ellipsis">{{ f.rel }}</span>
              <span class="xs dim" v-if="!f.editable">只读</span>
            </li>
          </ul>
          <div class="code-editor">
            <div v-if="codeCur" class="xs dim code-hint">正在编辑：{{ codeCur }}</div>
            <el-input
              v-if="codeCur"
              v-model="codeContent"
              type="textarea"
              :rows="18"
              spellcheck="false"
              class="code-area"
              resize="none"
            />
            <div v-else class="xs dim" style="padding:16px 0">从左侧选一个文本文件开始编辑</div>
          </div>
        </div>
      </template>
      <div v-else class="xs dim" style="padding:16px 0">没有可在线编辑的文本文件</div>
      <template #footer>
        <span class="xs dim" style="float:left">修改直接写入当前版本的源码；已发布作品保存后立即生效</span>
        <el-button @click="codeVisible = false">关闭</el-button>
        <el-button type="primary" :loading="codeSaving" :disabled="!codeCur" @click="saveCode">保存修改</el-button>
      </template>
    </el-dialog>

    <!-- 把已有作品加入系列 -->
    <el-dialog v-model="joinVisible" :title="`加入系列 · ${joinTarget?.title || ''}`" width="min(460px, 92vw)">
      <div v-if="joinVisible">
        <div class="vp-sub" style="margin-top:0">选择已有系列</div>
        <el-select v-model="joinSeriesId" placeholder="不选则新建" clearable style="width:100%" data-field="me_join_series">
          <el-option v-for="s in series" :key="s.series_id" :label="s.name" :value="s.series_id" />
        </el-select>
        <template v-if="!joinSeriesId">
          <div class="vp-sub">或创建新系列</div>
          <el-input v-model="joinNewName" maxlength="80" placeholder="例如：物理演示程序系列" data-field="me_new_series" />
          <div class="row center gap8" style="margin-top:8px">
            <span class="xs dim">公开</span>
            <el-switch v-model="joinNewPublic" />
            <span class="xs dim">公开后别人能在你的主页看到这个系列</span>
          </div>
        </template>
        <div v-if="joinTarget?.series_name" class="xs dim" style="margin-top:10px">
          当前系列：{{ joinTarget.series_name }}（保存后可改到这里）
        </div>
      </div>
      <template #footer>
        <el-button @click="joinVisible = false">取消</el-button>
        <el-button type="primary" :loading="joinSaving" @click="saveJoin">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppState from '@/components/AppState.vue'
import ProjectCard from '@/components/ProjectCard.vue'
import CoverUploader from '@/components/CoverUploader.vue'
import { seriesApi, uploadApi, safe } from '@/api'
import { pickList, pickTotal } from '@/utils/format'
import { pullFields } from '@/utils/formDom'

const router = useRouter()
const list = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref(null)
const status = ref('')
const coverVisible = ref(false)
const coverTarget = ref(null)
const submittingId = ref(null)
const deletingId = ref(null)
/* ---- 编辑元信息 ---- */
const editVisible = ref(false)
const editTarget = ref(null)
const editSaving = ref(false)
const editForm = reactive({ title: '', summary: '', description: '' })
/* ---- 在线改源码 ---- */
const codeVisible = ref(false)
const codeTarget = ref(null)
const codeLoading = ref(false)
const codeSaving = ref(false)
const codeFiles = ref([])
const codeCur = ref('')
const codeContent = ref('')
/* ---- 加入系列 ---- */
const joinVisible = ref(false)
const joinTarget = ref(null)
const joinSaving = ref(false)
const joinSeriesId = ref(null)
const joinNewName = ref('')
const joinNewPublic = ref(false)
/* ---- 项目系列 ---- */
const series = ref([])
const seriesFilter = ref('')
const seriesDlg = ref(false)
const manageVisible = ref(false)
const saving = ref(false)
const seriesForm = reactive({ series_id: null, name: '', intro: '', is_public: false })

async function loadSeries() {
  const r = await safe(seriesApi.mine('project'))
  series.value = r.ok && Array.isArray(r.data) ? r.data : []
}

function pickSeries(v) {
  seriesFilter.value = v
  load()
}

function openSeriesDialog(s) {
  seriesForm.series_id = s?.series_id || null
  seriesForm.name = s?.name || ''
  seriesForm.intro = s?.intro || ''
  seriesForm.is_public = !!s?.is_public
  seriesDlg.value = true
}

async function saveSeries() {
  const name = (seriesForm.name || '').trim()
  if (!name) { ElMessage.warning('请填写系列名称'); return }
  saving.value = true
  const payload = { kind: 'project', name, intro: seriesForm.intro || '', is_public: seriesForm.is_public ? 1 : 0 }
  const r = seriesForm.series_id
    ? await safe(seriesApi.update(seriesForm.series_id, payload))
    : await safe(seriesApi.create(payload))
  saving.value = false
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '保存失败'); return }
  seriesDlg.value = false
  ElMessage.success('已保存')
  loadSeries()
}

async function toggleSeriesPublic(s, v) {
  const r = await safe(seriesApi.update(s.series_id, { kind: 'project', is_public: v ? 1 : 0 }))
  if (!r.ok) { ElMessage.error('修改失败'); return }
  s.is_public = v ? 1 : 0
}

async function removeSeries(s) {
  try {
    await ElMessageBox.confirm(
      `删除系列《${s.name}》？系列里的作品不会被删除，只会变成「未归系列」。`,
      '删除项目系列',
      { type: 'warning' }
    )
  } catch (e) { return }
  const r = await safe(seriesApi.remove(s.series_id, 'project'))
  if (!r.ok) { ElMessage.error('删除失败'); return }
  if (seriesFilter.value === s.series_id) seriesFilter.value = ''
  ElMessage.success('系列已删除')
  loadSeries()
  load()
}

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
  // 「未归系列」= series_id 为空：后端 listMine 支持 no_series
  if (seriesFilter.value === 'none') params.no_series = 1
  else if (seriesFilter.value !== '') params.series_id = seriesFilter.value
  const r = await safe(uploadApi.myProjects(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  list.value = pickList(r.data)
  total.value = pickTotal(r.data)
}

function goDashboard(p) { router.push({ path: '/me/dashboard', query: { projectId: p.project_id || p.id } }) }
function resubmit(p) {
  // 老入口兼容：现在直接打开编辑弹窗
  openEdit(p)
}

/* ================= 作者自助管理 ================= */
const pidOf = (p) => p.project_id || p.id

function openEdit(p) {
  editTarget.value = p
  editForm.title = p.title || ''
  editForm.summary = p.summary || ''
  editForm.description = p.description || ''
  editVisible.value = true
}

async function saveEdit() {
  // App 内 v-model 可能漏事件：以 DOM 实际值为准
  pullFields(editForm, ['title'], { force: true })
  const title = (editForm.title || '').trim()
  if (!title) { ElMessage.warning('请填写标题'); return }
  editSaving.value = true
  const r = await safe(uploadApi.updateMine(pidOf(editTarget.value), {
    title, summary: editForm.summary || '', description: editForm.description || ''
  }))
  editSaving.value = false
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '保存失败'); return }
  editVisible.value = false
  // 就地更新列表那张卡，避免整页重刷
  const hit = list.value.find((x) => pidOf(x) === pidOf(editTarget.value))
  if (hit) { hit.title = title; hit.summary = editForm.summary || ''; hit.description = editForm.description || '' }
  ElMessage.success('已保存')
}

async function downProject(p) {
  try {
    await ElMessageBox.confirm(`确定下架《${p.title}》？下架后首页不再展示，可随时恢复上架。`, '下架作品', { type: 'warning' })
  } catch (e) { return }
  const r = await safe(uploadApi.downMine(pidOf(p)))
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '下架失败'); return }
  ElMessage.success('已下架')
  load()
}

async function restoreProject(p) {
  const r = await safe(uploadApi.restoreMine(pidOf(p)))
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '恢复失败'); return }
  ElMessage.success('已恢复上架')
  load()
}

async function deleteProject(p) {
  try {
    await ElMessageBox.confirm(
      `确定删除《${p.title}》？删除后不再展示（文件保留，可联系管理员恢复），此操作不可在页面自行撤销。`,
      '删除作品', { type: 'error', confirmButtonText: '确认删除' }
    )
  } catch (e) { return }
  deletingId.value = pidOf(p)
  const r = await safe(uploadApi.deleteMine(pidOf(p)))
  deletingId.value = null
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '删除失败'); return }
  ElMessage.success('已删除')
  load()
}

async function openCode(p) {
  codeTarget.value = p
  codeFiles.value = []
  codeCur.value = ''
  codeContent.value = ''
  codeLoading.value = true
  codeVisible.value = true
  const r = await safe(uploadApi.mySourceFiles(pidOf(p)))
  codeLoading.value = false
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '读取源码失败'); return }
  codeFiles.value = Array.isArray(r.data?.files) ? r.data.files : []
  const first = codeFiles.value.find((f) => f.editable)
  if (first) pickCodeFile(first)
}

function pickCodeFile(f) {
  codeCur.value = f.rel
  codeContent.value = f.content || ''
}

async function saveCode() {
  if (!codeCur.value) return
  codeSaving.value = true
  const r = await safe(uploadApi.saveMySource(pidOf(codeTarget.value), {
    rel: codeCur.value, content: codeContent.value
  }))
  codeSaving.value = false
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '保存失败'); return }
  const hit = codeFiles.value.find((f) => f.rel === codeCur.value)
  if (hit) hit.content = codeContent.value
  ElMessage.success('源码已保存')
}

function openSeriesFor(p) {
  joinTarget.value = p
  joinSeriesId.value = p.series_id || null
  joinNewName.value = ''
  joinNewPublic.value = false
  joinVisible.value = true
}

async function saveJoin() {
  if (!joinSeriesId.value && !(joinNewName.value || '').trim()) {
    ElMessage.warning('请选择系列或填写新系列名称'); return
  }
  joinSaving.value = true
  const payload = joinSeriesId.value
    ? { series_id: joinSeriesId.value }
    : { new_series_name: joinNewName.value.trim(), is_public: joinNewPublic.value ? 1 : 0 }
  const r = await safe(uploadApi.attachSeries(pidOf(joinTarget.value), payload))
  joinSaving.value = false
  if (!r.ok) { ElMessage.error(r.error?.response?.data?.message || '加入失败'); return }
  joinVisible.value = false
  ElMessage.success('已加入系列')
  loadSeries()
  load()
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

onMounted(async () => {
  await loadSeries()
  await load()
})
</script>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(300px, 100%), 1fr)); gap: 16px; }
.card { padding: 12px; }
.ops { display: flex; flex-wrap: nowrap; align-items: center; justify-content: flex-end; gap: 2px; margin-top: 10px; padding-top: 8px; border-top: 1px dashed var(--c-border); }
.ops :deep(.el-button + .el-button),
.ops :deep(a + .el-button),
.ops :deep(a + a) { margin-left: 0; }
.ops .op-btn.el-button { padding: 5px; margin: 0; height: 26px; min-height: 26px; }
.ops .op-btn .el-icon { font-size: 15px; }
.mb16 { margin-bottom: 16px; }
.series-tag { margin-top: 4px; }
.pub-dot { font-size: 8px; color: var(--c-success); margin-left: 2px; }
.mng-row { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--c-border); }
.mng-row:last-child { border-bottom: none; }
.vp-sub { margin: 10px 0 5px; font-size: var(--fs-sm); color: var(--c-text-2); }
.code-layout { display: flex; gap: 12px; min-height: 360px; }
.code-files {
  width: 210px; flex: none; list-style: none; margin: 0; padding: 4px;
  border: 1px solid var(--c-border); border-radius: var(--r-md); overflow-y: auto; max-height: 460px;
  background: var(--c-bg-sunken);
}
.code-files li {
  display: flex; align-items: center; justify-content: space-between; gap: 4px;
  padding: 6px 8px; border-radius: var(--r-sm); font-size: var(--fs-sm); cursor: pointer;
}
.code-files li:hover { background: var(--c-bg-card); }
.code-files li.sel { background: var(--c-primary); color: #fff; }
.code-files li.dis { opacity: .5; cursor: default; }
.code-editor { flex: 1; min-width: 0; }
.code-hint { margin-bottom: 6px; }
.code-area :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace !important;
  font-size: 12px !important; line-height: 1.6 !important; tab-size: 2;
}
@media (max-width: 767px) {
  .code-layout { flex-direction: column; }
  .code-files { width: auto; max-height: 130px; }
}
</style>
