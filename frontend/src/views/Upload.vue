<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">投稿上传</h2>
        <div class="sm dim">按向导三步走：选类型 → 填信息 → 传文件并提交</div>
      </div>
      <el-tag v-if="form.project_type" :type="isAuto ? 'success' : 'warning'" effect="plain">
        {{ isAuto ? '该类型免审，发布后立即公开' : '该类型需管理员人工审核' }}
      </el-tag>
      <el-tag v-else type="info" effect="plain">请先选择发布类型</el-tag>
    </div>

    <el-steps :active="step" finish-status="success" align-center class="mb24">
      <el-step title="选择类型" description="HTML / Scratch / 素材" />
      <el-step title="填写信息" description="标题、简介、分类" />
      <el-step title="上传并提交" description="分片上传 · 支持续传" />
    </el-steps>

    <!-- 步骤 1：类型（必须先进这一步：不同的类型走的审核与文件形态完全不同，
         不能让用户一进来就被默认成 html —— 见 restoreDraft 的注释） -->
    <div v-show="step === 0" class="card">
      <div class="type-grid">
        <div
          v-for="t in types"
          :key="t.value"
          class="tcard"
          :class="{ active: form.project_type === t.value }"
          @click="form.project_type = t.value"
        >
          <div class="t-ico" :style="{ background: t.color + '1f', color: t.color }">
            <el-icon :size="24"><component :is="t.icon" /></el-icon>
          </div>
          <div class="t-name">{{ t.label }}</div>
          <div class="xs dim">{{ t.desc }}</div>
          <el-tag size="small" :type="t.auto ? 'success' : 'warning'" effect="plain" class="t-tag">
            {{ t.auto ? '免审 · 直发' : '人工审核' }}
          </el-tag>
        </div>

        <!-- 素材不是「作品类型」，不建项目、不进审核队列，点了直接去素材库 -->
        <div
          class="tcard goto"
          @click="router.push(MATERIALS_CARD.to)"
          :title="`去${MATERIALS_CARD.label}：${MATERIALS_CARD.desc}`"
        >
          <div class="t-ico" :style="{ background: MATERIALS_CARD.color + '1f', color: MATERIALS_CARD.color }">
            <el-icon :size="24"><component :is="MATERIALS_CARD.icon" /></el-icon>
          </div>
          <div class="t-name">{{ MATERIALS_CARD.label }}</div>
          <div class="xs dim">{{ MATERIALS_CARD.desc }}</div>
          <el-tag size="small" type="info" effect="plain" class="t-tag">去上传素材</el-tag>
        </div>
      </div>
      <div class="xs dim" style="margin-top:14px">
        素材不会单独成「作品」，上传后拿到直链，写进自己的 HTML 里即可引用。
      </div>
      <div class="row" style="justify-content:flex-end;margin-top:20px">
        <el-button type="primary" :disabled="!form.project_type" @click="step = 1">下一步</el-button>
      </div>
    </div>

    <!-- 步骤 2：信息 -->
    <div v-show="step === 1" class="card">
      <el-form :model="form" label-position="top" style="max-width:640px">
        <!-- data-field：App 里 v-model 可能收不到中文输入，靠它读 DOM 真实值兜底，
             详见 utils/formDom.js。删掉这个属性输入框在手机端会「填了等于没填」 -->
        <el-form-item label="作品标题" required>
          <div data-field="title" class="fld">
            <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="例如：像素小迷宫" />
          </div>
        </el-form-item>
        <el-form-item label="一句话简介" required>
          <div data-field="summary" class="fld">
            <el-input v-model="form.summary" maxlength="80" show-word-limit placeholder="用一句话说清它是什么、怎么玩" />
          </div>
        </el-form-item>
        <el-form-item label="详细介绍">
          <div data-field="description" class="fld">
            <el-input v-model="form.description" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="玩法说明、操作方式、素材来源等" />
          </div>
        </el-form-item>
        <el-form-item label="项目系列">
          <div class="row gap8 center wrap" style="width:100%">
            <el-select
              v-model="form.series_id"
              clearable
              placeholder="可不选；同一系列的作品会成体系展示"
              style="flex:1;min-width:200px"
            >
              <el-option v-for="s in mySeries" :key="s.series_id" :label="s.name" :value="s.series_id" />
            </el-select>
            <el-input
              v-model="form.new_series_name"
              placeholder="或新建系列"
              maxlength="80"
              style="width:170px"
              data-field="series"
            />
          </div>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category_id" placeholder="请选择分类" style="width:100%">
            <el-option v-for="c in categories" :key="c.category_id" :label="c.name" :value="c.category_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入后回车创建标签（最多 5 个）"
            style="width:100%"
          >
            <el-option v-for="t in form.tags" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="row between upload-actions">
        <el-button @click="step = 0">上一步</el-button>
        <!-- 不再用 :disabled 拦住：手机端模型可能没跟上输入，按钮灰着用户只会觉得「点不动」。
             改成永远可点，点下去先读 DOM 再校验，缺什么就明确告诉他 -->
        <el-button type="primary" :loading="creating" @click="saveDraft">
          保存并继续（创建草稿）
        </el-button>
      </div>
    </div>

    <!-- 步骤 3：上传 -->
    <div v-show="step === 2">
      <div class="card mb16">
        <div class="card-hd">
          <span>上传文件</span>
          <span class="xs dim" v-if="form.project_id">草稿 ID：#{{ form.project_id }}</span>
        </div>

        <!-- HTML 类型才提供「粘贴源码」：网页端与手机端共用同一入口（二期反馈第 2 条） -->
        <div v-if="canPaste" class="src-mode">
          <button class="sm-btn" :class="{ on: srcMode === 'file' }" @click="srcMode = 'file'">
            <el-icon><UploadFilled /></el-icon> 上传文件
          </button>
          <button class="sm-btn" :class="{ on: srcMode === 'paste' }" @click="srcMode = 'paste'">
            <el-icon><EditPen /></el-icon> 粘贴源码
          </button>
        </div>

        <template v-if="canPaste && srcMode === 'paste'">
          <div class="paste-box">
            <div class="row between center" style="margin-bottom:8px">
              <span class="xs dim">把完整 HTML 粘进来（含 &lt;!DOCTYPE html&gt;），保存时自动转成单文件上传</span>
              <span class="xs dim num">{{ pasteSize }}</span>
            </div>
            <el-input
              v-model="pastedHtml"
              data-field="paste"
              type="textarea"
              :rows="14"
              resize="vertical"
              class="paste-area"
              placeholder="<!DOCTYPE html>\n<html>\n  <head>…</head>\n  <body>…</body>\n</html>"
            />
            <div class="row between center wrap gap12" style="margin-top:12px">
              <span class="xs dim">
                <template v-if="pasteWarn">{{ pasteWarn }}</template>
                <template v-else>会以 index.html 为文件名进入同一个分片上传流程</template>
              </span>
              <el-button
                type="primary"
                :loading="pasteUploading"
                @click="uploadPasted"
              >
                用这段源码上传
              </el-button>
            </div>
          </div>
        </template>

        <ChunkUploader
          v-show="!canPaste || srcMode === 'file' || pasteUploading || uploaded"
          ref="chunkRef"
          :project-id="form.project_id"
          @completed="onUploaded"
          @reset="uploaded = null"
        />
      </div>

      <div class="card mb16">
        <div class="card-hd">
          <span>项目封面</span>
          <span class="xs dim" v-if="form.cover_path">已设置</span>
        </div>
        <CoverUploader :project-id="form.project_id" :cover-path="form.cover_path" @updated="onCover" />
      </div>

      <div v-if="uploaded" class="card mb16">
        <div class="card-hd">上传结果</div>
        <div class="kv"><span class="dim">文件 ID</span><span class="mono">{{ uploaded.project_file_id }}</span></div>
        <div class="kv">
          <span class="dim">服务器存储路径</span>
          <span class="mono xs" style="word-break:break-all">{{ uploaded.storage_path || '（后端未返回）' }}</span>
        </div>
        <div v-if="uploaded.build_task_id" class="kv">
          <span class="dim">打包任务</span><span class="mono">#{{ uploaded.build_task_id }}</span>
        </div>
      </div>

      <div class="card">
        <div class="card-hd">提交</div>
        <el-alert
          :type="isAuto ? 'success' : 'warning'"
          :closable="false"
          show-icon
          :title="isAuto ? '该类型免审：提交后立即发布，首页可见' : '该类型需审核：提交后进入待审队列，管理员通过后公开'"
        />
                <el-alert
          v-if="autoSubmitted"
          type="success"
          :closable="false"
          show-icon
          style="margin-top:12px"
          :title="autoMsg"
        />
