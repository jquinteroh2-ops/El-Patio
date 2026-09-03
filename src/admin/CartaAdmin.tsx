import { useMemo, useState } from 'react'
import { BookOpen, GlassWater, Pencil, Plus, Search, Soup } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import { formatoCOP } from '@/compartido/formato'
import { enPromocion } from '@/compartido/calculos'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { CategoriaCarta, ItemCarta } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Interruptor } from '@/componentes/ui/Interruptor'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'
import { EditorProducto } from './EditorProducto'

const sinTildes = (texto: string): string =>
  texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

export default function CartaAdmin() {
  const { mostrar } = useAvisos()

  const { datos: carta } = useSyncedState<ItemCarta[]>(() => api.listarCarta(), [], [], ['carta', 'todo'])
  const { datos: categorias } = useSyncedState<CategoriaCarta[]>(
    () => api.listarCategorias(),
    [],
    [],
    ['carta', 'todo'],
  )

  const [busqueda, setBusqueda] = useState('')
  const [categoriaActiva, setCategoriaActiva] = useState<string | 'todas'>('todas')
  const [editando, setEditando] = useState<ItemCarta | null>(null)
  const [creando, setCreando] = useState(false)
  const [guardando, setGuardando] = useState(false)

  const visibles = useMemo(() => {
    const q = sinTildes(busqueda.trim())
    return carta
      .filter((i) => (categoriaActiva === 'todas' ? true : i.categoriaId === categoriaActiva))
      .filter((i) => (q ? sinTildes(i.nombre).includes(q) : true))
  }, [carta, categoriaActiva, busqueda])

  const agotados = carta.filter((i) => !i.disponible).length

  const alternarDisponible = async (item: ItemCarta, disponible: boolean) => {
    try {
      await api.cambiarDisponibilidad(item.id, disponible)
      mostrar(
        disponible ? `${item.nombre} vuelve a la carta` : `${item.nombre} marcado como agotado`,
        disponible ? 'exito' : 'info',
      )
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo actualizar', 'error')
    }
  }

  const guardar = async (producto: ItemCarta) => {
    setGuardando(true)
    try {
      await api.guardarItemCarta(producto)
      mostrar(producto.id ? 'Producto actualizado' : 'Producto agregado a la carta', 'exito')
      setEditando(null)
      setCreando(false)
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo guardar', 'error')
    } finally {
      setGuardando(false)
    }
  }

  const eliminar = async (id: string) => {
    setGuardando(true)
    try {
      await api.eliminarItemCarta(id)
      mostrar('Producto eliminado de la carta', 'info')
      setEditando(null)
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo eliminar', 'error')
    } finally {
      setGuardando(false)
    }
  }

  const nombreCategoria = (id: string) => categorias.find((c) => c.id === id)?.nombre ?? ''

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-[200px] flex-1">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-noche-500"
            aria-hidden
          />
          <input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar producto"
            className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-900 pl-9 pr-3 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
          />
        </div>
        <Boton
          variante="principal"
          icono={<Plus className="h-4 w-4" />}
          onClick={() => {
            setEditando(null)
            setCreando(true)
          }}
        >
          Nuevo producto
        </Boton>
      </div>

      <div className="flex items-center gap-3 text-xs text-noche-400">
        <span>
          <strong className="text-crema-100">{carta.length}</strong> productos
        </span>
        {agotados > 0 && (
          <span className="font-medium text-estado-demorado">{agotados} agotados</span>
        )}
      </div>

      <div className="sin-scrollbar flex gap-2 overflow-x-auto pt-1 pb-1">
        {(['todas', ...categorias.map((c) => c.id)] as const).map((id) => (
          <button
            key={id}
            type="button"
            onClick={() => setCategoriaActiva(id)}
            className={`min-h-[38px] shrink-0 rounded-xl border px-3.5 text-sm font-medium transition ${
              categoriaActiva === id
                ? 'border-oro-500 bg-oro-500/15 text-oro-300'
                : 'border-noche-700 bg-noche-900 text-noche-300 hover:bg-noche-800'
            }`}
          >
            {id === 'todas' ? 'Todas' : nombreCategoria(id)}
          </button>
        ))}
      </div>

      {visibles.length === 0 ? (
        <Vacio icono={BookOpen} titulo="Sin productos" descripcion="Prueba con otra búsqueda o categoría." />
      ) : (
        <ul className="grid gap-2 lg:grid-cols-2">
          {visibles.map((item) => (
            <li
              key={item.id}
              className={`flex items-center gap-3 rounded-2xl border bg-noche-900 p-3 ${
                item.disponible ? 'border-noche-800' : 'border-estado-demorado/30'
              }`}
            >
              {item.imagen ? (
                <img
                  src={api.urlImagenCarta(item.imagen, 80)}
                  alt=""
                  className="h-9 w-9 shrink-0 rounded-xl object-cover"
                />
              ) : (
                <span
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-noche-850 text-noche-400"
                  title={item.destino === 'bar' ? 'Va a la barra' : 'Va a cocina'}
                >
                  {item.destino === 'bar' ? (
                    <GlassWater className="h-4 w-4" aria-hidden />
                  ) : (
                    <Soup className="h-4 w-4" aria-hidden />
                  )}
                </span>
              )}

              <div className="min-w-0 flex-1">
                <p
                  className={`truncate text-sm font-medium ${
                    item.disponible ? 'text-crema-100' : 'text-noche-400 line-through'
                  }`}
                >
                  {item.nombre}
                </p>
                <p className="truncate text-xs text-noche-500">
                  {nombreCategoria(item.categoriaId)} · {item.tiempoPreparacionMin} min
                  {item.modificadores && item.modificadores.length > 0 && (
                    <span className="ml-1.5 text-oro-300">
                      {item.modificadores.length} modificador
                      {item.modificadores.length === 1 ? '' : 'es'}
                    </span>
                  )}
                </p>
              </div>

              {/* Con promoción se muestran los dos precios, el de lista tachado.
                  Un solo número no dejaría ver que el plato está rebajado, que
                  es justo lo que el dueño viene a comprobar a esta pantalla. */}
              <span className="shrink-0 text-right text-sm font-semibold tabular-nums text-crema-100">
                {enPromocion(item) ? (
                  <>
                    <span className="block text-xs font-normal text-noche-400 line-through">
                      {formatoCOP(item.precio)}
                    </span>
                    <span className="text-oro-300">{formatoCOP(item.precioPromocional!)}</span>
                  </>
                ) : (
                  formatoCOP(item.precio)
                )}
              </span>

              <Interruptor
                activo={item.disponible}
                onCambiar={(v) => alternarDisponible(item, v)}
                etiqueta={`Disponibilidad de ${item.nombre}`}
              />

              <button
                type="button"
                onClick={() => {
                  setCreando(false)
                  setEditando(item)
                }}
                aria-label={`Editar ${item.nombre}`}
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-noche-400 transition hover:bg-noche-800 hover:text-crema-100"
              >
                <Pencil className="h-4 w-4" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      )}

      <EditorProducto
        abierto={creando || !!editando}
        producto={editando}
        categorias={categorias}
        guardando={guardando}
        onCerrar={() => {
          setEditando(null)
          setCreando(false)
        }}
        onGuardar={guardar}
        onEliminar={eliminar}
      />
    </div>
  )
}
