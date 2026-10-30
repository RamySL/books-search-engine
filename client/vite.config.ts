import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // en développement, l'API est servie par Spring Boot (make run)
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
