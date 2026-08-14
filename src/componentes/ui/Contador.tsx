import { Minus, Plus, Trash2 } from 'lucide-react'

interface Props {
  valor: number
  onCambiar: (valor: number) => void
  minimo?: number
  maximo?: number
  /** Muestra papelera en vez de menos cuando bajar quitaria el producto. */
  permiteQuitar?: boolean
  compacto?: boolean
}

/** Control de cantidad con blancos de toque grandes: nada de flechitas. */
export function Contador({
  valor,
  onCambiar,
  minimo = 1,
  maximo = 99,
  permiteQuitar = false,
  compacto = false,
}: Props) {
  const alBajar = permiteQuitar && valor <= minimo ? 0 : Math.max(minimo, valor - 1)
  const quitaria = permiteQuitar && valor <= minimo
  const lado = compacto ? 'h-10 w-10' : 'h-toque w-12'

  return (
    <div className="inline-flex items-center gap-1 rounded-xl border border-noche-700 bg-noche-850 p-1">
      <button
        type="button"
        onClick={() => onCambiar(alBajar)}
        aria-label={quitaria ? 'Quitar producto' : 'Restar uno'}
        className={`${lado} flex items-center justify-center rounded-lg transition active:scale-95 ${
          quitaria
            ? 'text-estado-demorado hover:bg-estado-demorado/15'
            : 'text-crema-100 hover:bg-noche-700'
        }`}
      >
        {quitaria ? <Trash2 className="h-4 w-4" aria-hidden /> : <Minus className="h-5 w-5" aria-hidden />}
      </button>

      <span
        className={`min-w-[2ch] text-center font-semibold tabular-nums text-crema-100 ${
          compacto ? 'text-base' : 'text-lg'
        }`}
      >
        {valor}
      </span>

      <button
        type="button"
        onClick={() => onCambiar(Math.min(maximo, valor + 1))}
        aria-label="Sumar uno"
        disabled={valor >= maximo}
        className={`${lado} flex items-center justify-center rounded-lg text-crema-100 transition hover:bg-noche-700 active:scale-95 disabled:opacity-40`}
      >
        <Plus className="h-5 w-5" aria-hidden />
      </button>
    </div>
  )
}
