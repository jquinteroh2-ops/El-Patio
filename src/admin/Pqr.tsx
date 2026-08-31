import { useState } from 'react'
import { EsqueletoTarjetas } from '@/componentes/ui/Esqueleto'
import { AlertTriangle, Clock, Download, Mail, MessageSquare, Paperclip, Phone } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { EstadoPqr, PaginaDe, SolicitudPqr, TipoSolicitud } from '@/compartido/mockApi'
import { claveDia, formatoFecha, formatoFechaHora } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Insignia } from '@/componentes/ui/Insignia'
import { MenuExportar } from '@/componentes/ui/MenuExportar'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'

const VACIO: PaginaDe<SolicitudPqr> = { contenido: [], pagina: 0, tamano: 20, total: 0 }

const TIPOS: { id: TipoSolicitud; etiqueta: string }[] = [
  { id: 'peticion', etiqueta: 'Petición' },
  { id: 'queja', etiqueta: 'Queja' },
  { id: 'reclamo', etiqueta: 'Reclamo' },
  { id: 'sugerencia', etiqueta: 'Sugerencia' },
  { id: 'felicitacion', etiqueta: 'Felicitación' },
]

const ESTADOS: { id: EstadoPqr; etiqueta: string }[] = [
  { id: 'radicada', etiqueta: 'Radicada' },
  { id: 'en_tramite', etiqueta: 'En trámite' },
  { id: 'resuelta', etiqueta: 'Resuelta' },
  { id: 'cerrada', etiqueta: 'Cerrada' },
]

const etiquetaTipo = (id: TipoSolicitud) => TIPOS.find((t) => t.id === id)?.etiqueta ?? id
const etiquetaEstado = (id: EstadoPqr) => ESTADOS.find((e) => e.id === id)?.etiqueta ?? id

const CAMPO =
  'min-h-toque rounded-xl border border-noche-700 bg-noche-900 px-3 text-sm text-crema-100 focus:border-oro-500 focus:outline-none'

/**
 * Cómo se lee el plazo de una solicitud.
 *
 * Es lo que decide a qué se atiende primero, así que se dice en palabras y no
 * en una fecha suelta: «vencida hace 2 días» mueve a alguien, «vence el
 * 12/09/2026» hay que calcularlo mentalmente.
 */
function Plazo({ solicitud }: { solicitud: SolicitudPqr }) {
  if (!solicitud.fechaLimiteRespuesta) {
    return <span className="text-xs text-noche-500">Sin plazo</span>
  }
  if (solicitud.estado === 'resuelta' || solicitud.estado === 'cerrada') {
    return <span className="text-xs text-noche-400">Atendida</span>
  }
  if (solicitud.vencida) {
    return (
      <span className="flex items-center gap-1.5 text-xs font-medium text-estado-demorado">
        <AlertTriangle className="h-3.5 w-3.5 shrink-0" aria-hidden />
        Vencida hace {Math.abs(solicitud.diasHabilesRestantes)}{' '}
        {Math.abs(solicitud.diasHabilesRestantes) === 1 ? 'día' : 'días'}
      </span>
    )
  }
  if (solicitud.porVencer) {
    return (
      <span className="flex items-center gap-1.5 text-xs font-medium text-estado-proceso">
        <Clock className="h-3.5 w-3.5 shrink-0" aria-hidden />
        Vence en {solicitud.diasHabilesRestantes}{' '}
        {solicitud.diasHabilesRestantes === 1 ? 'día' : 'días'}
      </span>
    )
  }
  return (
    <span className="text-xs text-noche-400">
      {solicitud.diasHabilesRestantes} días hábiles
    </span>
  )
}

