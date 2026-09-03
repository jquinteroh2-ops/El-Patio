import { useEffect, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'

interface Props {
  abierta: boolean
  titulo: string
  descripcion?: string
  onCerrar: () => void
  children: ReactNode
  /** Barra fija al pie, para la accion que confirma. */
  pie?: ReactNode
}

/**
 * Cuantas hojas hay abiertas ahora mismo, y como estaba el desborde antes.
 *
 * Se cuentan porque una hoja puede abrir otra encima —de la ficha de una
 * reserva sale el mensaje de WhatsApp— y sin contar, la de arriba al cerrarse
 * restauraba el desborde y devolvia el desplazamiento al fondo mientras la de
 * abajo seguia abierta. Peor: la segunda guardaba «hidden» como valor anterior,
 * asi que al cerrarse la ultima el `body` quedaba bloqueado PARA SIEMPRE y solo
 * se arreglaba recargando.
 *
 * Viven fuera del componente porque son del documento, no de una hoja: cada
 * instancia tiene su propio estado, y el `body` es uno solo.
 */
let hojasAbiertas = 0
let desbordeOriginal = ''

function bloquearFondo(): void {
  if (hojasAbiertas === 0) {
    desbordeOriginal = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  }
  hojasAbiertas += 1
}

function soltarFondo(): void {
  hojasAbiertas = Math.max(0, hojasAbiertas - 1)
  if (hojasAbiertas === 0) document.body.style.overflow = desbordeOriginal
}

/**
 * Hoja que sube desde abajo. En un celular sostenido con una mano, todo lo que
 * hay que tocar queda cerca del pulgar; por eso las decisiones del pedido se
 * resuelven aqui y no en dialogos centrados.
 *
 * ── Por que se dibuja en el `body` y no donde se escribio ────────────────────
 * ESTO NO ES UN ADORNO: es lo unico que hace que la hoja aparezca donde debe.
 *
 * `position: fixed` se mide contra la ventana del navegador SALVO que algun
 * ancestro tenga `transform`, `filter`, `backdrop-filter`, `perspective`,
 * `contain` o `will-change` de esas propiedades. Con cualquiera de ellas, el
 * ancestro pasa a ser el marco de referencia y la hoja se coloca respecto a EL.
 *
 * Eso ya paso: una animacion de entrada en el `<main>` del panel dejaba puesto
 * un `transform: translateY(0)` —cero, pero transform al fin— y las hojas
 * aparecian cientos de pixeles mas abajo, fuera de la pantalla. Como al abrirse
 * se bloquea el desplazamiento del fondo, no habia forma de bajar a buscarlas:
 * parecia que el boton no hacia nada.
 *
 * El mismo `transform` creaba ademas un contexto de apilamiento, y el `z-50` de
 * la hoja quedaba encerrado por debajo de las barras superiores.
 *
 * Dibujando en el `body` no hay ancestro que valga. Cualquiera puede animar
 * cualquier contenedor sin volver a romper esto, que es justo lo que hace falta
 * en un sistema donde el que anade la animacion no es el que escribio la hoja.
 * ─────────────────────────────────────────────────────────────────────────────
 */
export function HojaInferior({ abierta, titulo, descripcion, onCerrar, children, pie }: Props) {
  useEffect(() => {
    if (!abierta) return
    const alTeclear = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCerrar()
    }
    document.addEventListener('keydown', alTeclear)
    bloquearFondo()
    return () => {
      document.removeEventListener('keydown', alTeclear)
      soltarFondo()
    }
  }, [abierta, onCerrar])

  if (!abierta) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <div
        className="absolute inset-0 animate-aparecer bg-black/70"
        onClick={onCerrar}
        aria-hidden
      />

      <div
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        className="relative flex max-h-[88vh] w-full animate-deslizar flex-col rounded-t-3xl border border-noche-700 bg-noche-900 sm:max-w-lg sm:rounded-3xl"
      >
        <header className="flex items-start gap-3 border-b border-noche-800 px-4 py-3.5">
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-base font-semibold text-crema-100">{titulo}</h2>
            {descripcion && <p className="mt-0.5 text-sm text-noche-400">{descripcion}</p>}
          </div>
          <button
            type="button"
            onClick={onCerrar}
            aria-label="Cerrar"
            className="-mr-1 flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-noche-400 transition hover:bg-noche-800 hover:text-crema-100"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </header>

        <div className="scroll-fino flex-1 overflow-y-auto px-4 py-4">{children}</div>

        {pie && (
          <footer className="border-t border-noche-800 bg-noche-900 px-4 py-3 pb-segura">{pie}</footer>
        )}
      </div>
    </div>,
    document.body,
  )
}
