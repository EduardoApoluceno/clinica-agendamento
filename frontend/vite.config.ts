/// <reference types="vitest/config" />
import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Em desenvolvimento (npm run dev) as chamadas ao backend passam pelo "web" do compose (porta 8080),
// mantendo a mesma origem lógica para cookie de sessão e CSRF.
const alvo = process.env.VITE_PROXY_ALVO ?? 'http://localhost:8080'
const rotasBackend = ['/api', '/oauth2', '/login', '/logout']

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, 'src') },
  },
  server: {
    port: 5173,
    proxy: Object.fromEntries(rotasBackend.map((rota) => [rota, { target: alvo, changeOrigin: false }])),
  },
  build: {
    sourcemap: false,
    target: 'es2023',
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