<div class="row between" style="margin-top:16px">
          <el-button @click="step = 1">上一步</el-button>
          <el-button v-if="!autoSubmitted" type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
            提交作品
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChunkUploader from '@/components/ChunkUploader.vue'
import CoverUploader from '@/components/CoverUploader.vue'
import { categoryApi, seriesApi, uploadApi, safe } from '@/api'
import { reportClientError } from '@/api/http'
import { isAutoApproved } from '@/utils/format'
import { attachFieldSync, focusField, pullFields, readDomField } from '@/utils/formDom'

const router = useRouter()
const step = ref(0)
const creating = ref(false)
const submitting = ref(false)
const autoSubmitted = ref(false)
const autoMsg = ref('')
const uploaded = ref(null)
const categories = ref([])

/* ---- 粘贴 HTML 源码（二期反馈第 2 条）：依赖 form，放到 form 定义之后 ---- */

const form = reactive({
  project_id: null,
  project_type: '',
  title: '',
  summary: '',
  description: '',
  category_id: '',
  cover_path: '',
  tags: [],
  // 五期：项目系列（可不选；填 new_series_name 会就近建一个）
  series_id: null,
  new_series_name: ''
})

/** 我的项目系列（用于「归档到现有系列」） */
const mySeries = ref([])
async function loadSeries() {
  const r = await safe(seriesApi.mine('project'))
  mySeries.value = r.ok && Array.isArray(r.data) ? r.data : []
}

