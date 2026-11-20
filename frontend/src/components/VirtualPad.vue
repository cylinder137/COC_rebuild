<template>
  <div
    ref="padEl"
    class="vpad"
    :class="{ 'is-edit': editing }"
    @pointerdown.self="onBlankDown"
    @contextmenu.prevent
  >
    <!-- ---------- 两个摇杆（左：WASD / 右：方向键，映射互不相干） ---------- -->
    <div
      v-for="j in joyNodes"
      :key="j.id"
      class="vjoy"
      :class="{ sel: editing && selId === j.id, live: joyPid[j.id] != null }"
      :style="nodeStyle(j)"
      @pointerdown="onDown($event, j, 'joy')"
      @pointermove="onMove($event, j, 'joy')"
      @pointerup="onUp($event, j, 'joy')"
      @pointercancel="onUp($event, j, 'joy')"
    >
      <span class="vjoy-base" />
      <span class="vjoy-knob" :style="knobStyle(j)" />
      <span class="vjoy-tag">{{ j.name }}<i>{{ j.desc }}</i></span>
    </div>

    <!-- ---------- 自定义按键 ---------- -->
    <div
      v-for="b in scheme.buttons"
      :key="b.id"
      class="vkbtn"
      :class="{ sel: editing && selId === b.id, on: !!pressed[b.field] }"
      :style="nodeStyle(b)"
      @pointerdown="onDown($event, b, 'btn')"
      @pointermove="onMove($event, b, 'btn')"
      @pointerup="onUp($event, b, 'btn')"
      @pointercancel="onUp($event, b, 'btn')"
    >
      <span class="vkbtn-label">{{ b.label }}</span>
    </div>

    <!-- ---------- 设置面板（只在设置模式出现） ---------- -->
    <aside v-if="editing" class="vpad-panel" @pointerdown.stop>
      <div class="vp-hd">
        <strong>虚拟按键设置</strong>
        <button class="vp-done" @click="finish">完成</button>
      </div>

      <p class="vp-tip">在画面上拖动可移动位置；点选后可调透明度与尺寸。设置模式下按键不会触发游戏。</p>

      <div class="vp-row">
        <span>左摇杆 <i>WASD</i></span>
        <el-switch v-model="scheme.joyA.show" size="small" />
      </div>
      <div class="vp-row">
        <span>右摇杆 <i>方向键</i></span>
        <el-switch v-model="scheme.joyB.show" size="small" />
      </div>

      <div class="vp-sub">新建按键</div>
      <div class="vp-add">
        <input
          v-model="newField"
          class="vp-input"
          data-field="vk_field"
          maxlength="12"
          placeholder="a / 1 / space / enter / ctrl"
          @keyup.enter="addNew"
        />
        <el-button size="small" type="primary" @click="addNew">添加</el-button>
      </div>
      <div class="vp-chips">
        <button v-for="f in QUICK_FIELDS" :key="f" @click="addField(f)">
          {{ labelOf(f) }}<i>{{ f }}</i>
        </button>
      </div>

      <div v-if="scheme.buttons.length" class="vp-sub">已有按键（{{ scheme.buttons.length }}）</div>
      <ul v-if="scheme.buttons.length" class="vp-list">
        <li
          v-for="b in scheme.buttons"
          :key="b.id"
          :class="{ sel: selId === b.id }"
          @click="selId = b.id"
        >
          <span class="nm">{{ b.label }}</span>
          <code>{{ b.field }}</code>
          <button class="del" title="删除" @click.stop="removeBtn(b.id)">×</button>
        </li>
      </ul>

      <div v-if="selected" class="vp-sliders">
        <div class="vp-slider">
          <span>透明度 <b>{{ Math.round(selected.opacity * 100) }}%</b></span>
          <el-slider v-model="selected.opacity" :min="0.15" :max="1" :step="0.05" size="small" :show-tooltip="false" />
        </div>
        <div class="vp-slider">
          <span>尺寸 <b>{{ Math.round(selected.size) }}px</b></span>
          <el-slider v-model="selected.size" :min="44" :max="180" :step="4" size="small" :show-tooltip="false" />
        </div>
      </div>
      <p v-else class="vp-tip">未选中元素：点一下摇杆或按键即可调它的透明度 / 尺寸。</p>

      <button class="vp-reset" @click="resetScheme">恢复默认布局</button>
    </aside>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { readDomField } from '@/utils/formDom'
