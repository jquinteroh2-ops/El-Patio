import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Pencil, Search, ShoppingBag, X } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { CategoriaConItems } from '@/compartido/mockApi'
import { formatoCOP } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { ItemCarta, ModificadorSeleccionado } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Cargando } from '@/componentes/ui/Cargando'
import { Contador } from '@/componentes/ui/Contador'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'
import { HojaModificadores, type SeleccionProducto } from './HojaModificadores'

interface Linea {
  clave: string
  item: ItemCarta
  cantidad: number
  modificadores: ModificadorSeleccionado[]
  nota?: string
}

const firmaDe = (itemId: string, mods: ModificadorSeleccionado[], nota?: string): string =>
  `${itemId}|${JSON.stringify(mods)}|${nota ?? ''}`

/** Busca sin exigir tildes: "robalo" encuentra "Róbalo al bijao". */
const sinTildes = (texto: string): string =>
  texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

export default function SelectorProductos() {
  const { mesaId = '' } = useParams()
  const navegar = useNavigate()
  const { mostrar } = useAvisos()

  const { datos: categorias, cargando } = useSyncedState<CategoriaConItems[]>(
    () => api.cartaAgrupada(),
    [],
    [],
    ['carta', 'todo'],
  )
  const { datos: detalle } = useSyncedState(
    () => api.obtenerOrdenDeMesa(mesaId),
    null,
    [mesaId],
    ['ordenes', 'mesas', 'todo'],
  )

  const [categoriaActiva, setCategoriaActiva] = useState<string | null>(null)
  const [busqueda, setBusqueda] = useState('')
  const [borrador, setBorrador] = useState<Linea[]>([])
  const [enHoja, setEnHoja] = useState<{ item: ItemCarta; editando?: Linea } | null>(null)
  const [revisando, setRevisando] = useState(false)
  const [tocado, setTocado] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const categoriaVisible = categoriaActiva ?? categorias[0]?.id ?? null

  const visibles = useMemo(() => {
    if (busqueda.trim()) {
      const q = sinTildes(busqueda.trim())
      return categorias.flatMap((c) => c.items).filter((i) => sinTildes(i.nombre).includes(q))
    }
    return categorias.find((c) => c.id === categoriaVisible)?.items ?? []
  }, [categorias, categoriaVisible, busqueda])

  const cantidadEnBorrador = (itemId: string): number =>
    borrador.filter((l) => l.item.id === itemId).reduce((s, l) => s + l.cantidad, 0)

  const totalBorrador = borrador.reduce(
    (s, l) => s + (l.item.precio + l.modificadores.reduce((a, m) => a + m.precioAdicional, 0)) * l.cantidad,
    0,
  )
  const unidadesBorrador = borrador.reduce((s, l) => s + l.cantidad, 0)

  const confirmarToque = (itemId: string) => {
    setTocado(itemId)
    window.setTimeout(() => setTocado((actual) => (actual === itemId ? null : actual)), 450)
  }

  const agregar = (item: ItemCarta, seleccion: SeleccionProducto) => {
    const clave = firmaDe(item.id, seleccion.modificadores, seleccion.nota)
    setBorrador((lineas) => {
      const existente = lineas.find((l) => l.clave === clave)
      if (existente) {
        return lineas.map((l) => (l.clave === clave ? { ...l, cantidad: l.cantidad + seleccion.cantidad } : l))
      }
      return [
        ...lineas,
        {
          clave,
          item,
          cantidad: seleccion.cantidad,
          modificadores: seleccion.modificadores,
          nota: seleccion.nota,
        },
      ]
    })
    confirmarToque(item.id)
  }

  const tocarProducto = (item: ItemCarta) => {
    if (!item.disponible) {
      mostrar(`${item.nombre} está agotado`, 'error')
      return
    }
    const exigeEleccion = (item.modificadores ?? []).some((m) => m.obligatorio)
    if (exigeEleccion) {
      setEnHoja({ item })
      return
    }
    agregar(item, { cantidad: 1, modificadores: [] })
  }

  const cambiarCantidadLinea = (clave: string, cantidad: number) => {
    setBorrador((lineas) =>
      cantidad <= 0
        ? lineas.filter((l) => l.clave !== clave)
        : lineas.map((l) => (l.clave === clave ? { ...l, cantidad } : l)),
    )
  }

  const guardarEdicion = (seleccion: SeleccionProducto) => {
    if (!enHoja) return
    if (enHoja.editando) {
      const anterior = enHoja.editando
      setBorrador((lineas) => lineas.filter((l) => l.clave !== anterior.clave))
    }
    agregar(enHoja.item, seleccion)
    setEnHoja(null)
  }

  const enviarALaComanda = async () => {
    if (!detalle) return
    setGuardando(true)
    try {
      await api.agregarItems(
        detalle.orden.id,
        borrador.map((l) => ({
          itemCartaId: l.item.id,
          cantidad: l.cantidad,
          modificadoresSeleccionados: l.modificadores,
          notaCocina: l.nota,
        })),
      )
      mostrar(`${unidadesBorrador} productos agregados a la comanda`, 'exito')
      navegar(`/comandera/mesa/${mesaId}`)
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudieron agregar', 'error')
      setGuardando(false)
    }
  }

  const etiquetaMesa = detalle ? (detalle.mesa.nombre ?? `Mesa ${detalle.mesa.numero}`) : ''

  return (
    <div className="flex min-h-dvh flex-col bg-noche-950 pb-28">
      <header className="sticky top-0 z-30 border-b border-noche-800 bg-noche-900/95 backdrop-blur">
        <div className="flex items-center gap-2 px-3 py-2.5">
          <button
            type="button"
            onClick={() => navegar(`/comandera/mesa/${mesaId}`)}
            aria-label="Volver a la comanda"
            className="flex h-toque w-11 shrink-0 items-center justify-center rounded-xl text-noche-300 transition hover:bg-noche-800"
          >
            <ArrowLeft className="h-5 w-5" aria-hidden />
          </button>
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-noche-500" aria-hidden />
            <input
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              placeholder={`Buscar en la carta · ${etiquetaMesa}`}
              className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 pl-9 pr-9 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
            />
            {busqueda && (
              <button
                type="button"
                onClick={() => setBusqueda('')}
                aria-label="Limpiar búsqueda"
                className="absolute right-1 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-lg text-noche-400 hover:text-crema-100"
              >
                <X className="h-4 w-4" aria-hidden />
              </button>
            )}
          </div>
        </div>

        {!busqueda && (
          <div className="sin-scrollbar flex gap-2 overflow-x-auto px-3 pb-2.5">
            {categorias.map((categoria) => (
              <button
                key={categoria.id}
                type="button"
                onClick={() => setCategoriaActiva(categoria.id)}
                className={`min-h-[40px] shrink-0 rounded-xl border px-3.5 text-sm font-medium transition ${
                  categoriaVisible === categoria.id
                    ? 'border-oro-500 bg-oro-500/15 text-oro-300'
                    : 'border-noche-700 bg-noche-900 text-noche-300 hover:bg-noche-800'
                }`}
              >
                {categoria.nombre}
              </button>
            ))}
          </div>
        )}
      </header>

      <main className="flex-1 px-3 py-3">
        {cargando ? (
          <Cargando mensaje="Cargando la carta" />
        ) : visibles.length === 0 ? (
          <Vacio icono={Search} titulo="Sin resultados" descripcion="Prueba con otra palabra del nombre del plato." />
        ) : (
          <div className="grid grid-cols-2 gap-2.5 lg:grid-cols-4">
            {visibles.map((item) => {
              const cantidad = cantidadEnBorrador(item.id)
              const recienTocado = tocado === item.id
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => tocarProducto(item)}
                  className={`relative flex min-h-[104px] flex-col justify-between rounded-2xl border p-3 text-left transition active:scale-[0.98] ${
                    !item.disponible
                      ? 'border-noche-800 bg-noche-900/50 opacity-60'
                      : recienTocado
                        ? 'border-oro-400 bg-oro-500/15'
                        : cantidad > 0
                          ? 'border-oro-500/50 bg-noche-850'
                          : 'border-noche-700 bg-noche-900 hover:bg-noche-850'
                  }`}
                >
                  <span className="pr-7 text-sm font-medium leading-snug text-crema-100">
                    {item.nombre}
                  </span>
                  <span className="mt-2 flex items-end justify-between gap-2">
                    <span className="text-sm font-semibold tabular-nums text-oro-300">
                      {formatoCOP(item.precio)}
                    </span>
                    {!item.disponible && (
                      <span className="rounded-md bg-noche-800 px-1.5 py-0.5 text-[0.7rem] font-medium text-estado-demorado">
                        Agotado
                      </span>
                    )}
                    {item.destino === 'bar' && item.disponible && (
                      <span className="text-[0.7rem] text-noche-500">barra</span>
                    )}
                  </span>

                  {cantidad > 0 && (
                    <span className="absolute right-2 top-2 flex h-6 min-w-[24px] items-center justify-center rounded-lg bg-oro-500 px-1 text-xs font-bold text-noche-950">
                      {cantidad}
                    </span>
                  )}
                </button>
              )
            })}
          </div>
        )}
      </main>

      {borrador.length > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-30 border-t border-noche-700 bg-noche-900/98 px-3 py-3 pb-segura backdrop-blur">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setRevisando(true)}
              className="flex min-h-toque flex-1 items-center gap-2.5 rounded-xl border border-noche-700 bg-noche-850 px-3 text-left transition hover:bg-noche-800"
            >
              <ShoppingBag className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
              <span className="min-w-0">
                <span className="block text-sm font-semibold text-crema-100">
                  {unidadesBorrador} {unidadesBorrador === 1 ? 'producto' : 'productos'}
                </span>
                <span className="block text-xs text-noche-400">{formatoCOP(totalBorrador)} · revisar</span>
              </span>
            </button>
            <Boton
              variante="principal"
              tamano="grande"
              cargando={guardando}
              onClick={enviarALaComanda}
              className="flex-1"
            >
              Agregar
            </Boton>
          </div>
        </div>
      )}

      <HojaModificadores
        item={enHoja?.item ?? null}
        inicial={
          enHoja?.editando
            ? {
                cantidad: enHoja.editando.cantidad,
                modificadores: enHoja.editando.modificadores,
                nota: enHoja.editando.nota,
              }
            : undefined
        }
        onCerrar={() => setEnHoja(null)}
        onConfirmar={guardarEdicion}
      />

      <HojaInferior
        abierta={revisando}
        titulo="Productos por agregar"
        descripcion={`${etiquetaMesa} · ${formatoCOP(totalBorrador)}`}
        onCerrar={() => setRevisando(false)}
        pie={
          <Boton
            variante="principal"
            tamano="grande"
            bloque
            cargando={guardando}
            onClick={enviarALaComanda}
          >
            Agregar a la comanda · {formatoCOP(totalBorrador)}
          </Boton>
        }
      >
        <ul className="space-y-2">
          {borrador.map((linea) => {
            const adicionales = linea.modificadores.reduce((s, m) => s + m.precioAdicional, 0)
            return (
              <li key={linea.clave} className="rounded-xl border border-noche-700 bg-noche-850 p-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-crema-100">{linea.item.nombre}</p>
                    {linea.modificadores.length > 0 && (
                      <p className="mt-0.5 text-xs text-noche-400">
                        {linea.modificadores.map((m) => m.valor).join(' · ')}
                      </p>
                    )}
                    {linea.nota && (
                      <p className="mt-0.5 text-xs italic text-oro-300">{linea.nota}</p>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setRevisando(false)
                      setEnHoja({ item: linea.item, editando: linea })
                    }}
                    aria-label={`Editar ${linea.item.nombre}`}
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-noche-400 transition hover:bg-noche-700 hover:text-crema-100"
                  >
                    <Pencil className="h-4 w-4" aria-hidden />
                  </button>
                </div>
                <div className="mt-2.5 flex items-center justify-between gap-2">
                  <Contador
                    valor={linea.cantidad}
                    onCambiar={(v) => cambiarCantidadLinea(linea.clave, v)}
                    permiteQuitar
                    compacto
                  />
                  <span className="text-sm font-semibold tabular-nums text-crema-100">
                    {formatoCOP((linea.item.precio + adicionales) * linea.cantidad)}
                  </span>
                </div>
              </li>
            )
          })}
        </ul>
      </HojaInferior>
    </div>
  )
}
