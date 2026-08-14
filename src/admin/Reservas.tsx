import { useMemo, useState } from 'react'
import { CalendarClock, Cake, Check, Clock, Phone, Users, X } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { MesaEnMapa } from '@/compartido/mockApi'
import { formatoFechaLarga, formatoHora, formatoTelefono } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import { mensajeConfirmacion, mensajePropuesta, mensajeRechazo } from '@/compartido/whatsapp'
import type { EstadoReserva, Ocasion, Reserva } from '@/compartido/tipos'
import { NOMBRE_ZONA } from '@/compartido/estados'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Insignia } from '@/componentes/ui/Insignia'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'
import { ModalWhatsApp } from './ModalWhatsApp'

const NOMBRE_OCASION: Record<Ocasion, string> = {
  cumpleanos: 'Cumpleaños',
  aniversario: 'Aniversario',
  negocios: 'Negocios',
  ninguna: '',
}

const ETIQUETA_ESTADO: Record<EstadoReserva, { texto: string; tono: 'neutro' | 'listo' | 'proceso' | 'demorado' }> = {
  solicitada: { texto: 'Por responder', tono: 'proceso' },
  confirmada: { texto: 'Confirmada', tono: 'listo' },
  cancelada: { texto: 'Rechazada', tono: 'demorado' },
  cumplida: { texto: 'Cumplida', tono: 'neutro' },
  no_asistio: { texto: 'No asistió', tono: 'demorado' },
}

type Filtro = 'pendientes' | 'proximas' | 'todas'

