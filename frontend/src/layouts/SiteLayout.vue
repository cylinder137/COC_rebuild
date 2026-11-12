<template>
  <div class="shell" :class="{ 'is-native': isApp }">
    <AppHeader />
    <main class="shell-main">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </main>
    <BottomTabBar />
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
import BottomTabBar from '@/components/BottomTabBar.vue'
import { IS_APP } from '@/utils/platform'

/** App 内始终用底部 tab（含横屏，否则横屏时顶栏和底栏都没有导航） */
const isApp = IS_APP
</script>

<style scoped>
.shell { display: flex; flex-direction: column; min-height: 100vh; }
.shell-main { flex: 1; }
</style>
