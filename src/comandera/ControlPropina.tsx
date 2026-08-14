import { useState } from 'react'
import { Info } from 'lucide-react'
import { PROPINAS_SUGERIDAS } from '@/compartido/config'
import { formatoCOP } from '@/compartido/formato'

export interface Propina {
  /** Porcentaje elegido, o null cuando el valor se escribio a mano. */
  porcentaje: number | null
  valor: number
}

interface Props {
  subtotal: number
  propina: Propina
  onCambiar: (propina: Propina) => void
}

/**
 * Propina.
 *
 * En Colombia es voluntaria y el establecimiento esta obligado a informarla y
 * consultarla antes de incluirla. Por eso arranca en cero, ninguna opcion viene
 * preseleccionada y el aviso al cliente esta a la vista, no escondido.
 */
export function ControlPropina({ subtotal, propina, onCambiar }: Props) {
  const [libre, setLibre] = useState(false)

  const elegirPorcentaje = (porcentaje: number) => {
    setLibre(false)
    onCambiar({ porcentaje, valor: Math.round((subtotal * porcentaje) / 100) })
  }

  const escribirValor = (texto: string) => {
    const valor = Number(texto.replace(/\D/g, '')) || 0
    onCambiar({ porcentaje: null, valor })
  }

  return (
    <section className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
      <div className="mb-1 flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-crema-100">Propina</h2>
        <span className="rounded-lg border border-noche-700 px-2 py-0.5 text-xs text-noche-400">
          Voluntaria
        </span>
      </div>
      <p className="mb-3 flex items-start gap-1.5 text-xs leading-relaxed text-noche-400">
        <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
        Pregúntale al cliente si desea dejarla y cuánto. Si no la autoriza, deja el cero.
      </p>

      <div className="grid grid-cols-4 gap-2">
        {PROPINAS_SUGERIDAS.map((porcentaje) => {
          const activo = !libre && propina.porcentaje === porcentaje
          return (
            <button
              key={porcentaje}
              type="button"
              onClick={() => elegirPorcentaje(porcentaje)}
              className={`flex min-h-[56px] flex-col items-center justify-center rounded-xl border transition active:scale-95 ${
                activo
                  ? 'border-ambar-500 bg-ambar-500/15 text-crema-100'
                  : 'border-noche-700 bg-noche-850 text-noche-300 hover:bg-noche-800'
              }`}
            >
              <span className="text-base font-bold">{porcentaje}%</span>
              <span className="text-[0.7rem] tabular-nums text-noche-400">
                {porcentaje === 0 ? 'sin propina' : formatoCOP(Math.round((subtotal * porcentaje) / 100))}
              </span>
            </button>
          )
        })}

        <button
          type="button"
          onClick={() => {
            setLibre(true)
            onCambiar({ porcentaje: null, valor: propina.valor })
          }}
          className={`flex min-h-[56px] flex-col items-center justify-center rounded-xl border transition active:scale-95 ${
            libre
              ? 'border-ambar-500 bg-ambar-500/15 text-crema-100'
              : 'border-noche-700 bg-noche-850 text-noche-300 hover:bg-noche-800'
          }`}
        >
          <span className="text-base font-bold">Otro</span>
          <span className="text-[0.7rem] text-noche-400">valor libre</span>
        </button>
      </div>

      {libre && (
        <div className="mt-2.5 animate-entrada">
          <input
            autoFocus
            inputMode="numeric"
            value={propina.valor || ''}
            onChange={(e) => escribirValor(e.target.value)}
            placeholder="Valor de la propina en pesos"
            className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-ambar-500 focus:outline-none"
          />
        </div>
      )}
    </section>
  )
}
