import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  base: './',
  plugins: [vue(), tailwindcss()],
  // 開發時把 /api 轉給後端（cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev）
  server: { proxy: { '/api': 'http://localhost:8080' } },
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
})
