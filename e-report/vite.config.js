import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { readFileSync } from 'node:fs'

const packageVersion = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')).version

export default defineConfig({
  plugins: [react()],
  base: '/',
  define: {
    'import.meta.env.VITE_APP_VERSION': JSON.stringify(packageVersion),
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (/[/\\]node_modules[/\\](?:react|react-dom|scheduler|react-router|react-router-dom)[/\\]/.test(id)) {
            return 'react-runtime'
          }
        },
      },
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    fs: {
      allow: ['..'],
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      }
    }
  }
})
