import { useCallback, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  ArrowLeftRight,
  Ban,
  ClipboardList,
  Clock,
  CloudOff,
  Plus,
  Receipt,
  Send,
  StickyNote,
  Tag,
  UtensilsCrossed,
  Users,
  Wallet,
} from 'lucide-react'
import * as api from '@/compartido/mockApi'
import { IMPRIMIR_COMANDA_AUTOMATICO } from '@/compartido/config'
import { imprimir } from '@/impresion/impresora'
import { ComandaTermica } from '@/impresion/ComandaTermica'
import type { MesaEnMapa, OrdenDetallada } from '@/compartido/mockApi'
import { agruparPorTurno, calcularCuenta, itemsSinEnviar, precioItem } from '@/compartido/calculos'
import { CARGOS_FRECUENTES } from '@/compartido/config'
import { useSesionActiva } from '@/compartido/auth'
import { formatoCOP, formatoHora, minutosDesde, tiempoTranscurrido } from '@/compartido/formato'
import { useReloj, useSyncedState } from '@/compartido/useSyncedState'
import type { ItemOrden } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Cargando } from '@/componentes/ui/Cargando'
import { Contador } from '@/componentes/ui/Contador'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Insignia } from '@/componentes/ui/Insignia'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'
import { IndicadorConexion } from '@/componentes/IndicadorConexion'
import { ETIQUETA_ITEM, NOMBRE_ZONA, TONO_ITEM, colorEspera } from '@/compartido/estados'

type HojaAbierta = 'cargo' | 'traslado' | 'nota' | 'anular' | null

