<template>
  <div class="page">
    <div class="container">
      <el-breadcrumb class="crumb" separator="/">
        <el-breadcrumb-item to="/">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="typeLabel">{{ typeLabel }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ detail?.title || '项目' }}</el-breadcrumb-item>
      </el-breadcrumb>

      <AppState
        :loading="loading && !detail"
        :error="error"
        :empty="false"
        @retry="load"
      >
        <div v-if="detail">
          <!-- 状态带 -->
          <div v-if="statusBanner" class="status-band" :class="statusBanner.cls">
            <el-icon><component :is="statusBanner.icon" /></el-icon>
            <span>{{ statusBanner.text }}</span>
            <span v-if="statusBanner.reason" class="xs">（{{ statusBanner.reason }}）</span>
          </div>

          <div class="detail-grid">
            <!-- ============ 左主区 ============ -->
            <div class="main-col">
              <!-- 沙箱预览容器 -->
              <div class="viewer" :class="{ fullscreen: isFull }">
                <div class="viewer-bar">
                  <span class="type-pill" :style="pillStyle">{{ typeLabel }}</span>
                  <span class="ellipsis grow dim sm">{{ detail.title }}</span>
                  <el-tooltip content="重新加载" placement="bottom">
                    <button class="vbtn" @click="reloadFrame"><el-icon><Refresh /></el-icon></button>
                  </el-tooltip>
                  <el-tooltip :content="isFull ? '退出全屏 (Esc)' : '全屏运行'" placement="bottom">
                    <button class="vbtn" @click="toggleFull">
                      <el-icon><component :is="isFull ? 'Close' : 'FullScreen'" /></el-icon>
                    </button>
                  </el-tooltip>
                  <el-tooltip content="新窗口打开" placement="bottom">
                    <button class="vbtn" @click="openNew"><el-icon><TopRight /></el-icon></button>
                  </el-tooltip>
                </div>

                <div class="viewer-body">
                  <!-- 审核中 / 下架 / 结构类型：不给 iframe -->
                  <div v-if="!canRun" class="viewer-fallback">
                    <el-icon :size="38" class="dim"><component :is="fallbackIcon" /></el-icon>
                    <div class="fb-title">{{ fallbackTitle }}</div>
                    <div class="fb-desc xs dim">{{ fallbackDesc }}</div>
                  </div>
                  <iframe
                    v-else
                    :key="frameKey"
                    :src="runUrl"
                    class="frame"
                    sandbox="allow-scripts"
                    referrerpolicy="no-referrer"
                    :title="`项目预览：${detail.title}`"
                  />
                </div>
                <div class="viewer-foot xs dim">
                  沙箱隔离运行（sandbox="allow-scripts"，未授予 allow-same-origin，页面无法读取站点 cookie）
                  <template v-if="runUrl"> · 运行地址：<span class="mono">{{ runUrl }}</span></template>
                </div>
              </div>

              <!-- 标题 / 元信息 / 操作 -->
              <div class="head-block card">
                <div class="row between center wrap gap12">
                  <h1 class="p-title">{{ detail.title }}</h1>
                  <span class="type-pill" :style="pillStyle">{{ typeLabel }}</span>
                </div>
                <div class="meta sm dim">
                  @{{ detail.author?.nickname || '匿名' }}
                  · 发布于 {{ fmtTime(detail.published_at || detail.created_at) }}
                  · {{ compactNum(detail.view_count_total) }} 次浏览
                </div>
                <p class="summary clamp3">{{ detail.description || detail.summary || '作者还没有填写简介。' }}</p>

                <div class="actions">
                  <button class="act" :class="{ on: liked }" @click="toggleLike" aria-pressed="liked">
                    <el-icon><component :is="liked ? 'StarFilled' : 'Star'" /></el-icon>
                    <span>点赞</span><span class="num">{{ likeCount }}</span>
                  </button>
                  <button class="act fav" :class="{ on: favorited }" @click="toggleFav" aria-pressed="favorited">
                    <el-icon><component :is="favorited ? 'CollectionTag' : 'Collection'" /></el-icon>
                    <span>收藏</span><span class="num">{{ favCount }}</span>
                  </button>
                  <el-button :disabled="!canRun" @click="downloadSource">
                    <el-icon><Download /></el-icon>&nbsp;下载源码
                  </el-button>
                  <el-button @click="openReport">
                    <el-icon><Warning /></el-icon>&nbsp;举报
                  </el-button>
                  <el-button text @click="copyLink">
                    <el-icon><Link /></el-icon>&nbsp;复制链接
                  </el-button>
                </div>
                <div v-if="!auth.isLoggedIn" class="guest-hint sm">
                  <el-icon><InfoFilled /></el-icon>
                  当前是访客身份：可以浏览与试玩，评论/点赞/收藏/举报/下载源码需要先登录。
                  <router-link :to="`/login?redirect=${encodeURIComponent($route.fullPath)}`">去登录</router-link>
                </div>
              </div>

              <!-- ============ 评论区（常驻，不做 Tab） ============ -->
              <div class="card comment-block">
                <div class="card-hd">
                  <span>评论 <span class="dim num">{{ commentTotal }}</span></span>
                  <el-radio-group v-model="commentSort" size="small" @change="loadComments(true)">
                    <el-radio-button value="hot">最热</el-radio-button>
                    <el-radio-button value="latest">最新</el-radio-button>
                  </el-radio-group>
                </div>

                <!-- 输入框 / 登录引导条 -->
                <div v-if="auth.isLoggedIn" class="editor">
                  <el-input
                    v-model="draft"
                    type="textarea"
                    :rows="3"
                    maxlength="500"
                    show-word-limit
                    placeholder="友善发言，讲点有用的…（Ctrl/Cmd + Enter 发送）"
                    @keydown.ctrl.enter.prevent="submitComment()"
                    @keydown.meta.enter.prevent="submitComment()"
                  />
                  <div class="row between center" style="margin-top:8px">
                    <span class="xs dim">规则：楼中楼最多一层，回复会 @ 对方</span>
                    <el-button type="primary" :loading="posting" :disabled="!draft.trim()" @click="submitComment()">
                      发表评论
                    </el-button>
                  </div>
                </div>
                <div v-else class="login-strip" @click="requireLogin('评论需要登录')">
                  <el-icon><Lock /></el-icon>
                  <span>登录后即可参与评论</span>
                  <el-button type="primary" size="small" round>去登录</el-button>
                </div>

                <el-divider style="margin:16px 0" />

                <AppState
                  :loading="cLoading && !comments.length"
                  :error="cError"
                  :empty="!cLoading && !cError && !comments.length"
                  empty-text="还没有评论"
                  empty-desc="说点什么鼓励一下作者？"
                  :skeleton-count="2"
                  @retry="loadComments(true)"
                >
                  <template #skeleton>
                    <div v-for="i in 3" :key="i" style="margin-bottom:16px">
                      <div class="sk" style="width:36px;height:36px;border-radius:50%" />
                      <div class="sk" style="height:12px;width:30%;margin:8px 0" />
                      <div class="sk" style="height:12px;width:80%" />
                    </div>
                  </template>
                </AppState>

                <div class="comment-list">
                  <CommentItem
                    v-for="c in comments"
                    :key="c.comment_id"
                    :c="c"
                    :is-admin="auth.isAdmin"
                    @reply="onReply"
                    @like="onCommentLike"
                    @report="onReportComment"
                    @remove="onRemoveComment"
                  />
                </div>

                <div v-if="comments.length" class="load-more">
                  <el-button v-if="comments.length < commentTotal" text @click="loadComments(false)">
                    加载更多评论（{{ comments.length }}/{{ commentTotal }}）
                  </el-button>
                  <span v-else class="xs dim">已加载全部评论</span>
                </div>
              </div>
            </div>

            <!-- ============ 右信息栏 ============ -->
            <aside class="side-col">
              <div class="card">
                <div class="row center gap12">
                  <el-avatar :size="44" :src="detail.author?.avatar_path">
                    {{ (detail.author?.nickname || 'U').slice(0, 1) }}
                  </el-avatar>
                  <div class="grow">
                    <div class="row center gap8">
                      <router-link :to="`/users/${detail.author?.user_id}`" class="a-name ellipsis">
                        {{ detail.author?.nickname || '匿名用户' }}
                      </router-link>
                      <el-tag size="small" effect="plain" type="primary">作者</el-tag>
                    </div>
                    <div class="xs dim">已发布 {{ detail.author?.project_count ?? '—' }} 件作品</div>
                  </div>
                </div>
                <el-button
                  style="width:100%;margin-top:12px"
                  :type="following ? 'default' : 'primary'"
                  @click="toggleFollow"
                >
                  {{ following ? '已关注' : '+ 关注' }}
                </el-button>
              </div>

              <div class="card">
                <div class="card-hd">数据</div>
                <div class="stat-grid">
                  <div class="stat-cell"><div class="stat-num num">{{ compactNum(detail.view_count_total) }}</div><div class="xs dim">浏览</div></div>
                  <div class="stat-cell"><div class="stat-num num">{{ likeCount }}</div><div class="xs dim">点赞</div></div>
                  <div class="stat-cell"><div class="stat-num num">{{ favCount }}</div><div class="xs dim">收藏</div></div>
                  <div class="stat-cell"><div class="stat-num num">{{ commentTotal }}</div><div class="xs dim">评论</div></div>
                </div>
              </div>

              <div class="card">
                <div class="card-hd">信息</div>
                <div class="info-row"><span class="dim">类型</span><span>{{ typeLabel }}</span></div>
                <div class="info-row"><span class="dim">审核方式</span><span>{{ auditText }}</span></div>
                <div class="info-row"><span class="dim">状态</span><span>{{ statusText }}</span></div>
                <div class="info-row"><span class="dim">创建时间</span><span>{{ fmtDateTime(detail.created_at) }}</span></div>
                <div class="info-row"><span class="dim">更新时间</span><span>{{ fmtDateTime(detail.updated_at) }}</span></div>
                <div class="info-row"><span class="dim">文件数</span><span class="num">{{ detail.files?.length ?? '—' }}</span></div>
                <div v-if="tagsOfProject.length" class="tags-row">
                  <el-tag v-for="t in tagsOfProject" :key="t" size="small" effect="plain">{{ t }}</el-tag>
                </div>
              </div>
            </aside>
          </div>
        </div>
      </AppState>
    </div>

    <!-- 举报弹窗 -->
    <el-dialog v-model="reportVisible" title="举报" width="440px">
      <el-form label-width="70px">
        <el-form-item label="对象">
          <el-tag size="small">{{ reportTarget.label }}</el-tag>
        </el-form-item>
        <el-form-item label="原因">
          <el-select v-model="reportForm.reason_type" placeholder="请选择" style="width:100%">
            <el-option v-for="r in REASON_TYPES" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="补充">
          <el-input v-model="reportForm.detail" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="可补充说明（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="reporting" @click="submitReport">提交举报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppState from '@/components/AppState.vue'