/* ---- 粘贴 HTML 源码（二期反馈第 2 条） ---- */
const chunkRef = ref(null)
const srcMode = ref('file')       // file | paste
const pastedHtml = ref('')
const pasteUploading = ref(false)
/** 只有 html 类型才有「粘贴源码」；zip 包里有多个文件，粘贴表达不了 */
const canPaste = computed(() => form.project_type === 'html')
const pasteSize = computed(() => {
  const n = new Blob([pastedHtml.value || '']).size
  return n < 1024 ? `${n} B` : `${(n / 1024).toFixed(1)} KB`
})
/** 常见坑提示：粘贴的是片段而不是完整文档 */
const pasteWarn = computed(() => {
  const t = pastedHtml.value.trim()
  if (!t) return ''
  if (!/<html[\s>]/i.test(t) && !/<body[\s>]/i.test(t)) return '检测不到 <html>/<body>，粘贴的可能是片段，仍可上传'
  return ''
})

async function uploadPasted() {
  // 粘贴框同样可能收不到 v-model（App 内长按粘贴 + 中文输入法），以 DOM 为准
  const domText = readDomField('paste')
  if (domText != null && domText !== pastedHtml.value) pastedHtml.value = domText
  if (!String(pastedHtml.value || '').trim()) { ElMessage.warning('请先粘贴 HTML 源码'); return }
  if (!form.project_id) { ElMessage.warning('草稿尚未创建，请返回上一步'); return }
  pasteUploading.value = true
  try {
    await chunkRef.value?.startWithContent(pastedHtml.value, 'index.html')
  } finally {
    pasteUploading.value = false
  }
}

/* 五期：MC 结构板块下线（结构文件改到「素材」上传后引用直链） */
const types = [
  { value: 'html', label: 'HTML 网页', desc: 'zip 或 html 包，可含 JS', color: 'var(--c-type-html)', icon: 'Monitor', auto: false },
  { value: 'scratch', label: 'Scratch 作品', desc: '.sb3 / .sb2 积木项目', color: 'var(--c-type-scratch)', icon: 'Cpu', auto: true }
]

/** 第三张卡不是作品类型：素材不建项目、不进审核队列，只把用户送去素材库 */
const MATERIALS_CARD = {
  to: '/me/materials/upload',
  label: '素材',
  desc: '图片 / 视频 / 音频 / JSON，上传后复制直链',
  color: '#22c55e',
  icon: 'Picture'
}

const isAuto = computed(() => isAutoApproved(form.project_type))
// 只要有草稿(project_id)就允许点提交：即使刷新丢了上传状态也能提交，
// 缺源码时后端会返回明确错误（"请先上传项目源码后再提交"）
const canSubmit = computed(() => !!(uploaded.value?.project_file_id || form.project_id))

