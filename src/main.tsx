import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './estilos.css'

/**
 * Guarda el esqueleto de la aplicacion para que abra sin senal. Solo en la
 * version compilada: en desarrollo estorba, porque serviria codigo viejo.
 *
 * El sello de la compilacion viaja en la URL a proposito. El navegador decide
 * si un service worker es nuevo comparando el archivo, y sw.js no cambia entre
 * despliegues: sin esto, un aparato que ya lo tenia registrado no volvia a
 * mirarlo nunca y seguia abriendo la version guardada. Con el sello, cada
 * compilacion es un registro distinto, y al activarse borra las cachés viejas.
 */
function registrarServiceWorker(): void {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register(`/sw.js?v=${__VERSION_APP__}`).catch((error) => {
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
