import { useMemo, useState } from 'react'
import { Banknote, CreditCard, Plus, Smartphone, Trash2 } from 'lucide-react'
import { formatoCOP } from '@/compartido/formato'
import type { DivisionPago } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'

export type MetodoSimple = DivisionPago['metodo']

export interface PartePago {
  nombre: string
  valor: number
  metodo: MetodoSimple
}

const METODOS: { id: MetodoSimple; etiqueta: string; icono: typeof Banknote }[] = [
  { id: 'efectivo', etiqueta: 'Efectivo', icono: Banknote },
  { id: 'tarjeta', etiqueta: 'Tarjeta', icono: CreditCard },
  { id: 'transferencia', etiqueta: 'Transferencia', icono: Smartphone },
]

interface Props {
  abierta: boolean
  total: number
  partes: PartePago[]
  cobrando: boolean
  onCambiarPartes: (partes: PartePago[]) => void
  onCerrar: () => void
  onCobrar: () => void
}

/**
 * Cobro. Soporta el caso comun de un solo medio de pago y el pago mixto, que en
 * un restaurante de mantel aparece todas las noches: una parte en efectivo y el
 * resto con tarjeta.
 */
export function HojaPago({
  abierta,
  total,
  partes,
  cobrando,
  onCambiarPartes,
  onCerrar,
  onCobrar,
}: Props) {
  const [recibido, setRecibido] = useState('')

  const sumado = useMemo(() => partes.reduce((s, p) => s + p.valor, 0), [partes])
  const diferencia = total - sumado
  const cuadra = diferencia === 0

  const efectivo = useMemo(
    () => partes.filter((p) => p.metodo === 'efectivo').reduce((s, p) => s + p.valor, 0),
    [partes],
  )
  const recibidoNumero = Number(recibido.replace(/\D/g, '')) || 0
  const cambio = recibidoNumero - efectivo

  const actualizar = (indice: number, cambios: Partial<PartePago>) =>
    onCambiarPartes(partes.map((p, i) => (i === indice ? { ...p, ...cambios } : p)))

  const agregarParte = () =>
    onCambiarPartes([
      ...partes,
      { nombre: `Pago ${partes.length + 1}`, valor: Math.max(0, diferencia), metodo: 'tarjeta' },
    ])

  const quitarParte = (indice: number) =>
    onCambiarPartes(partes.filter((_, i) => i !== indice))

  const unaSolaParte = partes.length === 1

  return (
    <HojaInferior
      abierta={abierta}
      titulo="Cobrar"
      descripcion={`Total ${formatoCOP(total)}`}
      onCerrar={onCerrar}
      pie={
        <Boton
          variante="exito"
          tamano="grande"
          bloque
          disabled={!cuadra}
          cargando={cobrando}
          onClick={onCobrar}
        >
          {cuadra ? `Confirmar cobro · ${formatoCOP(total)}` : 'Las partes no suman el total'}
        </Boton>
      }
    >
      <div className="space-y-4">
        {unaSolaParte ? (
          <div>
            <p className="mb-2 text-sm text-noche-300">¿Cómo paga la mesa?</p>
            <div className="grid grid-cols-3 gap-2">
              {METODOS.map(({ id, etiqueta, icono: Icono }) => {
                const activo = partes[0].metodo === id
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => actualizar(0, { metodo: id, valor: total })}
                    className={`flex min-h-[76px] flex-col items-center justify-center gap-1.5 rounded-xl border transition active:scale-95 ${
                      activo
                        ? 'border-oro-500 bg-oro-500/15 text-crema-100'
                        : 'border-noche-700 bg-noche-850 text-noche-300 hover:bg-noche-800'
                    }`}
                  >
                    <Icono className="h-5 w-5" aria-hidden />
                    <span className="text-xs font-medium">{etiqueta}</span>
                  </button>
                )
              })}
            </div>
          </div>
        ) : (
          <ul className="space-y-2">
            {partes.map((parte, indice) => (
              <li key={indice} className="rounded-xl border border-noche-700 bg-noche-850 p-3">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-crema-100">{parte.nombre}</span>
                  {partes.length > 1 && (
                    <button
                      type="button"
                      onClick={() => quitarParte(indice)}
                      aria-label={`Quitar ${parte.nombre}`}
                      className="flex h-9 w-9 items-center justify-center rounded-lg text-noche-500 transition hover:bg-estado-demorado/15 hover:text-estado-demorado"
                    >
                      <Trash2 className="h-4 w-4" aria-hidden />
                    </button>
                  )}
                </div>

                <div className="flex gap-2">
                  <input
                    inputMode="numeric"
                    value={parte.valor || ''}
                    onChange={(e) =>
                      actualizar(indice, { valor: Number(e.target.value.replace(/\D/g, '')) || 0 })
                    }
                    className="min-h-toque w-32 rounded-xl border border-noche-700 bg-noche-900 px-3 text-crema-100 focus:border-oro-500 focus:outline-none"
                  />
                  <div className="flex flex-1 gap-1">
                    {METODOS.map(({ id, icono: Icono, etiqueta }) => (
                      <button
                        key={id}
                        type="button"
                        title={etiqueta}
                        aria-label={etiqueta}
                        onClick={() => actualizar(indice, { metodo: id })}
                        className={`flex min-h-toque flex-1 items-center justify-center rounded-xl border transition ${
                          parte.metodo === id
                            ? 'border-oro-500 bg-oro-500/15 text-oro-300'
                            : 'border-noche-700 bg-noche-900 text-noche-400 hover:bg-noche-800'
                        }`}
                      >
                        <Icono className="h-4 w-4" aria-hidden />
                      </button>
                    ))}
                  </div>
                </div>

                {diferencia !== 0 && (
                  <button
                    type="button"
                    onClick={() => actualizar(indice, { valor: parte.valor + diferencia })}
                    className="mt-2 text-xs font-medium text-oro-300 underline-offset-2 hover:underline"
                  >
                    Cargar aquí lo que falta ({formatoCOP(diferencia)})
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}

        <div className="flex items-center justify-between gap-2">
          <Boton tamano="compacto" icono={<Plus className="h-4 w-4" />} onClick={agregarParte}>
            {unaSolaParte ? 'Pago mixto' : 'Otra parte'}
          </Boton>
          {!cuadra && (
            <span className="text-sm font-medium text-estado-demorado">
              {diferencia > 0 ? `Faltan ${formatoCOP(diferencia)}` : `Sobran ${formatoCOP(-diferencia)}`}
            </span>
          )}
        </div>

        {efectivo > 0 && (
          <div className="rounded-xl border border-noche-700 bg-noche-850 p-3">
            <p className="mb-2 text-sm text-noche-300">
              Efectivo a recibir: <strong className="text-crema-100">{formatoCOP(efectivo)}</strong>
            </p>
            <input
              inputMode="numeric"
              value={recibido}
              onChange={(e) => setRecibido(e.target.value)}
              placeholder="¿Con cuánto paga?"
              className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-900 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
            />
            {recibidoNumero > 0 && (
              <p
                className={`mt-2 text-sm font-semibold ${
                  cambio < 0 ? 'text-estado-demorado' : 'text-estado-listo'
                }`}
              >
                {cambio < 0
                  ? `Faltan ${formatoCOP(-cambio)}`
                  : `Cambio: ${formatoCOP(cambio)}`}
              </p>
            )}
          </div>
        )}
      </div>
    </HojaInferior>
  )
}
