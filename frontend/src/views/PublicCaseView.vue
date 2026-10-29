<template>
  <div class="public-case-page">
    <Header />
    <main>
      <div v-if="isLoading" class="case-state" role="status">正在加载案例…</div>
      <div v-else-if="error" class="case-state" role="alert">
        <p>{{ error }}</p>
        <button @click="loadCase">重试</button>
        <RouterLink to="/cases">返回案例广场</RouterLink>
      </div>
      <template v-else-if="detail">
        <section class="case-toolbar">
          <div class="case-info">
            <RouterLink to="/cases">← 案例广场</RouterLink>
            <h1>{{ detail.appName }}</h1>
            <p>{{ detail.appDescription || '使用 Flashcode 创建的应用' }}</p>
          </div>
          <div class="case-actions">
            <span class="case-tag">{{ ['HTML', 'Vue3', 'Vue3 + Spring'][detail.appType] || '应用' }} · 已发布</span>
            <button @click="frameKey++">刷新</button>
            <a :href="publishedUrl" target="_blank" rel="noopener noreferrer">新窗口打开</a>
          </div>
        </section>
        <iframe :key="frameKey" :src="publishedUrl" :title="detail.appName + ' 已发布页面'"
          sandbox="allow-scripts allow-same-origin" class="published-frame"></iframe>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import Header from '@/components/Header.vue';
import { fetchPublishedAppDetail } from '@/apis';

const route = useRoute();
const detail = ref(null);
const isLoading = ref(true);
const error = ref('');
const frameKey = ref(0);
const publishedUrl = computed(() => detail.value?.appUrl || '');
let requestId = 0;
const loadCase = async () => {
  const current = ++requestId;
  isLoading.value = true;
  error.value = '';
  detail.value = null;
  try {
    const result = await fetchPublishedAppDetail({ appId: route.params.id });
    const url = new URL(result.appUrl);
    if (!['http:', 'https:'].includes(url.protocol)) throw new Error('发布地址不可用');
    if (current === requestId) detail.value = result;
  } catch (err) {
    if (current === requestId) error.value = err.message || '案例加载失败，请稍后重试';
  } finally {
    if (current === requestId) isLoading.value = false;
  }
};
watch(() => route.params.id, loadCase, { immediate: true });
</script>

<style scoped lang="scss">
.public-case-page { min-height: 100dvh; background: #f5f7fb; }
main { max-width: 1560px; margin: auto; padding: 24px; }
.case-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 22px 26px; margin-bottom: 18px; border: 1px solid #e3e8f0; border-radius: 16px; background: white; }
.case-info { min-width: 0; }
h1 { font-size: 24px; margin: 12px 0 6px; overflow-wrap: anywhere; }
p { color: #64748b; margin: 0; overflow-wrap: anywhere; }
a { color: #2563eb; text-decoration: none; }
.case-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; flex-shrink: 0; }
button, .case-actions a { border: 1px solid #d8e1f0; background: white; border-radius: 9px; padding: 9px 14px; font: inherit; color: #2563eb; cursor: pointer; }
.case-tag { color: #64748b; font-size: 13px; }
.published-frame { display: block; width: 100%; height: max(560px, calc(100dvh - 260px)); border: 1px solid #e3e8f0; border-radius: 16px; background: white; }
.case-state { min-height: 50dvh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 20px; text-align: center; }
@media (max-width: 768px) { main { padding: 12px; } .case-toolbar { flex-direction: column; align-items: stretch; padding: 18px; gap: 18px; } .case-actions { flex-shrink: 1; } h1 { font-size: 21px; } }
</style>
