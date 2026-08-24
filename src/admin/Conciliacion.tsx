import { useState } from 'react'
import { AlertTriangle, CheckCircle2, Clock, FileWarning, RefreshCw } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type {
  EstadoEnvioErp,
  FilaConciliacion,
  ResumenConciliacion,
} from '@/compartido/mockApi'
import { claveDia, formatoCOP, formatoFechaHora } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import { Boton } from '@/componentes/ui/Boton'
import { Cargando } from '@/componentes/ui/Cargando'
import { Insignia } from '@/componentes/ui/Insignia'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'

const VACIO: ResumenConciliacion = {
  totalVentas: 0,
  montoTotal: 0,
  facturadas: 0,
  sinConciliar: 0,
  conError: 0,
  montoSinConciliar: 0,
  adaptador: 'manual',
  filas: [],
}

/**
 * Como se lee cada estado, dicho para quien concilia y no para quien programa.
 *
 * «Enviada» no dice nada útil a un contador: lo que necesita saber es si esa
 * venta ya tiene documento o no. Por eso los dos estados intermedios se
 * describen por lo que le falta a la venta, no por dónde está el mensaje.
 */
const ESTADOS: Record<EstadoEnvioErp, { texto: string; tono: 'listo' | 'proceso' | 'demorado' }> = {
  pendiente_envio_erp: { texto: 'Sin enviar', tono: 'proceso' },
  enviada_erp: { texto: 'Sin documento', tono: 'proceso' },
  facturada_erp: { texto: 'Facturada', tono: 'listo' },
  error_erp: { texto: 'Con error', tono: 'demorado' },
}

/** Los filtros de la bandeja. «Sin conciliar» junta los dos estados a medias. */
type Filtro = 'todas' | 'sin_conciliar' | 'facturadas' | 'con_error'

const FILTROS: { valor: Filtro; etiqueta: string }[] = [
  { valor: 'todas', etiqueta: 'Todas' },
  { valor: 'sin_conciliar', etiqueta: 'Sin conciliar' },
  { valor: 'con_error', etiqueta: 'Con error' },
  { valor: 'facturadas', etiqueta: 'Facturadas' },
]

function pasaFiltro(fila: FilaConciliacion, filtro: Filtro): boolean {
  switch (filtro) {
    case 'facturadas':
      return fila.estado === 'facturada_erp'
    case 'con_error':
      return fila.estado === 'error_erp'
    case 'sin_conciliar':
      return fila.estado === 'pendiente_envio_erp' || fila.estado === 'enviada_erp'
    default:
      return true
  }
}

