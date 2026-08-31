import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { ArrowUpRight, Check, ChevronDown, Store } from 'lucide-react'
import { HAY_RESTAURANTE_HERMANO, RESTAURANTE, RESTAURANTE_HERMANO } from '@/compartido/config'

/**
 * El cambio de restaurante, arriba del panel administrativo.
 *
 * El dueño tiene dos locales y un solo panel que aprender. Este control es todo
 * lo que los une: dice en cuál está parado y lo lleva al otro sin salir a
 * buscar una dirección.
 *
 * ── Por qué salta en vez de cambiar los datos en el sitio ────────────────────
 * Cada restaurante es un despliegue completo y separado: su base, su carta, su
 * personal, su caja. No hay una consulta que traiga «las ventas del otro»
 * porque no hay un servidor que conozca a los dos, y no lo hay a propósito:
 * dos restaurantes en una sola base es un problema contable esperando el
 * cierre de mes.
 *
 * Así que esto no filtra nada: cambia de sistema. Y como son dos servidores
 * distintos, con sus propios usuarios y sus propios tokens, el de allá vuelve a
 * pedir la clave. Eso se ADVIERTE aquí en vez de dejar que el otro sistema lo
 * reciba con una pantalla de acceso que no esperaba.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Se conserva la sección: quien está mirando el cierre de caja de un local
 * quiere el cierre de caja del otro, no su portada. Las dos aplicaciones son el
 * mismo código, así que la ruta existe igual en las dos.
 */
export function SelectorRestaurante() {
  const [abierto, setAbierto] = useState(false)
  const contenedor = useRef<HTMLDivElement>(null)
  const { pathname } = useLocation()

  // Cerrar al hacer clic fuera y con Escape, igual que el resto de menús del
  // panel: uno que se queda abierto tapando la pantalla estorba más que ayuda.
  useEffect(() => {
    if (!abierto) return
    const fuera = (e: MouseEvent) => {
      if (!contenedor.current?.contains(e.target as Node)) setAbierto(false)
    }
    const escape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setAbierto(false)
    }
    document.addEventListener('mousedown', fuera)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', fuera)
      document.removeEventListener('keydown', escape)
    }
  }, [abierto])

  // Sin hermano configurado no hay nada que ofrecer. Un botón que lleva a una
  // dirección muerta es peor que no tener botón.
  if (!HAY_RESTAURANTE_HERMANO) return null

  /*
   * La misma sección, en el otro sistema.
   *
   * Se arma con `pathname` y no con la ruta completa: los parámetros de
   * búsqueda llevan filtros de fechas y de mesa que no significan lo mismo en
   * el otro local, y arrastrarlos abriría el panel de allá con un filtro que
   * nadie puso.
   */
  const destino = `${RESTAURANTE_HERMANO.url}${pathname}`

  return (
    <div ref={contenedor} className="relative shrink-0">
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={abierto}
        title="Cambiar de restaurante"
        className="flex min-h-[40px] items-center gap-2 rounded-xl border border-noche-700 bg-noche-900 px-3 text-sm font-medium text-crema-100 transition hover:bg-noche-800"
      >
        <Store className="h-4 w-4 text-oro-400" aria-hidden />
        {/* En un celular el nombre no cabe junto al título y al botón de salir.
            Se esconde el texto y queda el icono, que ya es un blanco de toque
            del tamaño correcto. */}
        <span className="hidden sm:inline">{RESTAURANTE.nombre}</span>
        <ChevronDown className="h-4 w-4 text-noche-400" aria-hidden />
      </button>

      {abierto && (
        <div
          role="menu"
          className="absolute right-0 z-40 mt-1.5 w-64 animate-caer overflow-hidden rounded-xl border border-noche-700 bg-noche-900 shadow-xl shadow-black/40"
        >
          <p className="border-b border-noche-800 px-3.5 py-2 text-[0.7rem] uppercase tracking-[0.2em] text-noche-400">
            Restaurante
          </p>

          {/* Donde ya está. No es un botón: no lleva a ninguna parte. */}
          <div
            role="menuitem"
            aria-current="true"
            className="flex items-center gap-2.5 bg-oro-500/10 px-3.5 py-2.5 text-sm text-crema-100"
          >
            <Check className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
            <span className="min-w-0 flex-1 truncate">{RESTAURANTE.nombreCompleto}</span>
          </div>

          {/*
            El salto. Va como enlace de verdad y no como un `onClick` que empuja
            la dirección: así el jefe puede abrir el otro local en una pestaña
            aparte con el clic del medio o el menú del botón derecho, que es
            justo lo que hace quien quiere comparar las dos cajas lado a lado.
          */}
          <a
            role="menuitem"
            href={destino}
            className="flex items-center gap-2.5 border-t border-noche-800 px-3.5 py-2.5 text-sm text-crema-100 transition hover:bg-noche-800"
          >
            <ArrowUpRight className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
            <span className="min-w-0 flex-1 truncate">{RESTAURANTE_HERMANO.nombreCompleto}</span>
          </a>

          <p className="border-t border-noche-800 px-3.5 py-2.5 text-xs leading-relaxed text-noche-400">
            Es otro sistema, con su propia base y su propio personal. Le va a
            pedir su clave de {RESTAURANTE_HERMANO.nombre}.
          </p>
        </div>
      )}
    </div>
  )
}