export default function Pqr() {
  const { mostrar } = useAvisos()
  const [tipo, setTipo] = useState<TipoSolicitud | ''>('')
  const [estado, setEstado] = useState<EstadoPqr | ''>('')
  const [busqueda, setBusqueda] = useState('')
  const [pagina, setPagina] = useState(0)
  const [detalle, setDetalle] = useState<SolicitudPqr | null>(null)
  const [respuesta, setRespuesta] = useState('')
  const [notas, setNotas] = useState('')
  const [ocupado, setOcupado] = useState(false)

  const { datos, cargando, refrescar } = useSyncedState<PaginaDe<SolicitudPqr>>(
    () =>
      api.listarPqr({
        tipo: tipo || undefined,
        estado: estado || undefined,
        busqueda: busqueda || undefined,
        pagina,
        tamano: 20,
      }),
    VACIO,
    [tipo, estado, busqueda, pagina],
    ['pqr', 'todo'],
  )

  const abrir = (solicitud: SolicitudPqr) => {
    setDetalle(solicitud)
    setRespuesta(solicitud.respuesta ?? '')
    setNotas(solicitud.notasInternas ?? '')
  }

  const conError = async (accion: () => Promise<unknown>, exito: string) => {
    setOcupado(true)
    try {
      await accion()
      mostrar(exito, 'exito')
      refrescar()
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo completar la operación', 'error')
    } finally {
      setOcupado(false)
    }
  }

  const responder = () => {
    if (!detalle || !respuesta.trim()) return
    return conError(async () => {
      const actualizada = await api.responderPqr(detalle.id, respuesta)
      setDetalle(actualizada)
    }, 'Respuesta registrada y enviada al cliente')
  }

  const cambiarEstado = (solicitud: SolicitudPqr, nuevo: EstadoPqr) =>
    conError(async () => {
      const actualizada = await api.cambiarEstadoPqr(solicitud.id, nuevo)
      setDetalle(actualizada)
    }, `${solicitud.radicado}: ${etiquetaEstado(nuevo).toLowerCase()}`)

  const vencidas = datos.contenido.filter((s) => s.vencida).length
  const totalPaginas = Math.max(1, Math.ceil(datos.total / (datos.tamano || 20)))

  return (
    <div className="space-y-4">
      {/* El aviso de vencidas va arriba y solo aparece si hay: un panel que
          siempre muestra la misma franja deja de leerse a la semana. */}
      {vencidas > 0 && (
        <div className="flex items-start gap-2 rounded-xl border border-estado-demorado/40 bg-estado-demorado/10 px-3 py-2.5 text-sm text-estado-demorado">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <p>
            {vencidas} {vencidas === 1 ? 'solicitud vencida' : 'solicitudes vencidas'} sin
            responder. El plazo de ley ya pasó.
          </p>
        </div>
      )}

      {/* ---------- Filtros ---------- */}
      <section className="revelar-corto flex flex-wrap items-end gap-2 rounded-2xl border border-noche-800 bg-noche-900 p-3">
        <label className="block">
          <span className="mb-1 block text-xs uppercase tracking-wide text-noche-400">Tipo</span>
          <select
            value={tipo}
            onChange={(e) => {
              setTipo(e.target.value as TipoSolicitud | '')
              setPagina(0)
            }}
            className={CAMPO}
          >
            <option value="">Todos</option>
            {TIPOS.map((t) => (
              <option key={t.id} value={t.id}>
                {t.etiqueta}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="mb-1 block text-xs uppercase tracking-wide text-noche-400">Estado</span>
          <select
            value={estado}
            onChange={(e) => {
              setEstado(e.target.value as EstadoPqr | '')
              setPagina(0)
            }}
            className={CAMPO}
          >
            <option value="">Todos</option>
            {ESTADOS.map((e) => (
              <option key={e.id} value={e.id}>
                {e.etiqueta}
              </option>
            ))}
          </select>
        </label>

        <label className="block min-w-[12rem] flex-1">
          <span className="mb-1 block text-xs uppercase tracking-wide text-noche-400">
            Buscar por radicado, nombre o asunto
          </span>
          <input
            value={busqueda}
            onChange={(e) => {
              setBusqueda(e.target.value)
              setPagina(0)
            }}
            className={`${CAMPO} w-full`}
            placeholder="PQR-2026-00047, Ana, plato frío…"
          />
        </label>

        <div className="flex items-end">
          <MenuExportar
            tipo="pqr"
            desde={claveDia(new Date(Date.now() - 365 * 86400000))}
            hasta={claveDia()}
            deshabilitado={datos.total === 0}
          />
        </div>
      </section>

      {cargando ? (
        <EsqueletoTarjetas cantidad={4} etiqueta="Cargando las solicitudes" />
      ) : datos.contenido.length === 0 ? (
        <Vacio
          icono={MessageSquare}
          titulo="No hay solicitudes"
          descripcion={
            tipo || estado || busqueda
              ? 'Ninguna cae en este filtro. Pruebe a quitarlo.'
              : 'Cuando un cliente radique una PQR en el sitio, aparece aquí.'
          }
        />
      ) : (
        <>
          <div className="overflow-x-auto rounded-2xl border border-noche-800">
            <table className="w-full min-w-[52rem] text-sm">
              <thead className="bg-noche-900 text-left text-xs uppercase tracking-wide text-noche-400">
                <tr>
                  <th className="px-3 py-2.5 font-medium">Radicado</th>
                  <th className="px-3 py-2.5 font-medium">Tipo</th>
                  <th className="px-3 py-2.5 font-medium">Asunto</th>
                  <th className="px-3 py-2.5 font-medium">Solicitante</th>
                  <th className="px-3 py-2.5 font-medium">Estado</th>
                  <th className="px-3 py-2.5 font-medium">Plazo</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-noche-800">
                {datos.contenido.map((s) => (
                  <tr
                    key={s.id}
                    onClick={() => abrir(s)}
                    className={`cursor-pointer transition hover:bg-noche-900 ${
                      s.vencida ? 'bg-estado-demorado-suave/40' : 'bg-noche-950'
                    }`}
                  >
                    <td className="whitespace-nowrap px-3 py-2.5 font-medium tabular-nums text-crema-100">
                      {s.radicado}
                      {s.tieneAdjunto && (
                        <Paperclip className="ml-1.5 inline h-3 w-3 text-noche-400" aria-label="Con adjunto" />
                      )}
                    </td>
                    <td className="px-3 py-2.5">
                      <Insignia tono={s.tipo === 'felicitacion' ? 'listo' : 'neutro'}>
                        {etiquetaTipo(s.tipo)}
                      </Insignia>
                    </td>
                    <td className="max-w-xs px-3 py-2.5">
                      <span className="block truncate text-crema-100">{s.asunto}</span>
                      <span className="text-xs text-noche-500">
                        {formatoFecha(s.fechaRadicacion)}
                      </span>
                    </td>
                    <td className="px-3 py-2.5 text-noche-300">{s.nombreCompleto}</td>
                    <td className="px-3 py-2.5">
                      <Insignia
                        tono={
                          s.estado === 'resuelta' || s.estado === 'cerrada' ? 'listo' : 'proceso'
                        }
                      >
                        {etiquetaEstado(s.estado)}
                      </Insignia>
                    </td>
                    <td className="px-3 py-2.5">
                      <Plazo solicitud={s} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPaginas > 1 && (
            <div className="flex items-center justify-between text-sm text-noche-400">
              <span>
                {datos.total} {datos.total === 1 ? 'solicitud' : 'solicitudes'} · página{' '}
                {pagina + 1} de {totalPaginas}
              </span>
              <div className="flex gap-2">
                <Boton
                  variante="secundario"
                  tamano="compacto"
                  disabled={pagina === 0}
                  onClick={() => setPagina((p) => Math.max(0, p - 1))}
                >
                  Anterior
                </Boton>
                <Boton
                  variante="secundario"
                  tamano="compacto"
                  disabled={pagina + 1 >= totalPaginas}
                  onClick={() => setPagina((p) => p + 1)}
                >
                  Siguiente
                </Boton>
              </div>
            </div>
          )}
        </>
      )}

      {/* ---------- Detalle ---------- */}
      <HojaInferior
        abierta={detalle !== null}
        onCerrar={() => setDetalle(null)}
        titulo={detalle?.radicado ?? 'Solicitud'}
      >
        {detalle && (
          <div className="space-y-4 pb-4">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <Insignia tono={detalle.tipo === 'felicitacion' ? 'listo' : 'neutro'}>
                  {etiquetaTipo(detalle.tipo)}
                </Insignia>
                <Plazo solicitud={detalle} />
              </div>
              <h3 className="mt-2 text-lg font-semibold text-crema-100">{detalle.asunto}</h3>
              <p className="text-xs text-noche-500">
                Radicada el {formatoFechaHora(detalle.fechaRadicacion)}
                {detalle.fechaVisita && ` · visitó el ${formatoFecha(detalle.fechaVisita)}`}
              </p>
            </div>

            <div className="rounded-xl border border-noche-800 bg-noche-900 p-3">
              <p className="whitespace-pre-line text-sm leading-relaxed text-crema-100/85">
                {detalle.descripcion}
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              <a
                href={`mailto:${detalle.email}`}
                className="flex min-h-[40px] items-center gap-2 rounded-xl border border-noche-700 bg-noche-900 px-3.5 text-sm text-crema-100 transition hover:bg-noche-800"
              >
                <Mail className="h-4 w-4 text-oro-400" aria-hidden />
                {detalle.email}
              </a>
              {detalle.telefono && (
                <a
                  href={`tel:${detalle.telefono.replace(/\s/g, '')}`}
                  className="flex min-h-[40px] items-center gap-2 rounded-xl border border-noche-700 bg-noche-900 px-3.5 text-sm text-crema-100 transition hover:bg-noche-800"
                >
                  <Phone className="h-4 w-4 text-oro-400" aria-hidden />
                  {detalle.telefono}
                </a>
              )}
              {detalle.tieneAdjunto && (
                <Boton
                  variante="secundario"
                  icono={<Download className="h-4 w-4" aria-hidden />}
                  cargando={ocupado}
                  onClick={() =>
                    void conError(() => api.descargarAdjuntoPqr(detalle.id), 'Descargando adjunto')
                  }
                >
                  Adjunto
                </Boton>
              )}
            </div>

            {/* ---------- Respuesta ----------
                Es lo primero que se ve al abrir porque es lo que hay que hacer.
                Registrarla pasa la solicitud a resuelta y se la manda al
                cliente por correo. */}
            <div>
              <label className="mb-1 block text-xs uppercase tracking-wide text-noche-400">
                Respuesta al cliente
              </label>
              <textarea
                value={respuesta}
                onChange={(e) => setRespuesta(e.target.value)}
                rows={5}
                className="w-full rounded-xl border border-noche-700 bg-noche-900 p-3 text-sm text-crema-100 focus:border-oro-500 focus:outline-none"
                placeholder="Lo que se le va a responder. Se le envía al correo con el que radicó."
              />
              <Boton
                variante="principal"
                bloque
                cargando={ocupado}
                disabled={!respuesta.trim()}
                onClick={() => void responder()}
                className="mt-2"
              >
                {detalle.fechaRespuesta ? 'Actualizar respuesta' : 'Responder y notificar'}
              </Boton>
              {detalle.fechaRespuesta && (
                <p className="mt-2 text-xs text-noche-500">
                  Respondida el {formatoFechaHora(detalle.fechaRespuesta)}
                  {detalle.respondidoPor && ` por ${detalle.respondidoPor}`}.
                </p>
              )}
            </div>

            {/* ---------- Estado ---------- */}
            <div>
              <p className="mb-2 text-xs uppercase tracking-wide text-noche-400">Estado</p>
              <div className="flex flex-wrap gap-2">
                {ESTADOS.map((e) => (
                  <button
                    key={e.id}
                    type="button"
                    disabled={ocupado}
                    onClick={() => void cambiarEstado(detalle, e.id)}
                    className={`min-h-[40px] rounded-xl border px-3.5 text-sm transition disabled:opacity-50 ${
                      detalle.estado === e.id
                        ? 'border-oro-500 bg-oro-500/15 text-oro-300'
                        : 'border-noche-700 bg-noche-900 text-noche-300 hover:bg-noche-800'
                    }`}
                  >
                    {e.etiqueta}
                  </button>
                ))}
              </div>
              <p className="mt-2 text-xs text-noche-500">
                Para darla por resuelta hay que registrar antes la respuesta.
              </p>
            </div>

            {/* ---------- Notas internas ---------- */}
            <div>
              <label className="mb-1 block text-xs uppercase tracking-wide text-noche-400">
                Notas internas <span className="normal-case">(no las ve el cliente)</span>
              </label>
              <textarea
                value={notas}
                onChange={(e) => setNotas(e.target.value)}
                rows={3}
                maxLength={2000}
                className="w-full rounded-xl border border-noche-700 bg-noche-900 p-3 text-sm text-crema-100 focus:border-oro-500 focus:outline-none"
                placeholder="Qué se averiguó, con quién se habló…"
              />
              <Boton
                variante="secundario"
                tamano="compacto"
                cargando={ocupado}
                onClick={() =>
                  void conError(() => api.anotarPqr(detalle.id, notas), 'Notas guardadas')
                }
                className="mt-2"
              >
                Guardar notas
              </Boton>
            </div>
          </div>
        )}
      </HojaInferior>
    </div>
  )
}
