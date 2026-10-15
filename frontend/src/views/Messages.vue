<template>
  <div class="msg-wrap">
    <!-- 会话列表 -->
    <aside class="conv" :class="{ show: !active || isNarrow === false }">
      <div class="conv-hd">
        <el-radio-group v-model="tab" size="small" @change="filterConvs">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="1">单聊</el-radio-button>
          <el-radio-button value="2">项目群</el-radio-button>
        </el-radio-group>
        <el-tooltip :content="wsState.text" placement="bottom">
          <span class="ws-dot" :class="wsState.cls" />
        </el-tooltip>
      </div>
      <AppState
        :loading="loadingConvs && !convs.length"
        :error="convError"
        :empty="!loadingConvs && !convError && !convs.length"
        empty-text="暂无会话"
        empty-desc="在项目详情页发起讨论，或由管理员拉你进项目群。"
        :skeleton-count="4"
        @retry="loadConvs"
      >
        <div class="conv-list">
          <div
            v-for="c in filteredConvs"
            :key="keyOf(c)"
            class="conv-item"
            :class="{ active: isActive(c) }"
            @click="openConv(c)"
          >
            <div class="ci-avatar">
              <el-avatar :size="38" :src="c.avatar_path">{{ (c.title || 'U').slice(0, 1) }}</el-avatar>
              <el-badge v-if="c.unread" :value="c.unread" :max="99" class="ci-badge" />
            </div>
            <div class="grow">
              <div class="row between center gap8">
                <span class="ci-title ellipsis">{{ c.title || c.nickname || '会话' }}</span>
                <span class="xs dim">{{ c.last_message_at ? fmtTime(c.last_message_at) : '' }}</span>
              </div>
              <div class="xs dim ellipsis">
                <el-tag v-if="c.kind === 2" size="small" effect="plain" type="primary">项目群</el-tag>
                <el-tag v-else size="small" effect="plain">单聊</el-tag>
                {{ c.last_message_preview || '暂无消息' }}
              </div>
            </div>
          </div>
        </div>
      </AppState>
    </aside>

    <!-- 消息区 -->
    <section class="chat">
      <template v-if="active">
        <div class="chat-hd">
          <el-button class="hide-lg" text @click="active = null"><el-icon><Back /></el-icon></el-button>
          <span class="ch-title">{{ active.title || active.nickname || '会话' }}</span>
          <el-tag v-if="active.kind === 2" size="small" effect="plain" type="primary">项目群</el-tag>
          <span class="grow" />
          <el-button text size="small" @click="loadMessages(true)">
            <el-icon><Refresh /></el-icon>&nbsp;刷新
          </el-button>
        </div>

        <div ref="scrollEl" class="chat-body" @scroll="onScroll">
          <div v-if="loadingMore" class="xs dim" style="text-align:center;padding:8px">加载更早的消息…</div>
          <div v-else-if="!hasMore && messages.length" class="xs dim" style="text-align:center;padding:8px">
            没有更早的消息了
          </div>
          <div v-if="!messages.length && !loadingMsgs" class="empty-chat xs dim">还没有消息，打个招呼吧 👋</div>
          <div v-for="m in messages" :key="m.im_message_id || m.client_msg_id" class="bubble-row" :class="{ mine: m.sender_id === myId }">
            <el-avatar :size="28" :src="m.sender?.avatar_path">{{ (m.sender?.nickname || 'U').slice(0, 1) }}</el-avatar>
            <div class="bubble-wrap">
              <div class="xs dim bubble-meta">
                {{ m.sender?.nickname || '用户' }} · {{ fmtTime(m.created_at) }}
              </div>
              <div class="bubble">{{ m.content }}</div>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="draft"
            placeholder="输入消息，Enter 发送（Shift + Enter 换行）"
            @keydown.enter.exact.prevent="send"
          />
          <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send">发送</el-button>
        </div>
      </template>
      <div v-else class="chat-placeholder">
        <el-icon :size="42" class="dim"><ChatDotRound /></el-icon>
        <div class="dim">选择左侧会话开始聊天</div>
        <div class="xs dim">支持单聊与项目群；每页 20 条，滚动到顶部自动加载更早消息</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import AppState from '@/components/AppState.vue'
import { imApi, safe, WS_BASE, getToken } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { fmtTime, pickList } from '@/utils/format'

const auth = useAuthStore()
const ui = useUiStore()

