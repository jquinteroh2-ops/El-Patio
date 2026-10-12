import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Check, CheckCheck, ClipboardCopy, DoorOpen, Receipt, Wallet } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { MesaEnMapa, OrdenDetallada } from '@/compartido/mockApi'
import { calcularCuenta, precioItem } from '@/compartido/calculos'
import { formatoCOP, formatoHora, tiempoTranscurrido } from '@/compartido/formato'
import { useReloj, useSyncedState } from '@/compartido/useSyncedState'
import type { ItemOrden } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Insignia } from '@/componentes/ui/Insignia'
import { Vacio } from '@/componentes/ui/Vacio'
import { Esqueleto } from '@/componentes/ui/Esqueleto'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { useAvisos } from '@/componentes/ui/Avisos'
import { ETIQUETA_ITEM, TONO_ITEM } from '@/compartido/estados'

/**
 * La caja: las cuentas abiertas del salon, para facturarlas en Globalsoft.
 *
 * La factura electronica no sale de este sistema. La emite el software que el
 * restaurante ya tiene habilitado ante la DIAN, y el cajero la arma ahi a mano
 * con lo que ve en esta pantalla. Por eso lo que se muestra es justo lo que hay
 * que digitar: cantidad, producto, valor, y el total que debe cuadrar.
 *
 * Arriba van las mesas que ya pidieron la cuenta, que son las que hay que
 * facturar ya. Debajo las que siguen consumiendo, para que el cajero vea venir
 * el trabajo. Cuando exista la conexion directa con Globalsoft esta pantalla
 * sigue sirviendo para revisar que lo que llego alla es lo mismo.
 *
 * No toca el servidor: lee lo mismo que la comandera, con los permisos que el
 * cajero ya tenia.
 */

const etiquetaMesa = (mesa: Pick<MesaEnMapa, 'nombre' | 'numero'>): string =>
  mesa.nombre ?? `Mesa ${mesa.numero}`

const enCuenta = (items: ItemOrden[]): ItemOrden[] => items.filter((i) => i.estado !== 'anulado')

const descripcionItem = (item: ItemOrden): string => {
  const modificadores = item.modificadoresSeleccionados.map((m) => m.valor).filter(Boolean)
  return modificadores.length ? `${item.nombre} (${modificadores.join(', ')})` : item.nombre
}

/** Precio de una unidad con sus adicionales: el que se digita en la factura. */
const unitarioItem = (item: ItemOrden): number =>
  item.cantidad > 0 ? precioItem(item) / item.cantidad : 0

