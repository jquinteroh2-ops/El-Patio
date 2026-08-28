import { useMemo } from 'react'
import { CalendarClock, ShoppingBag } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import * as api from '@/compartido/mockApi'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { Reserva } from '@/compartido/tipos'
import { useAvisoNuevaComanda } from '@/cocina/avisoNuevaComanda'
import { useSonidoRecepcion } from './sonido'

/**
 * Las dos bandejas del mostrador: lo que se pide desde la calle y lo que se
 * reserva desde el sitio.
 *
 * El contador de pendientes y el aviso sonoro se consultan aqui y no en cada
 * pantalla para que la solicitud que entra mientras recepcion esta despachando
 * domicilios se oiga y se vea sin cambiar de pestana. Es el mismo motivo por el
 * que la pestana de reservas lleva el numero encima y no dentro.
 */
export function PestanasRecepcion({ activa }: { activa: 'pedidos' | 'reservas' }) {
  const navegar = useNavigate()
  const { activo: sonidoActivo } = useSonidoRecepcion()

  const { datos: reservas, cargando } = useSyncedState<Reserva[]>(
    () => api.listarReservas(),
    [],
    [],
    ['reservas', 'todo'],
  )

  const porResponder = useMemo(
    () => reservas.filter((r) => r.estado === 'solicitada').map((r) => r.id),
    [reservas],
  )
  const pendientes = porResponder.length

  // Tono propio: una reserva importa, pero no corre como un domicilio.
  useAvisoNuevaComanda(porResponder, sonidoActivo, 'reserva', !cargando)

  const clase = (propia: 'pedidos' | 'reservas') =>
    `relative flex h-10 items-center gap-1.5 rounded-lg px-3 text-sm font-medium transition ${
      activa === propia ? 'bg-oro-500 text-noche-950' : 'text-noche-300 hover:bg-noche-800'
    }`

  return (
    <div className="flex rounded-xl border border-noche-700 bg-noche-850 p-1">
      <button type="button" onClick={() => navegar('/recepcion')} className={clase('pedidos')}>
        <ShoppingBag className="h-4 w-4" aria-hidden />
        Pedidos
      </button>
      <button
        type="button"
        onClick={() => navegar('/recepcion/reservas')}
        className={clase('reservas')}
        aria-label={
          pendientes > 0 ? `Reservas, ${pendientes} por responder` : 'Reservas'
        }
      >
        <CalendarClock className="h-4 w-4" aria-hidden />
        Reservas
        {pendientes > 0 && (
          <span
            aria-hidden
            className={`ml-0.5 inline-flex min-w-[1.25rem] items-center justify-center rounded-full px-1.5 text-xs font-bold ${
              activa === 'reservas'
                ? 'bg-noche-950/25 text-noche-950'
                : 'bg-estado-proceso text-noche-950'
            }`}
          >
            {pendientes}
          </span>
        )}
      </button>
    </div>
  )
}