/* ---------------- 输入兜底（Android WebView） ----------------
   手机端 App 里中文输入法打字时 v-model 可能收不到值：输入框有字、模型是空，
   于是「保存」拿空标题去建草稿，或被 disabled 卡住，请求根本发不出去。
   这里在原生 input/textarea 上直接监听，把 DOM 真实值回填给模型；
   保存/提交前再整体拉一次，保证「看到什么就提交什么」。详见 utils/formDom.js */
const TEXT_FIELDS = ['title', 'summary', 'description']
let detachFieldSync = () => {}

function applyField(name, value) {
  if (name === 'paste') { pastedHtml.value = value; return }
  if (name in form) form[name] = value
}

/** 保存/提交前同步一次；force=true 表示以 DOM 为准（含空值） */
function syncFromDom(force) {
  const fixed = pullFields(form, TEXT_FIELDS, { force: !!force })
  const pasted = readDomField('paste')
  if (pasted != null && pasted !== pastedHtml.value) {
    pastedHtml.value = pasted
    fixed.push('paste')
  }
  return fixed
}

/* ---------------- 本地暂存：App 被系统回收后回来能接着填 ----------------
   只暂存「还没建成草稿」的填写内容；草稿一旦入库就以数据库为准，避免两份数据打架 */
const DRAFT_KEY = 'coc_upload_draft_v1'
let persistTimer = null

function persistDraft() {
  try {
    if (form.project_id) { localStorage.removeItem(DRAFT_KEY); return }
    localStorage.setItem(DRAFT_KEY, JSON.stringify({
      v: 1,
      project_type: form.project_type,
      title: form.title,
      summary: form.summary,
      description: form.description,
      category_id: form.category_id,
      tags: form.tags
    }))
  } catch (e) {}
}
function persistDraftSoon() {
  clearTimeout(persistTimer)
  persistTimer = setTimeout(persistDraft, 400)
}
function clearDraftCache() {
  try { localStorage.removeItem(DRAFT_KEY) } catch (e) {}
}
/** @returns {boolean} 是否恢复出了内容 */
function restoreDraft() {
  try {
    const d = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null')
    if (!d || typeof d !== 'object') return false
    for (const k of ['title', 'summary', 'description', 'category_id']) {
      if (d[k] != null && d[k] !== '') form[k] = d[k]
    }
    if (Array.isArray(d.tags)) form.tags = d.tags
    /* 类型只在「确实填过东西」时才恢复高亮：只点过一次卡就关掉页面留下的 project_type，
       不该让下次进来像被默认成了 html。 */
    const hasContent = !!(form.title || form.summary)
    if (hasContent && d.project_type) form.project_type = d.project_type
    /* ⚠️ 这里以前有一句 `if (form.project_type) step.value = 1`，是个隐蔽的坑：
       暂存只要求「点过一次类型卡」就会写下 project_type，于是下次进来直接被推进第 2 步，
       「选择类型」这一步永远看不到 —— 用户以为是「一进上传就默认成 html」。
       现在一律回到第 1 步：内容照常恢复，之前选过的类型保持高亮，让用户自己确认一次再往下走。 */
    return hasContent
  } catch (e) {
    return false
  }
}

