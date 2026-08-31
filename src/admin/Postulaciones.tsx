import { useState } from 'react'
import { EsqueletoTarjetas } from '@/componentes/ui/Esqueleto'
import { Download, Mail, Phone, Trash2, Users } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { EstadoPostulacion, PaginaDe, Postulacion } from '@/compartido/mockApi'
import { claveDia, formatoFechaHora } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Insignia } from '@/componentes/ui/Insignia'
import { MenuExportar } from '@/componentes/ui/MenuExportar'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'

const VACIO: PaginaDe<Postulacion> = { contenido: [], pagina: 0, tamano: 20, total: 0 }

const ESTADOS: {
  id: EstadoPostulacion
  etiqueta: string
  tono: 'neutro' | 'proceso' | 'oro' | 'listo' | 'demorado'
}[] = [
  { id: 'recibida', etiqueta: 'Recibida', tono: 'proceso' },
  { id: 'en_revision', etiqueta: 'En revisión', tono: 'oro' },
  { id: 'contactado', etiqueta: 'Contactado', tono: 'oro' },
  { id: 'seleccionado', etiqueta: 'Seleccionado', tono: 'listo' },
  { id: 'descartado', etiqueta: 'Descartado', tono: 'neutro' },
]

const estadoDe = (id: EstadoPostulacion) => ESTADOS.find((e) => e.id === id) ?? ESTADOS[0]

const CAMPO =
  'min-h-toque rounded-xl border border-noche-700 bg-noche-900 px-3 text-sm text-crema-100 focus:border-oro-500 focus:outline-none'

