import { useNavigate } from 'react-router-dom'
import {
  AlertTriangle,
  CalendarClock,
  ChefHat,
  Clock,
  Receipt,
  TrendingUp,
  Users,
  Utensils,
} from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { Alerta, IndicadoresDia, MesaEnMapa } from '@/compartido/mockApi'
import { UMBRAL_ALERTA_ADMIN } from '@/compartido/config'
import { formatoCOP, formatoHora, tiempoTranscurrido } from '@/compartido/formato'
import { useReloj, useSyncedState } from '@/compartido/useSyncedState'
import type { Reserva } from '@/compartido/tipos'
import { ESTILO_MESA, NOMBRE_ZONA } from '@/compartido/estados'
import { Insignia } from '@/componentes/ui/Insignia'
import { Vacio } from '@/componentes/ui/Vacio'

const VACIOS: IndicadoresDia = {
  ventaTotal: 0,
  ordenes: 0,
  ticketPromedio: 0,
  mesasOcupadas: 0,
  mesasTotales: 0,
  propinas: 0,
  inc: 0,
  minutosPromedioPreparacion: 0,
  comensales: 0,
}

const esHoy = (fecha: string): boolean =>
  new Date(fecha).toDateString() === new Date().toDateString()

export default function InicioAdmin() {
  const navegar = useNavigate()

  const { datos: indicadores } = useSyncedState<IndicadoresDia>(
    () => api.indicadoresDia(),
    VACIOS,
    [],
    ['ordenes', 'pagos', 'mesas', 'todo'],
  )
  const { datos: mesas } = useSyncedState<MesaEnMapa[]>(
    () => api.listarMesas(),
    [],
    [],
    ['mesas', 'ordenes', 'todo'],
  )
  const { datos: alertas } = useSyncedState<Alerta[]>(
    () => api.alertas(UMBRAL_ALERTA_ADMIN),
    [],
    [],
    ['ordenes', 'mesas', 'cocina', 'todo'],
  )
  const { datos: reservas } = useSyncedState<Reserva[]>(
    () => api.listarReservas(),
    [],
    [],
    ['reservas', 'todo'],
  )

  useReloj(20000)

  const activas = mesas.filter((m) => m.ordenActivaId)
  const reservasHoy = reservas.filter(
    (r) => esHoy(r.fechaHora) && (r.estado === 'confirmada' || r.estado === 'solicitada'),
  )
  const solicitudes = reservas.filter((r) => r.estado === 'solicitada')

  const tarjetas = [
    { icono: TrendingUp, etiqueta: 'Ventas del día', valor: formatoCOP(indicadores.ventaTotal) },
    { icono: Receipt, etiqueta: 'Cuentas cobradas', valor: String(indicadores.ordenes) },
    { icono: Utensils, etiqueta: 'Ticket promedio', valor: formatoCOP(indicadores.ticketPromedio) },
    {
      icono: Users,
      etiqueta: 'Mesas ocupadas',
      valor: `${indicadores.mesasOcupadas} / ${indicadores.mesasTotales}`,
    },
    {
      icono: ChefHat,
      etiqueta: 'Preparación promedio',
      valor: `${indicadores.minutosPromedioPreparacion} min`,
    },
    { icono: Users, etiqueta: 'Comensales atendidos', valor: String(indicadores.comensales) },
  ]

  return (
    <div className="space-y-4">
      {/* ---------- Indicadores ---------- */}
      <section className="grid grid-cols-2 gap-2.5 lg:grid-cols-6">
        {tarjetas.map(({ icono: Icono, etiqueta, valor }) => (
          <div key={etiqueta} className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
            <Icono className="mb-2 h-4 w-4 text-noche-500" aria-hidden />
            <p className="text-lg font-bold tabular-nums text-crema-100">{valor}</p>
            <p className="text-xs leading-tight text-noche-400">{etiqueta}</p>
          </div>
        ))}
      </section>

      {/* ---------- Alertas ---------- */}
      {alertas.length > 0 && (
        <section className="rounded-2xl border border-estado-demorado/40 bg-estado-demorado-suave p-3">
          <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-estado-demorado">
            <AlertTriangle className="h-4 w-4" aria-hidden />
            Requieren atención
          </h2>
          <ul className="space-y-1.5">
            {alertas.map((alerta) => (
              <li key={alerta.id}>
                <button
                  type="button"
                  onClick={() =>
                    navegar(
                      alerta.tipo === 'cobro'
                        ? `/comandera/mesa/${alerta.mesaId}/cuenta`
                        : `/comandera/mesa/${alerta.mesaId}`,
                    )
                  }
                  className="flex w-full items-center justify-between gap-3 rounded-xl border border-noche-800 bg-noche-900/60 px-3 py-2.5 text-left transition hover:bg-noche-850"
                >
                  <span className="min-w-0 truncate text-sm text-crema-100">{alerta.mensaje}</span>
                  <Insignia tono={alerta.tipo === 'cobro' ? 'proceso' : 'demorado'}>
                    {alerta.tipo === 'cobro' ? 'Cobrar' : 'Demora'}
                  </Insignia>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        {/* ---------- Mesas activas ---------- */}
        <section className="rounded-2xl border border-noche-800 bg-noche-900 p-3 lg:col-span-2">
          <h2 className="mb-2.5 flex items-center justify-between gap-2 text-sm font-semibold text-crema-100">
            <span className="flex items-center gap-2">
              <Utensils className="h-4 w-4" aria-hidden />
              Mesas activas
            </span>
            <span className="text-xs font-normal text-noche-400">
              {formatoCOP(activas.reduce((s, m) => s + m.total, 0))} en salón
            </span>
          </h2>

          {activas.length === 0 ? (
            <Vacio icono={Utensils} titulo="No hay mesas abiertas" />
          ) : (
            <ul className="space-y-1.5">
              {activas.map((mesa) => (
                <li key={mesa.id}>
                  <button
                    type="button"
                    onClick={() => navegar(`/comandera/mesa/${mesa.id}/cuenta`)}
                    className="flex w-full items-center gap-3 rounded-xl border border-noche-800 bg-noche-850 px-3 py-2.5 text-left transition hover:bg-noche-800"
                  >
                    <span
                      className={`h-2.5 w-2.5 shrink-0 rounded-full ${ESTILO_MESA[mesa.estado].punto}`}
                      aria-hidden
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-crema-100">
                        {mesa.nombre ?? `Mesa ${mesa.numero}`}
                        <span className="ml-2 text-xs font-normal text-noche-400">
                          {NOMBRE_ZONA[mesa.zona]}
                        </span>
                      </span>
                      <span className="block truncate text-xs text-noche-400">
                        {mesa.meseroNombre} · {mesa.comensales} comensales
                      </span>
                    </span>
                    <span className="shrink-0 text-right">
                      <span className="block text-sm font-semibold tabular-nums text-crema-100">
                        {formatoCOP(mesa.total)}
                      </span>
                      <span className="flex items-center justify-end gap-1 text-xs text-noche-400">
                        <Clock className="h-3 w-3" aria-hidden />
                        {tiempoTranscurrido(mesa.abiertaEn ?? new Date())}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* ---------- Reservas de hoy ---------- */}
        <section className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
          <h2 className="mb-2.5 flex items-center justify-between gap-2 text-sm font-semibold text-crema-100">
            <span className="flex items-center gap-2">
              <CalendarClock className="h-4 w-4" aria-hidden />
              Reservas de hoy
            </span>
            {solicitudes.length > 0 && (
              <button
                type="button"
                onClick={() => navegar('/admin/reservas')}
                className="rounded-lg bg-oro-500/15 px-2 py-0.5 text-xs font-semibold text-oro-300 transition hover:bg-oro-500/25"
              >
                {solicitudes.length} por responder
              </button>
            )}
          </h2>

          {reservasHoy.length === 0 ? (
            <Vacio icono={CalendarClock} titulo="Sin reservas para hoy" />
          ) : (
            <ul className="space-y-1.5">
              {reservasHoy.map((reserva) => (
                <li
                  key={reserva.id}
                  className="rounded-xl border border-noche-800 bg-noche-850 px-3 py-2.5"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-crema-100">
                        {reserva.nombreCliente}
                      </p>
                      <p className="text-xs text-noche-400">
                        {formatoHora(reserva.fechaHora)} · {reserva.personas} personas
                      </p>
                    </div>
                    <Insignia tono={reserva.estado === 'confirmada' ? 'listo' : 'proceso'}>
                      {reserva.estado === 'confirmada' ? 'Confirmada' : 'Por responder'}
                    </Insignia>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