import {
  JOY_DEFS, QUICK_FIELDS, labelOf, loadScheme, saveScheme, normalizeField
} from '@/utils/vkeys'

const props = defineProps({
  /** 设置模式：拖动/点选不触发按键 */
  editing: { type: Boolean, default: false },
  /** 画面旋转角（0/90/270），拖动 delta 要从屏幕坐标换算回舞台坐标 */
  rot: { type: Number, default: 0 },
  /** (field, down) => void —— 由父组件 postMessage 给 iframe 内的桥 */
  send: { type: Function, required: true }
})
const emit = defineEmits(['update:editing', 'change'])

const padEl = ref(null)
const scheme = reactive(loadScheme())
const selId = ref('')
const newField = ref('')
/** 每个摇杆各自记住自己的 pointerId：双指同时操作时不能共用一个状态 */
const joyPid = reactive({ joyA: null, joyB: null })
const pressed = reactive({})
const knobs = reactive({ joyA: { x: 0, y: 0 }, joyB: { x: 0, y: 0 } })

/** 每个摇杆当前按下的方向集合，用于算差集 */
const joyHeld = { joyA: new Set(), joyB: new Set() }
/** 同一个字段可能被多个控件按住，用计数避免早松 */
const counts = Object.create(null)

/**
 * 方波微调（PWM 模拟模拟量）：键盘事件只有按下/抬起两个状态，
 * 小幅度推杆时用「按键方波」模拟——占空比随推杆幅度连续变化，
 * 推得越轻按键按下的时间占比越低，角色就以对应慢速微调移动。
 * PWM 频率 60Hz：每帧为一个 PWM 周期，用 Bresenham 误差扩散
 * 累加器把连续占空比摊到帧上，无离散的模式切换、无相位拍频抖动。
 */
const SW = { dz: 0.16, full: 0.82, freq: 60 }
const wave = {
  joyA: { active: false, m: 0, want: new Set(), acc: 0 },
  joyB: { active: false, m: 0, want: new Set(), acc: 0 }
}
/** 摇杆吸附：本次手势里底座从哪儿弹回（null = 没吸附过） */
const joyHome = { joyA: null, joyB: null }

const joyNodes = computed(() =>
  ['joyA', 'joyB']
    .map((id) => ({ ...JOY_DEFS[id], ...scheme[id], id }))
    .filter((j) => j.show || props.editing)
)

const selected = computed(() => {
  if (!selId.value) return null
  if (selId.value === 'joyA') return scheme.joyA
  if (selId.value === 'joyB') return scheme.joyB
  return scheme.buttons.find((b) => b.id === selId.value) || null
})

function labelFor(b) { return b.label || labelOf(b.field) }

function nodeStyle(n) {
  return {
    left: `${n.x}%`,
    top: `${n.y}%`,
    width: `${n.size}px`,
    height: `${n.size}px`,
    opacity: String(n.opacity)
  }
}
function knobStyle(j) {
  const k = knobs[j.id] || { x: 0, y: 0 }
  return { transform: `translate(calc(-50% + ${k.x}px), calc(-50% + ${k.y}px))` }
}

/* ---------------- 按键收发 ---------------- */
function press(field) {
  const f = normalizeField(field)
  if (!f) return
  counts[f] = (counts[f] || 0) + 1
  pressed[f] = true
  if (counts[f] === 1) props.send(f, true)
}
function release(field) {
  const f = normalizeField(field)
  if (!f || !counts[f]) return
  counts[f] -= 1
  if (counts[f] <= 0) {
    delete counts[f]
    pressed[f] = false
    props.send(f, false)
  }
}
/** 退出设置 / 退出全屏 / 页面失焦时兜底：把所有还按着的键松开，避免角色一直走 */
function releaseAll() {
  for (const f of Object.keys(counts)) {
    counts[f] = 1
    release(f)
  }
  for (const id of ['joyA', 'joyB']) {
    joyHeld[id].clear()
    knobs[id] = { x: 0, y: 0 }
    joyPid[id] = null
    wave[id].active = false
    // 弹回被吸附的摇杆底座
    const t = id === 'joyA' ? scheme.joyA : scheme.joyB
    const home = joyHome[id]
    if (home && t) { t.x = home.x; t.y = home.y }
    joyHome[id] = null
  }
}

