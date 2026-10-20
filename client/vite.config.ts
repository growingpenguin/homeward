import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// In dev, /api goes to the local backend (same origin, like Netlify's proxy in production).
// API_TARGET overrides it, e.g. when something else already uses localhost:8080.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': process.env.API_TARGET ?? 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
})
