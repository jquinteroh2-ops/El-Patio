import { useEffect, type ReactNode } from 'react'
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
 * Hoja que sube desde abajo. En un celular sostenido con una mano, todo lo que
 * hay que tocar queda cerca del pulgar; por eso las decisiones del pedido se
 * resuelven aqui y no en dialogos centrados.
 */
export function HojaInferior({ abierta, titulo, descripcion, onCerrar, children, pie }: Props) {
  useEffect(() => {
    if (!abierta) return
    const alTeclear = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCerrar()
    }
    document.addEventListener('keydown', alTeclear)
    const desbordeAnterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', alTeclear)
      document.body.style.overflow = desbordeAnterior
    }
  }, [abierta, onCerrar])

  if (!abierta) return null

  return (
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
    </div>
  )
}
