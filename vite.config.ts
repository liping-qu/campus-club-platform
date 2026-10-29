import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    // H5 开发时把 /api 代理到本地 Java 后端，前端请求层用同源路径即可，无需处理跨域。
    // 其余平台（小程序 / App）在 src/utils/request.ts 中直连后端地址。
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: true
      }
    }
  }
})
