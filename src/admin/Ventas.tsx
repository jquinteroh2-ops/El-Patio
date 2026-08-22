import { useMemo, useState } from 'react'
import { MapPin, Receipt } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { VentaHistorica } from '@/compartido/mockApi'
import { claveDia, formatoCOP, formatoFecha, formatoHora } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { MetodoPago, Usuario } from '@/compartido/tipos'
import { MapaEntrega } from '@/componentes/MapaEntrega'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Insignia } from '@/componentes/ui/Insignia'
import { Vacio } from '@/componentes/ui/Vacio'
import { Comprobante } from '@/comandera/Comprobante'

const METODOS: { id: MetodoPago | 'todos'; etiqueta: string }[] = [
  { id: 'todos', etiqueta: 'Todos' },
  { id: 'efectivo', etiqueta: 'Efectivo' },
  { id: 'tarjeta', etiqueta: 'Tarjeta' },
  { id: 'transferencia', etiqueta: 'Transferencia' },
  { id: 'mixto', etiqueta: 'Mixto' },
]

const NOMBRE_METODO: Record<MetodoPago, string> = {
  efectivo: 'Efectivo',
  tarjeta: 'Tarjeta',
  transferencia: 'Transferencia',
  mixto: 'Mixto',
}

const CAMPO =
  'min-h-toque rounded-xl border border-noche-700 bg-noche-900 px-3 text-crema-100 focus:border-oro-500 focus:outline-none'

const haceDias = (dias: number): string => claveDia(new Date(Date.now() - dias * 86400000))

