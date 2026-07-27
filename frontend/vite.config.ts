import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const backendPort = process.env.VITE_BACKEND_PORT || '3002';
  const backendTarget = `http://localhost:${backendPort}`;

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': { target: backendTarget, changeOrigin: true },
      },
      open: mode === 'development',
    },
    build: {
      outDir: 'dist',
    },
  };
})
