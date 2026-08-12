import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 前端所有 /api 请求转发到 Spring Boot 后端 (开发 server 与生产 preview 共用)
// 默认 8080;可用环境变量 VITE_API_TARGET 覆盖(如隔离端口联调 / 部署到其他地址)
const apiProxy = {
  '/api': {
    target: process.env.VITE_API_TARGET || 'http://localhost:8080',
    changeOrigin: true,
  },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: apiProxy,
  },
  preview: {
    host: '127.0.0.1',
    port: 4173,
    proxy: apiProxy,
  },
})
