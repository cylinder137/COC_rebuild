<template>
  <div class="err-page">
    <div class="code brand-grad">404</div>
    <h1 class="title">页面不存在</h1>
    <p class="dim">你要找的作品可能已被删除、下架，或者链接输错了。</p>
    <div class="row gap12 center" style="justify-content:center;margin-top:20px">
      <router-link to="/"><el-button type="primary">回到首页</el-button></router-link>
      <el-button @click="$router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.err-page { min-height: 70vh; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 40px 20px; }
.code {
  font-size: 90px; font-weight: 800; line-height: 1;
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.title { font-size: var(--fs-h2); margin: 16px 0 8px; }
.dim { color: var(--c-text-2); max-width: 520px; }
</style>