/* ---------------- 拖动 / 摇杆 ---------------- */
const drag = ref(null)

/** 屏幕位移 → 舞台位移（旋转后坐标轴互换） */
function toStage(dx, dy) {
  if (props.rot === 90) return [dy, -dx]
  if (props.rot === 270) return [-dy, dx]
  return [dx, dy]
}
function findNode(id) {
  if (id === 'joyA') return scheme.joyA
  if (id === 'joyB') return scheme.joyB
  return scheme.buttons.find((b) => b.id === id) || null
}
const clamp = (v, a, b) => Math.min(b, Math.max(a, v))

function onDown(e, node, kind) {
  if (e.pointerType === 'mouse' && e.button > 0) return
  e.preventDefault()
  e.stopPropagation()
  selId.value = node.id
  const el = e.currentTarget
  try { el.setPointerCapture(e.pointerId) } catch (err) { /* 老 WebView 忽略 */ }

  if (props.editing) {
    // 设置模式：拖位置，不触发按键
    drag.value = { id: node.id, pid: e.pointerId, sx: e.clientX, sy: e.clientY, bx: node.x, by: node.y }
    return
  }
  if (kind === 'btn') {
    press(node.field)
    drag.value = { id: node.id, pid: e.pointerId, sx: e.clientX, sy: e.clientY, press: true }
    return
  }
  joyPid[node.id] = e.pointerId
  // 同一摇杆被第二根手指按下：先松掉上一根手指留下的方向键，避免卡住
  if (joyHeld[node.id].size) {
    for (const f of joyHeld[node.id]) release(f)
    joyHeld[node.id].clear()
  }
  wave[node.id].active = false
  wave[node.id].m = 0
  wave[node.id].acc = 0

  // 摇杆吸附：热区内偏心按下时底座贴向手指落点，拇指不必精确对准圆心；
  // 松手后底座弹回原位（joyHome 记住来时的地方）
  const r = el.getBoundingClientRect()
  const [sdx, sdy] = toStage(e.clientX - (r.left + r.width / 2), e.clientY - (r.top + r.height / 2))
  if (!joyHome[node.id] && Math.hypot(sdx, sdy) > node.size * 0.22) {
    joyHome[node.id] = { x: node.x, y: node.y }
    const pad = padEl.value
    if (pad) {
      const w = pad.offsetWidth || 1
      const h = pad.offsetHeight || 1
      const t = findNode(node.id)
      if (t) {
        t.x = clamp(t.x + (sdx / w) * 100, 5, 95)
        t.y = clamp(t.y + (sdy / h) * 100, 5, 95)
      }
    }
    // 底座位置变了，等渲染完再以新圆心算摇杆向量
    nextTick(() => { if (joyPid[node.id] === e.pointerId) updateJoy(e, node, el) })
  } else {
    updateJoy(e, node, el)
  }
}

function onMove(e, node, kind) {
  if (drag.value && drag.value.pid === e.pointerId) {
    if (drag.value.press) return
    const pad = padEl.value
    if (!pad) return
    const [sdx, sdy] = toStage(e.clientX - drag.value.sx, e.clientY - drag.value.sy)
    const w = pad.offsetWidth || 1
    const h = pad.offsetHeight || 1
    const t = findNode(drag.value.id)
    if (!t) return
    t.x = clamp(drag.value.bx + (sdx / w) * 100, 2, 98)
    t.y = clamp(drag.value.by + (sdy / h) * 100, 2, 98)
    return
  }
  if (kind === 'joy' && joyPid[node.id] === e.pointerId && !props.editing) updateJoy(e, node)
}

function onUp(e, node, kind) {
  if (drag.value && drag.value.pid === e.pointerId) {
    if (drag.value.press) release(node.field)
    drag.value = null
  }
  if (kind === 'joy' && joyPid[node.id] === e.pointerId) {
    joyPid[node.id] = null
    endJoy(node)
  }
}

