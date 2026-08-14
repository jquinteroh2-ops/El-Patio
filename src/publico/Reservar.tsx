import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Check, MessageCircle } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import { RESTAURANTE } from '@/compartido/config'
import { claveDia, formatoFechaLarga, formatoHora } from '@/compartido/formato'
import { enlaceWhatsApp } from '@/compartido/whatsapp'
import type { Ocasion } from '@/compartido/tipos'
import { Filete, Ornamento } from './Ornamento'

const OCASIONES: { id: Ocasion; etiqueta: string }[] = [
  { id: 'ninguna', etiqueta: 'Sin ocasión especial' },
  { id: 'cumpleanos', etiqueta: 'Cumpleaños' },
  { id: 'aniversario', etiqueta: 'Aniversario' },
  { id: 'negocios', etiqueta: 'Negocios' },
]

/** Horas de servicio, en pasos de media hora. */
const HORAS = Array.from({ length: 21 }, (_, i) => {
  const minutos = 12 * 60 + i * 30
  const h = Math.floor(minutos / 60)
  return `${String(h).padStart(2, '0')}:${minutos % 60 === 0 ? '00' : '30'}`
})

const CAMPO =
  'min-h-[52px] w-full rounded-sm border border-crema-100/25 bg-bosque-900/60 px-4 text-crema-100 ' +
  'placeholder:text-crema-100/35 focus:border-ambar-400 focus:outline-none transition'

