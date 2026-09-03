import { useEffect, useState } from 'react'
import { CalendarPlus } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { MesaEnMapa } from '@/compartido/mockApi'
import { claveDia, formatoHora } from '@/compartido/formato'
import { NOMBRE_ZONA } from '@/compartido/estados'
import type { Canal, Ocasion, Reserva } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Campo, CampoArea, CampoSelect } from '@/componentes/ui/Campo'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { useAvisos } from '@/componentes/ui/Avisos'

/**
 * La reserva que anota el personal, no el cliente.
 *
 * Hasta ahora una reserva solo podia nacer del formulario del sitio público, y
 * la mayoría no nacen ahí: nacen en un WhatsApp o en una llamada. Esas se
 * apuntaban en una libreta y no existían para el sistema —no sonaban, no
 * separaban mesa y no aparecían en ninguna lista—. Este formulario es esa
 * libreta, pero dentro.
 *
 * Se monta en el panel de reservas, que es el mismo que ven administración y
 * recepción, así que las dos áreas la anotan igual.
 */

const OCASIONES: { id: Ocasion; etiqueta: string }[] = [
  { id: 'ninguna', etiqueta: 'Sin ocasión' },
  { id: 'cumpleanos', etiqueta: 'Cumpleaños' },
  { id: 'aniversario', etiqueta: 'Aniversario' },
  { id: 'negocios', etiqueta: 'Negocios' },
]

/** Por dónde pidió el cliente. No incluye 'web': eso lo escribe el sitio solo. */
const CANALES: { id: Canal; etiqueta: string }[] = [
  { id: 'whatsapp', etiqueta: 'WhatsApp' },
  { id: 'telefono', etiqueta: 'Teléfono' },
  { id: 'presencial', etiqueta: 'En el restaurante' },
]

/** Horas de servicio, en pasos de media hora. Las mismas del sitio público. */
const HORAS = Array.from({ length: 21 }, (_, i) => {
  const minutos = 12 * 60 + i * 30
  const h = Math.floor(minutos / 60)
  return `${String(h).padStart(2, '0')}:${minutos % 60 === 0 ? '00' : '30'}`
})

interface Props {
  abierta: boolean
  onCerrar: () => void
  /** Para separar mesa en el mismo gesto, sin volver a la lista. */
  mesas: MesaEnMapa[]
  /** Se llama con la reserva ya creada, para ofrecer el WhatsApp de confirmación. */
  onCreada: (reserva: Reserva) => void
}