export default function Conciliacion() {
  const { mostrar } = useAvisos()
  const hoy = claveDia()
  const [desde, setDesde] = useState(hoy)
  const [hasta, setHasta] = useState(hoy)
  const [filtro, setFiltro] = useState<Filtro>('todas')
  const [reintentando, setReintentando] = useState<string | null>(null)

  const { datos, cargando, refrescar } = useSyncedState<ResumenConciliacion>(
    () => api.conciliacionErp(desde, hasta),
    VACIO,
    [desde, hasta],
    ['pagos', 'todo'],
  )

  const reintentar = async (fila: FilaConciliacion) => {
    setReintentando(fila.envioId)
    try {
      await api.reintentarEnvioErp(fila.envioId)
      mostrar(`Comanda #${fila.numeroComanda} devuelta a la cola`, 'exito')
      refrescar()
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo reintentar', 'error')
    } finally {
      setReintentando(null)
    }
  }

  const visibles = datos.filas.filter((f) => pasaFiltro(f, filtro))

  return (
    <div className="space-y-4">
      {/* ---------- Qué sistema está emitiendo ----------
          Va arriba del todo y no en un pie de página: sin esta línea, un
          administrador que ve todo «sin documento» no sabe si el sistema está
          roto o si simplemente le toca digitar, que son dos reacciones muy
          distintas. */}
      {datos.adaptador === 'manual' && (
        <div className="flex items-start gap-2 rounded-xl border border-oro-500/35 bg-oro-500/10 px-3 py-2.5 text-sm text-oro-200">
          <FileWarning className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <p>
            El Patio no está conectado a Globalsoft todavía. Estas ventas hay que
            digitarlas allá; esta pantalla es la lista de las que faltan.
          </p>
        </div>
      )}

      {/* ---------- Rango y filtros ---------- */}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-3">
          <label className="text-xs uppercase tracking-wide text-noche-400">
            Desde
            <input
              type="date"
              value={desde}
              max={hasta}
              onChange={(e) => setDesde(e.target.value)}
              className="mt-1 block min-h-[40px] rounded-xl border border-noche-700 bg-noche-900 px-3 text-sm text-crema-100"
            />
          </label>
          <label className="text-xs uppercase tracking-wide text-noche-400">
            Hasta
            <input
              type="date"
              value={hasta}
              min={desde}
              onChange={(e) => setHasta(e.target.value)}
              className="mt-1 block min-h-[40px] rounded-xl border border-noche-700 bg-noche-900 px-3 text-sm text-crema-100"
            />
          </label>
        </div>

        <div className="flex flex-wrap gap-2">
          {FILTROS.map(({ valor, etiqueta }) => (
            <button
              key={valor}
              type="button"
              onClick={() => setFiltro(valor)}
              className={`min-h-[40px] rounded-xl border px-3.5 text-sm font-medium transition ${
                filtro === valor
                  ? 'border-oro-500 bg-oro-500/15 text-oro-300'
                  : 'border-noche-700 bg-noche-900 text-noche-300 hover:bg-noche-800'
              }`}
            >
              {etiqueta}
            </button>
          ))}
        </div>
      </div>

      {cargando ? (
        <Cargando mensaje="Cruzando las ventas con el ERP" />
      ) : (
        <>
          {/* ---------- El resumen, que es lo que se mira primero ---------- */}
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Tarjeta
              titulo="Ventas del periodo"
              valor={String(datos.totalVentas)}
              detalle={formatoCOP(datos.montoTotal)}
            />
            <Tarjeta
              titulo="Facturadas"
              valor={String(datos.facturadas)}
              detalle="con documento del ERP"
              tono="listo"
            />
            <Tarjeta
              titulo="Sin conciliar"
              valor={String(datos.sinConciliar)}
              detalle={formatoCOP(datos.montoSinConciliar)}
              tono={datos.sinConciliar > 0 ? 'proceso' : undefined}
            />
            <Tarjeta
              titulo="Con error"
              valor={String(datos.conError)}
              detalle={datos.conError > 0 ? 'requieren revisión' : 'ninguna'}
              tono={datos.conError > 0 ? 'demorado' : undefined}
            />
          </div>

          {visibles.length === 0 ? (
            <Vacio
              icono={CheckCircle2}
              titulo="No hay ventas que mostrar"
              descripcion={
                datos.totalVentas === 0
                  ? 'En este rango de fechas no se cobró ninguna cuenta.'
                  : 'Ninguna venta del periodo cae en este filtro.'
              }
            />
          ) : (
            <div className="overflow-x-auto rounded-2xl border border-noche-800">
              <table className="w-full min-w-[52rem] text-sm">
                <thead className="bg-noche-900 text-left text-xs uppercase tracking-wide text-noche-400">
                  <tr>
                    <th className="px-3 py-2.5 font-medium">Comanda</th>
                    <th className="px-3 py-2.5 font-medium">Fecha</th>
                    <th className="px-3 py-2.5 text-right font-medium">Total</th>
                    <th className="px-3 py-2.5 font-medium">Estado</th>
                    <th className="px-3 py-2.5 font-medium">Documento</th>
                    <th className="px-3 py-2.5 font-medium">Detalle</th>
                    <th className="px-3 py-2.5" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-noche-800">
                  {visibles.map((fila) => {
                    const estado = ESTADOS[fila.estado]
                    return (
                      <tr key={fila.envioId} className="align-top">
                        <td className="px-3 py-2.5 font-medium text-crema-100">
                          #{fila.numeroComanda}
                        </td>
                        <td className="px-3 py-2.5 text-noche-300">
                          {formatoFechaHora(fila.fechaVenta)}
                        </td>
                        <td className="px-3 py-2.5 text-right tabular-nums text-crema-100">
                          {formatoCOP(fila.total)}
                        </td>
                        <td className="px-3 py-2.5">
                          <Insignia tono={estado.tono}>{estado.texto}</Insignia>
                        </td>
                        <td className="px-3 py-2.5 tabular-nums text-noche-300">
                          {fila.documentoExterno ?? '—'}
                        </td>
                        <td className="max-w-xs px-3 py-2.5 text-xs text-noche-400">
                          {fila.error ? (
                            <span className="flex items-start gap-1.5 text-estado-demorado">
                              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
                              {fila.error}
                            </span>
                          ) : fila.intentos > 0 ? (
                            <span className="flex items-center gap-1.5">
                              <Clock className="h-3.5 w-3.5 shrink-0" aria-hidden />
                              {fila.intentos} {fila.intentos === 1 ? 'intento' : 'intentos'}
                            </span>
                          ) : (
                            '—'
                          )}
                        </td>
                        <td className="px-3 py-2.5 text-right">
                          {/* Una venta ya facturada no se reintenta: el backend
                              lo rechaza porque duplicaría el documento, y el
                              botón no debe sugerir lo contrario. */}
                          {fila.estado !== 'facturada_erp' && (
                            <Boton
                              variante="secundario"
                              tamano="compacto"
                              cargando={reintentando === fila.envioId}
                              icono={<RefreshCw className="h-4 w-4" aria-hidden />}
                              onClick={() => reintentar(fila)}
                            >
                              Reintentar
                            </Boton>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function Tarjeta({
  titulo,
  valor,
  detalle,
  tono,
}: {
  titulo: string
  valor: string
  detalle: string
  tono?: 'listo' | 'proceso' | 'demorado'
}) {
  const color =
    tono === 'listo'
      ? 'text-estado-listo'
      : tono === 'proceso'
        ? 'text-estado-proceso'
        : tono === 'demorado'
          ? 'text-estado-demorado'
          : 'text-crema-100'

  return (
    <div className="rounded-2xl border border-noche-800 bg-noche-900 p-3.5">
      <p className="text-xs uppercase tracking-wide text-noche-400">{titulo}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${color}`}>{valor}</p>
      <p className="mt-0.5 text-xs text-noche-400">{detalle}</p>
    </div>
  )
}
