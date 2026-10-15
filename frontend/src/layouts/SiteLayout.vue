<template>
  <div class="shell">
    <AppHeader />
    <main class="shell-main">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
</script>

<style scoped>
.shell { display: flex; flex-direction: column; min-height: 100vh; }
.shell-main { flex: 1; }
</style>
