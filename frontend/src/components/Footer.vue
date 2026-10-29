<template>
  <footer class="footer">
    <div class="container">
      <div><h2>Flashcode</h2><p>从一个想法，到可以运行的应用。</p></div>
      <nav aria-label="页脚导航"><RouterLink to="/">应用工作台</RouterLink><RouterLink to="/cases">案例广场</RouterLink><RouterLink to="/about">关于项目</RouterLink></nav>
    </div>
  </footer>
</template>

<script setup>
import { RouterLink } from 'vue-router';
</script>

<style scoped>
.footer { background: #111a2d; color: #abb9d1; padding: 40px 24px; }
.container { max-width: 1200px; margin: auto; display: flex; align-items: center; justify-content: space-between; gap: 28px; flex-wrap: wrap; }
h2 { color: #fff; margin: 0 0 10px; }
p { line-height: 1.8; margin: 0; font-size: 14px; }
nav { display: flex; gap: 24px; flex-wrap: wrap; }
a { color: #c8d5ef; text-decoration: none; }
a:hover { color: #fff; }
</style>