export default function Postulaciones() {
  const { mostrar } = useAvisos()
  const [estado, setEstado] = useState<EstadoPostulacion | ''>('')
  const [busqueda, setBusqueda] = useState('')
  const [pagina, setPagina] = useState(0)
  const [detalle, setDetalle] = useState<Postulacion | null>(null)
  const [notas, setNotas] = useState('')
  const [ocupado, setOcupado] = useState(false)

  const { datos, cargando, refrescar } = useSyncedState<PaginaDe<Postulacion>>(
    () =>
      api.listarPostulaciones({
        estado: estado || undefined,
        busqueda: busqueda || undefined,
        pagina,
        tamano: 20,
      }),
    VACIO,
    [estado, busqueda, pagina],
    ['postulaciones', 'todo'],
  )

  const abrir = (postulacion: Postulacion) => {
    setDetalle(postulacion)
    setNotas(postulacion.notasInternas ?? '')
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

  const cambiarEstado = (postulacion: Postulacion, nuevo: EstadoPostulacion) =>
    conError(
      async () => {
        const actualizada = await api.cambiarEstadoPostulacion(postulacion.id, nuevo)
        if (detalle?.id === postulacion.id) setDetalle(actualizada)
      },
      `${postulacion.nombreCompleto}: ${estadoDe(nuevo).etiqueta.toLowerCase()}`,
    )

  const guardarNotas = () => {
    if (!detalle) return
    return conError(
      () => api.anotarPostulacion(detalle.id, notas),
      'Notas guardadas',
    )
  }

  const descargar = (postulacion: Postulacion) =>
    conError(() => api.descargarHojaDeVida(postulacion.id), 'Descargando hoja de vida')

  /**
   * Borra la postulación y su archivo.
   *
   * La confirmación es un `confirm` del navegador y no una hoja bonita a
   * propósito: es irreversible —el PDF desaparece del disco— y conviene que
   * interrumpa de verdad. No hay papelera, porque una papelera es justo lo que
   * hace que un dato «borrado» siga existiendo.
   */
  const eliminar = (postulacion: Postulacion) => {
    const seguro = window.confirm(
      `¿Eliminar la postulación de ${postulacion.nombreCompleto}?\n\n` +
        'Se borra también su hoja de vida del servidor. No se puede deshacer.',
    )
    if (!seguro) return
    return conError(
      async () => {
        await api.eliminarPostulacion(postulacion.id)
        setDetalle(null)
      },
      'Postulación eliminada',
    )
  }

  const totalPaginas = Math.max(1, Math.ceil(datos.total / (datos.tamano || 20)))

  return (
    <div className="space-y-4">
      {/* ---------- Filtros ---------- */}
      <section className="flex flex-wrap items-end gap-2 rounded-2xl border border-noche-800 bg-noche-900 p-3">
        <label className="block">
          <span className="mb-1 block text-xs uppercase tracking-wide text-noche-400">Estado</span>
          <select
            value={estado}
            onChange={(e) => {
              setEstado(e.target.value as EstadoPostulacion | '')
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

        <label className="block flex-1 min-w-[12rem]">
          <span className="mb-1 block text-xs uppercase tracking-wide text-noche-400">
            Buscar por nombre o documento
          </span>
          <input
            value={busqueda}
            onChange={(e) => {
              setBusqueda(e.target.value)
              setPagina(0)
            }}
            className={`${CAMPO} w-full`}
            placeholder="Ana, o 1050123456"
          />
        </label>

        <div className="flex items-end">
          <MenuExportar
            tipo="postulaciones"
            desde={claveDia(new Date(Date.now() - 365 * 86400000))}
            hasta={claveDia()}
            deshabilitado={datos.total === 0}
          />
        </div>
      </section>

      {cargando ? (
        <EsqueletoTarjetas cantidad={4} etiqueta="Cargando las postulaciones" />
      ) : datos.contenido.length === 0 ? (
        <Vacio
          icono={Users}
          titulo="No hay postulaciones"
          descripcion={
            estado || busqueda
              ? 'Ninguna cae en este filtro. Pruebe a quitarlo.'
              : 'Cuando alguien deje su hoja de vida en el sitio, aparece aquí.'
          }
        />
      ) : (
        <>
          <div className="overflow-x-auto rounded-2xl border border-noche-800">
            <table className="w-full min-w-[46rem] text-sm">
              <thead className="bg-noche-900 text-left text-xs uppercase tracking-wide text-noche-400">
                <tr>
                  <th className="px-3 py-2.5 font-medium">Fecha</th>
                  <th className="px-3 py-2.5 font-medium">Nombre</th>
                  <th className="px-3 py-2.5 font-medium">Cargo</th>
                  <th className="px-3 py-2.5 font-medium">Contacto</th>
                  <th className="px-3 py-2.5 font-medium">Estado</th>
                  <th className="px-3 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-noche-800">
                {datos.contenido.map((p) => (
                  <tr
                    key={p.id}
                    onClick={() => abrir(p)}
                    className="cursor-pointer bg-noche-950 transition hover:bg-noche-900"
                  >
                    <td className="whitespace-nowrap px-3 py-2.5 text-noche-300">
                      {formatoFechaHora(p.fechaPostulacion)}
                    </td>
                    <td className="px-3 py-2.5">
                      <span className="block text-crema-100">{p.nombreCompleto}</span>
                      <span className="text-xs text-noche-400">
                        {p.tipoDocumento} {p.numeroDocumento}
                      </span>
                    </td>
                    <td className="px-3 py-2.5 text-noche-300">{p.cargoInteres}</td>
                    <td className="px-3 py-2.5 text-xs text-noche-400">
                      <span className="block">{p.telefono}</span>
                      <span className="block truncate">{p.email}</span>
                    </td>
                    <td className="px-3 py-2.5">
                      <Insignia tono={estadoDe(p.estado).tono}>
                        {estadoDe(p.estado).etiqueta}
                      </Insignia>
                    </td>
                    <td className="px-3 py-2.5 text-right">
                      <Boton
                        variante="secundario"
                        tamano="compacto"
                        icono={<Download className="h-4 w-4" aria-hidden />}
                        onClick={(e) => {
                          e.stopPropagation()
                          void descargar(p)
                        }}
                      >
                        HV
                      </Boton>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPaginas > 1 && (
            <div className="flex items-center justify-between text-sm text-noche-400">
              <span>
                {datos.total} {datos.total === 1 ? 'postulación' : 'postulaciones'} · página{' '}
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
      <HojaInferior abierta={detalle !== null} onCerrar={() => setDetalle(null)} titulo="Postulación">
        {detalle && (
          <div className="space-y-4 pb-4">
            <div>
              <h3 className="text-lg font-semibold text-crema-100">{detalle.nombreCompleto}</h3>
              <p className="text-sm text-noche-400">
                {detalle.tipoDocumento} {detalle.numeroDocumento} · {detalle.cargoInteres}
              </p>
              <p className="mt-1 text-xs text-noche-500">
                Recibida el {formatoFechaHora(detalle.fechaPostulacion)}
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              <a
                href={`tel:${detalle.telefono.replace(/\s/g, '')}`}
                className="flex min-h-[40px] items-center gap-2 rounded-xl border border-noche-700 bg-noche-900 px-3.5 text-sm text-crema-100 transition hover:bg-noche-800"
              >
                <Phone className="h-4 w-4 text-oro-400" aria-hidden />
                {detalle.telefono}
              </a>
              <a
                href={`mailto:${detalle.email}`}
                className="flex min-h-[40px] items-center gap-2 rounded-xl border border-noche-700 bg-noche-900 px-3.5 text-sm text-crema-100 transition hover:bg-noche-800"
              >
                <Mail className="h-4 w-4 text-oro-400" aria-hidden />
                {detalle.email}
              </a>
            </div>

            {detalle.mensaje && (
              <div className="rounded-xl border border-noche-800 bg-noche-900 p-3">
                <p className="mb-1 text-xs uppercase tracking-wide text-noche-400">Se presenta así</p>
                <p className="text-sm leading-relaxed text-crema-100/85">{detalle.mensaje}</p>
              </div>
            )}

            <Boton
              variante="secundario"
              bloque
              icono={<Download className="h-4 w-4" aria-hidden />}
              cargando={ocupado}
              onClick={() => void descargar(detalle)}
            >
              Descargar hoja de vida
            </Boton>

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
            </div>

            {/* ---------- Notas internas ---------- */}
            <div>
              <label className="mb-1 block text-xs uppercase tracking-wide text-noche-400">
                Notas internas
              </label>
              <textarea
                value={notas}
                onChange={(e) => setNotas(e.target.value)}
                rows={3}
                maxLength={2000}
                className="w-full rounded-xl border border-noche-700 bg-noche-900 p-3 text-sm text-crema-100 focus:border-oro-500 focus:outline-none"
                placeholder="Lo que se habló, cuándo se le llamó…"
              />
              <Boton
                variante="secundario"
                tamano="compacto"
                cargando={ocupado}
                onClick={() => void guardarNotas()}
                className="mt-2"
              >
                Guardar notas
              </Boton>
            </div>

            {/* ---------- Supresión ----------
                Separado del resto y en rojo: borra el PDF del servidor y no se
                puede deshacer. Es el derecho de supresión de la Ley 1581, no
                una limpieza de bandeja. */}
            <div className="border-t border-noche-800 pt-4">
              <Boton
                variante="peligro"
                bloque
                icono={<Trash2 className="h-4 w-4" aria-hidden />}
                cargando={ocupado}
                onClick={() => void eliminar(detalle)}
              >
                Eliminar postulación y su hoja de vida
              </Boton>
              <p className="mt-2 text-xs leading-relaxed text-noche-500">
                Borra los datos de la persona y su archivo del servidor. Use esto cuando el
                titular pida la supresión de sus datos.
              </p>
            </div>
          </div>
        )}
      </HojaInferior>
    </div>
  )
}
