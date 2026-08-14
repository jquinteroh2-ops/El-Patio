import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './estilos.css'

/**
 * Guarda el esqueleto de la aplicacion para que abra sin senal. Solo en la
 * version compilada: en desarrollo estorba, porque serviria codigo viejo.
 */
function registrarServiceWorker(): void {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/sw.js').catch((error) => {
      console.error('[sw] no se pudo registrar', error)
    })
  })
}

const contenedor = document.getElementById('root')
if (!contenedor) throw new Error('No se encontró el contenedor #root')

createRoot(contenedor).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

registrarServiceWorker()
