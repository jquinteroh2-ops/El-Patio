import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './estilos.css'

/** True cuando la app vive en una subcarpeta, como en GitHub Pages (/El-Patio/). */
const EN_SUBCARPETA = import.meta.env.BASE_URL !== '/'

/**
 * Guarda el esqueleto de la aplicacion para que abra sin senal. Solo en la
 * version compilada: en desarrollo estorba, porque serviria codigo viejo.
 *
 * El sello de la compilacion viaja en la URL a proposito. El navegador decide
 * si un service worker es nuevo comparando el archivo, y sw.js no cambia entre
 * despliegues: sin esto, un aparato que ya lo tenia registrado no volvia a
 * mirarlo nunca y seguia abriendo la version guardada. Con el sello, cada
 * compilacion es un registro distinto, y al activarse borra las cachés viejas.
 *
 * En GitHub Pages no se registra: sw.js guarda rutas de la raiz del dominio.
 */
function registrarServiceWorker(): void {
  if (!import.meta.env.PROD || EN_SUBCARPETA || !('serviceWorker' in navigator)) return
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register(`/sw.js?v=${__VERSION_APP__}`).catch((error) => {
      console.error('[sw] no se pudo registrar', error)
    })
  })
}

/**
 * En GitHub Pages el servidor corre en el computador del restaurante detras de
 * un tunel, y su direccion cambia cada vez que se enciende. Esa direccion se
 * publica en servidor.json; se lee antes de cargar la app porque config.ts la
 * fija al importarse.
 */
async function leerServidor(): Promise<void> {
  if (!EN_SUBCARPETA) return
  try {
    const r = await fetch(`${import.meta.env.BASE_URL}servidor.json`, { cache: 'no-store' })
    if (!r.ok) return
    const { api } = (await r.json()) as { api?: string }
    if (api) (globalThis as { __ELPATIO_API__?: string }).__ELPATIO_API__ = api
  } catch (error) {
    console.error('[api] no se pudo leer servidor.json', error)
  }
}

const contenedor = document.getElementById('root')
if (!contenedor) throw new Error('No se encontró el contenedor #root')

void leerServidor().then(async () => {
  const { default: App } = await import('./App')
  createRoot(contenedor).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
  registrarServiceWorker()
})
