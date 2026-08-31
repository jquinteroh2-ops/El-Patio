import { useState } from 'react'
import { BarChart3, Table2 } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { Reportes as DatosReportes } from '@/compartido/mockApi'
import { UMBRALES_COCINA } from '@/compartido/config'
import { claveDia, formatoCOP, formatoCOPCorto, formatoFecha } from '@/compartido/formato'
import { MenuExportar } from '@/componentes/ui/MenuExportar'
import { useSyncedState } from '@/compartido/useSyncedState'
import { BarrasHora, BarrasHorizontales, Tabla, Tarjeta } from './Graficas'
import { ETIQUETA_TIPO_PEDIDO } from '@/compartido/estados'

const VACIO: DatosReportes = {
  masVendidos: [],
  porFranja: [],
  porMesero: [],
  tiemposPorProducto: [],
  ventasPorDia: [],
  porCanal: [],
}

const DIAS = [7, 10, 30]

/** El rango en fechas concretas, que es lo que entiende el exportador. */
const haceDias = (dias: number): string => claveDia(new Date(Date.now() - dias * 86400000))

export default function Reportes() {
  const [dias, setDias] = useState(10)
  const [comoTabla, setComoTabla] = useState(false)

  const { datos, cargando } = useSyncedState<DatosReportes>(
    () => api.reportes(dias),
    VACIO,
    [dias],
    ['pagos', 'ordenes', 'todo'],
  )

  const totalPeriodo = datos.ventasPorDia.reduce((s, d) => s + d.total, 0)
  const maxDia = datos.ventasPorDia.reduce(
    (a, b) => (b.total > a.total ? b : a),
    { dia: '', total: 0 },
  )

  return (
    <div className="space-y-4">
      {/* ---------- Controles, en una sola fila sobre las gráficas ---------- */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-2">
          {DIAS.map((n) => (
            <button
              key={n}
              type="button"
              onClick={() => setDias(n)}
              className={`min-h-[40px] rounded-xl border px-3.5 text-sm font-medium transition ${
                dias === n
                  ? 'border-oro-500 bg-oro-500/15 text-oro-300'
                  : 'border-noche-700 bg-noche-900 text-noche-300 hover:bg-noche-800'
              }`}
            >
              {n} días
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setComoTabla((v) => !v)}
            className="flex min-h-[40px] items-center gap-2 rounded-xl border border-noche-700 bg-noche-900 px-3.5 text-sm font-medium text-noche-300 transition hover:bg-noche-800"
          >
            {comoTabla ? <BarChart3 className="h-4 w-4" aria-hidden /> : <Table2 className="h-4 w-4" aria-hidden />}
            {comoTabla ? 'Ver gráficas' : 'Ver tablas'}
          </button>

          {/* El rango de esta pantalla son «los últimos N días», así que se
              traduce a fechas concretas antes de mandarlo: el archivo lleva
              impreso el periodo exacto, no un «últimos 10 días» que dentro de
              un mes ya no significa lo mismo. */}
          <MenuExportar tipo="productos" desde={haceDias(dias)} hasta={claveDia()} />
        </div>
      </div>

      {cargando ? (
        <p className="py-12 text-center text-sm text-noche-400">Calculando reportes…</p>
      ) : (
        <>
          <section className="revelar-corto grid grid-cols-2 gap-2.5 lg:grid-cols-4">
            {[
              { etiqueta: `Ventas en ${dias} días`, valor: formatoCOP(totalPeriodo) },
              {
                etiqueta: 'Promedio por día',
                valor: formatoCOP(
                  datos.ventasPorDia.length ? Math.round(totalPeriodo / datos.ventasPorDia.length) : 0,
                ),
              },
              {
                etiqueta: 'Mejor día',
                valor: maxDia.dia ? formatoCOPCorto(maxDia.total) : '—',
                pie: maxDia.dia ? formatoFecha(maxDia.dia) : undefined,
              },
              {
                etiqueta: 'Productos distintos vendidos',
                valor: String(datos.masVendidos.length),
              },
            ].map((t) => (
              <div key={t.etiqueta} className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
                <p className="text-lg font-bold tabular-nums text-crema-100">{t.valor}</p>
                <p className="text-xs leading-tight text-noche-400">{t.etiqueta}</p>
                {t.pie && <p className="mt-0.5 text-xs text-oro-300">{t.pie}</p>}
              </div>
            ))}
          </section>

          <div className="grid gap-4 lg:grid-cols-2">
            {/* ---------- Venta por canal ---------- */}
            <Tarjeta
              titulo="De dónde viene la venta"
              descripcion={`Salón, domicilio y para llevar en los últimos ${dias} días`}
            >
              {comoTabla ? (
                <Tabla
                  columnas={['Canal', 'Pedidos', 'Venta', 'Ticket']}
                  filas={datos.porCanal.map((c) => [
                    ETIQUETA_TIPO_PEDIDO[c.canal],
                    c.ordenes,
                    formatoCOP(c.ventas),
                    formatoCOP(c.ticketPromedio),
                  ])}
                />
              ) : (
                <BarrasHorizontales
                  datos={datos.porCanal.map((c) => ({
                    etiqueta: ETIQUETA_TIPO_PEDIDO[c.canal],
                    valor: c.ventas,
                    texto: formatoCOPCorto(c.ventas),
                    detalle: `${c.ordenes} ${c.ordenes === 1 ? 'pedido' : 'pedidos'} · ${formatoCOPCorto(c.ticketPromedio)} c/u`,
                  }))}
                  vacio="Todavía no hay ventas en el período"
                />
              )}
            </Tarjeta>

            {/* ---------- Más vendidos ---------- */}
            <Tarjeta
              titulo="Productos más vendidos"
              descripcion={`Unidades despachadas en los últimos ${dias} días`}
            >
              {comoTabla ? (
                <Tabla
                  columnas={['Producto', 'Unidades', 'Ingreso']}
                  filas={datos.masVendidos.map((p) => [p.nombre, p.unidades, formatoCOP(p.ingreso)])}
                />
              ) : (
                <BarrasHorizontales
                  datos={datos.masVendidos.map((p) => ({
                    etiqueta: p.nombre,
                    valor: p.unidades,
                    texto: `${p.unidades} und.`,
                    detalle: formatoCOPCorto(p.ingreso),
                  }))}
                />
              )}
            </Tarjeta>

            {/* ---------- Franja horaria ---------- */}
            <Tarjeta
              titulo="Ventas por franja horaria"
              descripcion="A qué hora entra la plata. Sirve para armar los turnos."
            >
              {comoTabla ? (
                <Tabla
                  columnas={['Hora', 'Ventas', 'Cuentas']}
                  filas={datos.porFranja.map((f) => [f.franja, formatoCOP(f.ventas), f.ordenes])}
                />
              ) : (
                <BarrasHora
                  datos={datos.porFranja.map((f) => ({
                    hora: f.hora,
                    franja: f.franja,
                    valor: f.ventas,
                    texto: formatoCOPCorto(f.ventas),
                    detalle: `${f.ordenes} ${f.ordenes === 1 ? 'cuenta' : 'cuentas'}`,
                  }))}
                />
              )}
            </Tarjeta>

            {/* ---------- Meseros ---------- */}
            <Tarjeta
              titulo="Ventas por mesero"
              descripcion="Cuánto vendió cada uno y qué propina dejó su mesa"
            >
              {comoTabla ? (
                <Tabla
                  columnas={['Mesero', 'Cuentas', 'Ventas', 'Ticket', 'Propinas']}
                  filas={datos.porMesero.map((m) => [
                    m.nombre,
                    m.ordenes,
                    formatoCOP(m.ventas),
                    formatoCOP(m.ticketPromedio),
                    formatoCOP(m.propinas),
                  ])}
                />
              ) : (
                <BarrasHorizontales
                  datos={datos.porMesero.map((m) => ({
                    etiqueta: m.nombre,
                    valor: m.ventas,
                    texto: formatoCOP(m.ventas),
                    detalle: `${m.ordenes} cuentas · ticket ${formatoCOPCorto(m.ticketPromedio)}`,
                  }))}
                />
              )}
            </Tarjeta>

            {/* ---------- Tiempos de cocina ---------- */}
            <Tarjeta
              titulo="Tiempo de preparación por producto"
              descripcion={`Desde que sale la comanda hasta que el plato está listo. Rojo sobre ${UMBRALES_COCINA.demorado} min.`}
            >
              {comoTabla ? (
                <Tabla
                  columnas={['Producto', 'Minutos', 'Muestras']}
                  filas={datos.tiemposPorProducto.map((t) => [t.nombre, t.minutos, t.muestras])}
                />
              ) : (
                <BarrasHorizontales
                  vacio="Todavía no hay platos cronometrados"
                  datos={datos.tiemposPorProducto.map((t) => ({
                    etiqueta: t.nombre,
                    valor: t.minutos,
                    texto: `${t.minutos} min`,
                    detalle: `${t.muestras} ${t.muestras === 1 ? 'plato' : 'platos'}`,
                    tono:
                      t.minutos >= UMBRALES_COCINA.demorado
                        ? ('demorado' as const)
                        : t.minutos >= UMBRALES_COCINA.atencion
                          ? ('atencion' as const)
                          : ('normal' as const),
                  }))}
                />
              )}
            </Tarjeta>
          </div>

          {/* ---------- Serie diaria ---------- */}
          <Tarjeta titulo="Ventas por día" descripcion={`Últimos ${dias} días`}>
            {comoTabla ? (
              <Tabla
                columnas={['Día', 'Ventas']}
                filas={datos.ventasPorDia.map((d) => [formatoFecha(d.dia), formatoCOP(d.total)])}
              />
            ) : (
              <BarrasHorizontales
                datos={datos.ventasPorDia.map((d) => ({
                  etiqueta: formatoFecha(d.dia),
                  valor: d.total,
                  texto: formatoCOP(d.total),
                }))}
              />
            )}
          </Tarjeta>
        </>
      )}
    </div>
  )
}