import CommentItem from '@/components/CommentItem.vue'
import { projectApi, interactApi, userApi, categoryApi, safe } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import {
  TYPE_LABEL, REASON_TYPES, auditModeOf, compactNum, fmtTime, fmtDateTime, statusOf, pickList, pickTotal
} from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const pid = computed(() => route.params.id)
const detail = ref(null)
const loading = ref(true)
const error = ref(null)
const runUrl = ref('')
const frameKey = ref(0)
const isFull = ref(false)
const liked = ref(false)
const favorited = ref(false)
const following = ref(false)
const likeCount = ref(0)
const favCount = ref(0)
const categories = ref([])

const comments = ref([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentSort = ref('hot')
const cLoading = ref(false)
const cError = ref(null)
const draft = ref('')
const posting = ref(false)
const replyTo = ref(null)

const reportVisible = ref(false)
const reportTarget = reactive({ type: 'project', id: null, label: '本项目' })
const reportForm = reactive({ reason_type: '', detail: '' })
const reporting = ref(false)

const typeLabel = computed(() => TYPE_LABEL[detail.value?.project_type] || detail.value?.project_type || '')
const pillStyle = computed(() => {
  const t = detail.value?.project_type
  const color = t === 'scratch' ? 'var(--c-type-scratch)' : t === 'html' ? 'var(--c-type-html)' : 'var(--c-type-structure)'
  return { background: `color-mix(in srgb, ${color} 16%, transparent)`, color, borderColor: `color-mix(in srgb, ${color} 35%, transparent)` }
})
const st = computed(() => statusOf(detail.value?.status))
const statusText = computed(() => st.value.label)
const auditText = computed(() => (auditModeOf(detail.value?.project_type) === 'auto' ? '免审直发' : '人工审核'))
const tagsOfProject = computed(() => detail.value?.tags?.map((t) => t.name || t) || [])

/** 只有「已发布」且类型可运行才给 iframe */
const canRun = computed(() => {
  const d = detail.value
  if (!d) return false
  if (d.status !== 2) return false
  return ['html', 'scratch'].includes(d.project_type)
})
const fallbackIcon = computed(() => {
  const s = detail.value?.status
  if (s === 3) return 'CircleCloseFilled'
  if (s === 1) return 'Clock'
  if (detail.value?.project_type === 'structure') return 'Box'
  return 'Lock'
})
const fallbackTitle = computed(() => {
  const d = detail.value
  if (!d) return ''
  if (d.status === 1) return '审核中，暂不可公开预览'
  if (d.status === 3) return '该作品已被下架'
  if (d.status === 4) return '该作品已被拒绝，作者可修改后重新提交'
  if (d.project_type === 'structure') return '结构文件预览'
  return '此类型暂不支持在线运行'
})
const fallbackDesc = computed(() => {
  const d = detail.value
  if (d?.status === 1) return '审核通常在 1 个工作日内完成，可先去我的作品查看状态。'
  if (d?.status === 3) return d.down_reason ? `原因：${d.down_reason}` : '如需申诉请联系管理员（QQ 3254328344）。'
  if (d?.project_type === 'structure') return '一期只提供元数据卡，3D 渲染能力验证中。'
  return '可下载源码后本地运行（需要登录）。'
})
const statusBanner = computed(() => {
  const d = detail.value
  if (!d) return null
  if (d.status === 1) return { cls: 'warn', icon: 'Clock', text: '该项目正在审核中，仅作者本人与管理员可见' }
  if (d.status === 3) return { cls: 'danger', icon: 'CircleCloseFilled', text: '该项目已被下架（代码仍保留在服务器）', reason: d.down_reason }
  if (d.status === 4) return { cls: 'danger', icon: 'CircleCloseFilled', text: '该项目已被拒绝', reason: d.reject_reason }
  return null
})

/* ---------------- 数据加载 ---------------- */
async function load() {
  loading.value = true
  error.value = null
  const r = await safe(projectApi.detail(pid.value))
  loading.value = false
  if (!r.ok) {
    if (r.error?.response?.status === 404) {
      router.replace('/404')
      return
    }
    error.value = r.error || true
    return
  }
  detail.value = r.data
  likeCount.value = r.data?.like_count ?? 0
  favCount.value = r.data?.favorite_count ?? 0
  liked.value = !!r.data?.liked
  favorited.value = !!r.data?.favorited
  loadRunUrl()
  loadComments(true)
  loadFollowStatus()
  // 浏览计数（失败静默）
  projectApi.view(pid.value).catch(() => {})
}

/** 运行地址：优先用后端给的 run.url，后端没起来时退回约定路径 */
async function loadRunUrl() {
  const r = await safe(projectApi.run(pid.value))
  if (r.ok && r.data?.url) runUrl.value = r.data.url
  else {
    const origin = (import.meta.env.VITE_API_BASE || '').replace(/\/api\/v1\/?$/, '')
    runUrl.value = `${origin}/static/p/${pid.value}/index.html`
  }
}

async function loadComments(reset) {
  if (reset) { commentPage.value = 1; comments.value = [] }
  cLoading.value = true
  cError.value = null
  const r = await safe(projectApi.comments(pid.value, { page: commentPage.value, size: 20, sort: commentSort.value }))
  cLoading.value = false
  if (!r.ok) { cError.value = r.error || true; return }
  const items = pickList(r.data)
  comments.value = reset ? items : comments.value.concat(items)
  commentTotal.value = pickTotal(r.data)
}

async function loadFollowStatus() {
  const uid = detail.value?.author?.user_id
  if (!uid) return
  const r = await safe(userApi.followStatus(uid))
  if (r.ok && r.data) following.value = !!r.data.following
}

/* ---------------- 互动 ---------------- */
function requireLogin(reason) {
  if (auth.isLoggedIn) return false
  ui.promptLogin(reason || '该操作需要登录', route.fullPath)
  return true
}

async function toggleLike() {
  if (requireLogin('点赞需要登录')) return
  const prev = { liked: liked.value, n: likeCount.value }
  liked.value = !liked.value
  likeCount.value += liked.value ? 1 : -1
  const r = await safe(interactApi.likeProject(pid.value))
  if (!r.ok) {
    liked.value = prev.liked; likeCount.value = prev.n
    ElMessage.error('点赞失败，请稍后重试')
    return
  }
  if (r.data) {
    liked.value = !!r.data.liked
    likeCount.value = r.data.like_count ?? likeCount.value
  }
}

async function toggleFav() {
  if (requireLogin('收藏需要登录')) return
  const prev = { f: favorited.value, n: favCount.value }
  favorited.value = !favorited.value
  favCount.value += favorited.value ? 1 : -1
  const r = await safe(interactApi.favoriteProject(pid.value))
  if (!r.ok) {
    favorited.value = prev.f; favCount.value = prev.n
    ElMessage.error('收藏失败，请稍后重试')
    return
  }
  if (r.data) {
    favorited.value = !!r.data.favorited
    favCount.value = r.data.favorite_count ?? favCount.value
  }
  if (favorited.value) ElMessage.success('已加入收藏')
}

async function toggleFollow() {
  const uid = detail.value?.author?.user_id
  if (!uid) return
  if (requireLogin('关注需要登录')) return
  const r = await safe(interactApi.follow(uid))
  if (!r.ok) { ElMessage.error('操作失败，请稍后重试'); return }
  following.value = r.data ? !!r.data.following : !following.value
  ElMessage.success(following.value ? '已关注' : '已取消关注')
}

function downloadSource() {
  if (requireLogin('下载源码需要登录（访客只能浏览与试玩）')) return
  const files = detail.value?.files || []
  if (!files.length) { ElMessage.warning('该项目暂无源码文件'); return }
  ElMessageBox.confirm(
    '下载即表示你已阅读并同意该项目的开源协议，请勿将源码用于商业用途或二次售卖。',
    '下载确认',
    { confirmButtonText: '同意并下载', cancelButtonText: '取消' }
  ).then(() => {
    const f = files.find((x) => x.file_type === 'source') || files[0]
    ElMessage.success(`开始下载：${f.file_name || '源码'}`)
    window.open(`${(import.meta.env.VITE_API_BASE || '').replace(/\/api\/v1\/?$/, '')}/api/v1/files/${f.project_file_id}/content`, '_blank')
  }).catch(() => {})
}

function openReport() {
  if (requireLogin('举报需要登录')) return
  reportTarget.type = 'project'
  reportTarget.id = Number(pid.value)
  reportTarget.label = `项目：${detail.value?.title || pid.value}`
  reportForm.reason_type = ''
  reportForm.detail = ''
  reportVisible.value = true
}

function onReportComment(c) {
  if (requireLogin('举报评论需要登录')) return
  reportTarget.type = 'comment'
  reportTarget.id = c.comment_id
  reportTarget.label = `评论：${(c.content || '').slice(0, 20)}`
  reportForm.reason_type = ''
  reportForm.detail = ''
  reportVisible.value = true
}

async function submitReport() {
  if (!reportForm.reason_type) { ElMessage.warning('请选择举报原因'); return }
  reporting.value = true
  const r = await safe(interactApi.report({
    target_type: reportTarget.type,
    target_id: reportTarget.id,
    reason_type: reportForm.reason_type,
    detail: reportForm.detail
  }))
  reporting.value = false
  if (!r.ok) { ElMessage.error('举报提交失败，请稍后重试'); return }
  reportVisible.value = false
  ElMessage.success('举报已提交，管理员处理后会在「我的举报」中通知你（有效举报可获得正反馈积分）')
}

function onReply(c) {
  if (requireLogin('回复评论需要登录')) return
  replyTo.value = c
  draft.value = `@${c.user?.nickname || '用户'} `
  document.querySelector('.editor textarea')?.focus()
}

async function submitComment() {
  if (requireLogin('评论需要登录')) return
  const content = draft.value.trim()
  if (!content) return
  posting.value = true
  const r = await safe(interactApi.comment({
    project_id: Number(pid.value),
    content,
    parent_comment_id: replyTo.value?.comment_id || null
  }))
  posting.value = false
  if (!r.ok) { ElMessage.error('评论发送失败，请稍后重试'); return }
  draft.value = ''
  replyTo.value = null
  ElMessage.success('评论已发布')
  loadComments(true)
}

async function onCommentLike(c) {
  if (requireLogin('点赞评论需要登录')) return
  const r = await safe(interactApi.likeComment(c.comment_id))
  if (!r.ok) return
  if (r.data) { c.liked = !!r.data.liked; c.like_count = r.data.like_count }
  else { c.liked = !c.liked; c.like_count = (c.like_count || 0) + (c.liked ? 1 : -1) }
}

async function onRemoveComment(c) {
  if (!auth.isAdmin) return
  try {
    const { value } = await ElMessageBox.prompt('请填写删除理由（会写入审计日志）', '删除评论', {
      inputPlaceholder: '例如：含辱骂内容',
      inputValidator: (v) => (!!v && v.trim().length > 0) || '必须填写理由'
    })
    const { adminApi } = await import('@/api')
    const res = await safe(adminApi.removeComment(c.comment_id, { reason: value }))
    if (!res.ok) { ElMessage.error('删除失败'); return }
    ElMessage.success('评论已删除')
    loadComments(true)
  } catch (e) { /* 用户取消 */ }
}

/* ---------------- 预览交互 ---------------- */
function reloadFrame() { frameKey.value += 1; ElMessage.success('已重新加载') }
function toggleFull() { isFull.value = !isFull.value }
function openNew() {
  if (!runUrl.value) { ElMessage.warning('运行地址尚未就绪'); return }
  window.open(runUrl.value, '_blank', 'noopener')
}
function copyLink() {
  navigator.clipboard?.writeText(window.location.href).then(
    () => ElMessage.success('链接已复制'),
    () => ElMessage.warning('复制失败，请手动复制地址栏链接')
  )
}
function onKey(e) {
  if (e.key === 'Escape' && isFull.value) isFull.value = false
}
watch(isFull, (v) => { document.body.style.overflow = v ? 'hidden' : '' })

onMounted(async () => {
  window.addEventListener('keydown', onKey)
  load()
  const r = await safe(categoryApi.list())
  if (r.ok && Array.isArray(r.data)) categories.value = r.data
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.crumb { margin-bottom: 16px; }
.status-band {
  display: flex; align-items: center; gap: 8px; padding: 12px 16px; margin-bottom: 16px;
  border-radius: var(--r-md); font-size: var(--fs-sm);
}
.status-band.warn { background: rgba(245, 158, 11, .12); color: var(--c-warning); border: 1px solid rgba(245, 158, 11, .3); }
.status-band.danger { background: rgba(239, 68, 68, .12); color: var(--c-danger); border: 1px solid rgba(239, 68, 68, .3); }

.detail-grid { display: grid; grid-template-columns: 1fr 320px; gap: 24px; align-items: start; }

.viewer {
  position: relative; border: 1px solid var(--c-border); border-radius: var(--r-xl);
  overflow: hidden; background: var(--c-bg-sunken); margin-bottom: 20px;
}
.viewer.fullscreen {
  position: fixed; inset: 0; z-index: 2000; margin: 0; border-radius: 0; border: none;
  display: flex; flex-direction: column;
}
.viewer-bar {
  display: flex; align-items: center; gap: 10px; height: 38px; padding: 0 10px;
  background: var(--c-bg-card); border-bottom: 1px solid var(--c-border);
}
.viewer.fullscreen .viewer-bar { height: 44px; }
.viewer-body { position: relative; padding-top: 62.5%; }
.viewer.fullscreen .viewer-body { padding-top: 0; flex: 1; }
.frame {
  position: absolute; inset: 0; width: 100%; height: 100%; border: 0; background: #fff;
}
.viewer-fallback {
  position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center;
  justify-content: center; gap: 8px; text-align: center; padding: 24px;
}
.fb-title { font-size: var(--fs-h3); font-weight: 600; }
.fb-desc { max-width: 420px; }
.viewer-foot { padding: 8px 12px; border-top: 1px solid var(--c-border); background: var(--c-bg-card); }
.viewer.fullscreen .viewer-foot { display: none; }
.vbtn {
  width: 28px; height: 28px; border-radius: var(--r-sm); border: 1px solid var(--c-border);
  background: var(--c-bg-sunken); color: var(--c-text-2); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.vbtn:hover { color: var(--c-primary); border-color: var(--c-primary-border); }
.type-pill {
  padding: 2px 10px; border-radius: var(--r-pill); font-size: var(--fs-xs); font-weight: 600;
  border: 1px solid transparent; flex: 0 0 auto;
}

.head-block { margin-bottom: 20px; }
.p-title { font-size: var(--fs-h1); font-weight: 700; margin: 0; }
.meta { margin: 8px 0; }
.summary { color: var(--c-text-2); margin: 8px 0 16px; }
.actions { display: flex; gap: 10px; flex-wrap: wrap; }
.act {
  display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 14px;
  border-radius: var(--r-pill); border: 1px solid var(--c-border-strong);
  background: var(--c-bg-card); color: var(--c-text-1); cursor: pointer;
  font-size: var(--fs-sm); transition: all var(--dur-fast) var(--ease-out);
}
.act:hover { border-color: var(--c-primary); color: var(--c-primary); }
.act.on { background: rgba(239, 68, 68, .14); border-color: var(--c-danger); color: var(--c-danger); }
.act.fav.on { background: rgba(245, 158, 11, .14); border-color: var(--c-warning); color: var(--c-warning); }
.guest-hint {
  margin-top: 12px; padding: 8px 12px; border-radius: var(--r-md);
  background: var(--c-primary-weak); color: var(--c-text-2);
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
}

.login-strip {
  display: flex; align-items: center; gap: 10px; padding: 14px 16px; cursor: pointer;
  border: 1px dashed var(--c-primary-border); border-radius: var(--r-md);
  background: var(--c-primary-weak); color: var(--c-text-2);
}
.login-strip .el-button { margin-left: auto; }
.comment-block { margin-bottom: 24px; }
.comment-list { display: flex; flex-direction: column; }
.load-more { text-align: center; padding-top: 12px; }

.side-col { display: flex; flex-direction: column; gap: 16px; position: sticky; top: 80px; }
.a-name { font-weight: 600; color: var(--c-text-1); }
.stat-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.stat-cell { text-align: center; padding: 8px 0; background: var(--c-bg-sunken); border-radius: var(--r-md); }
.stat-num { font-size: 18px; font-weight: 700; }
.info-row { display: flex; justify-content: space-between; gap: 12px; font-size: var(--fs-sm); padding: 5px 0; }
.tags-row { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px; }

@media (max-width: 1199px) {
  .detail-grid { grid-template-columns: 1fr; }
  .side-col { position: static; flex-direction: row; overflow-x: auto; }
  .side-col .card { min-width: 260px; }
}
@media (max-width: 767px) {
  .viewer-body { padding-top: 75%; }
  .side-col { flex-direction: column; }
}
</style>
