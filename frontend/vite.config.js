import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
 const env = loadEnv(mode, process.cwd(), '')
 return {
  plugins: [
    vue(),
    vueDevTools(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/portal': {
        target: env.VITE_API_TARGET || 'http://127.0.0.1:18080',
        changeOrigin: true,
        secure: false,
      },
      // 如果需要代理预览页面以解决跨域问题，可以添加如下配置：
      '/preview': {
        target: env.VITE_PREVIEW_TARGET || 'http://127.0.0.1:5175',
        changeOrigin: true,
        secure: false,
        // 不重写路径，保留 /preview 前缀
        // rewrite: (path) => path.replace(/^\/preview/, ''),
      },
    },
  },
 }
})
