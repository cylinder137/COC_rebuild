<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">投稿上传</h2>
        <div class="sm dim">按向导三步走：选类型 → 填信息 → 传文件并提交</div>
      </div>
      <el-tag :type="isAuto ? 'success' : 'warning'" effect="plain">
        {{ isAuto ? '该类型免审，发布后立即公开' : '该类型需管理员人工审核' }}
      </el-tag>
    </div>

    <el-steps :active="step" finish-status="success" align-center class="mb24">
      <el-step title="选择类型" description="HTML / Scratch / 结构" />
      <el-step title="填写信息" description="标题、简介、分类" />
      <el-step title="上传并提交" description="分片上传 · 支持续传" />
    </el-steps>

    <!-- 步骤 1：类型 -->
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
      </div>
      <div class="row" style="justify-content:flex-end;margin-top:20px">
        <el-button type="primary" :disabled="!form.project_type" @click="step = 1">下一步</el-button>
      </div>
    </div>

    <!-- 步骤 2：信息 -->
    <div v-show="step === 1" class="card">
      <el-form :model="form" label-position="top" style="max-width:640px">
        <el-form-item label="作品标题" required>
          <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="例如：像素小迷宫" />
        </el-form-item>
        <el-form-item label="一句话简介" required>
          <el-input v-model="form.summary" maxlength="80" show-word-limit placeholder="用一句话说清它是什么、怎么玩" />
        </el-form-item>
        <el-form-item label="详细介绍">
          <el-input v-model="form.description" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="玩法说明、操作方式、素材来源等" />
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
      <div class="row between" style="margin-top:12px">
        <el-button @click="step = 0">上一步</el-button>
        <el-button type="primary" :loading="creating" :disabled="!canNext" @click="saveDraft">
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
        <ChunkUploader @completed="onUploaded" @reset="uploaded = null" />
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
        <div class="row between" style="margin-top:16px">
          <el-button @click="step = 1">上一步</el-button>
          <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
            提交作品
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChunkUploader from '@/components/ChunkUploader.vue'
import { categoryApi, uploadApi, safe } from '@/api'
import { isAutoApproved } from '@/utils/format'

const router = useRouter()
const step = ref(0)
const creating = ref(false)
const submitting = ref(false)
const uploaded = ref(null)
const categories = ref([])

const form = reactive({
  project_id: null,
  project_type: '',
  title: '',
  summary: '',
  description: '',
  category_id: '',
  tags: []
})

const types = [
  { value: 'html', label: 'HTML 网页', desc: 'zip 或 html 包，可含 JS', color: 'var(--c-type-html)', icon: 'Monitor', auto: false },
  { value: 'scratch', label: 'Scratch 作品', desc: '.sb2 / .sb3 积木项目', color: 'var(--c-type-scratch)', icon: 'Cpu', auto: true },
  { value: 'structure', label: 'MC 结构', desc: '.mcstructure 纯数据', color: 'var(--c-type-structure)', icon: 'Box', auto: true }
]

const isAuto = computed(() => isAutoApproved(form.project_type))
const canNext = computed(() => !!form.title.trim() && !!form.summary.trim())
const canSubmit = computed(() => !!uploaded.value?.project_file_id || !!uploaded.value?.storage_path)

async function saveDraft() {
  if (!canNext.value) { ElMessage.warning('请填写标题与简介'); return }
  creating.value = true
  const payload = { ...form, tags: form.tags.slice(0, 5) }
  delete payload.project_id
  const r = await safe(uploadApi.createProject(payload))
  creating.value = false
  if (!r.ok) {
    ElMessage.error(r.error?.response?.data?.message || '创建草稿失败（后端 8090 可能未启动）')
    return
  }
  form.project_id = r.data?.project_id || r.data?.id
  ElMessage.success('草稿已保存')
  step.value = 2
}

function onUploaded(res) { uploaded.value = res }

async function submit() {
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

onMounted(async () => {
  const r = await safe(categoryApi.list())
  if (r.ok && Array.isArray(r.data)) categories.value = r.data
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
.t-ico { width: 46px; height: 46px; border-radius: var(--r-md); display: flex; align-items: center; justify-content: center; margin-bottom: 12px; }
.t-name { font-weight: 600; font-size: var(--fs-h3); margin-bottom: 4px; }
.t-tag { margin-top: 12px; }
.mb16 { margin-bottom: 16px; }
.mb24 { margin-bottom: 24px; }
.kv { display: flex; gap: 12px; font-size: var(--fs-sm); padding: 4px 0; }
.kv .dim { min-width: 110px; }
@media (max-width: 767px) { .type-grid { grid-template-columns: 1fr; } }
</style>
