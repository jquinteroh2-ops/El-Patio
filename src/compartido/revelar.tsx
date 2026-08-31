import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

/**
 * Hace que los bloques marcados suban flotando cuando entran en pantalla.
 *
 * Sin esto, bajar por la página es ir destapando texto de golpe: ya estaba ahí
 * antes de llegar, y aparece entero contra el borde inferior como si lo hubieran
 * pegado. Con esto cada bloque sube un poco y se posa cuando el ojo llega.
 *
 * ── Por qué un observador global y no un componente que envuelva ─────────────
 * La alternativa era `<Revelar>…</Revelar>` alrededor de cada sección, y eso
 * obliga a tocar la etiqueta de apertura Y la de cierre en decenas de sitios,
 * que es justo donde se cuela un cierre mal puesto. Así, encender el efecto en
 * cualquier parte del proyecto es añadir la palabra `revelar` a un `className`
 * —una sola palabra, en un solo sitio— y nada más.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * ── Se revela UNA sola vez ───────────────────────────────────────────────────
 * Al aparecer se deja de observar. Volver a esconder lo que ya se leyó y
 * animarlo otra vez al subir convierte el desplazamiento en un parpadeo
 * constante, y es de las cosas que más marean en un teléfono.
 * ─────────────────────────────────────────────────────────────────────────────
 */

/**
 * Lo que se busca. Al revelarse se le añade `revelado` y deja de coincidir.
 *
 * Dos variantes: la larga del sitio público y la corta del panel, que recorta
 * el recorrido y la duración porque allí la misma animación elegante se
 * sentiría como que el sistema tarda.
 */
const SELECTOR = '.revelar:not(.revelado), .revelar-corto:not(.revelado)'

/** Si el aparato pide que se le ahorre el movimiento. */
function prefiereQuietud(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/** Los deja visibles sin animar ninguno. */
function revelarTodo(): void {
  document.querySelectorAll(SELECTOR).forEach((nodo) => nodo.classList.add('revelado'))
}

/**
 * Enciende el revelado en toda la aplicación.
 *
 * Se monta UNA vez, dentro del enrutador. Vuelve a barrer la página en cada
 * cambio de ruta y cada vez que alguien mete nodos nuevos, que es lo que hace
 * que también funcione con lo que llega del servidor —las publicaciones de la
 * portada, los platos de la carta— y no solo con lo que ya estaba pintado.
 */
export function RevelarAlDesplazar() {
  const { pathname } = useLocation()

  useEffect(() => {
    // Dos salidas de emergencia. Un fallo aquí no deja la página fea: la deja
    // EN BLANCO, porque lo que no se revela se queda transparente.
    if (prefiereQuietud() || typeof IntersectionObserver === 'undefined') {
      revelarTodo()
      return
    }

    const observador = new IntersectionObserver(
      (entradas) => {
        for (const entrada of entradas) {
          if (!entrada.isIntersecting) continue
          entrada.target.classList.add('revelado')
          observador.unobserve(entrada.target)
        }
      },
      // El margen inferior negativo adelanta un poco el disparo: la animación
      // termina cuando el bloque está a la vista y no cuando ya se pasó.
      { threshold: 0.05, rootMargin: '0px 0px -8% 0px' },
    )

    const barrer = () => document.querySelectorAll(SELECTOR).forEach((n) => observador.observe(n))
    barrer()

    // Lo que llega después: una carta que termina de cargar, una hoja que se
    // abre. Se agrupa en un cuadro para no barrer el documento entero por cada
    // nodo que React inserte mientras pinta una lista larga.
    let pendiente = 0
    const vigilante = new MutationObserver(() => {
      if (pendiente) return
      pendiente = requestAnimationFrame(() => {
        pendiente = 0
        barrer()
      })
    })
    vigilante.observe(document.body, { childList: true, subtree: true })

    return () => {
      if (pendiente) cancelAnimationFrame(pendiente)
      vigilante.disconnect()
      observador.disconnect()
    }
  }, [pathname])

  return null
}