export default function OrdenMesa() {
  const { mesaId = '' } = useParams()
  const navegar = useNavigate()
  const sesion = useSesionActiva()
  const { mostrar } = useAvisos()

  const { datos: detalle, cargando } = useSyncedState<OrdenDetallada | null>(
    () => api.obtenerOrdenDeMesa(mesaId),
    null,
    [mesaId],
    ['ordenes', 'mesas', 'cocina', 'todo'],
  )
  const { datos: mesas } = useSyncedState<MesaEnMapa[]>(() => api.listarMesas(), [], [], ['mesas', 'todo'])

  useReloj(15000)

  const [hoja, setHoja] = useState<HojaAbierta>(null)
  const [ocupado, setOcupado] = useState(false)
  const [nombreCargo, setNombreCargo] = useState('')
  const [valorCargo, setValorCargo] = useState('')
  const [nota, setNota] = useState('')
  const [itemAAnular, setItemAAnular] = useState<ItemOrden | null>(null)
  const [motivo, setMotivo] = useState('')

  const orden = detalle?.orden ?? null
  const mesa = detalle?.mesa ?? null

  const { datos: enCola } = useSyncedState<string[]>(
    async () => (orden ? api.itemsEnCola(orden.id) : []),
    [],
    [orden?.id],
    ['cola', 'ordenes', 'todo'],
  )

  const pendientesDeTomar = useMemo(() => (orden ? itemsSinEnviar(orden) : []), [orden])
  // Lo encolado ya salio de las manos del mesero: no se puede volver a enviar.
  const sinEnviar = useMemo(
    () => pendientesDeTomar.filter((i) => !enCola.includes(i.id)),
    [pendientesDeTomar, enCola],
  )
  const esperandoSenal = useMemo(
    () => pendientesDeTomar.filter((i) => enCola.includes(i.id)),
    [pendientesDeTomar, enCola],
  )
  const turnos = useMemo(() => (orden ? agruparPorTurno(orden.items) : []), [orden])
  const cuenta = useMemo(
    () => (orden ? calcularCuenta(orden, detalle?.porcentajeInc ?? 8) : null),
    [orden, detalle?.porcentajeInc],
  )

  const ejecutar = useCallback(
    async (accion: () => Promise<string>) => {
      setOcupado(true)
      try {
        mostrar(await accion(), 'exito')
      } catch (e) {
        mostrar(e instanceof Error ? e.message : 'No se pudo completar la acción', 'error')
      } finally {
        setOcupado(false)
      }
    },
    [mostrar],
  )

  if (cargando) return <Cargando pantallaCompleta mensaje="Abriendo la mesa" />

  if (!orden || !mesa || !cuenta) {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-noche-950 px-6">
        <Vacio
          icono={UtensilsCrossed}
          titulo="Esta mesa no tiene cuenta abierta"
          descripcion="Puede que ya la hayan cobrado desde otra pestaña."
          accion={
            <Boton variante="principal" onClick={() => navegar('/comandera')}>
              Volver al mapa de mesas
            </Boton>
          }
        />
      </div>
    )
  }

  const etiquetaMesa = mesa.nombre ?? `Mesa ${mesa.numero}`

  /**
   * Saca la comanda en papel para cocina y para barra.
   *
   * Salen dos tickets porque son dos puestos de trabajo distintos: el plato va
   * a la plancha y el coctel a la barra, y cada uno necesita el suyo. Si un
   * destino no lleva nada en ese turno, no se imprime en blanco.
   */
  const imprimirComandas = (turno: number) => {
    const delTurno = orden.items.filter((i) => i.turnoEnvio === turno && i.estado !== 'anulado')
    for (const destino of ['cocina', 'bar'] as const) {
      const suyos = delTurno.filter((i) => i.destino === destino)
      if (suyos.length === 0) continue
      imprimir(
        <ComandaTermica
          etiqueta={etiquetaMesa}
          numero={orden.numero}
          turno={turno}
          destino={destino}
          atendidoPor={detalle?.meseroNombre ?? ''}
          items={suyos}
          notas={orden.notas}
        />,
      )
    }
  }

  const enviar = () =>
    ejecutar(async () => {
      const resultado = await api.enviarACocina(orden.id)
      // La comanda impresa es un respaldo: si la pantalla de cocina se cae, el
      // papel sigue estando. Por eso sale sola solo si el dueno lo pidio.
      if (IMPRIMIR_COMANDA_AUTOMATICO && !resultado.encolado) imprimirComandas(resultado.turno)
      return resultado.encolado
        ? `Sin señal: el turno ${resultado.turno} quedó en cola y saldrá solo al reconectar`
        : `Turno ${resultado.turno} enviado · ${resultado.cantidadItems} productos`
    })

  const agregarCargo = () =>
    ejecutar(async () => {
      const valor = Number(valorCargo.replace(/\D/g, ''))
      if (!valor) throw new Error('Escribe el valor del cargo')
      await api.agregarCargo(orden.id, nombreCargo, valor, sesion.nombre)
      setHoja(null)
      setNombreCargo('')
      setValorCargo('')
      return 'Cargo agregado a la cuenta del cliente'
    })

  const trasladar = (destinoId: string) =>
    ejecutar(async () => {
      await api.trasladarMesa(orden.id, destinoId)
      setHoja(null)
      const destino = mesas.find((m) => m.id === destinoId)
      navegar(`/comandera/mesa/${destinoId}`, { replace: true })
      return `Cuenta trasladada a ${destino?.nombre ?? `mesa ${destino?.numero}`}`
    })

  const anular = () =>
    ejecutar(async () => {
      if (!itemAAnular) throw new Error('No hay producto seleccionado')
      if (!motivo.trim()) throw new Error('La anulación necesita un motivo')
      await api.anularItem(orden.id, itemAAnular.id, motivo.trim())
      setHoja(null)
      setItemAAnular(null)
      setMotivo('')
      return 'Producto anulado. Queda registrado en la comanda.'
    })

  const mesasLibres = mesas.filter((m) => m.estado === 'libre' && m.id !== mesa.id)

  return (
    <div className="flex min-h-dvh flex-col bg-noche-950 pb-40">
      <header className="sticky top-0 z-30 border-b border-noche-800 bg-noche-900/95 backdrop-blur">
        <div className="flex items-center gap-2 px-3 py-2.5">
          <button
            type="button"
            onClick={() => navegar('/comandera')}
            aria-label="Volver al mapa de mesas"
            className="flex h-toque w-11 shrink-0 items-center justify-center rounded-xl text-noche-300 transition hover:bg-noche-800"
          >
            <ArrowLeft className="h-5 w-5" aria-hidden />
          </button>
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-lg font-semibold text-crema-100">{etiquetaMesa}</h1>
            <p className="truncate text-xs text-noche-400">
              Comanda #{orden.numero} · {NOMBRE_ZONA[mesa.zona]} · {detalle?.meseroNombre}
            </p>
          </div>
          <IndicadorConexion />
        </div>

        <div className="grid grid-cols-3 gap-px border-t border-noche-800 bg-noche-800">
          {/* Editable, y no solo al abrir la mesa: una pareja a la que se le
              suman dos amigos es lo normal en un salon. Ademas de que el numero
              quede bien en la comanda, es el que propone la division de la
              cuenta, asi que dejarlo viejo descuadra el reparto. */}
          <div className="bg-noche-900 px-3 py-2">
            <p className="flex items-center gap-1 text-xs text-noche-400">
              <Users className="h-3 w-3" aria-hidden />
              Comensales
            </p>
            <div className="mt-1">
              <Contador
                valor={orden.comensales}
                compacto
                minimo={1}
                maximo={20}
                onCambiar={(v) =>
                  ejecutar(async () => {
                    await api.cambiarComensales(orden.id, v)
                    return v === 1 ? '1 comensal' : `${v} comensales`
                  })
                }
              />
            </div>
          </div>
          <div className="bg-noche-900 px-3 py-2">
            <p className="flex items-center gap-1 text-xs text-noche-400">
              <Clock className="h-3 w-3" aria-hidden />
              Abierta
            </p>
            <p className="text-base font-semibold text-crema-100">{tiempoTranscurrido(orden.abiertaEn)}</p>
          </div>
          <div className="bg-noche-900 px-3 py-2">
            <p className="text-xs text-noche-400">Total</p>
            <p className="text-base font-semibold tabular-nums text-oro-300">
              {formatoCOP(cuenta.total)}
            </p>
          </div>
        </div>
      </header>

      <main className="flex-1 space-y-4 px-3 py-4">
        {orden.notas && (
          <p className="flex items-start gap-2 rounded-xl border border-oro-500/30 bg-oro-500/10 px-3 py-2.5 text-sm text-oro-200">
            <StickyNote className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            {orden.notas}
          </p>
        )}

        {/* ---------- Todavia en la libreta del mesero ---------- */}
        {sinEnviar.length > 0 && (
          <section className="rounded-2xl border border-oro-500/40 bg-noche-900 p-3">
            <h2 className="mb-2.5 flex items-center gap-2 text-sm font-semibold text-oro-300">
              <ClipboardList className="h-4 w-4" aria-hidden />
              Sin enviar
              <span className="rounded-md bg-oro-500/20 px-1.5 text-xs">{sinEnviar.length}</span>
            </h2>
            <ul className="space-y-2">
              {sinEnviar.map((item) => (
                <li key={item.id} className="rounded-xl border border-noche-700 bg-noche-850 p-2.5">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium text-crema-100">{item.nombre}</p>
                      {item.modificadoresSeleccionados.length > 0 && (
                        <p className="mt-0.5 text-xs text-noche-400">
                          {item.modificadoresSeleccionados.map((m) => m.valor).join(' · ')}
                        </p>
                      )}
                      {item.notaCocina && (
                        <p className="mt-0.5 text-xs italic text-oro-300">{item.notaCocina}</p>
                      )}
                      {item.destino === 'bar' && (
                        <p className="mt-0.5 text-xs text-noche-500">va a la barra</p>
                      )}
                    </div>
                    <span className="text-sm font-semibold tabular-nums text-crema-100">
                      {formatoCOP(precioItem(item))}
                    </span>
                  </div>
                  <div className="mt-2">
                    <Contador
                      valor={item.cantidad}
                      compacto
                      permiteQuitar
                      onCambiar={(v) =>
                        ejecutar(async () => {
                          await api.cambiarCantidad(orden.id, item.id, v)
                          return v === 0 ? 'Producto retirado' : 'Cantidad actualizada'
                        })
                      }
                    />
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* ---------- Esperando que vuelva la senal ---------- */}
        {esperandoSenal.length > 0 && (
          <section className="rounded-2xl border border-estado-proceso/40 bg-estado-proceso-suave p-3">
            <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-estado-proceso">
              <CloudOff className="h-4 w-4" aria-hidden />
              En cola por falta de señal
              <span className="rounded-md bg-estado-proceso/20 px-1.5 text-xs">
                {esperandoSenal.length}
              </span>
            </h2>
            <ul className="space-y-1">
              {esperandoSenal.map((item) => (
                <li key={item.id} className="flex justify-between gap-2 text-sm text-crema-100">
                  <span className="min-w-0 truncate">
                    {item.cantidad} × {item.nombre}
                  </span>
                  <span className="shrink-0 tabular-nums text-noche-400">
                    {formatoCOP(precioItem(item))}
                  </span>
                </li>
              ))}
            </ul>
            <p className="mt-2 text-xs text-estado-proceso/80">
              Sale sola a cocina apenas vuelva la conexión. No hay que volver a enviarla.
            </p>
          </section>
        )}

        {/* ---------- Turnos ya enviados ---------- */}
        {turnos.map(({ turno, items }) => {
          const enviadoEn = items[0]?.enviadoEn
          const espera = enviadoEn ? minutosDesde(enviadoEn) : 0
          const todoServido = items.every((i) => i.estado === 'servido' || i.estado === 'anulado')

          return (
            <section key={turno} className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
              <div className="mb-2.5 flex items-center justify-between gap-2">
                <h2 className="text-sm font-semibold text-crema-100">
                  Turno {turno}
                  {enviadoEn && (
                    <span className="ml-2 text-xs font-normal text-noche-400">
                      {formatoHora(enviadoEn)}
                    </span>
                  )}
                </h2>
                {!todoServido && enviadoEn && (
                  <span className={`text-xs font-semibold tabular-nums ${colorEspera(espera)}`}>
                    {espera} min
                  </span>
                )}
              </div>

              <ul className="space-y-1.5">
                {items.map((item) => (
                  <li
                    key={item.id}
                    className={`flex items-start gap-2.5 rounded-xl border border-noche-800 bg-noche-850 p-2.5 ${
                      item.estado === 'anulado' ? 'opacity-50' : ''
                    }`}
                  >
                    <span className="mt-0.5 flex h-6 min-w-[24px] items-center justify-center rounded-md bg-noche-700 px-1 text-xs font-bold text-crema-100">
                      {item.cantidad}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p
                        className={`text-sm font-medium text-crema-100 ${
                          item.estado === 'anulado' ? 'line-through' : ''
                        }`}
                      >
                        {item.nombre}
                      </p>
                      {item.modificadoresSeleccionados.length > 0 && (
                        <p className="mt-0.5 text-xs text-noche-400">
                          {item.modificadoresSeleccionados.map((m) => m.valor).join(' · ')}
                        </p>
                      )}
                      {item.notaCocina && (
                        <p className="mt-0.5 text-xs italic text-oro-300">{item.notaCocina}</p>
                      )}
                    </div>

                    <div className="flex shrink-0 flex-col items-end gap-1.5">
                      <Insignia tono={TONO_ITEM[item.estado]}>{ETIQUETA_ITEM[item.estado]}</Insignia>
                      <span className="text-xs tabular-nums text-noche-400">
                        {formatoCOP(precioItem(item))}
                      </span>
                      {item.estado === 'listo' && (
                        <button
                          type="button"
                          disabled={ocupado}
                          onClick={() =>
                            ejecutar(async () => {
                              await api.cambiarEstadoItem(orden.id, item.id, 'servido')
                              return `${item.nombre} servido`
                            })
                          }
                          className="min-h-[36px] rounded-lg bg-estado-listo px-2.5 text-xs font-bold text-noche-950 transition active:scale-95"
                        >
                          Servir
                        </button>
                      )}
                      {item.estado !== 'anulado' && item.estado !== 'servido' && (
                        <button
                          type="button"
                          onClick={() => {
                            setItemAAnular(item)
                            setMotivo('')
                            setHoja('anular')
                          }}
                          aria-label={`Anular ${item.nombre}`}
                          className="flex h-8 w-8 items-center justify-center rounded-lg text-noche-500 transition hover:bg-estado-demorado/15 hover:text-estado-demorado"
                        >
                          <Ban className="h-3.5 w-3.5" aria-hidden />
                        </button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          )
        })}

        {orden.items.length === 0 && (
          <Vacio
            icono={UtensilsCrossed}
            titulo="La comanda está vacía"
            descripcion="Agrega los productos que pidió la mesa."
          />
        )}

        {/* ---------- Cargos adicionales ---------- */}
        {orden.cargosAdicionales.length > 0 && (
          <section className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
            <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-crema-100">
              <Tag className="h-4 w-4" aria-hidden />
              Cargos adicionales
            </h2>
            <ul className="space-y-1.5">
              {orden.cargosAdicionales.map((cargo) => (
                <li
                  key={cargo.id}
                  className="flex items-center justify-between gap-2 rounded-xl border border-noche-800 bg-noche-850 px-3 py-2"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm text-crema-100">{cargo.nombre}</p>
                    <p className="text-xs text-noche-500">Agregado por {cargo.agregadoPor}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <span className="text-sm font-semibold tabular-nums text-crema-100">
                      {formatoCOP(cargo.valor)}
                    </span>
                    <button
                      type="button"
                      onClick={() =>
                        ejecutar(async () => {
                          await api.quitarCargo(orden.id, cargo.id)
                          return 'Cargo retirado de la cuenta'
                        })
                      }
                      aria-label={`Quitar ${cargo.nombre}`}
                      className="flex h-9 w-9 items-center justify-center rounded-lg text-noche-500 transition hover:bg-estado-demorado/15 hover:text-estado-demorado"
                    >
                      <Ban className="h-4 w-4" aria-hidden />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* ---------- Cuenta ---------- */}
        <section className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
          <h2 className="mb-2.5 flex items-center gap-2 text-sm font-semibold text-crema-100">
            <Receipt className="h-4 w-4" aria-hidden />
            Cuenta
          </h2>
          <dl className="space-y-1.5 text-sm">
            <div className="flex justify-between text-noche-300">
              <dt>Subtotal</dt>
              <dd className="tabular-nums">{formatoCOP(cuenta.subtotal)}</dd>
            </div>
            <div className="flex justify-between text-noche-300">
              <dt>INC {cuenta.porcentajeInc}%</dt>
              <dd className="tabular-nums">{formatoCOP(cuenta.inc)}</dd>
            </div>
            {cuenta.cargosAdicionales > 0 && (
              <div className="flex justify-between text-noche-300">
                <dt>Cargos adicionales</dt>
                <dd className="tabular-nums">{formatoCOP(cuenta.cargosAdicionales)}</dd>
              </div>
            )}
            <div className="flex justify-between border-t border-noche-800 pt-1.5 text-base font-semibold text-crema-100">
              <dt>Total</dt>
              <dd className="tabular-nums">{formatoCOP(cuenta.total)}</dd>
            </div>
          </dl>
          <p className="mt-2 text-xs leading-relaxed text-noche-500">
            La propina es voluntaria y se consulta con el cliente al momento de cobrar. No está
            incluida en este total.
          </p>
        </section>

        {/* ---------- Acciones secundarias ---------- */}
        <Boton
          variante="exito"
          tamano="grande"
          bloque
          icono={<Wallet className="h-5 w-5" />}
          disabled={orden.items.length === 0}
          onClick={() => navegar(`/comandera/mesa/${mesaId}/cuenta`)}
        >
          Cobrar · {formatoCOP(cuenta.total)}
        </Boton>

        <div className="grid grid-cols-2 gap-2">
          <Boton icono={<Tag className="h-4 w-4" />} onClick={() => setHoja('cargo')}>
            Cargo adicional
          </Boton>
          <Boton
            icono={<StickyNote className="h-4 w-4" />}
            onClick={() => {
              setNota(orden.notas ?? '')
              setHoja('nota')
            }}
          >
            Nota de mesa
          </Boton>
          <Boton icono={<ArrowLeftRight className="h-4 w-4" />} onClick={() => setHoja('traslado')}>
            Trasladar
          </Boton>
          <Boton
            icono={<Receipt className="h-4 w-4" />}
            disabled={ocupado || orden.estado === 'cuenta_pedida'}
            onClick={() =>
              ejecutar(async () => {
                await api.pedirCuenta(orden.id)
                return 'Cuenta pedida. Caja ya la ve en el panel.'
              })
            }
          >
            {orden.estado === 'cuenta_pedida' ? 'Cuenta pedida' : 'Pedir la cuenta'}
          </Boton>
        </div>
      </main>

      {/* ---------- Acciones principales, siempre al alcance del pulgar ---------- */}
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-noche-700 bg-noche-900/98 px-3 py-3 pb-segura backdrop-blur">
        <div className="flex gap-2">
          <Boton
            variante="secundario"
            tamano="grande"
            className="flex-1"
            icono={<Plus className="h-5 w-5" />}
            onClick={() => navegar(`/comandera/mesa/${mesaId}/agregar`)}
          >
            Agregar productos
          </Boton>
          <Boton
            variante={sinEnviar.length > 0 ? 'exito' : 'secundario'}
            tamano="grande"
            className="flex-1"
            disabled={sinEnviar.length === 0 || ocupado}
            icono={<Send className="h-5 w-5" />}
            onClick={enviar}
          >
            Enviar {sinEnviar.length > 0 ? `(${sinEnviar.length})` : ''}
          </Boton>
        </div>
      </div>

      {/* ---------- Hojas ---------- */}
      <HojaInferior
        abierta={hoja === 'cargo'}
        titulo="Agregar un cargo a la cuenta"
        descripcion="El cliente lo verá en la cuenta antes de pagar"
        onCerrar={() => setHoja(null)}
        pie={
          <Boton variante="principal" tamano="grande" bloque cargando={ocupado} onClick={agregarCargo}>
            Agregar cargo
          </Boton>
        }
      >
        <div className="space-y-4">
          <div className="flex flex-wrap gap-2">
            {CARGOS_FRECUENTES.map((cargo) => (
              <button
                key={cargo.nombre}
                type="button"
                onClick={() => {
                  setNombreCargo(cargo.nombre)
                  setValorCargo(String(cargo.valor))
                }}
                className="min-h-[40px] rounded-xl border border-noche-700 bg-noche-850 px-3 text-sm text-noche-300 transition hover:bg-noche-800"
              >
                {cargo.nombre}
              </button>
            ))}
          </div>
          <input
            value={nombreCargo}
            onChange={(e) => setNombreCargo(e.target.value)}
            placeholder="Concepto visible para el cliente"
            className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
          />
          <input
            value={valorCargo}
            onChange={(e) => setValorCargo(e.target.value)}
            inputMode="numeric"
            placeholder="Valor en pesos"
            className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
          />
          <p className="text-xs leading-relaxed text-noche-500">
            Queda registrado con tu nombre y aparece como una línea aparte en la cuenta. Nunca debe
            haber un cargo que el cliente vea por primera vez al pagar.
          </p>
        </div>
      </HojaInferior>

      <HojaInferior
        abierta={hoja === 'traslado'}
        titulo="Trasladar la cuenta"
        descripcion={`Desde ${etiquetaMesa}`}
        onCerrar={() => setHoja(null)}
      >
        {mesasLibres.length === 0 ? (
          <Vacio icono={ArrowLeftRight} titulo="No hay mesas libres" />
        ) : (
          <div className="grid grid-cols-3 gap-2">
            {mesasLibres.map((destino) => (
              <button
                key={destino.id}
                type="button"
                disabled={ocupado}
                onClick={() => trasladar(destino.id)}
                className="flex min-h-[64px] flex-col items-center justify-center rounded-xl border border-noche-700 bg-noche-850 transition hover:bg-noche-800 active:scale-95"
              >
                <span className="text-lg font-bold text-crema-100">{destino.numero}</span>
                <span className="text-xs text-noche-400">{NOMBRE_ZONA[destino.zona]}</span>
              </button>
            ))}
          </div>
        )}
      </HojaInferior>

      <HojaInferior
        abierta={hoja === 'nota'}
        titulo="Nota de la mesa"
        descripcion="La ve cocina junto con la comanda"
        onCerrar={() => setHoja(null)}
        pie={
          <Boton
            variante="principal"
            tamano="grande"
            bloque
            cargando={ocupado}
            onClick={() =>
              ejecutar(async () => {
                await api.agregarNota(orden.id, nota.trim())
                setHoja(null)
                return 'Nota guardada'
              })
            }
          >
            Guardar nota
          </Boton>
        }
      >
        <textarea
          value={nota}
          onChange={(e) => setNota(e.target.value)}
          rows={4}
          placeholder="Cumpleaños, alergias, forma de servir..."
          className="w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 py-3 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
        />
      </HojaInferior>

      <HojaInferior
        abierta={hoja === 'anular'}
        titulo={itemAAnular ? `Anular ${itemAAnular.nombre}` : 'Anular producto'}
        descripcion="Queda registrado en la comanda con el motivo"
        onCerrar={() => setHoja(null)}
        pie={
          <Boton variante="peligro" tamano="grande" bloque cargando={ocupado} onClick={anular}>
            Anular producto
          </Boton>
        }
      >
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            {['El cliente cambió de opinión', 'Error al tomar el pedido', 'Producto agotado', 'Demora en cocina'].map(
              (texto) => (
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
              ),
            )}
          </div>
          <input
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="Motivo de la anulación"
            className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
          />
        </div>
      </HojaInferior>
    </div>
  )
}
