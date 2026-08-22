import { useMemo, useState } from 'react'
import { Check, Equal, SplitSquareHorizontal } from 'lucide-react'
import { dividirEnPartesIguales, dividirPorItems, precioItem, type Cuenta } from '@/compartido/calculos'
import { formatoCOP } from '@/compartido/formato'
import type { ItemOrden } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Contador } from '@/componentes/ui/Contador'
import { HojaInferior } from '@/componentes/ui/HojaInferior'

export interface ParteCuenta {
  nombre: string
  valor: number
}

interface Props {
  abierta: boolean
  cuenta: Cuenta
  items: ItemOrden[]
  comensales: number
  onCerrar: () => void
  onAplicar: (partes: ParteCuenta[]) => void
}

type Modo = 'iguales' | 'productos'

/**
 * Division de la cuenta.
 *
 * En partes iguales el residuo en pesos se carga a la primera parte, y por
 * productos cada seleccion arrastra su porcion de INC, cargos y propina. En los
 * dos casos las partes suman exactamente el total: nadie paga de mas ni de menos.
 */
export function HojaDivision({ abierta, cuenta, items, comensales, onCerrar, onAplicar }: Props) {
  const [modo, setModo] = useState<Modo>('iguales')
  const [partes, setPartes] = useState(Math.max(2, Math.min(comensales, 6)))
  const [seleccion, setSeleccion] = useState<string[]>([])

  const vigentes = useMemo(() => items.filter((i) => i.estado !== 'anulado'), [items])

  const valoresIguales = useMemo(
    () => dividirEnPartesIguales(cuenta.total, partes),
    [cuenta.total, partes],
  )

  const porProductos = useMemo(
    () => dividirPorItems(vigentes, seleccion, cuenta),
    [vigentes, seleccion, cuenta],
  )

  const aplicar = () => {
    if (modo === 'iguales') {
      onAplicar(valoresIguales.map((valor, i) => ({ nombre: `Parte ${i + 1}`, valor })))
    } else {
      onAplicar([
        { nombre: 'Productos marcados', valor: porProductos.valorSeleccion },
        { nombre: 'El resto de la mesa', valor: porProductos.valorResto },
      ])
    }
    onCerrar()
  }

  const alternar = (id: string) =>
    setSeleccion((actual) =>
      actual.includes(id) ? actual.filter((x) => x !== id) : [...actual, id],
    )

  return (
    <HojaInferior
      abierta={abierta}
      titulo="Dividir la cuenta"
      descripcion={`Total ${formatoCOP(cuenta.total)}`}
      onCerrar={onCerrar}
      pie={
        <Boton
          variante="principal"
          tamano="grande"
          bloque
          disabled={modo === 'productos' && seleccion.length === 0}
          onClick={aplicar}
        >
          Aplicar división
        </Boton>
      }
    >
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => setModo('iguales')}
            className={`flex min-h-toque items-center justify-center gap-2 rounded-xl border text-sm font-medium transition ${
              modo === 'iguales'
                ? 'border-oro-500 bg-oro-500/15 text-crema-100'
                : 'border-noche-700 bg-noche-850 text-noche-300'
            }`}
          >
            <Equal className="h-4 w-4" aria-hidden />
            Partes iguales
          </button>
          <button
            type="button"
            onClick={() => setModo('productos')}
            className={`flex min-h-toque items-center justify-center gap-2 rounded-xl border text-sm font-medium transition ${
              modo === 'productos'
                ? 'border-oro-500 bg-oro-500/15 text-crema-100'
                : 'border-noche-700 bg-noche-850 text-noche-300'
            }`}
          >
            <SplitSquareHorizontal className="h-4 w-4" aria-hidden />
            Por productos
          </button>
        </div>

        {modo === 'iguales' ? (
          <div className="space-y-3">
            <div className="flex items-center justify-between gap-3">
              <span className="text-sm text-noche-300">¿Entre cuántos?</span>
              <Contador valor={partes} onCambiar={setPartes} minimo={2} maximo={20} />
            </div>
            <ul className="space-y-1.5">
              {valoresIguales.map((valor, i) => (
                <li
                  key={i}
                  className="flex items-center justify-between rounded-xl border border-noche-700 bg-noche-850 px-3 py-2.5"
                >
                  <span className="text-sm text-noche-300">Parte {i + 1}</span>
                  <span className="text-base font-semibold tabular-nums text-crema-100">
                    {formatoCOP(valor)}
                  </span>
                </li>
              ))}
            </ul>
            {valoresIguales[0] !== valoresIguales[1] && (
              <p className="text-xs text-noche-500">
                Los pesos que no reparten exacto se cargan a la primera parte, para que la suma
                cuadre con el total.
              </p>
            )}
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-noche-300">Marca lo que paga esta persona</p>
            <ul className="space-y-1.5">
              {vigentes.map((item) => {
                const activo = seleccion.includes(item.id)
                return (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => alternar(item.id)}
                      className={`flex w-full min-h-toque items-center gap-2.5 rounded-xl border px-3 py-2 text-left transition ${
                        activo
                          ? 'border-oro-500 bg-oro-500/15'
                          : 'border-noche-700 bg-noche-850 hover:bg-noche-800'
                      }`}
                    >
                      <span
                        className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-md border ${
                          activo ? 'border-oro-400 bg-oro-500' : 'border-noche-600'
                        }`}
                      >
                        {activo && <Check className="h-3.5 w-3.5 text-noche-950" aria-hidden />}
                      </span>
                      <span className="min-w-0 flex-1 truncate text-sm text-crema-100">
                        {item.cantidad} × {item.nombre}
                      </span>
                      <span className="shrink-0 text-sm tabular-nums text-noche-300">
                        {formatoCOP(precioItem(item))}
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>

            <div className="space-y-1.5 rounded-xl border border-noche-700 bg-noche-850 p-3">
              <div className="flex justify-between text-sm">
                <span className="text-noche-300">Productos marcados</span>
                <span className="font-semibold tabular-nums text-crema-100">
                  {formatoCOP(porProductos.valorSeleccion)}
                </span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-noche-300">El resto de la mesa</span>
                <span className="font-semibold tabular-nums text-crema-100">
                  {formatoCOP(porProductos.valorResto)}
                </span>
              </div>
              <p className="pt-1 text-xs text-noche-500">
                Cada parte lleva su porción del INC, los cargos y la propina.
              </p>
            </div>
          </div>
        )}
      </div>
    </HojaInferior>
  )
}
