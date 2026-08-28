import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, ArrowRight, Check, Search, ShoppingBag, Trash2, X } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { CategoriaConItems } from '@/compartido/mockApi'
import { DIGITOS_TELEFONO } from '@/compartido/config'
import { formatoCOP } from '@/compartido/formato'
import { precioVigente } from '@/compartido/calculos'
import { useSyncedState } from '@/compartido/useSyncedState'
import type {
  Canal,
  EstadoCanal,
  ItemCarta,
  MetodoPago,
  ModificadorSeleccionado,
  TipoPedido,
} from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Campo, CampoArea } from '@/componentes/ui/Campo'
import { Contador } from '@/componentes/ui/Contador'
import { useAvisos } from '@/componentes/ui/Avisos'
import { HojaModificadores, type SeleccionProducto } from '@/comandera/HojaModificadores'

/**
 * El pedido que toma una persona del restaurante.
 *
 * Es el que llega por WhatsApp o por teléfono y hasta ahora vivía en un papel
 * del mostrador: cocina no lo veía, la caja no lo sumaba y al cliente no había
 * cómo avisarle en qué iba. Aquí entra como cualquier otro pedido y aparece en
 * el tablero de al lado en el acto.
 *
 * Va en dos pasos y en ese orden a propósito. Quien contesta el teléfono
 * primero apunta a quién le habla y a dónde va, que es lo que se dice al
 * principio de la llamada, y después arma el pedido mientras se lo dictan. Al
 * revés obligaría a pedirle al cliente que espere.
 */

type TipoExterno = Exclude<TipoPedido, 'mesa'>

/** Por dónde escribió. No incluye 'web': eso lo escribe el sitio solo. */
const CANALES: { id: Canal; etiqueta: string }[] = [
  { id: 'whatsapp', etiqueta: 'WhatsApp' },
  { id: 'telefono', etiqueta: 'Teléfono' },
  { id: 'presencial', etiqueta: 'En el mostrador' },
]

const METODOS: { valor: MetodoPago; etiqueta: string }[] = [
  { valor: 'efectivo', etiqueta: 'Efectivo' },
  { valor: 'tarjeta', etiqueta: 'Tarjeta' },
  { valor: 'transferencia', etiqueta: 'Transferencia' },
]

interface Linea {
  clave: string
  item: ItemCarta
  cantidad: number
  modificadores: ModificadorSeleccionado[]
  nota?: string
}

/** Dos veces el mismo plato con las mismas elecciones es una sola línea. */
const firmaDe = (itemId: string, mods: ModificadorSeleccionado[], nota?: string): string =>
  `${itemId}|${JSON.stringify(mods)}|${nota ?? ''}`

/** Busca sin exigir tildes: "robalo" encuentra "Róbalo al bijao". */
const sinTildes = (texto: string): string =>
  texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

const precioLinea = (linea: Linea): number =>
  (precioVigente(linea.item) + linea.modificadores.reduce((s, m) => s + m.precioAdicional, 0)) *
  linea.cantidad

interface Props {
  abierto: boolean
  onCerrar: () => void
  /** Se llama con el número del pedido creado, para avisar en la pantalla. */
  onCreado: (numero: number) => void
}