export default function Reservar() {
  const [nombre, setNombre] = useState('')
  const [telefono, setTelefono] = useState('')
  const [fecha, setFecha] = useState(claveDia())
  const [hora, setHora] = useState('19:00')
  const [personas, setPersonas] = useState(2)
  const [ocasion, setOcasion] = useState<Ocasion>('ninguna')
  const [notas, setNotas] = useState('')

  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [enviada, setEnviada] = useState<{ fechaHora: string } | null>(null)

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault()
    setError(null)

    const digitos = telefono.replace(/\D/g, '')
    if (digitos.length < 10) {
      setError('Escribe un número de celular de 10 dígitos para poder confirmarte.')
      return
    }

    setEnviando(true)
    try {
      const fechaHora = new Date(`${fecha}T${hora}:00`).toISOString()
      await api.crearReserva({
        nombreCliente: nombre.trim(),
        telefono: digitos,
        fechaHora,
        personas,
        ocasion,
        notas: notas.trim() || undefined,
      })
      setEnviada({ fechaHora })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo enviar la solicitud')
    } finally {
      setEnviando(false)
    }
  }

  // ---- Confirmación ----
  if (enviada) {
    return (
      <section className="mx-auto max-w-2xl px-5 py-24 text-center">
        <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full border border-ambar-400/50 text-ambar-300">
          <Check className="h-7 w-7" aria-hidden />
        </span>

        <h1 className="mt-8 font-titulo text-4xl font-light leading-tight text-crema-100 sm:text-5xl">
          Recibimos tu solicitud
        </h1>
        <p className="mt-5 text-lg text-crema-100/75">Te confirmamos por WhatsApp.</p>

        <Filete className="mx-auto mt-8 w-32 text-ambar-400" />

        <div className="mx-auto mt-8 max-w-sm rounded-sm border border-crema-100/15 bg-bosque-900/50 p-5 text-left">
          <dl className="space-y-2.5 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-crema-100/55">A nombre de</dt>
              <dd className="text-crema-100">{nombre}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-crema-100/55">Día</dt>
              <dd className="text-right text-crema-100">{formatoFechaLarga(enviada.fechaHora)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-crema-100/55">Hora</dt>
              <dd className="text-crema-100">{formatoHora(enviada.fechaHora)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-crema-100/55">Personas</dt>
              <dd className="text-crema-100">{personas}</dd>
            </div>
          </dl>
        </div>

        <p className="mx-auto mt-7 max-w-md text-sm leading-relaxed text-crema-100/55">
          La reserva queda apartada cuando te escribamos. Si necesitas algo antes, escríbenos
          directamente.
        </p>

        <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
          <a
            href={enlaceWhatsApp(
              RESTAURANTE.whatsapp,
              `Hola, acabo de solicitar una reserva a nombre de ${nombre} para el ${formatoFechaLarga(enviada.fechaHora)} a las ${formatoHora(enviada.fechaHora)}.`,
            )}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex min-h-[52px] items-center justify-center gap-2 rounded-sm border border-crema-100/30 px-8 text-sm uppercase tracking-[0.16em] text-crema-100 transition hover:border-ambar-400 hover:text-ambar-300"
          >
            <MessageCircle className="h-4 w-4" aria-hidden />
            Escribir por WhatsApp
          </a>
          <Link
            to="/carta"
            className="min-h-[52px] rounded-sm bg-ambar-500 px-8 text-sm font-semibold uppercase tracking-[0.16em] leading-[52px] text-bosque-950 transition hover:bg-ambar-400"
          >
            Ver la carta
          </Link>
        </div>
      </section>
    )
  }

  // ---- Formulario ----
  return (
    <section className="mx-auto max-w-xl px-5 py-16">
      <div className="text-center">
        <Ornamento className="mx-auto mb-6 h-14 w-24 text-ambar-400/60" />
        <p className="text-[0.7rem] uppercase tracking-[0.35em] text-ambar-400">Reservas</p>
        <h1 className="mt-4 font-titulo text-5xl font-light text-crema-100">Reserve su mesa</h1>
        <p className="mx-auto mt-5 max-w-md text-base leading-relaxed text-crema-100/65">
          Déjenos sus datos y le confirmamos por WhatsApp. Toma menos de un minuto.
        </p>
      </div>

      <form onSubmit={enviar} className="mt-10 space-y-5">
        <label className="block">
          <span className="mb-2 block text-xs uppercase tracking-[0.18em] text-crema-100/55">
            Nombre completo
          </span>
          <input
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            required
            autoComplete="name"
            placeholder="Carolina Mendoza"
            className={CAMPO}
          />
        </label>

        <label className="block">
          <span className="mb-2 block text-xs uppercase tracking-[0.18em] text-crema-100/55">
            Celular
          </span>
          <input
            value={telefono}
            onChange={(e) => setTelefono(e.target.value)}
            required
            inputMode="tel"
            autoComplete="tel"
            placeholder="300 123 4567"
            className={CAMPO}
          />
        </label>

        <div className="grid grid-cols-2 gap-4">
          <label className="block">
            <span className="mb-2 block text-xs uppercase tracking-[0.18em] text-crema-100/55">
              Día
            </span>
            <input
              type="date"
              value={fecha}
              min={claveDia()}
              onChange={(e) => setFecha(e.target.value)}
              required
              className={CAMPO}
            />
          </label>

          <label className="block">
            <span className="mb-2 block text-xs uppercase tracking-[0.18em] text-crema-100/55">
              Hora
            </span>
            <select value={hora} onChange={(e) => setHora(e.target.value)} className={CAMPO}>
              {HORAS.map((h) => (
                <option key={h} value={h}>
                  {formatoHora(new Date(`2026-01-01T${h}:00`))}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div>
          <span className="mb-2 block text-xs uppercase tracking-[0.18em] text-crema-100/55">
            ¿Cuántas personas?
          </span>
          <div className="flex flex-wrap gap-2">
            {[2, 3, 4, 6, 8, 10, 12].map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => setPersonas(n)}
                className={`min-h-[48px] w-14 rounded-sm border text-base transition ${
                  personas === n
                    ? 'border-ambar-400 bg-ambar-500/15 text-ambar-300'
                    : 'border-crema-100/25 text-crema-100/70 hover:border-ambar-400/60'
                }`}
              >
                {n}
              </button>
            ))}
            <input
              inputMode="numeric"
              value={personas}
              onChange={(e) => setPersonas(Math.max(1, Number(e.target.value.replace(/\D/g, '')) || 1))}
              aria-label="Otro número de personas"
              className="min-h-[48px] w-20 rounded-sm border border-crema-100/25 bg-bosque-900/60 px-3 text-center text-crema-100 focus:border-ambar-400 focus:outline-none"
            />
          </div>
        </div>

        <div>
          <span className="mb-2 block text-xs uppercase tracking-[0.18em] text-crema-100/55">
            Ocasión <span className="normal-case tracking-normal text-crema-100/35">(opcional)</span>
          </span>
          <div className="flex flex-wrap gap-2">
            {OCASIONES.map((o) => (
              <button
                key={o.id}
                type="button"
                onClick={() => setOcasion(o.id)}
                className={`min-h-[48px] rounded-sm border px-4 text-sm transition ${
                  ocasion === o.id
                    ? 'border-ambar-400 bg-ambar-500/15 text-ambar-300'
                    : 'border-crema-100/25 text-crema-100/70 hover:border-ambar-400/60'
                }`}
              >
                {o.etiqueta}
              </button>
            ))}
          </div>
        </div>

        <label className="block">
          <span className="mb-2 block text-xs uppercase tracking-[0.18em] text-crema-100/55">
            Algo que debamos saber{' '}
            <span className="normal-case tracking-normal text-crema-100/35">(opcional)</span>
          </span>
          <textarea
            value={notas}
            onChange={(e) => setNotas(e.target.value)}
            rows={3}
            placeholder="Preferencia de mesa, alergias, decoración…"
            className={`${CAMPO} py-3`}
          />
        </label>

        {error && (
          <p className="rounded-sm border border-ambar-400/50 bg-ambar-500/10 px-4 py-3 text-sm text-ambar-200">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={enviando}
          className="min-h-[56px] w-full rounded-sm bg-ambar-500 text-sm font-semibold uppercase tracking-[0.18em] text-bosque-950 transition hover:bg-ambar-400 disabled:opacity-60"
        >
          {enviando ? 'Enviando…' : 'Solicitar reserva'}
        </button>

        <p className="text-center text-xs leading-relaxed text-crema-100/45">
          Es una solicitud, no una reserva confirmada. Le escribimos por WhatsApp para apartarla.
        </p>
      </form>
    </section>
  )
}
