import { BellRing, Clock, Users } from 'lucide-react'
import type { MesaEnMapa } from '@/compartido/mockApi'
import { formatoCOP, minutosDesde, tiempoTranscurrido } from '@/compartido/formato'
import { ESTILO_MESA } from '@/compartido/estados'

interface Props {
  mesa: MesaEnMapa
  onTocar: (mesa: MesaEnMapa) => void
}

/**
 * Una mesa del mapa. Toda la tarjeta es el blanco de toque: en un salon con
 * poca luz nadie acierta un boton pequeno.
 */
export function TarjetaMesa({ mesa, onTocar }: Props) {
  const estilo = ESTILO_MESA[mesa.estado]
  const ocupada = mesa.estado !== 'libre' && !!mesa.ordenActivaId
  const minutos = mesa.abiertaEn ? minutosDesde(mesa.abiertaEn) : 0

  return (
    <button
      type="button"
      onClick={() => onTocar(mesa)}
      className={`relative flex min-h-[124px] flex-col rounded-2xl border p-3 text-left transition active:scale-[0.98] ${estilo.borde} ${estilo.fondo} hover:brightness-125`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-2xl font-bold leading-none text-crema-100">{mesa.numero}</p>
          <p className="mt-1 truncate text-xs text-noche-400">
            {mesa.nombre ?? `Mesa ${mesa.numero}`}
          </p>
        </div>
        <span className={`mt-1 h-2.5 w-2.5 shrink-0 rounded-full ${estilo.punto}`} aria-hidden />
      </div>

      {ocupada ? (
        <div className="mt-auto space-y-1.5 pt-2">
          <p className="text-base font-semibold tabular-nums text-crema-100">
            {formatoCOP(mesa.total)}
          </p>
          <div className="flex items-center gap-3 text-xs text-noche-400">
            <span className="inline-flex items-center gap-1">
              <Clock className="h-3.5 w-3.5" aria-hidden />
              {tiempoTranscurrido(mesa.abiertaEn ?? new Date())}
            </span>
            <span className="inline-flex items-center gap-1">
              <Users className="h-3.5 w-3.5" aria-hidden />
              {mesa.comensales}
            </span>
          </div>
        </div>
      ) : (
        <div className="mt-auto pt-2">
          <p className={`text-sm font-medium ${estilo.texto}`}>{estilo.etiqueta}</p>
          <p className="text-xs text-noche-500">{mesa.capacidad} puestos</p>
        </div>
      )}

      {mesa.itemsListos > 0 && (
        <span className="absolute right-2 top-11 inline-flex items-center gap-1 rounded-lg bg-estado-listo px-1.5 py-0.5 text-[0.7rem] font-bold text-noche-950">
          <BellRing className="h-3 w-3" aria-hidden />
          {mesa.itemsListos}
        </span>
      )}

      {mesa.estado === 'cuenta_pedida' && (
        <span className="absolute right-2 top-11 rounded-lg bg-estado-proceso px-1.5 py-0.5 text-[0.7rem] font-bold text-noche-950">
          Cobrar
        </span>
      )}

      {minutos >= 90 && ocupada && (
        <span className="absolute bottom-2 right-2 text-[0.7rem] font-medium text-estado-proceso">
          larga
        </span>
      )}
    </button>
  )
}
