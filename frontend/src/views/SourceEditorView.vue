<template>
  <div class="editor-page">
    <header class="editor-header"><strong>Flashcode 源码编辑器</strong><span>应用 {{ route.params.id }}</span><span class="status">{{ status }}</span></header>
    <main class="editor-layout">
      <aside class="file-list"><h3>源码文件</h3><button v-for="name in names" :key="name" :class="{active:name===selected}" @click="select(name)">{{ name }}</button><p v-if="!names.length" class="muted">暂无可编辑源码</p></aside>
      <section class="workspace"><div class="toolbar"><span>{{ selected || '选择文件' }}</span><button :disabled="!selected || saving || !dirty" @click="save">{{ saving ? '保存中…' : '保存文件' }}</button><button class="primary" :disabled="building || dirty" @click="build">{{ building ? '构建中…' : '构建并返回预览' }}</button></div><textarea v-if="selected" v-model="content" spellcheck="false" @input="dirty=true"></textarea><div v-else class="empty">选择左侧文件开始编辑</div></section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { completeEdit, fetchSourceFiles, saveSourceFile } from '@/apis';
const route=useRoute(), files=ref({}), selected=ref(''), content=ref(''), dirty=ref(false), saving=ref(false), building=ref(false), status=ref('正在加载…');
const names=computed(()=>Object.keys(files.value).sort());
const select=name=>{selected.value=name;content.value=files.value[name]??'';dirty.value=false;status.value='已加载';};
const load=async()=>{try{files.value=await fetchSourceFiles({appId:route.params.id});if(names.value.length)select(names.value[0]);status.value='就绪';}catch(e){status.value=e.message||'加载失败';}};
const save=async()=>{if(!selected.value||!dirty.value)return;saving.value=true;try{await saveSourceFile({appId:Number(route.params.id),path:selected.value,content:content.value});files.value[selected.value]=content.value;dirty.value=false;status.value='已保存';}catch(e){status.value=e.message||'保存失败';}finally{saving.value=false;}};
const build=async()=>{if(dirty.value) return;building.value=true;status.value='正在构建预览…';try{await completeEdit({appId:Number(route.params.id)});status.value='构建完成，可返回预览';window.parent.postMessage({type:'flashcode:editor-complete',appId:String(route.params.id)},location.origin);}catch(e){status.value=e.message||'构建失败，原预览保持不变';}finally{building.value=false;}};
onMounted(load);
</script>

<style scoped>
.editor-page{height:100%;min-height:100vh;background:#101827;color:#dbeafe;font:14px system-ui}.editor-header{height:54px;padding:0 18px;display:flex;align-items:center;gap:18px;border-bottom:1px solid #26354b}.editor-header strong{font-size:16px}.editor-header .status{margin-left:auto;color:#93c5fd}.editor-layout{display:grid;grid-template-columns:240px 1fr;height:calc(100vh - 54px)}.file-list{padding:16px;border-right:1px solid #26354b;overflow:auto}.file-list h3{margin:0 0 12px;font-size:13px;color:#93c5fd}.file-list button{display:block;width:100%;padding:9px 10px;text-align:left;border:0;border-radius:6px;color:#cbd5e1;background:transparent;cursor:pointer}.file-list button:hover,.file-list button.active{background:#1e3a5f;color:#fff}.muted,.empty{color:#94a3b8}.workspace{min-width:0;display:flex;flex-direction:column}.toolbar{height:48px;padding:7px 12px;display:flex;align-items:center;gap:8px;border-bottom:1px solid #26354b}.toolbar span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin-right:auto}.toolbar button{padding:7px 12px;border:1px solid #38506d;border-radius:6px;color:#dbeafe;background:#18283d;cursor:pointer}.toolbar button:disabled{opacity:.45;cursor:not-allowed}.toolbar .primary{background:#2563eb;border-color:#2563eb}textarea{flex:1;width:100%;resize:none;border:0;outline:0;padding:18px;background:#0b1220;color:#e2e8f0;font:13px/1.6 Consolas,monospace}.empty{display:grid;place-items:center;height:100%}@media(max-width:700px){.editor-layout{grid-template-columns:150px 1fr}.editor-header span:not(.status){display:none}}
</style>
