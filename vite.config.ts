import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'

/**
 * Sello de esta compilacion.
 *
 * Va a parar a la URL con la que se registra el service worker. Sin el, sw.js
 * es un archivo identico en cada despliegue: el navegador compara bytes para
 * decidir si hay version nueva, no encuentra diferencia, y el aparato se queda
 * sirviendo indefinidamente el index.html que tenia guardado. Es lo que hacia
 * que el mismo sistema se viera distinto en el computador y en el celular.
 */
const sello = Date.now().toString(36)

export default defineConfig({
  plugins: [react()],
  define: {
    __VERSION_APP__: JSON.stringify(sello),
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
  },
})
