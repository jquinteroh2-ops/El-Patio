import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Images, Plus, ShoppingBag } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { CategoriaConItems } from '@/compartido/mockApi'
import { formatoCOP } from '@/compartido/formato'
import { enPromocion } from '@/compartido/calculos'
import { fotosDePlato } from '@/compartido/fotosDePlato'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { EstadoCanal, ItemCarta } from '@/compartido/tipos'
import type { SeleccionProducto } from '@/comandera/HojaModificadores'
import { FichaDePlato } from './FichaDePlato'
import { useCarrito } from './carrito'
import { Filete } from './Ornamento'
import { Esqueleto, EsqueletoTexto, ZonaCargando } from '@/componentes/ui/Esqueleto'

export default function Carta() {
  // Sin filtrar: un plato agotado se marca, no se esconde. El cliente merece
  // saber que existe, y manana vuelve a estar.
  const { datos: categorias, cargando } = useSyncedState<CategoriaConItems[]>(
    () => api.cartaAgrupada(),
    [],
    [],
    ['carta', 'todo'],
  )

  // El canal decide si se puede pedir: fuera de horario o con la cocina
  // saturada, la carta se sigue leyendo pero no aparece el boton de agregar.
  const { datos: canal } = useSyncedState<EstadoCanal | null>(
    () => api.estadoCanal(),
    null,
    [],
    ['pedidos', 'ajustes', 'todo'],
  )

  const carrito = useCarrito()
  /** El plato cuya ficha esta abierta. */
  const [enFicha, setEnFicha] = useState<ItemCarta | null>(null)

  const [activa, setActiva] = useState<string | null>(null)
  const navRef = useRef<HTMLDivElement>(null)

  const sePuedePedir = canal?.abierto === true

  // Resalta en la barra la categoría que se está leyendo.
  useEffect(() => {
    if (categorias.length === 0) return

    const observador = new IntersectionObserver(
      (entradas) => {
        const visible = entradas
          .filter((e) => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0]
        if (visible) setActiva(visible.target.id)
      },
      { rootMargin: '-140px 0px -60% 0px', threshold: 0 },
    )

    for (const categoria of categorias) {
      const seccion = document.getElementById(categoria.id)
      if (seccion) observador.observe(seccion)
    }
    return () => observador.disconnect()
  }, [categorias])

  // Mantiene visible la pestaña activa cuando la barra se desborda.
  useEffect(() => {
    if (!activa || !navRef.current) return
    const boton = navRef.current.querySelector<HTMLElement>(`[data-categoria="${activa}"]`)
    boton?.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' })
  }, [activa])

  const aviso = useMemo(() => {
    if (!canal) return null
    if (canal.abierto) return null
    if (canal.pausado) {
      return 'Por ahora no estamos recibiendo pedidos en línea. La cocina está a tope; inténtelo en un rato.'
    }
    const hora = (t: string) => t.slice(0, 5)
    return `Recibimos pedidos entre las ${hora(canal.desde)} y las ${hora(canal.hasta)}. Fuera de ese horario lo esperamos en el salón.`
  }, [canal])

  /**
   * El atajo: lo que no hay que elegir entra de un toque.
   *
   * Una limonada no tiene termino ni guarnicion, y hacerle abrir la ficha para
   * confirmar que quiere la limonada que acaba de pedir es un paso de mas. Lo
   * que si hay que elegir abre la ficha, que es donde estan las fotos y las
   * opciones juntas.
   */
  const alTocarAgregar = (item: ItemCarta) => {
    if ((item.modificadores?.length ?? 0) === 0) {
      carrito.agregar(item, 1, [])
      return
    }
    setEnFicha(item)
  }

  const alConfirmarSeleccion = (seleccion: SeleccionProducto) => {
    if (!enFicha) return
    carrito.agregar(enFicha, seleccion.cantidad, seleccion.modificadores, seleccion.nota)
    setEnFicha(null)
  }

  return (
    <>
      <section className="mx-auto max-w-3xl px-5 pb-10 pt-16 text-center">
        <p className="text-[0.7rem] uppercase tracking-[0.35em] text-oro-400">Nuestra carta</p>
        <h1 className="mt-4 font-titulo text-5xl font-light text-crema-100">La carta</h1>
        <Filete className="mx-auto mt-6 w-32 text-oro-400" />
        <p className="mx-auto mt-6 max-w-xl text-base leading-relaxed text-crema-100/65">
          Cocina de fusión con producto del Caribe. Los precios están en pesos colombianos e
          incluyen el impuesto al consumo al momento de la cuenta.
        </p>
      </section>

      {aviso && (
        <p className="mx-auto mb-2 max-w-3xl rounded-sm border border-oro-400/30 bg-oro-500/10 px-5 py-3 text-center text-sm text-oro-200">
          {aviso}
        </p>
      )}

      {/* Navegación por categorías, siempre a la vista */}
      <div className="sticky top-16 z-30 border-y border-oro-500/15 bg-onix-950/95 backdrop-blur">
        <div ref={navRef} className="sin-scrollbar mx-auto flex max-w-5xl gap-1 overflow-x-auto px-3 py-2">
          {categorias.map((categoria) => (
            <a
              key={categoria.id}
              href={`#${categoria.id}`}
              data-categoria={categoria.id}
              className={`min-h-[40px] shrink-0 rounded-sm px-3.5 text-sm leading-[40px] transition ${
                activa === categoria.id
                  ? 'bg-oro-500/15 text-oro-300'
                  : 'text-crema-100/60 hover:text-oro-300'
              }`}
            >
              {categoria.nombre}
            </a>
          ))}
        </div>
      </div>

      <div className="mx-auto max-w-3xl px-5 py-14">
        {cargando ? (
          /*
            Dos categorias de cuatro platos. No es el numero real -no se sabe
            hasta que llega la carta- pero llena la primera pantalla, que es lo
            unico que se ve mientras carga.
          */
          <ZonaCargando etiqueta="Cargando la carta">
            <div className="space-y-16">
              {[0, 1].map((categoria) => (
                <section key={categoria}>
                  <Esqueleto claro className="h-8 w-1/2" />
                  <span className="mt-3 mb-7 block h-px w-full bg-crema-100/10" aria-hidden />
                  <ul className="space-y-7">
                    {[0, 1, 2, 3].map((plato) => (
                      <li
                        key={plato}
                        className="animate-entrada"
                        style={{ animationDelay: `${(categoria * 4 + plato) * 45}ms` }}
                      >
                        <div className="flex items-baseline gap-4">
                          <Esqueleto claro className="h-5 w-2/5" />
                          <span className="h-px flex-1 bg-crema-100/10" aria-hidden />
                          <Esqueleto claro className="h-5 w-20" />
                        </div>
                        <div className="mt-2.5">
                          <EsqueletoTexto lineas={2} claro />
                        </div>
                      </li>
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          </ZonaCargando>
        ) : (
          <div className="space-y-16">
            {categorias.map((categoria) => (
              <section key={categoria.id} id={categoria.id} className="revelar scroll-mt-36">
                <h2 className="font-titulo text-3xl font-light text-oro-300 sm:text-4xl">
                  {categoria.nombre}
                </h2>
                <span className="mt-3 mb-7 block h-px w-full bg-crema-100/10" aria-hidden />

                <ul className="space-y-7">
                  {categoria.items.map((item) => {
                    const fotos = fotosDePlato(item)

                    /*
                      La fila entera se puede tocar, y por eso es `relative`.

                      El nombre lleva un `::after` estirado hasta los bordes de
                      este `li`, así que el toque vale en cualquier parte de la
                      fila —la foto, la descripción, el hueco de la derecha—
                      sin tener que meter el `<h3>` dentro de un botón. Eso
                      último no sirve: dentro de un botón solo cabe texto, el
                      `<h3>` habría que cambiarlo por un `<span>`, y la carta
                      se quedaría sin la jerarquía de títulos con la que la
                      recorre un lector de pantalla y la lee Google.
                    */
                    return (
                      <li
                        key={item.id}
                        className={`relative flex gap-4 ${item.disponible ? '' : 'opacity-55'}`}
                      >
                        {fotos.length > 0 && (
                          <div className="relative shrink-0">
                            <img
                              src={api.urlImagenCarta(fotos[0], 240)}
                              alt=""
                              loading="lazy"
                              className="h-20 w-20 rounded-sm object-cover sm:h-24 sm:w-24"
                            />
                            {/* Cuántas fotos más hay dentro. Sin esto, la ficha
                                del plato parece llevar solo la que ya se ve. */}
                            {fotos.length > 1 && (
                              <span className="absolute bottom-1 left-1 flex items-center gap-1 rounded-sm bg-onix-950/85 px-1.5 py-0.5 text-[0.65rem] text-crema-100/80">
                                <Images className="h-3 w-3" aria-hidden />
                                {fotos.length}
                              </span>
                            )}
                          </div>
                        )}

                        <div className="min-w-0 flex-1">
                          <div className="flex items-baseline justify-between gap-4">
                            <h3 className="font-titulo text-xl font-normal leading-snug text-crema-100">
                              <button
                                type="button"
                                onClick={() => setEnFicha(item)}
                                className="text-left transition after:absolute after:inset-0 after:content-[''] hover:text-oro-300"
                              >
                                {item.nombre}
                              </button>
                              {!item.disponible && (
                                <span className="ml-2 rounded-sm border border-crema-100/25 px-1.5 py-0.5 align-middle text-[0.65rem] uppercase tracking-wider text-crema-100/60">
                                  Agotado
                                </span>
                              )}
                              {item.disponible && enPromocion(item) && (
                                <span className="ml-2 rounded-sm border border-oro-400/50 bg-oro-500/10 px-1.5 py-0.5 align-middle text-[0.65rem] uppercase tracking-wider text-oro-300">
                                  Promoción
                                </span>
                              )}
                            </h3>
                            {/* En promoción se muestran los dos precios, con el
                                de lista tachado. Poner solo el rebajado
                                ahorraría un renglón y escondería justo lo que
                                hace atractiva la oferta: cuánto se está
                                ahorrando el cliente. */}
                            <span className="shrink-0 text-right font-titulo text-xl tabular-nums text-oro-300">
                              {enPromocion(item) ? (
                                <>
                                  <span className="mr-2 text-base text-crema-100/40 line-through">
                                    {formatoCOP(item.precio)}
                                  </span>
                                  {formatoCOP(item.precioPromocional!)}
                                </>
                              ) : (
                                formatoCOP(item.precio)
                              )}
                            </span>
                          </div>

                          {item.descripcion && (
                            <p className="mt-1.5 max-w-2xl text-[0.95rem] leading-relaxed text-crema-100/60">
                              {item.descripcion}
                            </p>
                          )}

                          {sePuedePedir && item.disponible && (
                            <button
                              type="button"
                              onClick={() => alTocarAgregar(item)}
                              /* `relative` lo saca de debajo del `::after` que
                                 cubre la fila: sin esto, tocar «Agregar»
                                 abriría la ficha en vez de agregar. */
                              className="relative mt-3 inline-flex min-h-[40px] items-center gap-1.5 rounded-sm border border-crema-100/25 px-3.5 text-sm text-crema-100 transition hover:border-oro-400 hover:text-oro-300"
                            >
                              <Plus className="h-4 w-4" aria-hidden />
                              Agregar
                            </button>
                          )}
                        </div>
                      </li>
                    )
                  })}
                </ul>
              </section>
            ))}
          </div>
        )}

        <p className="mt-16 border-t border-oro-500/15 pt-8 text-center text-sm leading-relaxed text-crema-100/50">
          La propina es voluntaria. Si desea dejarla, su mesero se la consultará antes de incluirla
          en la cuenta.
        </p>
      </div>

      {/* Espacio para que el botón flotante no tape el último plato. */}
      {carrito.unidades > 0 && <div className="h-24" aria-hidden />}

      {/* ---------- Botón flotante con el conteo y el total corriente ---------- */}
      {carrito.unidades > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-40 border-t border-oro-500/15 bg-onix-950/95 px-4 pt-4 pb-segura backdrop-blur">
          <Link
            to="/pedir"
            className="mx-auto flex min-h-[56px] max-w-3xl items-center justify-between gap-4 rounded-sm bg-oro-500 px-5 text-onix-950 transition hover:bg-oro-400"
          >
            <span className="flex items-center gap-2 font-semibold">
              <ShoppingBag className="h-5 w-5" aria-hidden />
              {carrito.unidades} {carrito.unidades === 1 ? 'producto' : 'productos'}
            </span>
            <span className="flex items-center gap-3">
              <span className="font-titulo text-lg tabular-nums">
                {formatoCOP(carrito.subtotal)}
              </span>
              <span className="text-sm font-semibold uppercase tracking-wider">Continuar</span>
            </span>
          </Link>
        </div>
      )}

      {/* La ficha: las fotos, lo que lleva el plato y el pedido en la misma
          hoja. Por dentro es la misma que usa la comandera —la elección del
          cliente y la del mesero son la misma decisión— con las fotos encima. */}
      <FichaDePlato
        item={enFicha}
        canalAbierto={sePuedePedir}
        onCerrar={() => setEnFicha(null)}
        onConfirmar={alConfirmarSeleccion}
      />
    </>
  )
}