export default function Ventas() {
  const [desde, setDesde] = useState(haceDias(7))
  const [hasta, setHasta] = useState(claveDia())
  const [meseroId, setMeseroId] = useState('')
  const [metodo, setMetodo] = useState<MetodoPago | 'todos'>('todos')
  const [detalle, setDetalle] = useState<VentaHistorica | null>(null)

  const { datos: usuarios } = useSyncedState<Usuario[]>(
    () => api.listarUsuarios(),
    [],
    [],
    ['usuarios', 'todo'],
  )

  const { datos: ventas, cargando } = useSyncedState<VentaHistorica[]>(
    () =>
      api.historicoVentas({
        desde,
        hasta,
        meseroId: meseroId || undefined,
        metodo: metodo === 'todos' ? undefined : metodo,
      }),
    [],
    [desde, hasta, meseroId, metodo],
    ['pagos', 'ordenes', 'todo'],
  )

  const meseros = usuarios.filter((u) => u.rol === 'mesero')

  const totales = useMemo(
    () => ({
      venta: ventas.reduce((s, v) => s + v.pago.total, 0),
      propinas: ventas.reduce((s, v) => s + v.pago.propina, 0),
      inc: ventas.reduce((s, v) => s + v.pago.inc, 0),
      comensales: ventas.reduce((s, v) => s + v.orden.comensales, 0),
    }),
    [ventas],
  )

  return (
    <div className="space-y-4">
      {/* ---------- Filtros ---------- */}
      <section className="flex flex-wrap items-end gap-2 rounded-2xl border border-noche-800 bg-noche-900 p-3">
        <label className="block">
          <span className="mb-1 block text-xs uppercase tracking-wide text-noche-400">Desde</span>
          <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} className={CAMPO} />
        </label>
        <label className="block">
          <span className="mb-1 block text-xs uppercase tracking-wide text-noche-400">Hasta</span>
          <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} className={CAMPO} />
        </label>
        <label className="block">
          <span className="mb-1 block text-xs uppercase tracking-wide text-noche-400">Mesero</span>
          <select value={meseroId} onChange={(e) => setMeseroId(e.target.value)} className={CAMPO}>
            <option value="">Todos</option>
            {meseros.map((u) => (
              <option key={u.id} value={u.id}>
                {u.nombre}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1 block text-xs uppercase tracking-wide text-noche-400">
            Medio de pago
          </span>
          <select
            value={metodo}
            onChange={(e) => setMetodo(e.target.value as MetodoPago | 'todos')}
            className={CAMPO}
          >
            {METODOS.map((m) => (
              <option key={m.id} value={m.id}>
                {m.etiqueta}
              </option>
            ))}
          </select>
        </label>
      </section>

      {/* ---------- Totales del filtro ---------- */}
      <section className="grid grid-cols-2 gap-2.5 lg:grid-cols-4">
        {[
          { etiqueta: 'Ventas', valor: formatoCOP(totales.venta) },
          { etiqueta: 'Cuentas', valor: String(ventas.length) },
          { etiqueta: 'Propinas', valor: formatoCOP(totales.propinas) },
          { etiqueta: 'INC recaudado', valor: formatoCOP(totales.inc) },
        ].map((t) => (
          <div key={t.etiqueta} className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
            <p className="text-lg font-bold tabular-nums text-crema-100">{t.valor}</p>
            <p className="text-xs text-noche-400">{t.etiqueta}</p>
          </div>
        ))}
      </section>

      {/* ---------- Listado ---------- */}
      {cargando ? (
        <p className="py-8 text-center text-sm text-noche-400">Cargando ventas…</p>
      ) : ventas.length === 0 ? (
        <Vacio
          icono={Receipt}
          titulo="Sin ventas en ese rango"
          descripcion="Cambia las fechas o quita los filtros."
        />
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-noche-800">
          <table className="w-full min-w-[720px] text-sm">
            <thead className="bg-noche-900 text-left text-xs uppercase tracking-wide text-noche-400">
              <tr>
                <th className="px-3 py-2.5 font-medium">Fecha</th>
                <th className="px-3 py-2.5 font-medium">Comanda</th>
                <th className="px-3 py-2.5 font-medium">Mesa</th>
                <th className="px-3 py-2.5 font-medium">Mesero</th>
                <th className="px-3 py-2.5 font-medium">Medio</th>
                <th className="px-3 py-2.5 text-right font-medium">Propina</th>
                <th className="px-3 py-2.5 text-right font-medium">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-noche-800">
              {ventas.map((venta) => (
                <tr
                  key={venta.pago.id}
                  onClick={() => setDetalle(venta)}
                  className="cursor-pointer bg-noche-950 transition hover:bg-noche-900"
                >
                  <td className="whitespace-nowrap px-3 py-2.5 text-noche-300">
                    {formatoFecha(venta.pago.fechaHora)}
                    <span className="ml-1.5 text-xs text-noche-500">
                      {formatoHora(venta.pago.fechaHora)}
                    </span>
                  </td>
                  <td className="px-3 py-2.5 text-noche-300">#{venta.orden.numero}</td>
                  <td className="px-3 py-2.5 text-crema-100">{venta.mesaEtiqueta}</td>
                  <td className="px-3 py-2.5 text-noche-300">{venta.meseroNombre}</td>
                  <td className="px-3 py-2.5">
                    <Insignia tono={venta.pago.metodo === 'mixto' ? 'oro' : 'neutro'}>
                      {NOMBRE_METODO[venta.pago.metodo]}
                    </Insignia>
                  </td>
                  <td className="px-3 py-2.5 text-right tabular-nums text-noche-300">
                    {venta.pago.propina > 0 ? formatoCOP(venta.pago.propina) : '—'}
                  </td>
                  <td className="px-3 py-2.5 text-right font-semibold tabular-nums text-crema-100">
                    {formatoCOP(venta.pago.total)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <HojaInferior
        abierta={!!detalle}
        titulo={detalle ? `Comanda #${detalle.orden.numero}` : ''}
        descripcion={detalle ? `${detalle.mesaEtiqueta} · ${detalle.meseroNombre}` : undefined}
        onCerrar={() => setDetalle(null)}
      >
        {detalle && (
          <>
            {/*
              Un domicilio ya cobrado no dice a dónde fue: la dirección queda
              guardada en la orden y quien revisa el día no la ve. Con el mapa
              aquí, un «no me llegó» o una zona que salió cara se resuelven
              mirando, sin ir a preguntarle a recepción.
            */}
            {detalle.orden.tipo === 'domicilio' && (
              <div className="mb-3 rounded-2xl border border-noche-800 bg-noche-950 p-3">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-noche-400">
                  Dónde se entregó
                </p>
                <p className="flex items-start gap-1.5 text-sm text-crema-100">
                  <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-noche-500" aria-hidden />
                  <span>
                    {detalle.orden.cliente?.direccion ?? 'Sin dirección anotada'}
                    {detalle.orden.cliente?.barrio && (
                      <span className="text-noche-400"> · {detalle.orden.cliente.barrio}</span>
                    )}
                  </span>
                </p>
                <MapaEntrega
                  ubicacion={detalle.orden.ubicacion}
                  direccion={detalle.orden.cliente?.direccion}
                  barrio={detalle.orden.cliente?.barrio}
                  alto="h-52"
                  titulo={`Dónde se entregó el pedido n.º ${detalle.orden.numero}`}
                  className="mt-2 rounded-xl border border-noche-800"
                />
              </div>
            )}

            <Comprobante
              pago={detalle.pago}
              orden={detalle.orden}
              mesaEtiqueta={detalle.mesaEtiqueta}
              meseroNombre={detalle.meseroNombre}
            />
          </>
        )}
      </HojaInferior>
    </div>
  )
}
