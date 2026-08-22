import { Check, ChefHat, StickyNote } from 'lucide-react'
import type { TurnoEnCocina } from '@/compartido/mockApi'
import { formatoHora, minutosDesde } from '@/compartido/formato'
import { fondoEspera } from '@/compartido/estados'
import type { EstadoItem } from '@/compartido/tipos'

interface Props {
  bloque: TurnoEnCocina
  /** Recien llegada: se resalta unos segundos para que nadie la pase por alto. */
  reciente: boolean
  ocupado: boolean
  onCambiarItem: (itemId: string, estado: EstadoItem) => void
  onCambiarTurno: (estado: EstadoItem) => void
}

const ACCION: Record<TurnoEnCocina['estado'], { etiqueta: string; siguiente: EstadoItem; clase: string }> = {
  pendiente: {
    etiqueta: 'Empezar',
    siguiente: 'en_preparacion',
    clase: 'bg-estado-proceso text-noche-950 hover:brightness-110',
  },
  en_preparacion: {
    etiqueta: 'Marcar listo',
    siguiente: 'listo',
    clase: 'bg-estado-listo text-noche-950 hover:brightness-110',
  },
  listo: {
    etiqueta: 'Entregado a mesa',
    siguiente: 'servido',
    clase: 'bg-noche-700 text-crema-100 hover:bg-noche-600',
  },
  servido: {
    etiqueta: 'Servido',
    siguiente: 'servido',
    clase: 'bg-noche-800 text-noche-400',
  },
}

/**
 * Una comanda de un turno. Pensada para leerse desde un metro de distancia:
 * mesa grande, cronometro con fondo solido y un solo boton que ocupa el ancho.
 */
export function TarjetaComanda({ bloque, reciente, ocupado, onCambiarItem, onCambiarTurno }: Props) {
  const espera = minutosDesde(bloque.enviadoEn)
  const accion = ACCION[bloque.estado]

  return (
    <article
      className={`overflow-hidden rounded-2xl border-2 bg-noche-900 transition-colors duration-500 ${
        reciente ? 'animate-destello border-oro-400' : 'border-noche-700'
      }`}
    >
      <header className="flex items-center justify-between gap-2 border-b border-noche-800 px-3 py-2.5">
        <div className="min-w-0">
          <p className="truncate text-xl font-bold leading-tight text-crema-100">
            {bloque.mesaEtiqueta}
          </p>
          <p className="text-xs text-noche-400">
            Turno {bloque.turno} · #{bloque.numeroOrden} · {formatoHora(bloque.enviadoEn)}
          </p>
        </div>
        <span
          className={`shrink-0 rounded-xl border px-2.5 py-1.5 text-xl font-bold tabular-nums ${fondoEspera(espera)}`}
        >
          {espera}
          <span className="ml-0.5 text-xs font-semibold">min</span>
        </span>
      </header>

      {bloque.notas && (
        <p className="flex items-start gap-1.5 border-b border-noche-800 bg-oro-500/10 px-3 py-2 text-sm text-oro-200">
          <StickyNote className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
          {bloque.notas}
        </p>
      )}

      <ul className="divide-y divide-noche-800">
        {bloque.items.map((item) => {
          const listo = item.estado === 'listo'
          return (
            <li key={item.id}>
              <button
                type="button"
                disabled={ocupado}
                onClick={() => onCambiarItem(item.id, listo ? 'en_preparacion' : 'listo')}
                className={`flex w-full items-start gap-2.5 px-3 py-2.5 text-left transition active:scale-[0.99] ${
                  listo ? 'bg-estado-listo-suave' : 'hover:bg-noche-850'
                }`}
              >
                <span
                  className={`mt-0.5 flex h-7 min-w-[28px] items-center justify-center rounded-lg px-1 text-base font-bold ${
                    listo ? 'bg-estado-listo text-noche-950' : 'bg-noche-700 text-crema-100'
                  }`}
                >
                  {item.cantidad}
                </span>

                <span className="min-w-0 flex-1">
                  <span
                    className={`block text-base font-semibold leading-snug ${
                      listo ? 'text-estado-listo line-through' : 'text-crema-100'
                    }`}
                  >
                    {item.nombre}
                  </span>
                  {item.modificadoresSeleccionados.length > 0 && (
                    <span className="mt-0.5 block text-sm font-medium text-oro-300">
                      {item.modificadoresSeleccionados.map((m) => m.valor).join(' · ')}
                    </span>
                  )}
                  {item.notaCocina && (
                    <span className="mt-0.5 block text-sm italic text-estado-proceso">
                      {item.notaCocina}
                    </span>
                  )}
                </span>

                {listo && <Check className="mt-1 h-5 w-5 shrink-0 text-estado-listo" aria-hidden />}
              </button>
            </li>
          )
        })}
      </ul>

      <footer className="p-2">
        <button
          type="button"
          disabled={ocupado}
          onClick={() => onCambiarTurno(accion.siguiente)}
          className={`flex min-h-[52px] w-full items-center justify-center gap-2 rounded-xl text-base font-bold transition active:scale-[0.98] disabled:opacity-50 ${accion.clase}`}
        >
          <ChefHat className="h-5 w-5" aria-hidden />
          {accion.etiqueta}
        </button>
      </footer>
    </article>
  )
}
