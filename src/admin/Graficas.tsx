import { useState, type ReactNode } from 'react'

/**
 * Graficas de los reportes, dibujadas a mano.
 *
 * Todas son de una sola serie: la longitud de la barra ya codifica la magnitud,
 * asi que el color no vuelve a codificar lo mismo. Un solo ambar plano, sin
 * leyenda —el titulo nombra la serie— y el valor escrito al lado de cada barra,
 * que es lo que de verdad se lee.
 */

interface Punto {
  etiqueta: string
  valor: number
  /** Texto ya formateado que se escribe junto a la barra. */
  texto: string
  /** Segunda linea bajo la etiqueta: contexto, nunca el dato principal. */
  detalle?: string
  /** Semantica de estado (demora). Siempre acompanada del valor escrito. */
  tono?: 'normal' | 'atencion' | 'demorado'
}

const RELLENO: Record<NonNullable<Punto['tono']>, string> = {
  normal: 'bg-ambar-500',
  atencion: 'bg-estado-proceso',
  demorado: 'bg-estado-demorado',
}

export function Tarjeta({
  titulo,
  descripcion,
  children,
  accion,
}: {
  titulo: string
  descripcion?: string
  children: ReactNode
  accion?: ReactNode
}) {
  return (
    <section className="rounded-2xl border border-noche-800 bg-noche-900 p-4">
      <header className="mb-4 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold text-crema-100">{titulo}</h2>
          {descripcion && <p className="mt-0.5 text-xs text-noche-400">{descripcion}</p>}
        </div>
        {accion}
      </header>
      {children}
    </section>
  )
}

/** Barras horizontales: la forma correcta cuando las etiquetas son nombres. */
export function BarrasHorizontales({ datos, vacio = 'Sin datos' }: { datos: Punto[]; vacio?: string }) {
  if (datos.length === 0) return <p className="py-6 text-center text-sm text-noche-500">{vacio}</p>

  const maximo = Math.max(...datos.map((d) => d.valor), 1)

  return (
    <ul className="space-y-2.5">
      {datos.map((punto) => (
        <li key={punto.etiqueta} className="group">
          <div className="mb-1 flex items-baseline justify-between gap-3">
            <span className="min-w-0 truncate text-sm text-crema-100">
              {punto.etiqueta}
              {punto.detalle && (
                <span className="ml-1.5 text-xs text-noche-500">{punto.detalle}</span>
              )}
            </span>
            <span className="shrink-0 text-sm font-semibold tabular-nums text-crema-100">
              {punto.texto}
            </span>
          </div>
          {/* Riel recesivo; la barra arranca pegada a la linea base. */}
          <div className="h-2 w-full overflow-hidden rounded-full bg-noche-800">
            <div
              className={`h-full rounded-r-[4px] transition-all duration-500 ${RELLENO[punto.tono ?? 'normal']} group-hover:brightness-110`}
              style={{ width: `${Math.max(2, (punto.valor / maximo) * 100)}%` }}
            />
          </div>
        </li>
      ))}
    </ul>
  )
}

/**
 * Barras verticales para la distribucion por hora. Son demasiado angostas para
 * escribir el valor en cada una, asi que solo se rotula el pico y el resto se
 * consulta al pasar el puntero.
 */
export function BarrasHora({
  datos,
}: {
  datos: { hora: number; franja: string; valor: number; texto: string; detalle: string }[]
}) {
  const [activa, setActiva] = useState<number | null>(null)

  if (datos.length === 0)
    return <p className="py-6 text-center text-sm text-noche-500">Sin ventas en el periodo</p>

  const maximo = Math.max(...datos.map((d) => d.valor), 1)
  const pico = datos.reduce((a, b) => (b.valor > a.valor ? b : a))
  const enfocada = datos.find((d) => d.hora === activa)

  return (
    <div>
      <div className="mb-2 flex h-6 items-center justify-end">
        {enfocada ? (
          <p className="animate-entrada text-xs text-noche-300">
            <span className="font-semibold text-crema-100">{enfocada.franja}</span> ·{' '}
            {enfocada.texto} · {enfocada.detalle}
          </p>
        ) : (
          <p className="text-xs text-noche-500">
            Hora pico: <span className="font-semibold text-ambar-300">{pico.franja}</span> con{' '}
            {pico.texto}
          </p>
        )}
      </div>

      {/* gap-[2px] deja el respiro de superficie entre barras vecinas */}
      <div className="flex h-40 items-end gap-[2px]">
        {datos.map((punto) => (
          <button
            key={punto.hora}
            type="button"
            onMouseEnter={() => setActiva(punto.hora)}
            onMouseLeave={() => setActiva(null)}
            onFocus={() => setActiva(punto.hora)}
            onBlur={() => setActiva(null)}
            aria-label={`${punto.franja}: ${punto.texto}, ${punto.detalle}`}
            className="group flex h-full flex-1 flex-col justify-end"
          >
            <span
              className={`w-full rounded-t-[4px] transition-all duration-300 ${
                activa === punto.hora || (activa === null && punto.hora === pico.hora)
                  ? 'bg-ambar-400'
                  : 'bg-ambar-500/70 group-hover:bg-ambar-400'
              }`}
              style={{ height: `${Math.max(3, (punto.valor / maximo) * 100)}%` }}
            />
          </button>
        ))}
      </div>

      <div className="mt-1.5 flex gap-[2px]">
        {datos.map((punto) => (
          <span
            key={punto.hora}
            className="flex-1 text-center text-[0.6rem] tabular-nums text-noche-500"
          >
            {punto.hora}
          </span>
        ))}
      </div>
      <p className="mt-1 text-center text-[0.65rem] text-noche-600">hora del día</p>
    </div>
  )
}

/** Vista de tabla: el mismo dato sin depender de la vista ni del color. */
export function Tabla({
  columnas,
  filas,
}: {
  columnas: string[]
  filas: (string | number)[][]
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="text-left text-xs uppercase tracking-wide text-noche-400">
          <tr>
            {columnas.map((c, i) => (
              <th key={c} className={`pb-2 font-medium ${i > 0 ? 'text-right' : ''}`}>
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-noche-800">
          {filas.map((fila, i) => (
            <tr key={i}>
              {fila.map((celda, j) => (
                <td
                  key={j}
                  className={`py-2 ${j > 0 ? 'text-right tabular-nums text-noche-300' : 'text-crema-100'}`}
                >
                  {celda}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