async function saveDraft() {
  if (creating.value) return
  // 1) 先把 DOM 里的真实输入拉回模型（手机端 v-model 可能漏事件）
  const fixed = syncFromDom(true)
  try {
    reportClientError(
      'up-save',
      'model=' + (form.title || '').length + '/' + (form.summary || '').length +
      ' dom=' + (readDomField('title') || '').length + '/' + (readDomField('summary') || '').length +
      ' fix=' + fixed.join('|') + ' type=' + (form.project_type || '-') +
      ' token=' + (!!localStorage.getItem('coc_token'))
    )
  } catch (e) {}

  // 2) 校验（失败要把光标送到出问题的框里，否则用户不知道哪里没填上）
  const title = (form.title || '').trim()
  const summary = (form.summary || '').trim()
  form.title = title
  form.summary = summary
  if (!form.project_type) { ElMessage.warning('请先选择项目类型'); step.value = 0; return }
  if (!title) { ElMessage.warning('请先填写作品标题'); focusField('title'); return }
  if (!summary) { ElMessage.warning('请先填写一句话简介'); focusField('summary'); return }

  const payload = {
    project_type: form.project_type,
    title,
    summary,
    description: form.description || '',
    category_id: form.category_id || null,
    tags: (Array.isArray(form.tags) ? form.tags : []).slice(0, 5)
  }
  // 项目系列：选已有系列优先；没选但有新名字就让后端建（同名会复用）
  if (form.series_id) payload.series_id = form.series_id
  else if ((form.new_series_name || '').trim()) payload.new_series_name = form.new_series_name.trim()
  // 封面是在草稿建好之后由 CoverUploader 直接写库的，这里空值不能回传（否则会把已上传的封面覆盖成空）
  if (form.cover_path) payload.cover_path = form.cover_path

  creating.value = true
  let r
  try {
    r = await safe(uploadApi.createProject(payload))
  } catch (e) {
    // 连 axios 都没跑起来（极少见）：不要卡在 loading 里
    creating.value = false
    reportClientError('up-throw', String((e && (e.message || e)) || ''))
    ElMessage.error('创建草稿失败：' + String((e && (e.message || e)) || '未知错误'))
    return
  }
  creating.value = false

  if (!r.ok) {
    // 超时/断网/网关错误时后端可能已经建好了（CF 链路偶发 20s+），先对账再报错，避免重复创建
    const status = r.error?.response?.status
    const likelyLanding = !r.error?.response || r.error?.code === 'ECONNABORTED' || status >= 500
    if (likelyLanding) {
      ElMessage.info('网络较慢，正在确认草稿是否已创建…')
      const rec = await safe(uploadApi.myProjects({ page: 1, size: 5 }))
      const list = rec.ok ? (rec.data?.list || rec.data?.records || rec.data?.items || rec.data || []) : []
      const hit = Array.isArray(list)
        ? list.find((x) => x && String(x.title || '').trim() === title && Number(x.status) === 0)
        : null
      if (hit) {
        form.project_id = hit.project_id || hit.id
        clearDraftCache()
        ElMessage.success('草稿已创建（网络超时，但数据已保存）')
        step.value = 2
        return
      }
    }
    const detail = r.error?.response?.data?.message || r.error?.message || ''
    ElMessage.error('创建草稿失败' + (status ? '（' + status + '）' : '') + '：' + (detail || '请重试'))
    return
  }

  form.project_id = r.data?.project_id || r.data?.id
  if (r.data?.cover_path) form.cover_path = r.data.cover_path
  clearDraftCache()
  ElMessage.success('草稿已保存')
  step.value = 2
}

function onUploaded(res) {
  uploaded.value = res
  // 上传成功后收起粘贴面板，让用户看到结果卡片
  if (res && srcMode.value === 'paste') srcMode.value = 'file'
  autoSubmitAfterUpload()
}

/* 手机端关键修复：步进切换后页面不自动回顶，用户看到的还是上一屏，
   误以为「填不了信息」。每次步进都回顶，并等 DOM 渲染完再滚。 */
watch(step, async () => {
  await nextTick()
  window.scrollTo({ top: 0, behavior: 'auto' })
  persistDraftSoon()
})

/* 表单内容变化 → 本地暂存（App 被系统回收 / 切后台杀进程后回来还在） */
watch(form, () => persistDraftSoon(), { deep: true })

/** 上传完成即自动提交：html → status=1 待审核；scratch/structure → status=2 直接发布
 *  服务端 submit 会校验「有版本 + 有 source 文件」；已在审核/已是发布态时静默视为成功 */
async function autoSubmitAfterUpload() {
  if (!form.project_id || autoSubmitted.value) return
  const r = await safe(uploadApi.submit(form.project_id))
  if (r.ok) {
    const st = r.data?.status
    autoSubmitted.value = true
    if (st === 2) {
      autoMsg.value = '上传完成，已自动发布，刷新首页即可看到'
    } else {
      autoMsg.value = '上传完成，已自动提交审核，管理员通过后出现在首页'
    }
    ElMessage.success(autoMsg.value)
    return
  }
  const msg = r.error?.response?.data?.message || ''
  if (/审核中|已提交|只有草稿|重复/.test(msg)) {
    autoSubmitted.value = true
    autoMsg.value = msg
    return
  }
  ElMessage.warning('自动提交未成功：' + (msg || '请点下方按钮重试'))
}

function onCover(d) { form.cover_path = d?.cover_path || '' }