const convs = ref([])
const tab = ref('')
const active = ref(null)
const messages = ref([])
const draft = ref('')
const cursor = ref(null)
const hasMore = ref(true)
const loadingConvs = ref(false)
const convError = ref(null)
const loadingMsgs = ref(false)
const loadingMore = ref(false)
const sending = ref(false)
const scrollEl = ref(null)
const isNarrow = ref(window.innerWidth < 992)

const myId = computed(() => auth.user?.user_id)

/* ---------- WebSocket：连不上就降级为轮询/手动刷新 ---------- */
const wsState = ref({ text: '实时连接中…', cls: 'connecting' })
let ws = null
let wsRetry = 0
let pollTimer = null

function connectWs() {
  const token = getToken()
  if (!token) { wsState.value = { text: '未登录，无法建立实时连接', cls: 'off' }; return }
  try {
    ws = new WebSocket(`${WS_BASE}/ws?token=${encodeURIComponent(token)}`)
  } catch (e) {
    fallbackPolling('实时连接不可用，已切换为轮询')
    return
  }
  ws.onopen = () => {
    wsRetry = 0
    wsState.value = { text: '实时连接已建立', cls: 'on' }
    ws.send(JSON.stringify({ type: 'hello' }))
    stopPolling()
  }
  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data)
      if (msg.type === 'message' && msg.message) onIncoming(msg.session_id, msg.message)
      if (msg.type === 'ping') ws.send(JSON.stringify({ type: 'pong' }))
    } catch (e) { /* 忽略非 JSON */ }
  }
  ws.onclose = () => {
    wsState.value = { text: '实时连接已断开，已切换为轮询', cls: 'off' }
    if (wsRetry++ < 3) setTimeout(connectWs, 3000 * wsRetry)
    else fallbackPolling('实时连接不可用，已切换为轮询')
  }
  ws.onerror = () => { wsState.value = { text: '实时连接异常（后端 WS 可能未启动）', cls: 'off' } }
}

function fallbackPolling(msg) {
  if (pollTimer) return
  wsState.value = { text: '实时连接不可用，已切换为 15s 轮询', cls: 'warn' }
  pollTimer = setInterval(() => { loadConvs(true); if (active.value) loadMessages(false) }, 15000)
}
function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } }

function onIncoming(sessionId, msg) {
  if (active.value && String(sessionId) === String(active.value.im_session_id || active.value.id)) {
    messages.value.push(msg)
    nextTick(scrollBottom)
    imApi.read(active.value.kind, active.value.im_session_id || active.value.id).catch(() => {})
  } else {
    const c = convs.value.find((x) => String(x.im_session_id || x.id) === String(sessionId))
    if (c) c.unread = (c.unread || 0) + 1
    ui.unreadTotal += 1
  }
}

/* ---------- 会话 ---------- */
const filteredConvs = computed(() => (tab.value ? convs.value.filter((c) => String(c.kind) === tab.value) : convs.value))

function keyOf(c) { return `${c.kind}-${c.im_session_id || c.id}` }
function isActive(c) { return active.value && keyOf(active.value) === keyOf(c) }

async function loadConvs(silent) {
  if (!silent) loadingConvs.value = true
  convError.value = null
  const r = await safe(imApi.conversations())
  loadingConvs.value = false
  if (!r.ok) { if (!silent) convError.value = r.error || true; return }
  convs.value = pickList(r.data)
}

async function openConv(c) {
  active.value = c
  c.unread = 0
  cursor.value = null
  hasMore.value = true
  await loadMessages(true)
  imApi.read(c.kind, c.im_session_id || c.id).catch(() => {})
}

/** cursor 分页，每页 20 条 */
async function loadMessages(reset) {
  if (!active.value) return
  if (reset) { messages.value = []; cursor.value = null; hasMore.value = true; loadingMsgs.value = true }
  const id = active.value.im_session_id || active.value.id
  const params = { limit: 20 }
  if (!reset && cursor.value) params.cursor = cursor.value
  const r = await safe(imApi.messages(active.value.kind, id, params))
  loadingMsgs.value = false
  loadingMore.value = false
  if (!r.ok) return
  const items = pickList(r.data)
  const nextCursor = r.data?.next_cursor ?? r.data?.cursor ?? null
  if (reset) {
    messages.value = items.slice().reverse()
    await nextTick(); scrollBottom()
  } else {
    const el = scrollEl.value
    const prevH = el?.scrollHeight || 0
    messages.value = items.slice().reverse().concat(messages.value)
    await nextTick()
    if (el) el.scrollTop = el.scrollHeight - prevH
  }
  cursor.value = nextCursor
  hasMore.value = items.length >= 20 && !!nextCursor
}