export function NuevoPedido({ abierto, onCerrar, onCreado }: Props) {
  const { mostrar } = useAvisos()

  const { datos: categorias } = useSyncedState<CategoriaConItems[]>(
    () => api.cartaAgrupada(true),
    [],
    [],
    ['carta', 'todo'],
  )
  // Las zonas y sus tarifas. Se piden por el mismo sitio que las pide el
  // cliente: si una zona se desactiva, el mostrador tampoco puede escogerla.
  const { datos: canalEstado } = useSyncedState<EstadoCanal | null>(
    () => api.estadoCanal(),
    null,
    [],
    ['zonas', 'ajustes', 'pedidos', 'todo'],
  )

  const [paso, setPaso] = useState<'cliente' | 'productos'>('cliente')
  const [canal, setCanal] = useState<Canal>('whatsapp')
  const [tipo, setTipo] = useState<TipoExterno>('domicilio')
  const [nombre, setNombre] = useState('')
  const [telefono, setTelefono] = useState('')
  const [direccion, setDireccion] = useState('')
  const [zonaId, setZonaId] = useState('')
  const [metodo, setMetodo] = useState<MetodoPago>('efectivo')
  const [notas, setNotas] = useState('')

  const [lineas, setLineas] = useState<Linea[]>([])
  const [categoriaActiva, setCategoriaActiva] = useState<string | null>(null)
  const [busqueda, setBusqueda] = useState('')
  const [enHoja, setEnHoja] = useState<ItemCarta | null>(null)
  const [guardando, setGuardando] = useState(false)

  // Cada vez que se abre arranca limpio: lo del pedido anterior en pantalla
  // solo sirve para mandarle a alguien lo que pidió otro.
  useEffect(() => {
    if (!abierto) return
    setPaso('cliente')
    setCanal('whatsapp')
    setTipo('domicilio')
    setNombre('')
    setTelefono('')
    setDireccion('')
    setZonaId('')
    setMetodo('efectivo')
    setNotas('')
    setLineas([])
    setBusqueda('')
    setCategoriaActiva(null)
  }, [abierto])

  const zonas = canalEstado?.zonas ?? []
  const zona = zonas.find((z) => z.id === zonaId)

  const categoriaVisible = categoriaActiva ?? categorias[0]?.id ?? null
  const visibles = useMemo(() => {
    if (busqueda.trim()) {
      const q = sinTildes(busqueda.trim())
      return categorias.flatMap((c) => c.items).filter((i) => sinTildes(i.nombre).includes(q))
    }
    return categorias.find((c) => c.id === categoriaVisible)?.items ?? []
  }, [categorias, categoriaVisible, busqueda])

  const subtotal = lineas.reduce((s, l) => s + precioLinea(l), 0)
  const envio = tipo === 'domicilio' ? (zona?.tarifa ?? 0) : 0
  const minimo = tipo === 'domicilio' ? (zona?.montoMinimo ?? 0) : 0
  const faltaParaElMinimo = Math.max(0, minimo - subtotal)

  const digitos = telefono.replace(/\D/g, '')

  /** Lo que impide seguir al paso de productos. */
  const problemaCliente = useMemo(() => {
    if (!nombre.trim()) return 'Escriba el nombre del cliente'
    if (digitos.length !== DIGITOS_TELEFONO) return 'El teléfono va con 10 dígitos'
    if (tipo === 'domicilio') {
      if (!zonaId) return 'Escoja el barrio'
      if (!direccion.trim()) return 'Escriba la dirección'
    }
    return null
  }, [nombre, digitos, tipo, zonaId, direccion])

  /** Lo que impide registrar el pedido. */
  const problemaPedido = useMemo(() => {
    if (lineas.length === 0) return 'El pedido está vacío'
    if (faltaParaElMinimo > 0) {
      return `Faltan ${formatoCOP(faltaParaElMinimo)} para el mínimo de ${zona?.nombre}`
    }
    return null
  }, [lineas.length, faltaParaElMinimo, zona])

  // ---------------------------------------------------------------------------
  // Productos
  // ---------------------------------------------------------------------------

  const agregar = (item: ItemCarta, seleccion: SeleccionProducto) => {
    const clave = firmaDe(item.id, seleccion.modificadores, seleccion.nota)
    setLineas((actuales) => {
      const existente = actuales.find((l) => l.clave === clave)
      if (existente) {
        return actuales.map((l) =>
          l.clave === clave ? { ...l, cantidad: l.cantidad + seleccion.cantidad } : l,
        )
      }
      return [
        ...actuales,
        {
          clave,
          item,
          cantidad: seleccion.cantidad,
          modificadores: seleccion.modificadores,
          nota: seleccion.nota,
        },
      ]
    })
  }

  const tocarProducto = (item: ItemCarta) => {
    // Un producto con elecciones obligatorias no se puede agregar de un toque:
    // en un restaurante de mantel no sale una carne sin término.
    if ((item.modificadores ?? []).some((m) => m.obligatorio)) {
      setEnHoja(item)
      return
    }
    agregar(item, { cantidad: 1, modificadores: [] })
  }

  const cambiarCantidad = (clave: string, cantidad: number) =>
    setLineas((actuales) =>
      cantidad <= 0
        ? actuales.filter((l) => l.clave !== clave)
        : actuales.map((l) => (l.clave === clave ? { ...l, cantidad } : l)),
    )

  // ---------------------------------------------------------------------------
  // Registro
  // ---------------------------------------------------------------------------

  const registrar = async () => {
    if (problemaCliente || problemaPedido) {
      mostrar(problemaCliente ?? problemaPedido ?? '', 'error')
      return
    }
    setGuardando(true)
    try {
      const creado = await api.crearPedidoDeMostrador({
        canal,
        tipo,
        nombre: nombre.trim(),
        telefono: digitos,
        direccion: tipo === 'domicilio' ? direccion.trim() : undefined,
        barrio: tipo === 'domicilio' ? zona?.nombre : undefined,
        zonaDomicilioId: tipo === 'domicilio' ? zonaId : undefined,
        metodoPagoPrevisto: metodo,
        notas: notas.trim() || undefined,
        items: lineas.map((l) => ({
          itemCartaId: l.item.id,
          cantidad: l.cantidad,
          modificadoresSeleccionados: l.modificadores,
          notaCocina: l.nota,
        })),
      })
      onCerrar()
      onCreado(creado.numero)
    } catch (e) {
      // El pedido escrito no se pierde: se corrige lo que el servidor rechazó
      // sin volver a preguntárselo todo al cliente.
      mostrar(e instanceof Error ? e.message : 'No se pudo registrar el pedido', 'error')
    } finally {
      setGuardando(false)
    }
  }

  if (!abierto) return null

  const opcion = (activa: boolean) =>
    `min-h-[44px] rounded-xl border px-3 text-sm transition ${
      activa
        ? 'border-oro-500 bg-oro-500/15 text-oro-300'
        : 'border-noche-700 bg-noche-850 text-noche-300 hover:border-noche-600'
    }`

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-noche-950">
      <header className="flex items-center gap-3 border-b border-noche-800 px-4 py-3">
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-base font-semibold text-crema-100">
            Nuevo pedido {paso === 'productos' && nombre.trim() ? `· ${nombre.trim()}` : ''}
          </h2>
          <p className="truncate text-xs text-noche-400">
            {paso === 'cliente'
              ? 'Quién pide y a dónde va'
              : `${lineas.reduce((s, l) => s + l.cantidad, 0)} productos · ${formatoCOP(subtotal + envio)}`}
          </p>
        </div>
        <button
          type="button"
          onClick={onCerrar}
          aria-label="Cerrar sin registrar"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-noche-400 transition hover:bg-noche-800 hover:text-crema-100"
        >
          <X className="h-5 w-5" aria-hidden />
        </button>
      </header>

      {/* ---------- Paso 1: el cliente ---------- */}
      {paso === 'cliente' && (
        <>
          <div className="flex-1 overflow-y-auto px-4 py-4">
            <div className="mx-auto max-w-lg space-y-4">
              <div>
                <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-noche-400">
                  ¿Por dónde pidió?
                </p>
                <div className="grid grid-cols-3 gap-2">
                  {CANALES.map((c) => (
                    <button key={c.id} type="button" onClick={() => setCanal(c.id)} className={opcion(canal === c.id)}>
                      {c.etiqueta}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-noche-400">
                  ¿Cómo lo recibe?
                </p>
                <div className="grid grid-cols-2 gap-2">
                  <button type="button" onClick={() => setTipo('domicilio')} className={opcion(tipo === 'domicilio')}>
                    A domicilio
                  </button>
                  <button type="button" onClick={() => setTipo('llevar')} className={opcion(tipo === 'llevar')}>
                    Lo recoge
                  </button>
                </div>
              </div>

              <Campo
                etiqueta="Nombre"
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                placeholder="Carolina Mendoza"
              />

              <Campo
                etiqueta="Celular"
                value={telefono}
                onChange={(e) => setTelefono(e.target.value)}
                inputMode="tel"
                placeholder="300 123 4567"
                ayuda="Es a este número al que se le avisa cuando salga."
              />

              {tipo === 'domicilio' && (
                <>
                  <div>
                    <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-noche-400">
                      Barrio
                    </p>
                    {zonas.length === 0 ? (
                      <p className="rounded-xl border border-noche-700 bg-noche-850 px-3 py-2.5 text-sm text-noche-400">
                        No hay zonas de domicilio activas. Se agregan en Ajustes.
                      </p>
                    ) : (
                      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                        {zonas.map((z) => (
                          <button
                            key={z.id}
                            type="button"
                            onClick={() => setZonaId(z.id)}
                            className={`flex flex-col items-start justify-center px-3 py-2 ${opcion(zonaId === z.id)}`}
                          >
                            <span className="truncate text-sm text-crema-100">{z.nombre}</span>
                            <span className="text-[0.7rem] text-noche-400">
                              Envío {formatoCOP(z.tarifa)}
                            </span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  <Campo
                    etiqueta="Dirección"
                    value={direccion}
                    onChange={(e) => setDireccion(e.target.value)}
                    placeholder="Calle 30 #12-45, apto 302"
                    ayuda="Escriba el punto de referencia si la dirección es difícil."
                  />
                </>
              )}

              <div>
                <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-noche-400">
                  ¿Cómo va a pagar?
                </p>
                <div className="grid grid-cols-3 gap-2">
                  {METODOS.map((m) => (
                    <button key={m.valor} type="button" onClick={() => setMetodo(m.valor)} className={opcion(metodo === m.valor)}>
                      {m.etiqueta}
                    </button>
                  ))}
                </div>
                <p className="mt-1.5 text-xs text-noche-500">
                  Es lo que dijo que iba a pagar. El cobro real se registra al entregar.
                </p>
              </div>

              <CampoArea
                etiqueta="Notas"
                rows={2}
                value={notas}
                onChange={(e) => setNotas(e.target.value)}
                placeholder="Sin cebolla, tocar el timbre del 302…"
              />
            </div>
          </div>

          <footer className="border-t border-noche-800 px-4 py-3">
            <div className="mx-auto max-w-lg">
              {problemaCliente && (
                <p className="mb-2 text-center text-sm text-noche-400">{problemaCliente}</p>
              )}
              <Boton
                variante="principal"
                tamano="grande"
                bloque
                disabled={!!problemaCliente}
                onClick={() => setPaso('productos')}
                icono={<ArrowRight className="h-5 w-5" aria-hidden />}
              >
                Armar el pedido
              </Boton>
            </div>
          </footer>
        </>
      )}

      {/* ---------- Paso 2: los productos ---------- */}
      {paso === 'productos' && (
        <>
          <div className="flex min-h-0 flex-1 flex-col lg:flex-row">
            {/* Carta */}
            <div className="flex min-h-0 flex-1 flex-col border-noche-800 lg:border-r">
              <div className="border-b border-noche-800 px-4 py-2.5">
                <div className="relative">
                  <Search
                    className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-noche-500"
                    aria-hidden
                  />
                  <input
                    value={busqueda}
                    onChange={(e) => setBusqueda(e.target.value)}
                    placeholder="Buscar un plato"
                    aria-label="Buscar un plato"
                    className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-900 pl-9 pr-3 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
                  />
                </div>

                {!busqueda.trim() && (
                  <div className="mt-2 flex gap-1.5 overflow-x-auto pb-1">
                    {categorias.map((c) => (
                      <button
                        key={c.id}
                        type="button"
                        onClick={() => setCategoriaActiva(c.id)}
                        className={`shrink-0 rounded-lg px-3 py-1.5 text-sm transition ${
                          categoriaVisible === c.id
                            ? 'bg-oro-500 font-medium text-noche-950'
                            : 'bg-noche-850 text-noche-300 hover:bg-noche-800'
                        }`}
                      >
                        {c.nombre}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div className="flex-1 overflow-y-auto p-3">
                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-4">
                  {visibles.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => tocarProducto(item)}
                      className="flex min-h-[76px] flex-col justify-between rounded-xl border border-noche-700 bg-noche-900 p-2.5 text-left transition hover:border-oro-500/60 hover:bg-noche-850"
                    >
                      <span className="text-sm leading-snug text-crema-100">{item.nombre}</span>
                      <span className="mt-1 text-sm tabular-nums text-oro-300">
                        {formatoCOP(precioVigente(item))}
                      </span>
                    </button>
                  ))}
                </div>
                {visibles.length === 0 && (
                  <p className="pt-10 text-center text-sm text-noche-500">
                    Nada con ese nombre en la carta.
                  </p>
                )}
              </div>
            </div>

            {/* Lo que lleva */}
            <aside className="flex min-h-0 shrink-0 flex-col border-t border-noche-800 lg:w-[360px] lg:border-l-0 lg:border-t-0">
              <div className="max-h-[38vh] flex-1 overflow-y-auto p-3 lg:max-h-none">
                {lineas.length === 0 ? (
                  <div className="flex flex-col items-center gap-2 py-10 text-center">
                    <ShoppingBag className="h-7 w-7 text-noche-600" aria-hidden />
                    <p className="text-sm text-noche-400">Toque un plato para agregarlo</p>
                  </div>
                ) : (
                  <ul className="space-y-2">
                    {lineas.map((linea) => (
                      <li key={linea.clave} className="rounded-xl border border-noche-800 bg-noche-900 p-2.5">
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <p className="text-sm text-crema-100">{linea.item.nombre}</p>
                            {linea.modificadores.length > 0 && (
                              <p className="text-xs text-noche-400">
                                {linea.modificadores.map((m) => m.valor).join(' · ')}
                              </p>
                            )}
                            {linea.nota && (
                              <p className="text-xs italic text-noche-400">{linea.nota}</p>
                            )}
                          </div>
                          <span className="shrink-0 text-sm tabular-nums text-crema-100">
                            {formatoCOP(precioLinea(linea))}
                          </span>
                        </div>
                        <div className="mt-2 flex items-center justify-between">
                          <Contador
                            valor={linea.cantidad}
                            onCambiar={(v) => cambiarCantidad(linea.clave, v)}
                            compacto
                          />
                          <button
                            type="button"
                            onClick={() => cambiarCantidad(linea.clave, 0)}
                            aria-label={`Quitar ${linea.item.nombre}`}
                            className="flex h-10 w-10 items-center justify-center rounded-xl text-noche-500 transition hover:bg-noche-800 hover:text-estado-demorado"
                          >
                            <Trash2 className="h-4 w-4" aria-hidden />
                          </button>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="border-t border-noche-800 p-3">
                <dl className="mb-3 space-y-1 text-sm">
                  <div className="flex justify-between text-noche-300">
                    <dt>Productos</dt>
                    <dd className="tabular-nums">{formatoCOP(subtotal)}</dd>
                  </div>
                  {envio > 0 && (
                    <div className="flex justify-between text-noche-300">
                      <dt>Envío a {zona?.nombre}</dt>
                      <dd className="tabular-nums">{formatoCOP(envio)}</dd>
                    </div>
                  )}
                  <div className="flex justify-between border-t border-noche-800 pt-1 font-semibold text-crema-100">
                    <dt>Total sin impuesto</dt>
                    <dd className="tabular-nums">{formatoCOP(subtotal + envio)}</dd>
                  </div>
                </dl>
                <p className="mb-3 text-xs leading-relaxed text-noche-500">
                  El impuesto al consumo se suma al registrar, igual que en el sitio: el total
                  definitivo sale en el comprobante.
                </p>

                {problemaPedido && (
                  <p className="mb-2 text-center text-sm text-noche-400">{problemaPedido}</p>
                )}

                <div className="flex gap-2">
                  <Boton
                    variante="secundario"
                    tamano="grande"
                    onClick={() => setPaso('cliente')}
                    icono={<ArrowLeft className="h-5 w-5" aria-hidden />}
                  >
                    Datos
                  </Boton>
                  <Boton
                    variante="exito"
                    tamano="grande"
                    className="flex-1"
                    cargando={guardando}
                    disabled={!!problemaPedido}
                    onClick={registrar}
                    icono={<Check className="h-5 w-5" aria-hidden />}
                  >
                    Registrar pedido
                  </Boton>
                </div>
              </div>
            </aside>
          </div>

          <HojaModificadores
            item={enHoja}
            onCerrar={() => setEnHoja(null)}
            onConfirmar={(seleccion) => {
              if (enHoja) agregar(enHoja, seleccion)
              setEnHoja(null)
            }}
          />
        </>
      )}
    </div>
  )
}