export function NuevaReserva({ abierta, onCerrar, mesas, onCreada }: Props) {
  const { mostrar } = useAvisos()

  const [canal, setCanal] = useState<Canal>('whatsapp')
  const [nombre, setNombre] = useState('')
  const [telefono, setTelefono] = useState('')
  const [fecha, setFecha] = useState(claveDia())
  const [hora, setHora] = useState('19:00')
  const [personas, setPersonas] = useState(2)
  const [ocasion, setOcasion] = useState<Ocasion>('ninguna')
  const [notas, setNotas] = useState('')
  // Quien anota una llamada casi siempre ya dijo que sí: ese es el caso normal
  // y por eso arranca encendido. Se apaga cuando solo se está tomando nota y la
  // disponibilidad se mira después.
  const [confirmada, setConfirmada] = useState(true)
  const [mesaId, setMesaId] = useState('')
  const [guardando, setGuardando] = useState(false)

  // Cada vez que se abre, el formulario arranca limpio: la reserva anterior ya
  // quedó guardada y sus datos en pantalla solo sirven para equivocarse.
  useEffect(() => {
    if (!abierta) return
    setCanal('whatsapp')
    setNombre('')
    setTelefono('')
    setFecha(claveDia())
    setHora('19:00')
    setPersonas(2)
    setOcasion('ninguna')
    setNotas('')
    setConfirmada(true)
    setMesaId('')
  }, [abierta])

  const digitos = telefono.replace(/\D/g, '')
  const problema =
    !nombre.trim()
      ? 'Escriba el nombre de quien reserva'
      : digitos.length < 10
        ? 'El teléfono va con 10 dígitos, para poder confirmarle'
        : null

  const libres = mesas.filter((m) => m.estado === 'libre' && m.capacidad >= personas)

  const guardar = async () => {
    if (problema) {
      mostrar(problema, 'error')
      return
    }
    setGuardando(true)
    try {
      const reserva = await api.crearReservaDeMostrador({
        nombreCliente: nombre.trim(),
        telefono: digitos,
        fechaHora: new Date(`${fecha}T${hora}:00`).toISOString(),
        personas,
        ocasion,
        notas: notas.trim() || undefined,
        canal,
        confirmada,
        mesaAsignadaId: confirmada && mesaId ? mesaId : undefined,
      })
      mostrar(
        confirmada ? `Reserva de ${reserva.nombreCliente} confirmada` : 'Reserva anotada',
        'exito',
      )
      onCerrar()
      onCreada(reserva)
    } catch (e) {
      // El aviso deja la hoja abierta a propósito: lo escrito no se pierde y se
      // corrige lo que el servidor rechazó sin volver a teclearlo.
      mostrar(e instanceof Error ? e.message : 'No se pudo guardar la reserva', 'error')
    } finally {
      setGuardando(false)
    }
  }

  const opcion = (activa: boolean) =>
    `min-h-[44px] rounded-xl border px-3 text-sm transition ${
      activa
        ? 'border-oro-500 bg-oro-500/15 text-oro-300'
        : 'border-noche-700 bg-noche-850 text-noche-300 hover:border-noche-600'
    }`

  return (
    <HojaInferior
      abierta={abierta}
      titulo="Nueva reserva"
      descripcion="La que pidieron por WhatsApp, por teléfono o en la puerta."
      onCerrar={onCerrar}
      onEnviar={guardar}
      pie={
        <Boton
          variante="exito"
          tamano="grande"
          bloque
          cargando={guardando}
          type="submit"
          icono={<CalendarPlus className="h-5 w-5" aria-hidden />}
        >
          {confirmada ? 'Guardar y confirmar' : 'Guardar como solicitud'}
        </Boton>
      }
    >
      <div className="space-y-4">
        <div>
          <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-noche-400">
            ¿Por dónde pidió?
          </p>
          <div className="grid grid-cols-3 gap-2">
            {CANALES.map((c) => (
              <button key={c.id} type="button" onClick={() => setCanal(c.id)} className={opcion(canal === c.id)}>
                {c.etiqueta}
              </button>
            ))}
          </div>
        </div>

        <Campo
          etiqueta="Nombre"
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
          placeholder="Carolina Mendoza"
        />

        <Campo
          etiqueta="Celular"
          value={telefono}
          onChange={(e) => setTelefono(e.target.value)}
          inputMode="tel"
          placeholder="300 123 4567"
          ayuda="Es a este número al que se le confirma."
        />

        <div className="grid grid-cols-2 gap-3">
          <Campo
            etiqueta="Día"
            type="date"
            value={fecha}
            min={claveDia()}
            onChange={(e) => setFecha(e.target.value)}
          />
          <CampoSelect etiqueta="Hora" value={hora} onChange={(e) => setHora(e.target.value)}>
            {HORAS.map((h) => (
              <option key={h} value={h}>
                {formatoHora(new Date(`2026-01-01T${h}:00`))}
              </option>
            ))}
          </CampoSelect>
        </div>

        <div>
          <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-noche-400">
            Personas
          </p>
          <div className="flex flex-wrap gap-2">
            {[2, 3, 4, 6, 8, 10, 12].map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => setPersonas(n)}
                className={`w-12 ${opcion(personas === n)}`}
              >
                {n}
              </button>
            ))}
            <input
              inputMode="numeric"
              value={personas}
              onChange={(e) => setPersonas(Math.max(1, Number(e.target.value.replace(/\D/g, '')) || 1))}
              aria-label="Otro número de personas"
              className="min-h-[44px] w-20 rounded-xl border border-noche-700 bg-noche-850 px-3 text-center text-crema-100 focus:border-oro-500 focus:outline-none"
            />
          </div>
        </div>

        <div>
          <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-noche-400">Ocasión</p>
          <div className="grid grid-cols-2 gap-2">
            {OCASIONES.map((o) => (
              <button key={o.id} type="button" onClick={() => setOcasion(o.id)} className={opcion(ocasion === o.id)}>
                {o.etiqueta}
              </button>
            ))}
          </div>
        </div>

        <CampoArea
          etiqueta="Notas"
          rows={2}
          value={notas}
          onChange={(e) => setNotas(e.target.value)}
          placeholder="Preferencia de mesa, alergias, decoración…"
        />

        {/* ---------- ¿Queda en firme o solo anotada? ---------- */}
        <div className="rounded-xl border border-noche-700 bg-noche-850 p-3">
          <div className="grid grid-cols-2 gap-2">
            <button type="button" onClick={() => setConfirmada(true)} className={opcion(confirmada)}>
              Ya le dije que sí
            </button>
            <button type="button" onClick={() => setConfirmada(false)} className={opcion(!confirmada)}>
              Solo anotarla
            </button>
          </div>
          <p className="mt-2 text-xs leading-relaxed text-noche-500">
            {confirmada
              ? 'Queda confirmada y se le puede separar mesa.'
              : 'Entra en «por responder», igual que una solicitud del sitio, y se confirma después.'}
          </p>

          {confirmada && (
            <div className="mt-3">
              <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-noche-400">
                Separar mesa (opcional)
              </p>
              <div className="grid grid-cols-3 gap-2">
                <button type="button" onClick={() => setMesaId('')} className={`min-h-[52px] ${opcion(mesaId === '')}`}>
                  Sin asignar
                </button>
                {libres.map((mesa) => (
                  <button
                    key={mesa.id}
                    type="button"
                    onClick={() => setMesaId(mesa.id)}
                    className={`flex min-h-[52px] flex-col items-center justify-center ${opcion(mesaId === mesa.id)}`}
                  >
                    <span className="text-base font-bold text-crema-100">{mesa.numero}</span>
                    <span className="text-[0.7rem] text-noche-400">
                      {NOMBRE_ZONA[mesa.zona]} · {mesa.capacidad}p
                    </span>
                  </button>
                ))}
              </div>
              {libres.length === 0 && (
                <p className="mt-2 text-xs text-noche-500">
                  Ninguna mesa libre para {personas} personas ahora mismo. La reserva se guarda
                  igual y la mesa se asigna cuando se desocupe.
                </p>
              )}
            </div>
          )}
        </div>
      </div>
    </HojaInferior>
  )
}