function onScroll() {
  const el = scrollEl.value
  if (!el || loadingMore.value || !hasMore.value) return
  if (el.scrollTop < 40) { loadingMore.value = true; loadMessages(false) }
}
function scrollBottom() {
  const el = scrollEl.value
  if (el) el.scrollTop = el.scrollHeight
}

async function send() {
  const content = draft.value.trim()
  if (!content || !active.value) return
  sending.value = true
  const clientMsgId = `c-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const id = active.value.im_session_id || active.value.id
  const r = await safe(imApi.send(active.value.kind, id, { content, client_msg_id: clientMsgId }))
  sending.value = false
  if (!r.ok) { ElMessage.error('发送失败，请稍后重试'); return }
  draft.value = ''
  const saved = r.data?.message || r.data
  if (saved && typeof saved === 'object' && saved.content) messages.value.push(saved)
  else messages.value.push({ im_message_id: clientMsgId, sender_id: myId.value, sender: auth.user, content, created_at: new Date().toISOString() })
  await nextTick(); scrollBottom()
}

function onResize() { isNarrow.value = window.innerWidth < 992 }

onMounted(async () => {
  window.addEventListener('resize', onResize)
  await loadConvs()
  connectWs()
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  stopPolling()
  try { ws?.close() } catch (e) {}
})
</script>

<style scoped>
.msg-wrap {
  display: grid; grid-template-columns: 320px 1fr; gap: 0;
  height: calc(100vh - var(--h-header) - 48px);
  border: 1px solid var(--c-border); border-radius: var(--r-lg); overflow: hidden;
  background: var(--c-bg-card);
}
.conv { border-right: 1px solid var(--c-border); display: flex; flex-direction: column; min-height: 0; }
.conv-hd {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 10px 12px; border-bottom: 1px solid var(--c-border);
}
.ws-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--c-text-3); flex: 0 0 auto; }
.ws-dot.on { background: var(--c-success); box-shadow: 0 0 0 3px rgba(34, 197, 94, .18); }
.ws-dot.warn { background: var(--c-warning); }
.ws-dot.off { background: var(--c-danger); }
.ws-dot.connecting { background: var(--c-info); animation: pulse 1.2s infinite; }
@keyframes pulse { 0%, 100% { opacity: .5 } 50% { opacity: 1 } }
.conv-list { overflow-y: auto; flex: 1; }
.conv-item { display: flex; gap: 10px; padding: 12px; cursor: pointer; border-bottom: 1px solid var(--c-border); }
.conv-item:hover { background: var(--c-bg-hover); }
.conv-item.active { background: var(--c-primary-weak); }
.ci-avatar { position: relative; }
.ci-badge { position: absolute; top: -4px; right: -4px; }
.ci-title { font-size: var(--fs-sm); font-weight: 600; }
.chat { display: flex; flex-direction: column; min-height: 0; }
.chat-hd { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-bottom: 1px solid var(--c-border); }
.ch-title { font-weight: 600; }
.chat-body { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 14px; }
.bubble-row { display: flex; gap: 10px; }
.bubble-row.mine { flex-direction: row-reverse; }
.bubble-wrap { max-width: 68%; }
.bubble-meta { margin-bottom: 4px; }
.mine .bubble-meta { text-align: right; }
.bubble {
  padding: 9px 13px; border-radius: var(--r-lg); background: var(--c-bg-sunken);
  border: 1px solid var(--c-border); word-break: break-word; white-space: pre-wrap;
}
.mine .bubble { background: var(--c-primary-weak); border-color: var(--c-primary-border); }
.chat-input { display: flex; gap: 10px; padding: 12px; border-top: 1px solid var(--c-border); }
.chat-placeholder {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px;
}
.empty-chat { text-align: center; padding: 24px; }
.hide-lg { display: none; }
@media (max-width: 991px) {
  .msg-wrap { grid-template-columns: 1fr; }
  .conv { display: none; }
  .conv.show { display: flex; }
  .hide-lg { display: inline-flex; }
}
</style>
