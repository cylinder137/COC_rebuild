<template>
  <div class="page">
    <div class="container narrow">
      <div class="row between center wrap gap12 head">
        <div>
          <h1 class="page-title">上传素材</h1>
          <p class="sm dim intro">
            图片 / 动图 / 视频 / 音频 / JSON 都可以传，单个 ≤300MB，大文件自动分片续传。
            传完复制<strong>直链</strong>，就能写进自己的 HTML。
          </p>
        </div>
        <div class="row gap8">
          <router-link to="/me/materials">
            <el-button><el-icon><Files /></el-icon>&nbsp;我的素材</el-button>
          </router-link>
        </div>
      </div>

      <div class="card">
        <AssetUploader :series="series" @done="onDone" />
      </div>

      <!-- 刚传完的素材：当场给直链，省得再回列表里翻 -->
      <div v-if="uploaded.length" class="card done-card">
        <div class="card-hd">
          <span>本次上传成功 {{ uploaded.length }} 个</span>
          <el-button size="small" text @click="uploaded = []">清空</el-button>
        </div>
        <div v-for="a in uploaded" :key="a.asset_id" class="up-row">
          <div class="up-info">
            <div class="up-name ellipsis">{{ a.name || a.file_name }}</div>
            <code class="up-url ellipsis">{{ absUrlOf(a) }}</code>
          </div>
          <div class="row gap8">
            <el-button size="small" @click="copy(a)">复制直链</el-button>
            <a :href="absUrlOf(a)" target="_blank" rel="noopener">
              <el-button size="small" text>打开</el-button>
            </a>
          </div>
        </div>
      </div>

      <div class="card tips">
        <div class="card-hd"><span>怎么用这个直链</span></div>
        <p class="xs dim">拿图片举例，写进你自己的 HTML 就行：</p>
        <code class="block">&lt;img src="{{ SITE_ORIGIN }}/media/12/your-picture.png"&gt;</code>
        <p class="xs dim" style="margin-top:10px">
          素材本身不是「作品」，不会出现在作品列表、也不用审核；打开「公开」后才会显示在素材库，
          其他人也能看到（链接不会因此失效）。素材可以在「我的素材」里改名、归入系列、随时删除。
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Files } from '@element-plus/icons-vue'
import AssetUploader from '@/components/AssetUploader.vue'
import { seriesApi, safe } from '@/api'
import { SITE_ORIGIN } from '@/api/http'
import { absoluteUrl, mediaUrlOf } from '@/utils/format'

const series = ref([])
const uploaded = ref([])

const absUrlOf = (a) => absoluteUrl(mediaUrlOf(a))

async function loadSeries() {
  const r = await safe(seriesApi.mine('asset'))
  series.value = r.ok && Array.isArray(r.data) ? r.data : []
}

function onDone(data) {
  if (data && data.asset_id) uploaded.value.unshift(data)
  loadSeries()
  ElMessage.success('上传成功，可以复制直链了')
}

async function copy(a) {
  const text = absUrlOf(a)
  try {
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text)
    else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success('直链已复制：' + text)
  } catch (e) {
    ElMessage.warning('复制失败，请手动复制：' + text)
  }
}

onMounted(loadSeries)
</script>

<style scoped>
.head { margin-bottom: 16px; }
.card { padding: 18px; }
.done-card { margin-top: 16px; }
.card-hd {
  display: flex; align-items: center; justify-content: space-between;
  font-weight: 600; margin-bottom: 12px;
}
.up-row {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 0; border-top: 1px solid var(--c-border);
}
.up-row:first-of-type { border-top: none; }
.up-info { flex: 1; min-width: 0; }
.up-name { font-size: var(--fs-sm); margin-bottom: 2px; }
.up-url { font-size: 12px; color: var(--c-text-3); display: block; }
.tips { margin-top: 16px; }
code.block {
  display: block; padding: 10px 12px; border-radius: var(--r-md);
  background: var(--c-bg-sunken); border: 1px solid var(--c-border);
  font-size: 13px; color: var(--c-text-2); overflow-x: auto;
}
</style>