export default function Caja() {
  const navegar = useNavigate()
  const { mostrar } = useAvisos()
  useReloj(30000)

  const { datos: mesas, cargando } = useSyncedState<MesaEnMapa[]>(
    () => api.listarMesas(),
    [],
    [],
    ['mesas', 'ordenes', 'pagos', 'todo'],
  )

  // Primero las que pidieron la cuenta; dentro de cada grupo, la mas antigua
  // arriba, que es la que lleva mas rato esperando.
  const abiertas = useMemo(
    () =>
      mesas
        .filter((m) => m.ordenActivaId)
        .sort((a, b) => {
          const pa = a.estado === 'cuenta_pedida' ? 0 : 1
          const pb = b.estado === 'cuenta_pedida' ? 0 : 1
          if (pa !== pb) return pa - pb
          return (a.abiertaEn ?? '').localeCompare(b.abiertaEn ?? '')
        }),
    [mesas],
  )

  const porFacturar = abiertas.filter((m) => m.estado === 'cuenta_pedida')
  const consumiendo = abiertas.filter((m) => m.estado !== 'cuenta_pedida')

  const [seleccionId, setSeleccionId] = useState<string | null>(null)

  // Si la mesa elegida se cobro o se cerro, se pasa sola a la siguiente.
  useEffect(() => {
    if (seleccionId && abiertas.some((m) => m.id === seleccionId)) return
    setSeleccionId(abiertas[0]?.id ?? null)
  }, [abiertas, seleccionId])

  const { datos: detalle, cargando: cargandoDetalle } = useSyncedState<OrdenDetallada | null>(
    () => (seleccionId ? api.obtenerOrdenDeMesa(seleccionId) : Promise.resolve(null)),
    null,
    [seleccionId],
    ['ordenes', 'mesas', 'cocina', 'todo'],
  )

  const orden = detalle?.orden ?? null
  const items = orden ? enCuenta(orden.items) : []
  const cuenta = orden ? calcularCuenta(orden, detalle?.porcentajeInc ?? 8) : null

  const [ocupado, setOcupado] = useState(false)
  const [liberando, setLiberando] = useState(false)
  const [motivo, setMotivo] = useState('')

  // Lo enviado que sigue en la pantalla de cocina aunque ya este en la mesa.
  const porEntregar = orden
    ? orden.items.filter((i) => i.turnoEnvio > 0 && i.estado !== 'anulado' && i.estado !== 'servido').length
    : 0
  // Misma regla que el servidor: nada enviado a cocina y sin cargos.
  const sinConsumo =
    !!orden &&
    !orden.items.some((i) => i.turnoEnvio > 0 && i.estado !== 'anulado') &&
    orden.cargosAdicionales.length === 0

  const ejecutar = async (accion: () => Promise<string>) => {
    setOcupado(true)
    try {
      mostrar(await accion(), 'exito')
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo completar la acción', 'error')
    } finally {
      setOcupado(false)
    }
  }

  const entregarTodo = () =>
    ejecutar(async () => {
      if (!orden) throw new Error('No hay cuenta seleccionada')
      await api.marcarTodoEntregado(orden.id)
      return 'Todo entregado. Ya no aparece en cocina.'
    })

  const liberar = () =>
    ejecutar(async () => {
      if (!orden || !detalle) throw new Error('No hay cuenta seleccionada')
      if (!motivo.trim()) throw new Error('Escribe por qué se desocupa la mesa')
      await api.liberarMesaSinConsumo(orden.id, motivo.trim())
      setLiberando(false)
      setMotivo('')
      return `${etiquetaMesa(detalle.mesa)} quedó libre`
    })

  const copiarDetalle = async () => {
    if (!detalle || !orden || !cuenta) return
    const lineas = [
      `${etiquetaMesa(detalle.mesa)} · Orden #${orden.numero} · ${detalle.meseroNombre}`,
      '',
      ...items.map(
        (i) =>
          `${i.cantidad} x ${descripcionItem(i)} — ${formatoCOP(unitarioItem(i))} c/u = ${formatoCOP(precioItem(i))}`,
      ),
      ...orden.cargosAdicionales.map((c) => `Cargo: ${c.nombre} = ${formatoCOP(c.valor)}`),
      '',
      `Subtotal: ${formatoCOP(cuenta.subtotal)}`,
      `INC ${cuenta.porcentajeInc}%: ${formatoCOP(cuenta.inc)}`,
      ...(cuenta.cargosAdicionales ? [`Cargos: ${formatoCOP(cuenta.cargosAdicionales)}`] : []),
      `TOTAL (sin propina): ${formatoCOP(cuenta.total)}`,
    ]
    try {
      await navigator.clipboard.writeText(lineas.join('\n'))
      mostrar('Detalle copiado', 'exito')
    } catch {
      mostrar('No se pudo copiar. Selecciónelo a mano.', 'error')
    }
  }

  if (cargando) {
    return (
      <div className="grid gap-3 lg:grid-cols-[minmax(0,20rem)_1fr]">
        <Esqueleto className="h-64 rounded-2xl" />
        <Esqueleto className="h-96 rounded-2xl" />
      </div>
    )
  }

  if (abiertas.length === 0) {
    return (
      <Vacio
        icono={Receipt}
        titulo="No hay cuentas abiertas"
        descripcion="Cuando una mesa tenga pedido aparece aquí, y sube arriba en cuanto pida la cuenta."
      />
    )
  }

  const Tarjeta = ({ mesa }: { mesa: MesaEnMapa }) => {
    const activa = mesa.id === seleccionId
    const pidio = mesa.estado === 'cuenta_pedida'
    return (
      <button
        type="button"
        onClick={() => setSeleccionId(mesa.id)}
        className={`flex w-full items-center justify-between gap-3 rounded-xl border px-3.5 py-3 text-left transition ${
          activa
            ? 'border-oro-500 bg-oro-500/10'
            : pidio
              ? 'border-estado-proceso/40 bg-noche-900 hover:bg-noche-850'
              : 'border-noche-800 bg-noche-900 hover:bg-noche-850'
        }`}
      >
        <span className="min-w-0">
          <span className="block font-semibold text-crema-100">{etiquetaMesa(mesa)}</span>
          <span className="block truncate text-xs text-noche-400">
            {mesa.ordenNumero ? `#${mesa.ordenNumero} · ` : ''}
            {mesa.meseroNombre ?? 'Sin mesero'}
            {mesa.abiertaEn ? ` · ${tiempoTranscurrido(mesa.abiertaEn)}` : ''}
          </span>
        </span>
        <span className="shrink-0 text-right">
          <span className="block font-semibold tabular-nums text-crema-100">{formatoCOP(mesa.total)}</span>
          {pidio && <span className="text-xs font-medium text-estado-proceso">Pidió la cuenta</span>}
        </span>
      </button>
    )
  }

  return (
    <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,20rem)_1fr]">
      <aside className="space-y-4">
        <section>
          <h2 className="mb-2 text-xs font-semibold uppercase tracking-wider text-estado-proceso">
            Por facturar ({porFacturar.length})
          </h2>
          {porFacturar.length === 0 ? (
            <p className="rounded-xl border border-dashed border-noche-800 px-3.5 py-3 text-sm text-noche-400">
              Ninguna mesa ha pedido la cuenta.
            </p>
          ) : (
            <div className="space-y-2">
              {porFacturar.map((m) => (
                <Tarjeta key={m.id} mesa={m} />
              ))}
            </div>
          )}
        </section>

        {consumiendo.length > 0 && (
          <section>
            <h2 className="mb-2 text-xs font-semibold uppercase tracking-wider text-noche-400">
              Consumiendo ({consumiendo.length})
            </h2>
            <div className="space-y-2">
              {consumiendo.map((m) => (
                <Tarjeta key={m.id} mesa={m} />
              ))}
            </div>
          </section>
        )}
      </aside>

      <section className="rounded-2xl border border-noche-800 bg-noche-900 p-4 sm:p-5">
        {!detalle || !orden || !cuenta ? (
          cargandoDetalle ? (
            <Esqueleto className="h-80 rounded-xl" />
          ) : (
            <Vacio icono={Receipt} titulo="Esta mesa ya no tiene cuenta abierta" />
          )
        ) : (
          <>
            <header className="mb-4 flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-crema-100">
                  {etiquetaMesa(detalle.mesa)} · Orden #{orden.numero}
                </h2>
                <p className="text-sm text-noche-400">
                  {detalle.meseroNombre} · {orden.comensales}{' '}
                  {orden.comensales === 1 ? 'comensal' : 'comensales'} · abierta {formatoHora(orden.abiertaEn)}
                </p>
              </div>
              {orden.estado === 'cuenta_pedida' ? (
                <Insignia tono="proceso">Pidió la cuenta</Insignia>
              ) : (
                <Insignia>Consumiendo</Insignia>
              )}
            </header>

            {items.length === 0 ? (
              <p className="py-6 text-center text-sm text-noche-400">Todavía no hay productos en esta cuenta.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-noche-800 text-left text-xs uppercase tracking-wider text-noche-400">
                      <th className="py-2 pr-3 font-medium">Cant.</th>
                      <th className="py-2 pr-3 font-medium">Producto</th>
                      <th className="py-2 pr-3 font-medium">Cocina</th>
                      <th className="py-2 pr-3 text-right font-medium">Unitario</th>
                      <th className="py-2 text-right font-medium">Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((i) => {
                      // Lo enviado que sigue en la pantalla de cocina. La caja lo
                      // puede sacar cuando ve salir el plato: el cocinero muchas
                      // veces tiene las manos ocupadas para marcarlo.
                      const enCocina =
                        i.turnoEnvio > 0 &&
                        (i.estado === 'pendiente' || i.estado === 'en_preparacion' || i.estado === 'listo')
                      return (
                        <tr key={i.id} className="border-b border-noche-850 align-middle">
                          <td className="py-2 pr-3 tabular-nums text-crema-100">{i.cantidad}</td>
                          <td className="py-2 pr-3 text-crema-100">{descripcionItem(i)}</td>
                          <td className="py-2 pr-3">
                            <div className="flex flex-wrap items-center gap-2">
                              {i.turnoEnvio > 0 && (
                                <Insignia tono={TONO_ITEM[i.estado]}>{ETIQUETA_ITEM[i.estado]}</Insignia>
                              )}
                              {enCocina && (
                                <button
                                  type="button"
                                  disabled={ocupado}
                                  onClick={() =>
                                    ejecutar(async () => {
                                      await api.marcarEntregado(orden.id, i.id)
                                      return `${i.nombre} entregado. Ya no aparece en cocina.`
                                    })
                                  }
                                  className={`flex min-h-[36px] items-center gap-1 rounded-lg px-2.5 text-xs font-bold transition active:scale-95 disabled:opacity-50 ${
                                    i.estado === 'listo'
                                      ? 'bg-estado-listo text-noche-950'
                                      : 'border-2 border-estado-listo text-estado-listo hover:bg-estado-listo/15'
                                  }`}
                                >
                                  <Check className="h-3.5 w-3.5" aria-hidden />
                                  Entregado
                                </button>
                              )}
                            </div>
                          </td>
                          <td className="py-2 pr-3 text-right tabular-nums text-noche-300">
                            {formatoCOP(unitarioItem(i))}
                          </td>
                          <td className="py-2 text-right tabular-nums text-crema-100">{formatoCOP(precioItem(i))}</td>
                        </tr>
                      )
                    })}
                    {orden.cargosAdicionales.map((c) => (
                      <tr key={c.id} className="border-b border-noche-850">
                        <td className="py-2 pr-3" />
                        <td className="py-2 pr-3 text-noche-300">{c.nombre} (cargo)</td>
                        <td className="py-2 pr-3" />
                        <td className="py-2 pr-3" />
                        <td className="py-2 text-right tabular-nums text-crema-100">{formatoCOP(c.valor)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <dl className="ml-auto mt-4 max-w-xs space-y-1 text-sm">
              <div className="flex justify-between">
                <dt className="text-noche-400">Subtotal</dt>
                <dd className="tabular-nums text-crema-100">{formatoCOP(cuenta.subtotal)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-noche-400">INC {cuenta.porcentajeInc}%</dt>
                <dd className="tabular-nums text-crema-100">{formatoCOP(cuenta.inc)}</dd>
              </div>
              {cuenta.cargosAdicionales > 0 && (
                <div className="flex justify-between">
                  <dt className="text-noche-400">Cargos</dt>
                  <dd className="tabular-nums text-crema-100">{formatoCOP(cuenta.cargosAdicionales)}</dd>
                </div>
              )}
              <div className="flex justify-between border-t border-noche-800 pt-2 text-base font-semibold">
                <dt className="text-crema-100">Total</dt>
                <dd className="tabular-nums text-oro-300">{formatoCOP(cuenta.total)}</dd>
              </div>
              <p className="pt-1 text-right text-xs text-noche-500">Sin propina: se define al cobrar.</p>
            </dl>

            <div className="mt-5 flex flex-wrap justify-end gap-2">
              {porEntregar > 0 && (
                <Boton
                  variante="exito"
                  className="mr-auto"
                  icono={<CheckCheck className="h-4 w-4" aria-hidden />}
                  disabled={ocupado}
                  onClick={entregarTodo}
                >
                  Todo entregado ({porEntregar})
                </Boton>
              )}
              {sinConsumo && (
                <Boton
                  variante="peligro"
                  icono={<DoorOpen className="h-4 w-4" aria-hidden />}
                  disabled={ocupado}
                  onClick={() => {
                    setMotivo('')
                    setLiberando(true)
                  }}
                >
                  Desocupar mesa
                </Boton>
              )}
              <Boton
                icono={<ClipboardCopy className="h-4 w-4" aria-hidden />}
                onClick={copiarDetalle}
                disabled={items.length === 0}
              >
                Copiar detalle
              </Boton>
              <Boton
                variante="principal"
                icono={<Wallet className="h-4 w-4" aria-hidden />}
                disabled={items.length === 0}
                onClick={() => navegar(`/comandera/mesa/${detalle.mesa.id}/cuenta`)}
              >
                Cobrar
              </Boton>
            </div>
          </>
        )}
      </section>

      <HojaInferior
        abierta={liberando}
        titulo="Desocupar la mesa"
        descripcion="No hay nada que cobrar. Queda registrado con el motivo."
        onCerrar={() => setLiberando(false)}
        pie={
          <Boton variante="peligro" tamano="grande" bloque cargando={ocupado} onClick={liberar}>
            Desocupar mesa
          </Boton>
        }
      >
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            {['Se fueron sin pedir', 'Cancelaron el pedido', 'Mesa abierta por error'].map((texto) => (
              <button
                key={texto}
                type="button"
                onClick={() => setMotivo(texto)}
                className={`min-h-[40px] rounded-xl border px-3 text-sm transition ${
                  motivo === texto
                    ? 'border-oro-500 bg-oro-500/15 text-crema-100'
                    : 'border-noche-700 bg-noche-850 text-noche-300 hover:bg-noche-800'
                }`}
              >
                {texto}
              </button>
            ))}
          </div>
          <input
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="Motivo"
            className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
          />
        </div>
      </HojaInferior>
    </div>
  )
}