/** Convierte una fecha ISO al formato que exige input[type=datetime-local]. */
const paraCampo = (iso: string): string => {
  const f = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${f.getFullYear()}-${pad(f.getMonth() + 1)}-${pad(f.getDate())}T${pad(f.getHours())}:${pad(f.getMinutes())}`
}

export default function Reservas() {
  const { mostrar } = useAvisos()
  const { datos: reservas } = useSyncedState<Reserva[]>(
    () => api.listarReservas(),
    [],
    [],
    ['reservas', 'todo'],
  )
  const { datos: mesas } = useSyncedState<MesaEnMapa[]>(() => api.listarMesas(), [], [], ['mesas', 'todo'])

  const [filtro, setFiltro] = useState<Filtro>('pendientes')
  const [ocupado, setOcupado] = useState(false)

  const [confirmando, setConfirmando] = useState<Reserva | null>(null)
  const [mesaElegida, setMesaElegida] = useState<string>('')
  const [proponiendo, setProponiendo] = useState<Reserva | null>(null)
  const [nuevaFecha, setNuevaFecha] = useState('')
  const [whatsapp, setWhatsapp] = useState<{ titulo: string; telefono: string; mensaje: string } | null>(null)

  const visibles = useMemo(() => {
    const ahora = Date.now()
    if (filtro === 'pendientes') return reservas.filter((r) => r.estado === 'solicitada')
    if (filtro === 'proximas')
      return reservas.filter(
        (r) => r.estado === 'confirmada' && new Date(r.fechaHora).getTime() >= ahora - 3600000,
      )
    return reservas
  }, [reservas, filtro])

  const pendientes = reservas.filter((r) => r.estado === 'solicitada').length

  const ejecutar = async (accion: () => Promise<string>) => {
    setOcupado(true)
    try {
      mostrar(await accion(), 'exito')
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo actualizar la reserva', 'error')
    } finally {
      setOcupado(false)
    }
  }

  const confirmar = () =>
    ejecutar(async () => {
      if (!confirmando) throw new Error('No hay reserva seleccionada')
      await api.cambiarEstadoReserva(confirmando.id, 'confirmada', mesaElegida || undefined)
      const mesa = mesas.find((m) => m.id === mesaElegida)
      const etiqueta = mesa ? (mesa.nombre ?? `la mesa ${mesa.numero}`) : undefined

      setWhatsapp({
        titulo: 'Confirmar por WhatsApp',
        telefono: confirmando.telefono,
        mensaje: mensajeConfirmacion(confirmando, etiqueta),
      })
      setConfirmando(null)
      setMesaElegida('')
      return 'Reserva confirmada'
    })

  const proponer = () =>
    ejecutar(async () => {
      if (!proponiendo) throw new Error('No hay reserva seleccionada')
      if (!nuevaFecha) throw new Error('Elige el horario que vas a proponer')
      const iso = new Date(nuevaFecha).toISOString()

      // La reserva sigue solicitada: no se mueve hasta que el cliente acepte.
      setWhatsapp({
        titulo: 'Proponer otro horario',
        telefono: proponiendo.telefono,
        mensaje: mensajePropuesta(proponiendo, iso),
      })
      setProponiendo(null)
      setNuevaFecha('')
      return 'Propuesta lista para enviar'
    })

  const rechazar = (reserva: Reserva) =>
    ejecutar(async () => {
      await api.cambiarEstadoReserva(reserva.id, 'cancelada')
      setWhatsapp({
        titulo: 'Avisar que no hay disponibilidad',
        telefono: reserva.telefono,
        mensaje: mensajeRechazo(reserva),
      })
      return 'Reserva rechazada'
    })

  const marcar = (reserva: Reserva, estado: EstadoReserva) =>
    ejecutar(async () => {
      await api.cambiarEstadoReserva(reserva.id, estado)
      return estado === 'cumplida' ? 'Marcada como cumplida' : 'Marcada como no asistió'
    })

  const librePara = (personas: number) =>
    mesas.filter((m) => m.estado === 'libre' && m.capacidad >= personas)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {(
          [
            ['pendientes', `Por responder${pendientes ? ` (${pendientes})` : ''}`],
            ['proximas', 'Confirmadas'],
            ['todas', 'Todas'],
          ] as const
        ).map(([id, etiqueta]) => (
          <button
            key={id}
            type="button"
            onClick={() => setFiltro(id)}
            className={`min-h-[40px] rounded-xl border px-3.5 text-sm font-medium transition ${
              filtro === id
                ? 'border-ambar-500 bg-ambar-500/15 text-ambar-300'
                : 'border-noche-700 bg-noche-900 text-noche-300 hover:bg-noche-800'
            }`}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      {visibles.length === 0 ? (
        <Vacio
          icono={CalendarClock}
          titulo={filtro === 'pendientes' ? 'No hay solicitudes por responder' : 'Nada por aquí'}
          descripcion="Las solicitudes del sitio público aparecen aquí de inmediato."
        />
      ) : (
        <ul className="grid gap-2.5 lg:grid-cols-2">
          {visibles.map((reserva) => {
            const estado = ETIQUETA_ESTADO[reserva.estado]
            const ocasion = reserva.ocasion ? NOMBRE_OCASION[reserva.ocasion] : ''
            const mesa = mesas.find((m) => m.id === reserva.mesaAsignadaId)

            return (
              <li
                key={reserva.id}
                className="rounded-2xl border border-noche-800 bg-noche-900 p-3.5"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="truncate text-base font-semibold text-crema-100">
                      {reserva.nombreCliente}
                    </p>
                    <p className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-noche-400">
                      <span className="inline-flex items-center gap-1">
                        <Clock className="h-3 w-3" aria-hidden />
                        {formatoFechaLarga(reserva.fechaHora)}, {formatoHora(reserva.fechaHora)}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <Users className="h-3 w-3" aria-hidden />
                        {reserva.personas}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <Phone className="h-3 w-3" aria-hidden />
                        {formatoTelefono(reserva.telefono)}
                      </span>
                    </p>
                  </div>
                  <Insignia tono={estado.tono}>{estado.texto}</Insignia>
                </div>

                {(ocasion || reserva.notas || mesa) && (
                  <div className="mt-2 space-y-1">
                    {ocasion && (
                      <p className="inline-flex items-center gap-1.5 text-xs text-ambar-300">
                        <Cake className="h-3.5 w-3.5" aria-hidden />
                        {ocasion}
                      </p>
                    )}
                    {mesa && (
                      <p className="text-xs text-noche-400">
                        Mesa asignada: {mesa.nombre ?? `Mesa ${mesa.numero}`} ({NOMBRE_ZONA[mesa.zona]})
                      </p>
                    )}
                    {reserva.notas && (
                      <p className="rounded-lg border border-noche-800 bg-noche-850 px-2.5 py-1.5 text-xs italic text-noche-300">
                        {reserva.notas}
                      </p>
                    )}
                  </div>
                )}

                {reserva.estado === 'solicitada' && (
                  <div className="mt-3 grid grid-cols-3 gap-2">
                    <Boton
                      variante="exito"
                      tamano="compacto"
                      disabled={ocupado}
                      icono={<Check className="h-4 w-4" />}
                      onClick={() => {
                        setConfirmando(reserva)
                        setMesaElegida('')
                      }}
                    >
                      Confirmar
                    </Boton>
                    <Boton
                      tamano="compacto"
                      disabled={ocupado}
                      onClick={() => {
                        setProponiendo(reserva)
                        setNuevaFecha(paraCampo(reserva.fechaHora))
                      }}
                    >
                      Otro horario
                    </Boton>
                    <Boton
                      variante="peligro"
                      tamano="compacto"
                      disabled={ocupado}
                      icono={<X className="h-4 w-4" />}
                      onClick={() => rechazar(reserva)}
                    >
                      Rechazar
                    </Boton>
                  </div>
                )}

                {reserva.estado === 'confirmada' && (
                  <div className="mt-3 grid grid-cols-2 gap-2">
                    <Boton tamano="compacto" disabled={ocupado} onClick={() => marcar(reserva, 'cumplida')}>
                      Llegó
                    </Boton>
                    <Boton
                      variante="peligro"
                      tamano="compacto"
                      disabled={ocupado}
                      onClick={() => marcar(reserva, 'no_asistio')}
                    >
                      No asistió
                    </Boton>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}

      {/* ---------- Confirmar y asignar mesa ---------- */}
      <HojaInferior
        abierta={!!confirmando}
        titulo={confirmando ? `Confirmar a ${confirmando.nombreCliente}` : ''}
        descripcion={
          confirmando
            ? `${confirmando.personas} personas · ${formatoHora(confirmando.fechaHora)}`
            : undefined
        }
        onCerrar={() => setConfirmando(null)}
        pie={
          <Boton variante="exito" tamano="grande" bloque cargando={ocupado} onClick={confirmar}>
            Confirmar y redactar WhatsApp
          </Boton>
        }
      >
        <div className="space-y-3">
          <p className="text-sm text-noche-300">Asignar mesa (opcional)</p>
          <div className="grid grid-cols-3 gap-2">
            <button
              type="button"
              onClick={() => setMesaElegida('')}
              className={`min-h-[56px] rounded-xl border text-sm transition ${
                mesaElegida === ''
                  ? 'border-ambar-500 bg-ambar-500/15 text-crema-100'
                  : 'border-noche-700 bg-noche-850 text-noche-300'
              }`}
            >
              Sin asignar
            </button>
            {confirmando &&
              librePara(confirmando.personas).map((mesa) => (
                <button
                  key={mesa.id}
                  type="button"
                  onClick={() => setMesaElegida(mesa.id)}
                  className={`flex min-h-[56px] flex-col items-center justify-center rounded-xl border transition ${
                    mesaElegida === mesa.id
                      ? 'border-ambar-500 bg-ambar-500/15 text-crema-100'
                      : 'border-noche-700 bg-noche-850 text-noche-300 hover:bg-noche-800'
                  }`}
                >
                  <span className="text-base font-bold">{mesa.numero}</span>
                  <span className="text-[0.7rem] text-noche-400">
                    {NOMBRE_ZONA[mesa.zona]} · {mesa.capacidad}p
                  </span>
                </button>
              ))}
          </div>
          <p className="text-xs text-noche-500">
            Al asignarla, la mesa queda marcada como reservada en el mapa del salón.
          </p>
        </div>
      </HojaInferior>

      {/* ---------- Proponer otro horario ---------- */}
      <HojaInferior
        abierta={!!proponiendo}
        titulo="Proponer otro horario"
        descripcion={proponiendo ? proponiendo.nombreCliente : undefined}
        onCerrar={() => setProponiendo(null)}
        pie={
          <Boton variante="principal" tamano="grande" bloque cargando={ocupado} onClick={proponer}>
            Redactar propuesta
          </Boton>
        }
      >
        <div className="space-y-3">
          <label className="block">
            <span className="mb-1.5 block text-sm text-noche-300">Nuevo día y hora</span>
            <input
              type="datetime-local"
              value={nuevaFecha}
              onChange={(e) => setNuevaFecha(e.target.value)}
              className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 focus:border-ambar-500 focus:outline-none"
            />
          </label>
          <p className="text-xs text-noche-500">
            La reserva queda como solicitada hasta que el cliente acepte el nuevo horario.
          </p>
        </div>
      </HojaInferior>

      <ModalWhatsApp
        abierto={!!whatsapp}
        titulo={whatsapp?.titulo ?? ''}
        telefono={whatsapp?.telefono ?? ''}
        mensaje={whatsapp?.mensaje ?? ''}
        onCerrar={() => setWhatsapp(null)}
      />
    </div>
  )
}