/** el 可显式传入：nextTick 回调里 e.currentTarget 已被置空，必须用当时捕获的元素 */
function updateJoy(e, node, elIn) {
  const el = elIn || e.currentTarget
  if (!el) return
  const r = el.getBoundingClientRect()
  const cx = r.left + r.width / 2
  const cy = r.top + r.height / 2
  let [sx, sy] = toStage(e.clientX - cx, e.clientY - cy)
  const R = Math.max(18, node.size * 0.36)
  const len = Math.hypot(sx, sy)
  if (len > R) { sx = (sx / len) * R; sy = (sy / len) * R }
  knobs[node.id] = { x: sx, y: sy }

  const nx = sx / R
  const ny = sy / R
  const want = new Set()
  const d = node.dirs
  if (nx <= -0.35) want.add(d.left)
  if (nx >= 0.35) want.add(d.right)
  if (ny <= -0.35) want.add(d.up)
  if (ny >= 0.35) want.add(d.down)

  // 只记录幅度与目标方向，实际按键由方波循环统一调度
  const w = wave[node.id]
  w.m = Math.min(1, len / R)
  w.want = want
  if (!w.active) {
    w.active = true
    w.acc = 0
    startWaveLoop()
  }
}

/* ---------------- 方波微调循环 ---------------- */
let waveRaf = 0

function startWaveLoop() {
  if (waveRaf) return
  const step = () => {
    let any = false
    for (const id of ['joyA', 'joyB']) {
      const w = wave[id]
      if (!w.active) continue
      any = true
      applyWave(id, w)
    }
    waveRaf = any ? requestAnimationFrame(step) : 0
  }
  waveRaf = requestAnimationFrame(step)
}

/** 按目标方向集合对齐实际按着的键（差集式，不重复发事件） */
function syncKeys(id, want) {
  const held = joyHeld[id]
  for (const f of held) if (!want.has(f)) release(f)
  for (const f of want) if (!held.has(f)) press(f)
  joyHeld[id] = new Set(want)
}

/** 60Hz PWM：每帧一个周期，按连续占空比决定本帧按键通断 */
const PWM_ON = new Set()

function applyWave(id, w) {
  const held = joyHeld[id]
  // 死区：回中，全部松开
  if (w.m < SW.dz) {
    for (const f of held) release(f)
    held.clear()
    w.acc = 0
    return
  }
  // 占空比随幅度连续变化：duty ∈ [0,1]，无离散的阶段切换
  const duty = Math.min(1, (w.m - SW.dz) / (SW.full - SW.dz))
  // Bresenham 误差扩散：累加 duty，≥1 时本帧导通并扣除一个周期，
  // 长期导通占比精确等于 duty，且不受 rAF 与 PWM 相位对齐的拍频影响
  w.acc += duty
  if (w.acc >= 1) {
    w.acc -= 1
    syncKeys(id, w.want)
  } else {
    syncKeys(id, PWM_ON)
  }
}

function endJoy(node) {
  const id = node.id
  wave[id].active = false
  for (const f of joyHeld[id]) release(f)
  joyHeld[id].clear()
  knobs[id] = { x: 0, y: 0 }
  joyPid[id] = null
  // 摇杆底座弹回吸附前的位置
  const home = joyHome[id]
  if (home) {
    const t = findNode(id)
    if (t) { t.x = home.x; t.y = home.y }
    joyHome[id] = null
  }
}

function onBlankDown() {
  if (props.editing) selId.value = ''
}

/* ---------------- 设置面板 ---------------- */
function addField(field) {
  const f = normalizeField(field)
  if (!f) { ElMessage.warning('这个字段不支持，试试 a~z、0~9、space、enter、ctrl、shift'); return }
  if (scheme.buttons.some((b) => b.field === f)) { ElMessage.info('已经有 ' + f + ' 按键了'); return }
  if (scheme.buttons.length >= 12) { ElMessage.warning('最多 12 个自定义按键'); return }
  const i = scheme.buttons.length
  const btn = {
    id: 'k' + Date.now().toString(36) + Math.random().toString(36).slice(2, 5),
    field: f,
    label: labelOf(f),
    opacity: 0.6,
    size: 66,
    // 新按键落在中间偏左的空区：右侧被设置面板占着，放到那里用户看不到也拖不动
    x: clamp(30 + (i % 3) * 10, 5, 55),
    y: clamp(42 + Math.floor(i / 3) * 14, 5, 92)
  }
  scheme.buttons.push(btn)
  selId.value = btn.id
  ElMessage.success('已添加「' + btn.label + '」，可拖动调整位置')
}