async function submit() {
  syncFromDom(true)
  if (!form.project_id) { ElMessage.warning('草稿尚未创建，请返回上一步'); return }
  submitting.value = true
  const r = await safe(uploadApi.submit(form.project_id))
  submitting.value = false
  if (!r.ok) {
    ElMessage.error(r.error?.response?.data?.message || '提交失败，请稍后重试')
    return
  }
  if (isAuto.value) {
    ElMessage.success('提交成功：该类型免审，已直接发布！')
  } else {
    ElMessage.success('提交成功：已进入待审队列，管理员审核通过后公开')
  }
  router.push('/me/projects')
}

/** 重新挂输入兜底：粘贴面板是 v-if 渲染的，出现/切换后要重新绑定 */
function setupFieldSync() {
  try { detachFieldSync() } catch (e) {}
  detachFieldSync = attachFieldSync(['title', 'summary', 'description', 'paste'], applyField)
  // 刚挂上时把已有 DOM 值补进模型（恢复暂存 / 浏览器自动填充都可能只写了 DOM）
  syncFromDom(false)
}

watch([srcMode, canPaste], async () => {
  await nextTick()
  setupFieldSync()
})

onMounted(async () => {
  await nextTick()
  setupFieldSync()
  if (restoreDraft()) {
    ElMessage.info('已恢复上次未提交的填写内容')
    setTimeout(() => syncFromDom(false), 60)
  }
  const r = await safe(categoryApi.list())
  if (r.ok && Array.isArray(r.data)) categories.value = r.data
  loadSeries()
})

onBeforeUnmount(() => {
  try { detachFieldSync() } catch (e) {}
  clearTimeout(persistTimer)
})
</script>

<style scoped>
.type-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.tcard {
  position: relative; padding: 20px; border: 1px solid var(--c-border); border-radius: var(--r-lg);
  cursor: pointer; transition: all var(--dur-fast) var(--ease-out); background: var(--c-bg-card);
}
.tcard:hover { border-color: var(--c-border-strong); transform: translateY(-2px); }
.tcard.active { border-color: var(--c-primary); box-shadow: var(--sh-brand); }
/* 第三张卡是「去素材库」的跳转入口，不是可选项：虚线边框 + 不做选中态，避免和类型卡混淆 */
.tcard.goto { border-style: dashed; }
.tcard.goto::after {
  content: '→'; position: absolute; top: 16px; right: 18px;
  font-size: 16px; color: var(--c-text-3); transition: transform var(--dur-fast) var(--ease-out);
}
.tcard.goto:hover::after { transform: translateX(3px); color: var(--c-text-2); }
.t-ico { width: 46px; height: 46px; border-radius: var(--r-md); display: flex; align-items: center; justify-content: center; margin-bottom: 12px; }
.t-name { font-weight: 600; font-size: var(--fs-h3); margin-bottom: 4px; }
.t-tag { margin-top: 12px; }
.mb16 { margin-bottom: 16px; }
.mb24 { margin-bottom: 24px; }
.kv { display: flex; gap: 12px; font-size: var(--fs-sm); padding: 4px 0; }
.kv .dim { min-width: 110px; }
@media (max-width: 767px) { .type-grid { grid-template-columns: 1fr; } }

/* ---- 上传方式切换（上传文件 / 粘贴源码） ---- */
.src-mode { display: flex; gap: 8px; margin-bottom: 12px; }
.sm-btn {
  flex: 1 1 0; display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  height: 38px; padding: 0 12px; border-radius: var(--r-md); cursor: pointer;
  border: 1px solid var(--c-border); background: var(--c-bg-sunken); color: var(--c-text-2);
  font-size: var(--fs-sm);
}
.sm-btn.on {
  border-color: var(--c-primary); background: var(--c-primary-weak);
  color: var(--c-primary); font-weight: 600;
}
.paste-box { border: 1px solid var(--c-border); border-radius: var(--r-lg); padding: 12px; background: var(--c-bg-sunken); }
/* data-field 包装层：撑满 el-form-item 内容宽，否则输入框会缩成半宽 */
.fld { width: 100%; }
/* 手机上等宽字体更容易对齐缩进；字号不小于 13px 否则 iOS/Android 会自动放大 */
.paste-area :deep(textarea) {
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 13px; line-height: 1.55;
}
@media (max-width: 767px) {
  .sm-btn { height: 42px; }
}
</style>
