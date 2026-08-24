import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, FileText, Loader2, Upload } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { CargoDeInteres, TipoDocumento } from '@/compartido/mockApi'
import { RESTAURANTE } from '@/compartido/config'
import { Ornamento } from './Ornamento'

const DOCUMENTOS: { id: TipoDocumento; etiqueta: string }[] = [
  { id: 'CC', etiqueta: 'Cédula de ciudadanía' },
  { id: 'CE', etiqueta: 'Cédula de extranjería' },
  { id: 'PEP', etiqueta: 'Permiso Especial de Permanencia' },
  { id: 'PPT', etiqueta: 'Permiso por Protección Temporal' },
  { id: 'TI', etiqueta: 'Tarjeta de identidad' },
]

/** Cinco megas. El mismo tope que aplica el servidor. */
const PESO_MAXIMO = 5 * 1024 * 1024

const CAMPO =
  'w-full min-h-toque rounded-sm border border-crema-100/20 bg-onix-900 px-3.5 text-crema-100 ' +
  'placeholder:text-crema-100/35 focus:border-oro-400 focus:outline-none focus:ring-1 focus:ring-oro-400/40'

const ETIQUETA = 'mb-1.5 block text-xs uppercase tracking-[0.14em] text-crema-100/60'

export default function Trabaja() {
  const [cargos, setCargos] = useState<CargoDeInteres[]>([])
  const [archivo, setArchivo] = useState<File | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [enviada, setEnviada] = useState<string | null>(null)
  const formulario = useRef<HTMLFormElement>(null)

  useEffect(() => {
    void api.cargosDeInteres().then(setCargos).catch(() => setCargos([]))
  }, [])

  /**
   * Comprueba el archivo antes de mandarlo.
   *
   * El servidor lo vuelve a mirar —y por los bytes, no por el nombre—, pero
   * avisar aquí ahorra subir cinco megas por datos móviles para que el servidor
   * los rechace. Esto es cortesía, no seguridad.
   */
  const elegirArchivo = (elegido: File | null) => {
    setError(null)
    if (!elegido) {
      setArchivo(null)
      return
    }
    if (elegido.size > PESO_MAXIMO) {
      setError('La hoja de vida no puede pesar más de 5 MB.')
      setArchivo(null)
      return
    }
    if (!elegido.name.toLowerCase().endsWith('.pdf')) {
      setError('La hoja de vida tiene que ser un archivo PDF.')
      setArchivo(null)
      return
    }
    setArchivo(elegido)
  }

  const enviar = async (evento: React.FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    if (!archivo) {
      setError('Falta adjuntar la hoja de vida en PDF.')
      return
    }

    setEnviando(true)
    setError(null)
    try {
      const datos = new FormData(evento.currentTarget)
      datos.set('hojaDeVida', archivo)
      const respuesta = await api.enviarPostulacion(datos)
      setEnviada(respuesta.mensaje)
      formulario.current?.reset()
      setArchivo(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo enviar la hoja de vida.')
    } finally {
      setEnviando(false)
    }
  }

  // ---- Después de enviar ----
  if (enviada) {
    return (
      <div className="mx-auto max-w-2xl px-5 py-16 text-center sm:py-24">
        <CheckCircle2 className="mx-auto h-12 w-12 text-oro-400" aria-hidden />
        <h1 className="mt-5 font-titulo text-3xl text-crema-100 sm:text-4xl">
          Hoja de vida recibida
        </h1>
        <p className="mx-auto mt-4 max-w-md text-crema-100/70">{enviada}</p>
        <button
          type="button"
          onClick={() => setEnviada(null)}
          className="mt-8 min-h-toque rounded-sm border border-crema-100/25 px-5 text-sm uppercase tracking-[0.16em] text-crema-100 transition hover:border-oro-400 hover:text-oro-300"
        >
          Enviar otra
        </button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl px-5 py-12 sm:py-16">
      <header className="text-center">
        <Ornamento />
        <h1 className="mt-4 font-titulo text-3xl text-crema-100 sm:text-4xl">
          Trabaja con nosotros
        </h1>
        <p className="mx-auto mt-3 max-w-lg text-sm leading-relaxed text-crema-100/65">
          Déjenos su hoja de vida. Si su perfil encaja con una vacante en{' '}
          {RESTAURANTE.nombreCompleto}, nos comunicamos con usted.
        </p>
      </header>

      <form ref={formulario} onSubmit={enviar} className="mt-10 space-y-5" noValidate={false}>
        {/*
          El señuelo. Va escondido de forma que ni se vea ni entre en el orden
          de tabulación ni lo lea un lector de pantalla: una persona nunca lo
          llena, y un robot que rellena todo lo que encuentra sí. `display:none`
          bastaría, pero algunos robots omiten justamente lo que está oculto
          así; sacarlo de la pantalla con posición absoluta lo deja «visible»
          para ellos y fuera de la vista para todos los demás.
        */}
        <div className="absolute left-[-9999px] top-auto h-px w-px overflow-hidden" aria-hidden>
          <label htmlFor="sitioWeb">Sitio web</label>
          <input id="sitioWeb" name="sitioWeb" type="text" tabIndex={-1} autoComplete="off" />
        </div>

        <div>
          <label className={ETIQUETA} htmlFor="nombreCompleto">
            Nombre completo
          </label>
          <input
            id="nombreCompleto"
            name="nombreCompleto"
            required
            autoComplete="name"
            className={CAMPO}
            placeholder="Como aparece en su documento"
          />
        </div>

        <div className="grid gap-5 sm:grid-cols-2">
          <div>
            <label className={ETIQUETA} htmlFor="tipoDocumento">
              Tipo de documento
            </label>
            <select id="tipoDocumento" name="tipoDocumento" required defaultValue="CC" className={CAMPO}>
              {DOCUMENTOS.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.etiqueta}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={ETIQUETA} htmlFor="numeroDocumento">
              Número de identificación
            </label>
            <input
              id="numeroDocumento"
              name="numeroDocumento"
              required
              inputMode="numeric"
              className={CAMPO}
            />
          </div>
        </div>

        <div className="grid gap-5 sm:grid-cols-2">
          <div>
            <label className={ETIQUETA} htmlFor="email">
              Correo electrónico
            </label>
            <input
              id="email"
              name="email"
              type="email"
              required
              autoComplete="email"
              className={CAMPO}
              placeholder="nombre@correo.com"
            />
          </div>
          <div>
            <label className={ETIQUETA} htmlFor="telefono">
              Teléfono o WhatsApp
            </label>
            <input
              id="telefono"
              name="telefono"
              type="tel"
              required
              autoComplete="tel"
              inputMode="tel"
              className={CAMPO}
              placeholder="300 000 0000"
            />
          </div>
        </div>

        <div>
          <label className={ETIQUETA} htmlFor="cargoInteres">
            Cargo de interés
          </label>
          <select id="cargoInteres" name="cargoInteres" required defaultValue="" className={CAMPO}>
            <option value="" disabled>
              Seleccione un cargo
            </option>
            {cargos.map((c) => (
              <option key={c.id} value={c.id}>
                {c.etiqueta}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className={ETIQUETA} htmlFor="mensaje">
            Cuéntenos algo de usted <span className="normal-case tracking-normal">(opcional)</span>
          </label>
          <textarea
            id="mensaje"
            name="mensaje"
            rows={4}
            maxLength={500}
            className={`${CAMPO} min-h-[7rem] py-3`}
            placeholder="Su experiencia, su disponibilidad, lo que quiera contarnos."
          />
        </div>

        {/* ---------- Hoja de vida ---------- */}
        <div>
          <span className={ETIQUETA}>Hoja de vida (PDF)</span>
          <label
            htmlFor="hojaDeVida"
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
                  <span className="text-xs text-crema-100/50">
                    {(archivo.size / 1024).toFixed(0)} KB · toque para cambiar
                  </span>
                </>
              ) : (
                <>
                  <span className="block text-crema-100/80">Toque para adjuntar su hoja de vida</span>
                  <span className="text-xs text-crema-100/50">Solo PDF, máximo 5 MB</span>
                </>
              )}
            </span>
          </label>
          <input
            id="hojaDeVida"
            name="hojaDeVida"
            type="file"
            accept="application/pdf,.pdf"
            className="sr-only"
            onChange={(e) => elegirArchivo(e.target.files?.[0] ?? null)}
          />
        </div>

        {/* ---------- Habeas data ----------
            Obligatorio por la Ley 1581 de 2012. Sin marcarlo el servidor no
            recibe la hoja de vida, y no es una formalidad: sin autorización el
            restaurante no puede guardar estos datos. */}
        <label className="flex cursor-pointer items-start gap-3 rounded-sm border border-crema-100/15 bg-onix-900/60 p-4">
          <input
            type="checkbox"
            name="autorizacionDatos"
            value="true"
            required
            className="mt-0.5 h-5 w-5 shrink-0 accent-oro-500"
          />
          <span className="text-sm leading-relaxed text-crema-100/70">
            Autorizo a {RESTAURANTE.nombreCompleto} el tratamiento de mis datos personales
            con fines de selección de personal, conforme a la Ley 1581 de 2012 y a su{' '}
            <a
              href="/politica-de-datos"
              className="text-oro-300 underline underline-offset-2 hover:text-oro-200"
            >
              política de tratamiento de datos
            </a>
            .
          </span>
        </label>

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
          {enviando ? 'Enviando…' : 'Enviar hoja de vida'}
        </button>
      </form>
    </div>
  )
}