function addNew() {
  // App 内 v-model 可能漏事件：以 DOM 实际值为准（详见 utils/formDom.js）
  const dom = readDomField('vk_field')
  if (dom != null && dom !== newField.value) newField.value = dom
  const raw = (newField.value || '').trim()
  if (!raw) { ElMessage.warning('先输入要映射的按键字段'); return }
  addField(raw)
  newField.value = ''
}

function removeBtn(id) {
  const i = scheme.buttons.findIndex((b) => b.id === id)
  if (i < 0) return
  scheme.buttons.splice(i, 1)
  if (selId.value === id) selId.value = ''
}

function resetScheme() {
  const def = loadDefault()
  scheme.joyA = { ...def.joyA }
  scheme.joyB = { ...def.joyB }
  scheme.buttons = []
  selId.value = ''
  ElMessage.success('已恢复默认布局')
}
function loadDefault() {
  // 只取默认布局，不动本地已存的开关状态
  return { joyA: { show: true, opacity: 0.55, size: 132, x: 18, y: 72 }, joyB: { show: false, opacity: 0.55, size: 132, x: 82, y: 72 } }
}

function finish() {
  emit('update:editing', false)
}

/* ---------------- 保存 ---------------- */
let saveTimer = null
watch(
  scheme,
  () => {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => { saveScheme(scheme); emit('change', scheme) }, 350)
  },
  { deep: true }
)
// 退出设置模式：立刻落盘（用户可能马上杀进程）
watch(
  () => props.editing,
  (v) => {
    if (v) { selId.value = ''; releaseAll() } else { saveScheme(scheme); emit('change', scheme) }
    nextTick(() => {})
  }
)
onMounted(() => {
  // 摇杆初始角度清零
  knobs.joyA = { x: 0, y: 0 }
  knobs.joyB = { x: 0, y: 0 }
  // 手机端切后台 / WebView 被盖住时兜底松键，避免角色一直走
  document.addEventListener('visibilitychange', onVisibility)
  // 本组件只在「开着虚拟按键」时挂载，挂上就说明 on=true，写回方案供下次自动恢复
  scheme.on = true
})
onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibility)
  if (waveRaf) { cancelAnimationFrame(waveRaf); waveRaf = 0 }
})

function onVisibility() {
  if (document.hidden) releaseAll()
}

defineExpose({ releaseAll, scheme })
</script>

<style scoped>
.vpad {
  position: absolute;
  inset: 0;
  z-index: 6;
  pointer-events: none;
  touch-action: none;
  -webkit-user-select: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}
/* 设置模式下整层接管触摸：既方便点空白取消选中，也避免误触游戏 */
.vpad.is-edit { pointer-events: auto; }

/* ---------- 摇杆 ---------- */
.vjoy {
  position: absolute;
  transform: translate(-50%, -50%);
  pointer-events: auto;
  touch-action: none;
  border-radius: 50%;
  /* 底座位置变化（吸附/弹回）走过渡；推杆中（live）禁用，保证跟手 */
  transition: left 0.16s ease, top 0.16s ease;
}
/* 隐形热区：比可见圆大一圈，拇指不必精确落在圆心 */
.vjoy::before {
  content: '';
  position: absolute;
  inset: -46%;
  border-radius: 50%;
}
.vjoy.live { transition: none; }
.vjoy-base {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(12, 16, 24, 0.28);
  border: 2px solid rgba(255, 255, 255, 0.42);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.28) inset;
}
.vjoy-knob {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 44%;
  height: 44%;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.82);
  border: 2px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.35);
  transition: transform 0.06s linear;
}
.vjoy.live .vjoy-knob { transition: none; }
.vjoy-tag {
  position: absolute;
  left: 50%;
  bottom: -18px;
  transform: translateX(-50%);
  font-size: 10px;
  line-height: 1;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.75);
  white-space: nowrap;
}
.vjoy-tag i { font-style: normal; opacity: 0.7; margin-left: 3px; }

