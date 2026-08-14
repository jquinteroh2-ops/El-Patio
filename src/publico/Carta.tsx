import { useEffect, useRef, useState } from 'react'
import * as api from '@/compartido/mockApi'
import type { CategoriaConItems } from '@/compartido/mockApi'
import { formatoCOP } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import { Filete } from './Ornamento'

export default function Carta() {
  // Sin filtrar: un plato agotado se marca, no se esconde. El cliente merece
  // saber que existe, y manana vuelve a estar.
  const { datos: categorias, cargando } = useSyncedState<CategoriaConItems[]>(
    () => api.cartaAgrupada(),
    [],
    [],
    ['carta', 'todo'],
  )

  const [activa, setActiva] = useState<string | null>(null)
  const navRef = useRef<HTMLDivElement>(null)

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

  return (
    <>
      <section className="mx-auto max-w-3xl px-5 pb-10 pt-16 text-center">
        <p className="text-[0.7rem] uppercase tracking-[0.35em] text-ambar-400">Nuestra carta</p>
        <h1 className="mt-4 font-titulo text-5xl font-light text-crema-100">La carta</h1>
        <Filete className="mx-auto mt-6 w-32 text-ambar-400" />
        <p className="mx-auto mt-6 max-w-xl text-base leading-relaxed text-crema-100/65">
          Cocina de fusión con producto del Caribe. Los precios están en pesos colombianos e
          incluyen el impuesto al consumo al momento de la cuenta.
        </p>
      </section>

      {/* Navegación por categorías, siempre a la vista */}
      <div className="sticky top-16 z-30 border-y border-crema-100/10 bg-bosque-950/95 backdrop-blur">
        <div ref={navRef} className="sin-scrollbar mx-auto flex max-w-5xl gap-1 overflow-x-auto px-3 py-2">
          {categorias.map((categoria) => (
            <a
              key={categoria.id}
              href={`#${categoria.id}`}
              data-categoria={categoria.id}
              className={`min-h-[40px] shrink-0 rounded-sm px-3.5 text-sm leading-[40px] transition ${
                activa === categoria.id
                  ? 'bg-ambar-500/15 text-ambar-300'
                  : 'text-crema-100/60 hover:text-ambar-300'
              }`}
            >
              {categoria.nombre}
            </a>
          ))}
        </div>
      </div>

      <div className="mx-auto max-w-3xl px-5 py-14">
        {cargando ? (
          <p className="py-16 text-center text-crema-100/50">Cargando la carta…</p>
        ) : (
          <div className="space-y-16">
            {categorias.map((categoria) => (
              <section key={categoria.id} id={categoria.id} className="scroll-mt-36">
                <h2 className="font-titulo text-3xl font-light text-ambar-300 sm:text-4xl">
                  {categoria.nombre}
                </h2>
                <span className="mt-3 mb-7 block h-px w-full bg-crema-100/10" aria-hidden />

                <ul className="space-y-7">
                  {categoria.items.map((item) => (
                    <li key={item.id} className={item.disponible ? '' : 'opacity-55'}>
                      <div className="flex items-baseline justify-between gap-4">
                        <h3 className="font-titulo text-xl font-normal leading-snug text-crema-100">
                          {item.nombre}
                          {!item.disponible && (
                            <span className="ml-2 rounded-sm border border-crema-100/25 px-1.5 py-0.5 align-middle text-[0.65rem] uppercase tracking-wider text-crema-100/60">
                              Agotado
                            </span>
                          )}
                        </h3>
                        <span className="shrink-0 font-titulo text-xl tabular-nums text-ambar-300">
                          {formatoCOP(item.precio)}
                        </span>
                      </div>
                      {item.descripcion && (
                        <p className="mt-1.5 max-w-2xl text-[0.95rem] leading-relaxed text-crema-100/60">
                          {item.descripcion}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )}

        <p className="mt-16 border-t border-crema-100/10 pt-8 text-center text-sm leading-relaxed text-crema-100/50">
          La propina es voluntaria. Si desea dejarla, su mesero se la consultará antes de incluirla
          en la cuenta.
        </p>
      </div>
    </>
  )
}
