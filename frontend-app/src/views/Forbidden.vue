<template>
  <div class="err-page">
    <div class="code brand-grad">403</div>
    <h1 class="title">没有权限访问这个页面</h1>
    <p class="dim">该页面仅对管理员开放。如果你是开发者，请前往创作台；如果这是误判，请联系管理员 QQ 3254328344。</p>
    <div class="row gap12 center" style="justify-content:center;margin-top:20px">
      <router-link to="/"><el-button type="primary">返回首页</el-button></router-link>
      <router-link to="/me/projects"><el-button>去创作台</el-button></router-link>
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
