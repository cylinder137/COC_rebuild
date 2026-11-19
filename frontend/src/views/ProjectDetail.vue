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
              <div ref="viewerRef" class="viewer" :class="{ fullscreen: isFull }">
                <div class="viewer-bar">
                  <span class="type-pill" :style="pillStyle">{{ typeLabel }}</span>
                  <span class="ellipsis grow dim sm">{{ detail.title }}</span>
                  <!-- 全屏时顶栏只留「退出」：其余控制项收进右侧栏，避免占掉作品画面 -->
                  <template v-if="!isFull">
                    <span v-if="canSwitchView" class="view-pill xs">{{ viewMode === 'desktop' ? '电脑版' : '手机版' }}</span>
                    <el-tooltip content="重新加载" placement="bottom">
                      <button class="vbtn" @click="reloadFrame"><el-icon><Refresh /></el-icon></button>
                    </el-tooltip>
                    <el-tooltip content="全屏运行（自动横屏）" placement="bottom">
                      <button class="vbtn" @click="toggleFull"><el-icon><FullScreen /></el-icon></button>
                    </el-tooltip>
                    <el-tooltip content="新窗口打开" placement="bottom">
                      <button class="vbtn" @click="openNew"><el-icon><TopRight /></el-icon></button>
                    </el-tooltip>
                  </template>
                  <!-- 全屏退出：单独放大按钮，手机端顶栏容易被刘海/手势条挡住 -->
                  <button v-else class="vbtn vbtn-exit" @click="toggleFull">
                    <el-icon><Close /></el-icon><span>退出</span>
                  </button>
                </div>

                <div ref="bodyRef" class="viewer-body" :class="{ 'is-scaled': desktopSim && !isFull }">
                  <!-- stage = 作品画面的"虚拟视口"：旋转/缩放都作用在它上面，不改 iframe 内部 -->
                  <div class="stage" :style="stageStyle">
                    <!-- 审核中 / 下架 / 结构类型：不给 iframe -->
                    <div v-if="!canRun" class="viewer-fallback">
                      <el-icon :size="38" class="dim"><component :is="fallbackIcon" /></el-icon>
                      <div class="fb-title">{{ fallbackTitle }}</div>
                      <div class="fb-desc xs dim">{{ fallbackDesc }}</div>
                    </div>
                    <!-- 电脑版：固定 1280×720 渲染后等比缩放（只在手机端出现） -->
                    <div v-else-if="desktopSim" class="frame-scale-holder" :style="holderStyle">
                      <iframe
                        :key="frameKey"
                        :src="runUrl"
                        class="frame frame-base"
                        :style="frameStyle"
                        sandbox="allow-scripts"
                        referrerpolicy="no-referrer"
                        :title="`项目预览：${detail.title}`"
                      />
                      <span class="scale-hint xs">电脑版 1280×720 · 等比缩放</span>
                    </div>
                    <!-- 手机版：直接按舞台尺寸渲染，作品自己适配（默认） -->
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
                </div>

                <!-- 全屏控制侧栏：默认收起，右边缘留把手；展开为浮层，不挤压作品画面 -->
                <template v-if="isFull">
                  <button
                    class="rail-tab"
                    :class="{ open: railOpen }"
                    :aria-label="railOpen ? '收起控制栏' : '展开控制栏'"
                    :aria-expanded="railOpen"
                    @click="railOpen = !railOpen"
                  >
                    <el-icon><component :is="railOpen ? 'ArrowRight' : 'Operation'" /></el-icon>
                  </button>
                  <aside class="rail" :class="{ open: railOpen }">
                    <button class="rail-btn" @click="reloadFrame"><el-icon><Refresh /></el-icon><span>重载</span></button>
                    <button class="rail-btn" @click="rotateView(-1)"><el-icon><RefreshLeft /></el-icon><span>左旋</span></button>
                    <button class="rail-btn" @click="rotateView(1)"><el-icon><RefreshRight /></el-icon><span>右旋</span></button>
                    <button v-if="canSwitchView" class="rail-btn" @click="toggleViewMode">
                      <el-icon><component :is="viewMode === 'desktop' ? 'Cellphone' : 'Monitor'" /></el-icon>
                      <span>{{ viewMode === 'desktop' ? '手机版' : '电脑版' }}</span>
                    </button>
                    <button class="rail-btn" @click="openNew"><el-icon><TopRight /></el-icon><span>新窗口</span></button>
                    <button class="rail-btn is-primary" @click="toggleFull"><el-icon><Close /></el-icon><span>退出</span></button>
                  </aside>
                </template>
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
                <!-- 点赞/收藏/评论这类高频操作常驻，详情默认收起 -->
                <div class="actions">
                  <button class="act" :class="{ on: liked }" @click="toggleLike" aria-pressed="liked">
                    <el-icon><component :is="liked ? 'StarFilled' : 'Star'" /></el-icon>
                    <span>点赞</span><span class="num">{{ likeCount }}</span>
                  </button>
                  <button class="act fav" :class="{ on: favorited }" @click="toggleFav" aria-pressed="favorited">
                    <el-icon><component :is="favorited ? 'CollectionTag' : 'Collection'" /></el-icon>
                    <span>收藏</span><span class="num">{{ favCount }}</span>
                  </button>
                  <button class="act" @click="detailOpen = !detailOpen" :aria-expanded="detailOpen">
                    <el-icon><component :is="detailOpen ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
                    <span>{{ detailOpen ? '收起详情' : '展开详情' }}</span>
                  </button>
                </div>

                <!-- 简介 / 下载 / 链接 / 举报 / 运行说明：默认收起 -->
                <div v-show="detailOpen" class="detail-extra">
                  <p class="summary">{{ detail.description || detail.summary || '作者还没有填写简介。' }}</p>
                  <div class="actions">
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

                  <!-- 运行视图：默认手机版（作品按设备宽度自适应），需要看作者原版分栏时切电脑版 -->
                  <div v-if="canSwitchView" class="view-mode-row">
                    <span class="sm">运行视图</span>
                    <div class="seg" role="group" aria-label="运行视图">
                      <button :class="{ on: viewMode === 'mobile' }" :aria-pressed="viewMode === 'mobile'" @click="setViewMode('mobile')">
                        <el-icon><Cellphone /></el-icon>手机版
                      </button>
                      <button :class="{ on: viewMode === 'desktop' }" :aria-pressed="viewMode === 'desktop'" @click="setViewMode('desktop')">
                        <el-icon><Monitor /></el-icon>电脑版
                      </button>
                    </div>
                    <span class="xs dim">
                      {{ viewMode === 'desktop' ? '按 1280×720 渲染后等比缩放，保留作者电脑端布局' : '按本机宽度渲染，作品自适应手机（默认）' }}
                    </span>
                  </div>
                  <div class="run-note xs dim">
                    沙箱隔离运行（sandbox="allow-scripts"，未授予 allow-same-origin，页面无法读取站点 cookie）
                    <template v-if="runUrl"> · 运行地址：<span class="mono">{{ runUrl }}</span></template>
                  </div>
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
                    data-field="cmt_draft"
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
                    <el-button type="primary" :loading="posting" @click="submitComment()">
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
                  <el-avatar :size="44" :src="fileUrl(detail.author?.avatar_path)">
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
                <el-button
                  v-if="!isAuthorMe"
                  style="width:100%;margin:8px 0 0"
                  :loading="chatting"
                  @click="openChatWithAuthor"
                >
                  <el-icon><ChatDotRound /></el-icon>&nbsp;私聊作者
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
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppState from '@/components/AppState.vue'
import CommentItem from '@/components/CommentItem.vue'
import { projectApi, interactApi, userApi, categoryApi, uploadApi, imApi, safe } from '@/api'
import { readDomField } from '@/utils/formDom'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import {
  fileUrl,
  TYPE_LABEL, REASON_TYPES, auditModeOf, compactNum, fmtTime, fmtDateTime, statusOf, pickList, pickTotal, runUrlOf
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
/** 详情栏（简介 / 下载 / 举报 / 运行说明）默认收起 */
const detailOpen = ref(false)
const liked = ref(false)
const favorited = ref(false)
const following = ref(false)
const chatting = ref(false)
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
/* ---------------- 运行视图：手机版（默认）/ 电脑版 ----------------
   手机端不再强行按电脑布局渲染：默认给 iframe 设备宽度，让作品自己走手机断点，
   这才是"在手机上打开"的正常样子。需要看作者原定的电脑端分栏时，在「展开详情」
   里切到电脑版 —— 那时才固定 1280×720 渲染再等比缩放，避免横向滚动条。
   选择记在 localStorage，同一台设备下次进来沿用。 */
const BASE_W = 1280
const BASE_H = 720
const RUN_VIEW_KEY = 'coc_run_view'
const bodyRef = ref(null)
const viewerRef = ref(null)
/* 初值给个小的：1 会让电脑版首帧按 1280 铺出去，撑宽容器后比例算不回来 */
const frameScale = ref(0.3)
/* 是否按"手机视角"处理：横屏手机的 innerWidth 会到 800+，只看宽度会把横屏误判成桌面端 */
const UA_MOBILE = typeof navigator !== 'undefined' &&
  /Android|iPhone|iPad|iPod|Mobile|HarmonyOS|OpenHarmony/i.test(navigator.userAgent)
const isNarrow = ref(UA_MOBILE || (typeof window !== 'undefined' && window.innerWidth < 768))
const viewMode = ref((() => {
  try { return localStorage.getItem(RUN_VIEW_KEY) === 'desktop' ? 'desktop' : 'mobile' } catch (e) { return 'mobile' }
})())
/** 全屏画面的旋转角：0=不旋转，90/270=CSS 模拟横屏（原生方向锁定不可用时的兜底） */
const rot = ref(0)
/** 全屏右侧控制栏是否展开（默认收起，只有把手可见） */
const railOpen = ref(false)
/** 原生 screen.orientation.lock 是否生效：生效时画面已物理横屏，不能再叠加 CSS 旋转 */
const nativeLandscape = ref(false)
let scaleRO = null

const isHtml = computed(() => detail.value?.project_type === 'html')
/** 只有窄屏 + HTML + 可运行，才需要「手机版/电脑版」这个选择 */
const canSwitchView = computed(() => isHtml.value && isNarrow.value && canRun.value)
/** 电脑版模拟：只在手机端把 1280×720 缩放塞进窄屏 */
const desktopSim = computed(() => canRun.value && isHtml.value && isNarrow.value && viewMode.value === 'desktop')

/** 舞台的"虚拟视口"：旋转后宽高互换，iframe 得到的仍是横向视口。
    box 用 ref 存（而不是每次读 DOM），旋转/缩放/尺寸变化都能触发重算。 */
const box = ref({ w: 0, h: 0 })
const stageBox = computed(() => {
  const { w, h } = box.value
  const r = rot.value
  return { w, h, vw: r ? h : w, vh: r ? w : h }
})
const stageStyle = computed(() => {
  const { w, h, vw, vh } = stageBox.value
  if (rot.value === 90) {
    return {
      position: 'absolute', left: '0', top: '0',
      width: `${vw}px`, height: `${vh}px`,
      transform: `translate(${w}px, 0) rotate(90deg)`, transformOrigin: 'top left'
    }
  }
  if (rot.value === 270) {
    return {
      position: 'absolute', left: '0', top: '0',
      width: `${vw}px`, height: `${vh}px`,
      transform: `translate(0, ${h}px) rotate(270deg)`, transformOrigin: 'top left'
    }
  }
  // 非全屏的电脑版：由舞台高度撑开页面（不再用 padding-top 撑比例）
  if (desktopSim.value && !isFull.value) {
    return { position: 'relative', width: '100%', height: `${Math.round(BASE_H * frameScale.value)}px` }
  }
  return { position: 'absolute', inset: '0', width: '100%', height: '100%' }
})
const holderStyle = computed(() => ({
  width: `${Math.round(BASE_W * frameScale.value)}px`,
  height: `${Math.round(BASE_H * frameScale.value)}px`
}))
const frameStyle = computed(() => ({
  width: `${BASE_W}px`,
  height: `${BASE_H}px`,
  transform: `scale(${frameScale.value})`,
  transformOrigin: 'top left'
}))
function updateScale() {
  const el = bodyRef.value
  if (!el) return
  const w = el.clientWidth || 0
  const h = el.clientHeight || 0
  if (!w) return
  if (box.value.w !== w || box.value.h !== h) box.value = { w, h }
  let s
  if (isFull.value) {
    // 全屏：横竖都要装得下（旋转时宽高互换）
    const vw = rot.value ? h : w
    const vh = rot.value ? w : h
    s = Math.min(vw / BASE_W, vh / BASE_H, 1)
  } else {
    s = w / BASE_W
  }
  frameScale.value = Math.max(0.2, Math.min(s, 2))
}
function syncNarrow() {
  isNarrow.value = UA_MOBILE || window.innerWidth < 768
  if (!isNarrow.value) {
    // 回到宽屏：清理手机端的临时状态，避免下次进详情页还是歪的
    rot.value = 0
    railOpen.value = false
  }
  nextTick(updateScale)
}
function setViewMode(mode) {
  if (viewMode.value === mode) return
  viewMode.value = mode
  try { localStorage.setItem(RUN_VIEW_KEY, mode) } catch (e) { /* 隐私模式忽略 */ }
  nextTick(updateScale)
}
function toggleViewMode() { setViewMode(viewMode.value === 'desktop' ? 'mobile' : 'desktop') }

const fallbackIcon = computed(() => {  const s = detail.value?.status
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

/**
 * 运行地址：必须用「绝对地址」。
 * App 端页面源是 https://localhost（Capacitor 虚拟域名），
 * 相对路径 /api/v1/run/x/ 会打到 App 自己的本地服务器，拿回应用自身的 index.html，
 * 表现为「一直转圈 / 白屏」，这就是手机端无法加载任何 HTML 项目的根因。
 */
async function loadRunUrl() {
  runUrl.value = runUrlOf(pid.value)
  const r = await safe(projectApi.run(pid.value))
  if (r.ok && r.data?.url_absolute) runUrl.value = r.data.url_absolute
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

/** 当前登录者就是作者本人？（是的话不显示「私聊作者」） */
const isAuthorMe = computed(() => {
  const mine = auth.user?.user_id
  const aid = detail.value?.author?.user_id
  return !!mine && !!aid && String(mine) === String(aid)
})

/** 私聊作者：打开/复用单聊会话并跳转消息页 */
async function openChatWithAuthor() {
  const aid = detail.value?.author?.user_id
  if (!aid) { ElMessage.warning('作者信息不完整'); return }
  if (requireLogin('私聊需要登录')) return
  chatting.value = true
  const r = await safe(imApi.openDirect({ user_id: Number(aid) }))
  chatting.value = false
  if (!r.ok) { ElMessage.error(r.error?.message || '打开会话失败，请稍后重试'); return }
  router.push({ name: 'messages', query: { session: r.data?.im_session_id } })
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
  ).then(async () => {
    const f = files.find((x) => x.file_type === 'source') || files[0]
    const name = f.file_name || `project-${pid.value}-source.zip`
    const tip = ElMessage({ message: `正在获取${name}…`, duration: 0 })
    try {
      // 用 axios 拉 blob（带上 Authorization 头）；直接 window.open 新标签页发不出登录态，会被后端 401
      const blob = await uploadApi.fileContent(f.project_file_id)
      const url = URL.createObjectURL(blob instanceof Blob ? blob : new Blob([blob]))
      const a = document.createElement('a')
      a.href = url
      a.download = name
      document.body.appendChild(a)
      a.click()
      a.remove()
      setTimeout(() => URL.revokeObjectURL(url), 15000)
      tip.close()
      ElMessage.success(`已开始下载：${name}`)
    } catch (e) {
      tip.close()
      ElMessage.error(e?.message || '下载失败，请重试')
    }
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
  // App 内 v-model 可能漏事件：以 DOM 实际值为准（详见 utils/formDom.js）
  const dom = readDomField('cmt_draft')
  if (dom != null && dom !== draft.value) draft.value = dom
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

/* 全屏：手机端进全屏就自动横屏。
   优先用原生 screen.orientation.lock（手机物理转过去，最自然），
   拿不到（不发全屏、WebView 限制、桌面浏览器）就退回 CSS 旋转，保证画面一定是横的。 */
async function toggleFull() {
  if (isFull.value) { await exitFull(); return }
  isFull.value = true
  railOpen.value = false
  await nextTick()
  updateScale()
  if (!isNarrow.value) return
  try { await viewerRef.value?.requestFullscreen?.({ navigationUI: 'hide' }) } catch (e) { /* 不支持就算了 */ }
  const ok = await lockNative('landscape')
  if (!ok) {
    // 兜底：CSS 旋转到右横屏
    nativeLandscape.value = false
    rot.value = 90
  }
  await nextTick()
  updateScale()
}

async function lockNative(orient) {
  const so = typeof screen !== 'undefined' ? screen.orientation : null
  if (!so || typeof so.lock !== 'function') return false
  try {
    await so.lock(orient)
    nativeLandscape.value = true
    rot.value = 0
    return true
  } catch (e) {
    nativeLandscape.value = false
    return false
  }
}

async function exitFull() {
  isFull.value = false
  railOpen.value = false
  rot.value = 0
  nativeLandscape.value = false
  try { screen.orientation?.unlock?.() } catch (e) { /* 桌面浏览器无此 API */ }
  try { if (document.fullscreenElement) await document.exitFullscreen() } catch (e) { /* ignore */ }
  nextTick(updateScale)
}

/** 左旋 / 右旋：原生锁定生效时切 landscape-primary/secondary，否则切 CSS 旋转角 */
async function rotateView(dir) {
  if (!isFull.value) return
  if (nativeLandscape.value) {
    const ok = await lockNative(dir > 0 ? 'landscape-primary' : 'landscape-secondary')
    if (ok) { nextTick(updateScale); return }
  }
  rot.value = dir > 0 ? 90 : 270
  railOpen.value = false
  nextTick(updateScale)
}

/** 系统侧退出全屏（返回手势/ESC/F11）时同步状态，别让页面卡在 fixed 遮罩里 */
function onFullscreenChange() {
  if (!document.fullscreenElement && isFull.value) {
    isFull.value = false
    railOpen.value = false
    rot.value = 0
    nativeLandscape.value = false
    try { screen.orientation?.unlock?.() } catch (e) { /* ignore */ }
    nextTick(updateScale)
  }
}

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
  if (e.key !== 'Escape') return
  if (railOpen.value) { railOpen.value = false; return }
  if (isFull.value) exitFull()
}
watch(isFull, (v) => {
  document.body.style.overflow = v ? 'hidden' : ''
  nextTick(updateScale)
})
watch([desktopSim, rot], () => nextTick(updateScale))

onMounted(async () => {
  window.addEventListener('keydown', onKey)
  window.addEventListener('resize', syncNarrow, { passive: true })
  document.addEventListener('fullscreenchange', onFullscreenChange)
  syncNarrow()
  if (bodyRef.value && typeof ResizeObserver !== 'undefined') {
    scaleRO = new ResizeObserver(() => updateScale())
    scaleRO.observe(bodyRef.value)
  }
  load()
  const r = await safe(categoryApi.list())
  if (r.ok && Array.isArray(r.data)) categories.value = r.data
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('resize', syncNarrow)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  scaleRO?.disconnect()
  scaleRO = null
  document.body.style.overflow = ''
  try { screen.orientation?.unlock?.() } catch (e) { /* ignore */ }
  try { if (document.fullscreenElement) document.exitFullscreen() } catch (e) { /* ignore */ }
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

.detail-grid { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 24px; align-items: start; }
/* grid 子项默认 min-width:auto，里面放 1280px 的舞台会把整列撑宽 → 缩放比例恒为 1。
   显式归零，舞台只能被容器约束。 */
.main-col { min-width: 0; }

.viewer {
  position: sticky; top: calc(var(--h-header) + 12px); z-index: 6;
  border: 1px solid var(--c-border); border-radius: var(--r-xl);
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
.viewer.fullscreen .viewer-bar { height: 48px; padding-top: env(safe-area-inset-top, 0px); }
.viewer-body { position: relative; padding-top: min(62.5%, 58vh); overflow: hidden; }
/* 手机端电脑版：高度由内联 style 按 scale 给出，不再用 padding-top 撑 */
.viewer-body.is-scaled { padding-top: 0; background: #fff; }
/* 舞台：作品画面的虚拟视口。旋转/缩放都作用在它身上，iframe 内部不做任何改写 */
.stage {
  display: flex; align-items: center; justify-content: center;
  background: #fff; overflow: hidden;
}
.frame-scale-holder { position: relative; overflow: hidden; flex: 0 0 auto; }
.frame-base { left: 0; top: 0; }
.scale-hint {
  position: absolute; right: 8px; bottom: 6px; padding: 1px 8px;
  border-radius: var(--r-pill); background: rgba(0, 0, 0, .45); color: #fff;
  pointer-events: none; opacity: .75;
}
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
.vbtn {
  width: 28px; height: 28px; border-radius: var(--r-sm); border: 1px solid var(--c-border);
  background: var(--c-bg-sunken); color: var(--c-text-2); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.vbtn:hover { color: var(--c-primary); border-color: var(--c-primary-border); }
/* 全屏退出：按钮加大、默认显示文字，避免手机上顶栏被刘海/手势区挡住点不到 */
.vbtn-exit {
  width: auto; min-width: 34px; height: 34px; padding: 0 12px; gap: 6px;
  background: var(--c-primary); border-color: var(--c-primary); color: #fff; font-size: var(--fs-sm);
}
.vbtn-exit:hover { color: #fff; background: var(--c-primary-hover); border-color: var(--c-primary-hover); }
.detail-extra { margin-top: 14px; padding-top: 14px; border-top: 1px dashed var(--c-border); }
.run-note { margin-top: 12px; line-height: 1.7; }
.viewer.fullscreen .vbtn-exit { height: 38px; }

/* 顶栏里的「当前视图」小标 */
.view-pill {
  padding: 1px 8px; border-radius: var(--r-pill); white-space: nowrap;
  border: 1px solid var(--c-border); background: var(--c-bg-sunken); color: var(--c-text-3);
}

/* 全屏右侧控制栏：默认收起，只在右边缘露一个把手；展开是浮层，不挤压画面 */
.rail-tab {
  position: absolute; right: 0; top: 50%; transform: translateY(-50%);
  z-index: 5; width: 26px; height: 68px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid rgba(255, 255, 255, .18); border-right: 0;
  border-radius: var(--r-md) 0 0 var(--r-md);
  background: rgba(18, 18, 22, .72); color: #fff; cursor: pointer;
  opacity: .5; transition: opacity var(--dur-fast) var(--ease-out), right var(--dur-fast) var(--ease-out);
}
.rail-tab:hover, .rail-tab.open { opacity: 1; }
.rail-tab.open { right: 70px; }
.rail {
  position: absolute; right: 0; top: 50%;
  transform: translate(100%, -50%); z-index: 6;
  display: flex; flex-direction: column; gap: 4px;
  padding: 8px 6px; border-radius: var(--r-md) 0 0 var(--r-md);
  background: rgba(18, 18, 22, .9); backdrop-filter: blur(8px);
  transition: transform var(--dur-fast) var(--ease-out);
  padding-right: calc(6px + env(safe-area-inset-right, 0px));
}
.rail.open { transform: translate(0, -50%); }
.rail-btn {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  width: 52px; padding: 7px 0; border: 0; border-radius: var(--r-sm);
  background: transparent; color: #fff; font-size: 11px; line-height: 1.2; cursor: pointer;
}
.rail-btn .el-icon { font-size: 17px; }
.rail-btn:hover { background: rgba(255, 255, 255, .14); }
.rail-btn.is-primary { background: var(--c-primary); }
.rail-btn.is-primary:hover { background: var(--c-primary-hover); }

/* 详情里的「运行视图」切换 */
.view-mode-row {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  margin: 14px 0 4px; padding: 10px 12px;
  border: 1px dashed var(--c-border); border-radius: var(--r-md); background: var(--c-bg-sunken);
}
.seg { display: inline-flex; border: 1px solid var(--c-border-strong); border-radius: var(--r-pill); overflow: hidden; }
.seg button {
  display: inline-flex; align-items: center; gap: 4px;
  height: 30px; padding: 0 14px; border: 0; background: transparent;
  color: var(--c-text-2); font-size: var(--fs-sm); cursor: pointer;
  transition: background var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}
.seg button.on { background: var(--c-primary); color: #fff; }
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
  /* 手机端不做吸顶：默认手机版后画面高度占比更小，吸顶只会盖住下面的信息/按钮 */
  .viewer { position: static; }
  .viewer-body { padding-top: min(75%, 52vh); }
  .side-col { flex-direction: column; }
}
</style>
