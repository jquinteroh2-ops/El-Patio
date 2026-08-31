import type { ReactNode } from 'react'

/**
 * Los bloques que ocupan el sitio de un dato que todavía no llegó.
 *
 * ── Por qué un esqueleto y no «Cargando…» ────────────────────────────────────
 * Un texto centrado obliga a leerlo, no dice cuánto va a llegar, y al aparecer
 * los datos la pantalla salta entera: lo que había ocupaba una línea y lo que
 * llega ocupa quince. Un esqueleto con la forma de lo que viene reserva el sitio
 * desde el primer cuadro, así que nada se mueve cuando el dato aterriza, y de
 * paso dice qué va a llegar —una tabla, unas tarjetas— sin escribirlo.
 *
 * Van marcados con `aria-hidden` y el contenedor lleva el `role="status"`: un
 * lector de pantalla tiene que oír «cargando», no quince cajas vacías.
 * ─────────────────────────────────────────────────────────────────────────────
 */

interface BloqueProps {
  /** Alto y ancho en clases de Tailwind. Sin esto es una línea de texto. */
  className?: string
  /** Sobre el fondo cálido del sitio público en vez del neutro operativo. */
  claro?: boolean
}

/** Un bloque suelto. Es la pieza con la que se arma todo lo demás. */
export function Esqueleto({ className = 'h-4 w-full', claro = false }: BloqueProps) {
  return <div className={`${claro ? 'esqueleto-claro' : 'esqueleto'} ${className}`} aria-hidden />
}

/**
 * Envuelve un esqueleto y le pone el aviso que se oye.
 *
 * Se usa una vez por zona que carga, no una por bloque: quien navega con lector
 * de pantalla necesita oír «cargando» una sola vez.
 */
export function ZonaCargando({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div role="status" aria-busy="true" aria-live="polite" className="animate-aparecer">
      <span className="sr-only">{etiqueta}</span>
      {children}
    </div>
  )
}

/**
 * Varias líneas de texto de anchos distintos.
 *
 * La última sale más corta a propósito: los párrafos reales no terminan justo
 * en el margen derecho, y un bloque de líneas todas iguales se lee como una
 * tabla, no como texto.
 */
export function EsqueletoTexto({ lineas = 3, claro = false }: { lineas?: number; claro?: boolean }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: lineas }, (_, i) => (
        <Esqueleto
          key={i}
          claro={claro}
          className={`h-3.5 ${i === lineas - 1 ? 'w-2/5' : i % 2 === 0 ? 'w-full' : 'w-4/5'}`}
        />
      ))}
    </div>
  )
}

/** Una fila de tabla: unas cuantas celdas de anchos distintos. */
export function EsqueletoFila({ columnas = 4 }: { columnas?: number }) {
  return (
    <div className="flex items-center gap-3 border-b border-noche-800 px-3 py-3.5">
      {Array.from({ length: columnas }, (_, i) => (
        <Esqueleto key={i} className={`h-3.5 ${i === 0 ? 'w-2/5' : 'flex-1'}`} />
      ))}
    </div>
  )
}

/** Una tabla entera, con su encabezado. */
export function EsqueletoTabla({ filas = 6, columnas = 4 }: { filas?: number; columnas?: number }) {
  return (
    <ZonaCargando etiqueta="Cargando la tabla">
      <div className="overflow-hidden rounded-2xl border border-noche-800 bg-noche-900">
        <div className="flex items-center gap-3 border-b border-noche-800 bg-noche-850 px-3 py-3">
          {Array.from({ length: columnas }, (_, i) => (
            <Esqueleto key={i} className={`h-3 ${i === 0 ? 'w-2/5' : 'flex-1'}`} />
          ))}
        </div>
        {/*
          Cada fila entra un poco después de la anterior. El retraso se corta a
          las seis primeras: más allá, la última tardaría casi un segundo en
          aparecer y el esqueleto se volvería más lento que los datos.
        */}
        {Array.from({ length: filas }, (_, i) => (
          <div key={i} className="animate-entrada" style={{ animationDelay: `${Math.min(i, 6) * 45}ms` }}>
            <EsqueletoFila columnas={columnas} />
          </div>
        ))}
      </div>
    </ZonaCargando>
  )
}

/** Una tarjeta con su título, su texto y su pie. */
export function EsqueletoTarjeta({ claro = false }: { claro?: boolean }) {
  const marco = claro
    ? 'rounded-2xl border border-oro-500/15 bg-onix-900 p-5'
    : 'rounded-2xl border border-noche-800 bg-noche-900 p-4'

  return (
    <div className={marco}>
      <Esqueleto claro={claro} className="h-4 w-1/2" />
      <div className="mt-3">
        <EsqueletoTexto lineas={2} claro={claro} />
      </div>
      <div className="mt-4 flex items-center gap-3">
        <Esqueleto claro={claro} className="h-8 w-24 rounded-xl" />
        <Esqueleto claro={claro} className="h-8 w-16 rounded-xl" />
      </div>
    </div>
  )
}

/** Una rejilla de tarjetas. */
export function EsqueletoTarjetas({
  cantidad = 6,
  claro = false,
  etiqueta = 'Cargando',
}: {
  cantidad?: number
  claro?: boolean
  etiqueta?: string
}) {
  return (
    <ZonaCargando etiqueta={etiqueta}>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: cantidad }, (_, i) => (
          <div key={i} className="animate-entrada" style={{ animationDelay: `${Math.min(i, 6) * 45}ms` }}>
            <EsqueletoTarjeta claro={claro} />
          </div>
        ))}
      </div>
    </ZonaCargando>
  )
}

/**
 * Las cifras grandes de un tablero: el número y su etiqueta.
 *
 * Es la que más se nota si falta: sin ella, la fila de indicadores nace vacía y
 * empuja media pantalla hacia abajo cuando llegan las cifras.
 */
export function EsqueletoCifras({ cantidad = 4 }: { cantidad?: number }) {
  return (
    <ZonaCargando etiqueta="Cargando las cifras">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {Array.from({ length: cantidad }, (_, i) => (
          <div
            key={i}
            className="animate-entrada rounded-2xl border border-noche-800 bg-noche-900 p-4"
            style={{ animationDelay: `${i * 45}ms` }}
          >
            <Esqueleto className="h-2.5 w-20" />
            <Esqueleto className="mt-3 h-7 w-28" />
          </div>
        ))}
      </div>
    </ZonaCargando>
  )
}
