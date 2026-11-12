<template>
  <footer class="ft">
    <div class="container ft-inner">
      <div class="ft-brand">
        <span class="logo-tile">造</span>
        <div>
          <div class="ft-name">造物集</div>
          <div class="xs dim">记录每一个小造物 · 试运行版</div>
        </div>
      </div>
      <div class="ft-cols">
        <div class="ft-col">
          <div class="ft-col-title">产品</div>
          <router-link to="/">发现作品</router-link>
          <router-link to="/dev-apply">成为开发者</router-link>
          <router-link to="/upload">投稿上传</router-link>
        </div>
        <div class="ft-col">
          <div class="ft-col-title">帮助</div>
          <router-link to="/forgot">联系管理员</router-link>
          <router-link to="/me/reports">我的举报</router-link>
          <router-link to="/403">权限说明</router-link>
        </div>
        <div class="ft-col">
          <div class="ft-col-title">合作</div>
          <span class="dim">管理员 QQ：3254328344</span>
          <span class="dim">反馈与建议</span>
        </div>
      </div>
    </div>
    <div class="ft-bottom">
      <div class="container xs dim">© 2026 造物集 · coc-platform 试运行版 · 本平台等同全新立项，零继承旧站数据</div>
    </div>
  </footer>
</template>

<style scoped>
.ft { background: var(--c-footer); border-top: 1px solid var(--c-border); margin-top: 48px; }
.ft-inner { display: flex; gap: 48px; padding: 40px 24px 28px; flex-wrap: wrap; }
.ft-brand { display: flex; gap: 12px; align-items: flex-start; min-width: 220px; }
.ft-name { color: #fff; font-weight: 700; font-size: 16px; }
.ft-brand :deep(.logo-tile) { box-shadow: none; }
.ft-cols { display: flex; gap: 56px; flex-wrap: wrap; }
.ft-col { display: flex; flex-direction: column; gap: 8px; font-size: var(--fs-sm); }
.ft-col-title { color: #fff; font-weight: 600; margin-bottom: 2px; }
.ft-col a, .ft-col span { color: #8a91a3; }
.ft-col a:hover { color: var(--c-primary-hover); }
.ft-bottom { border-top: 1px solid rgba(255, 255, 255, .06); padding: 14px 0; }
@media (max-width: 767px) {
  .ft-inner { padding: 28px 16px 20px; gap: 28px; }
  .ft-cols { gap: 28px; }
}
</style>
