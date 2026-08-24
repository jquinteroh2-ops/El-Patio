import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, Copy, FileText, Loader2, Search, Upload } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { ConfiguracionPqr, ConsultaPqr, EstadoPqr, Radicada } from '@/compartido/mockApi'
import { RESTAURANTE } from '@/compartido/config'
import { formatoFecha, formatoFechaHora } from '@/compartido/formato'
import { Ornamento } from './Ornamento'

const PESO_MAXIMO = 5 * 1024 * 1024

const CAMPO =
  'w-full min-h-toque rounded-sm border border-crema-100/20 bg-onix-900 px-3.5 text-crema-100 ' +
  'placeholder:text-crema-100/35 focus:border-oro-400 focus:outline-none focus:ring-1 focus:ring-oro-400/40'

const ETIQUETA = 'mb-1.5 block text-xs uppercase tracking-[0.14em] text-crema-100/60'

const ESTADOS: Record<EstadoPqr, string> = {
  radicada: 'Radicada',
  en_tramite: 'En trámite',
  resuelta: 'Resuelta',
  cerrada: 'Cerrada',
}

type Pestana = 'radicar' | 'consultar'

export default function Pqr() {
  const [pestana, setPestana] = useState<Pestana>('radicar')

  return (
    <div className="mx-auto max-w-2xl px-5 py-12 sm:py-16">
      <header className="text-center">
        <Ornamento />
        <h1 className="mt-4 font-titulo text-3xl text-crema-100 sm:text-4xl">
          Peticiones, quejas y sugerencias
        </h1>
        <p className="mx-auto mt-3 max-w-lg text-sm leading-relaxed text-crema-100/65">
          Cuéntenos qué pasó. Toda solicitud recibe un número de radicado y una respuesta.
        </p>
      </header>

      {/* Dos pestañas y no dos páginas: quien viene a consultar suele llegar
          desde el correo del acuse, y buscar un enlace distinto sería una
          fricción de más para alguien que ya está molesto. */}
      <div className="mt-8 flex gap-2 border-b border-crema-100/15">
        {(
          [
            ['radicar', 'Radicar una solicitud'],
            ['consultar', 'Consultar mi radicado'],
          ] as [Pestana, string][]
        ).map(([id, etiqueta]) => (
          <button
            key={id}
            type="button"
            onClick={() => setPestana(id)}
            className={`-mb-px border-b-2 px-4 py-3 text-sm transition ${
              pestana === id
                ? 'border-oro-400 text-oro-300'
                : 'border-transparent text-crema-100/55 hover:text-crema-100'
            }`}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      {pestana === 'radicar' ? <Radicar /> : <Consultar />}
    </div>
  )
}

// ---------------------------------------------------------------------------

function Radicar() {
  const [config, setConfig] = useState<ConfiguracionPqr | null>(null)
  const [archivo, setArchivo] = useState<File | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [radicada, setRadicada] = useState<Radicada | null>(null)
  const [copiado, setCopiado] = useState(false)
  const formulario = useRef<HTMLFormElement>(null)

  useEffect(() => {
    void api.configuracionPqr().then(setConfig).catch(() => setConfig(null))
  }, [])

  const elegirArchivo = (elegido: File | null) => {
    setError(null)
    if (!elegido) {
      setArchivo(null)
      return
    }
    if (elegido.size > PESO_MAXIMO) {
      setError('El adjunto no puede pesar más de 5 MB.')
      setArchivo(null)
      return
    }
    setArchivo(elegido)
  }

  const enviar = async (evento: React.FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setEnviando(true)
    setError(null)
    try {
      const datos = new FormData(evento.currentTarget)
      if (archivo) datos.set('adjunto', archivo)
      else datos.delete('adjunto')
      setRadicada(await api.radicarPqr(datos))
      formulario.current?.reset()
      setArchivo(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo radicar la solicitud.')
    } finally {
      setEnviando(false)
    }
  }

  const copiar = async () => {
    if (!radicada) return
    try {
      await navigator.clipboard.writeText(radicada.radicado)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2000)
    } catch {
      // Sin portapapeles disponible el número sigue en pantalla para copiarlo
      // a mano. No vale la pena mostrar un error por esto.
    }
  }

  // ---- Después de radicar ----
  if (radicada) {
    return (
      <div className="py-12 text-center">
        <CheckCircle2 className="mx-auto h-12 w-12 text-oro-400" aria-hidden />
        <h2 className="mt-5 font-titulo text-2xl text-crema-100">Solicitud radicada</h2>

        {/* El número, grande y copiable. Es su comprobante: sin él no puede
            volver a consultar, y quien acaba de quejarse no está en ánimo de
            transcribir a mano. */}
        <div className="mx-auto mt-6 inline-flex items-center gap-3 rounded-sm border border-oro-500/40 bg-oro-500/10 px-5 py-4">
          <span className="font-marca text-xl tracking-[0.12em] text-oro-200">
            {radicada.radicado}
          </span>
          <button
            type="button"
            onClick={() => void copiar()}
            aria-label="Copiar el número de radicado"
            className="rounded-sm p-2 text-oro-300 transition hover:bg-oro-500/15"
          >
            {copiado ? (
              <CheckCircle2 className="h-4 w-4" aria-hidden />
            ) : (
              <Copy className="h-4 w-4" aria-hidden />
            )}
          </button>
        </div>

        <p className="mx-auto mt-5 max-w-md text-sm leading-relaxed text-crema-100/70">
          {radicada.mensaje}
        </p>
        {radicada.fechaLimiteRespuesta && (
          <p className="mx-auto mt-2 max-w-md text-sm text-crema-100/55">
            Tenemos plazo para responderle hasta el{' '}
            {formatoFecha(radicada.fechaLimiteRespuesta)}.
          </p>
        )}

        <button
          type="button"
          onClick={() => setRadicada(null)}
          className="mt-8 min-h-toque rounded-sm border border-crema-100/25 px-5 text-sm uppercase tracking-[0.16em] text-crema-100 transition hover:border-oro-400 hover:text-oro-300"
        >
          Radicar otra
        </button>
      </div>
    )
  }

  return (
    <form ref={formulario} onSubmit={enviar} className="mt-8 space-y-5">
      {/* El señuelo. Ver `Trabaja.tsx`. */}
      <div className="absolute left-[-9999px] top-auto h-px w-px overflow-hidden" aria-hidden>
        <label htmlFor="pqr-sitioWeb">Sitio web</label>
        <input id="pqr-sitioWeb" name="sitioWeb" type="text" tabIndex={-1} autoComplete="off" />
      </div>

      <div>
        <label className={ETIQUETA} htmlFor="tipo">
          Tipo de solicitud
        </label>
        <select id="tipo" name="tipo" required defaultValue="" className={CAMPO}>
          <option value="" disabled>
            Seleccione
          </option>
          {(config?.tipos ?? []).map((t) => (
            <option key={t.id} value={t.id}>
              {t.etiqueta}
            </option>
          ))}
        </select>
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <div>
          <label className={ETIQUETA} htmlFor="pqr-nombre">
            Nombre completo
          </label>
          <input
            id="pqr-nombre"
            name="nombreCompleto"
            required
            autoComplete="name"
            className={CAMPO}
          />
        </div>
        <div>
          <label className={ETIQUETA} htmlFor="pqr-email">
            Correo electrónico
          </label>
          <input
            id="pqr-email"
            name="email"
            type="email"
            required
            autoComplete="email"
            className={CAMPO}
            placeholder="Por aquí le respondemos"
          />
        </div>
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <div>
          <label className={ETIQUETA} htmlFor="pqr-telefono">
            Teléfono <span className="normal-case tracking-normal">(opcional)</span>
          </label>
          <input
            id="pqr-telefono"
            name="telefono"
            type="tel"
            autoComplete="tel"
            inputMode="tel"
            className={CAMPO}
          />
        </div>
        <div>
          <label className={ETIQUETA} htmlFor="fechaVisita">
            Fecha de la visita <span className="normal-case tracking-normal">(opcional)</span>
          </label>
          <input id="fechaVisita" name="fechaVisita" type="date" className={CAMPO} />
        </div>
      </div>

      <div>
        <label className={ETIQUETA} htmlFor="asunto">
          Asunto
        </label>
        <input
          id="asunto"
          name="asunto"
          required
          maxLength={120}
          className={CAMPO}
          placeholder="En una línea, de qué se trata"
        />
      </div>

      <div>
        <label className={ETIQUETA} htmlFor="descripcion">
          Cuéntenos qué pasó
        </label>
        <textarea
          id="descripcion"
          name="descripcion"
          required
          rows={6}
          maxLength={2000}
          className={`${CAMPO} min-h-[9rem] py-3`}
          placeholder="Con el detalle que quiera darnos: qué pidió, a qué hora, quién lo atendió."
        />
      </div>

      {/* ---------- Adjunto ---------- */}
      <div>
        <span className={ETIQUETA}>
          Adjunto <span className="normal-case tracking-normal">(opcional)</span>
        </span>
        <label
          htmlFor="adjunto"
          className="flex cursor-pointer items-center gap-3 rounded-sm border border-dashed border-crema-100/25 bg-onix-900 px-4 py-4 transition hover:border-oro-400/60"
        >
          {archivo ? (
            <FileText className="h-5 w-5 shrink-0 text-oro-400" aria-hidden />
          ) : (
            <Upload className="h-5 w-5 shrink-0 text-crema-100/40" aria-hidden />
          )}
          <span className="min-w-0 flex-1 text-sm">
            {archivo ? (
              <>
                <span className="block truncate text-crema-100">{archivo.name}</span>
                <span className="text-xs text-crema-100/50">toque para cambiar</span>
              </>
            ) : (
              <>
                <span className="block text-crema-100/80">
                  Adjunte una foto o un documento si ayuda
                </span>
                {/* Se aceptan imágenes y no solo PDF a propósito: quien se queja
                    tiene la foto del plato o del recibo en el celular, y
                    obligarlo a convertirla desde el teléfono es como se pierde
                    una queja legítima. */}
                <span className="text-xs text-crema-100/50">Imagen o PDF, máximo 5 MB</span>
              </>
            )}
          </span>
        </label>
        <input
          id="adjunto"
          name="adjunto"
          type="file"
          accept="image/jpeg,image/png,application/pdf,.jpg,.jpeg,.png,.pdf"
          className="sr-only"
          onChange={(e) => elegirArchivo(e.target.files?.[0] ?? null)}
        />
      </div>

      <label className="flex cursor-pointer items-start gap-3 rounded-sm border border-crema-100/15 bg-onix-900/60 p-4">
        <input
          type="checkbox"
          name="autorizacionDatos"
          value="true"
          required
          className="mt-0.5 h-5 w-5 shrink-0 accent-oro-500"
        />
        <span className="text-sm leading-relaxed text-crema-100/70">
          Autorizo a {RESTAURANTE.nombreCompleto} el tratamiento de mis datos personales para
          atender esta solicitud, conforme a la Ley 1581 de 2012 y a su{' '}
          <a
            href="/politica-de-datos"
            className="text-oro-300 underline underline-offset-2 hover:text-oro-200"
          >
            política de tratamiento de datos
          </a>
          .
        </span>
      </label>

      {config && (
        <p className="text-xs leading-relaxed text-crema-100/45">
          Respondemos dentro de los {config.diasHabilesDeRespuesta} días hábiles siguientes a la
          radicación.
        </p>
      )}

      {error && (
        <p
          role="alert"
          className="rounded-sm border border-estado-demorado/40 bg-estado-demorado/10 px-4 py-3 text-sm text-estado-demorado"
        >
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={enviando}
        className="flex min-h-toque w-full items-center justify-center gap-2 rounded-sm bg-oro-500 px-6 text-sm font-medium uppercase tracking-[0.16em] text-onix-950 transition hover:bg-oro-400 disabled:opacity-60"
      >
        {enviando && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
        {enviando ? 'Radicando…' : 'Radicar solicitud'}
      </button>
    </form>
  )
}

// ---------------------------------------------------------------------------

function Consultar() {
  const [buscando, setBuscando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [resultado, setResultado] = useState<ConsultaPqr | null>(null)

  const consultar = async (evento: React.FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    const datos = new FormData(evento.currentTarget)
    setBuscando(true)
    setError(null)
    setResultado(null)
    try {
      setResultado(
        await api.consultarPqr(String(datos.get('radicado')), String(datos.get('email'))),
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo consultar.')
    } finally {
      setBuscando(false)
    }
  }

  return (
    <div className="mt-8">
      <form onSubmit={consultar} className="space-y-5">
        <div>
          <label className={ETIQUETA} htmlFor="radicado">
            Número de radicado
          </label>
          <input
            id="radicado"
            name="radicado"
            required
            className={CAMPO}
            placeholder="PQR-2026-00047"
          />
        </div>
        <div>
          <label className={ETIQUETA} htmlFor="consulta-email">
            Correo con el que radicó
          </label>
          <input
            id="consulta-email"
            name="email"
            type="email"
            required
            autoComplete="email"
            className={CAMPO}
          />
          {/* Se dice por qué se piden los dos: sin explicación parece un
              trámite de más, y con ella se entiende que protege al propio
              solicitante. */}
          <p className="mt-2 text-xs text-crema-100/45">
            Pedimos los dos datos para que nadie más pueda ver su solicitud.
          </p>
        </div>

        {error && (
          <p
            role="alert"
            className="rounded-sm border border-estado-demorado/40 bg-estado-demorado/10 px-4 py-3 text-sm text-estado-demorado"
          >
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={buscando}
          className="flex min-h-toque w-full items-center justify-center gap-2 rounded-sm border border-crema-100/25 px-6 text-sm uppercase tracking-[0.16em] text-crema-100 transition hover:border-oro-400 hover:text-oro-300 disabled:opacity-60"
        >
          {buscando ? (
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          ) : (
            <Search className="h-4 w-4" aria-hidden />
          )}
          {buscando ? 'Consultando…' : 'Consultar'}
        </button>
      </form>

      {resultado && (
        <article className="mt-8 rounded-sm border border-crema-100/15 bg-onix-900/60 p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="font-marca text-lg tracking-[0.1em] text-oro-200">
              {resultado.radicado}
            </span>
            <span className="rounded-sm border border-oro-500/35 bg-oro-500/10 px-3 py-1 text-xs uppercase tracking-[0.14em] text-oro-200">
              {ESTADOS[resultado.estado]}
            </span>
          </div>

          <h3 className="mt-4 font-titulo text-xl text-crema-100">{resultado.asunto}</h3>

          <dl className="mt-4 space-y-2 text-sm text-crema-100/65">
            <div className="flex justify-between gap-4">
              <dt>Radicada</dt>
              <dd>{formatoFechaHora(resultado.fechaRadicacion)}</dd>
            </div>
            {resultado.fechaLimiteRespuesta && !resultado.fechaRespuesta && (
              <div className="flex justify-between gap-4">
                <dt>Plazo de respuesta</dt>
                <dd>{formatoFecha(resultado.fechaLimiteRespuesta)}</dd>
              </div>
            )}
            {resultado.fechaRespuesta && (
              <div className="flex justify-between gap-4">
                <dt>Respondida</dt>
                <dd>{formatoFechaHora(resultado.fechaRespuesta)}</dd>
              </div>
            )}
          </dl>

          {resultado.respuesta ? (
            <div className="mt-5 border-t border-crema-100/15 pt-4">
              <p className="mb-2 text-xs uppercase tracking-[0.14em] text-oro-400">
                Nuestra respuesta
              </p>
              <p className="whitespace-pre-line text-sm leading-relaxed text-crema-100/85">
                {resultado.respuesta}
              </p>
            </div>
          ) : (
            <p className="mt-5 border-t border-crema-100/15 pt-4 text-sm text-crema-100/55">
              Su solicitud está en trámite. Le responderemos al correo con el que la radicó.
            </p>
          )}
        </article>
      )}
    </div>
  )
}