/* ---------- 按键 ---------- */
.vkbtn {
  position: absolute;
  transform: translate(-50%, -50%);
  pointer-events: auto;
  touch-action: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(12, 16, 24, 0.34);
  border: 2px solid rgba(255, 255, 255, 0.45);
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.25);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.5px;
  transition: transform 0.08s ease, background 0.08s ease;
}
.vkbtn.on {
  background: rgba(255, 255, 255, 0.55);
  color: #16202c;
  transform: translate(-50%, -50%) scale(0.94);
}
.vkbtn-label { pointer-events: none; }

/* ---------- 设置态 ---------- */
.vpad.is-edit .vjoy,
.vpad.is-edit .vkbtn {
  outline: 1px dashed rgba(255, 255, 255, 0.55);
  outline-offset: 3px;
  cursor: move;
}
.vpad.is-edit .sel { outline: 2px solid var(--c-primary, #4c8dff); outline-offset: 4px; }

/* ---------- 设置面板 ---------- */
.vpad-panel {
  position: absolute;
  right: 8px;
  top: 8px;
  bottom: 8px;
  width: min(54%, 320px);
  pointer-events: auto;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 10px 12px 14px;
  border-radius: 12px;
  background: rgba(16, 22, 32, 0.9);
  color: #eef3fa;
  font-size: 12px;
  backdrop-filter: blur(6px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45);
}
.vp-hd { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.vp-hd strong { font-size: 13px; }
.vp-done {
  border: 0; border-radius: 999px; padding: 4px 12px;
  background: var(--c-primary, #4c8dff); color: #fff; font-size: 12px; cursor: pointer;
}
.vp-tip { margin: 4px 0 8px; font-size: 11px; line-height: 1.5; color: rgba(238, 243, 250, 0.62); }
.vp-row { display: flex; align-items: center; justify-content: space-between; padding: 5px 0; }
.vp-row i { font-style: normal; color: rgba(238, 243, 250, 0.5); margin-left: 4px; font-size: 11px; }
.vp-sub { margin: 10px 0 5px; font-size: 11px; color: rgba(238, 243, 250, 0.55); }
.vp-add { display: flex; gap: 6px; align-items: center; }
.vp-input {
  flex: 1; min-width: 0; height: 28px; padding: 0 8px;
  border-radius: 8px; border: 1px solid rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.08); color: #fff; font-size: 12px; outline: none;
}
.vp-input::placeholder { color: rgba(255, 255, 255, 0.35); }
.vp-chips { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 7px; }
.vp-chips button {
  border: 1px solid rgba(255, 255, 255, 0.18); background: rgba(255, 255, 255, 0.06);
  color: #eef3fa; border-radius: 999px; padding: 2px 8px; font-size: 11px; cursor: pointer;
}
.vp-chips i { font-style: normal; opacity: 0.45; margin-left: 3px; font-size: 10px; }
.vp-list { list-style: none; margin: 0; padding: 0; }
.vp-list li {
  display: flex; align-items: center; gap: 6px; padding: 5px 6px; border-radius: 8px;
  background: rgba(255, 255, 255, 0.05); margin-bottom: 4px; cursor: pointer;
}
.vp-list li.sel { background: rgba(76, 141, 255, 0.28); }
.vp-list .nm { flex: 1; }
.vp-list code { font-size: 10px; opacity: 0.55; }
.vp-list .del {
  border: 0; background: transparent; color: rgba(255, 255, 255, 0.6);
  font-size: 15px; line-height: 1; cursor: pointer; padding: 0 2px;
}
.vp-sliders { margin-top: 8px; }
.vp-slider { margin-bottom: 2px; }
.vp-slider span { font-size: 11px; color: rgba(238, 243, 250, 0.7); }
.vp-slider b { color: #fff; font-weight: 600; }
.vp-slider :deep(.el-slider) { --el-slider-height: 4px; }
.vp-reset {
  width: 100%; margin-top: 8px; padding: 6px; border-radius: 8px; cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.18); background: transparent; color: rgba(238, 243, 250, 0.75);
  font-size: 11px;
}
</style>
