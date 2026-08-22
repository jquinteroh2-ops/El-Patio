import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarDays } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import { formatoFechaLarga } from '@/compartido/formato'
import type { Publicacion } from '@/compartido/tipos'
import { Ornamento } from './Ornamento'

/**
 * Lo que el restaurante quiere contar: promociones, eventos y el local por
 * dentro.
 *
 * Las tres cosas viven en la misma página porque son la misma visita: quien
 * está decidiendo si viene mira la oferta, mira qué hay esta semana y quiere
 * ver dónde se va a sentar. Repartirlas en tres páginas obligaría a navegar
 * para tomar una sola decisión.
 *
 * No se filtra nada aquí: el servidor solo entrega lo publicado y vigente.
 */
export default function Novedades() {
  const [publicaciones, setPublicaciones] = useState<Publicacion[]>([])
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    let vigente = true
    api
      .publicacionesVisibles()
      .then((datos) => {
        if (vigente) setPublicaciones(datos)
      })
      .catch(() => {
        // Que no haya novedades no puede romper la página: el cliente vino a
        // ver el restaurante, no un mensaje de error. Se queda vacía y ya.
      })
      .finally(() => {
        if (vigente) setCargando(false)
      })
    return () => {
      vigente = false
    }
  }, [])

  const promociones = publicaciones.filter((p) => p.tipo === 'promocion')
  const eventos = publicaciones.filter((p) => p.tipo === 'evento')
  const galeria = publicaciones.filter((p) => p.tipo === 'galeria')

  const vacio = !cargando && publicaciones.length === 0

  return (
    <div className="mx-auto max-w-5xl px-5 py-16">
      <header className="text-center">
        <p className="text-[0.7rem] uppercase tracking-[0.35em] text-ambar-400">El Patio</p>
        <h1 className="mt-4 font-titulo text-4xl font-light leading-tight text-crema-100 sm:text-5xl">
          Lo que está pasando
          <br />
          <span className="italic text-ambar-300">en el restaurante</span>
        </h1>
        <Ornamento className="mx-auto mt-6" />
      </header>

      {vacio && (
        <p className="mt-14 text-center text-base leading-relaxed text-crema-100/60">
          Por ahora no hay promociones ni eventos anunciados.
          <br />
          La carta, en cambio, siempre está.{' '}
          <Link to="/carta" className="text-ambar-300 underline underline-offset-4">
            Verla
          </Link>
          .
        </p>
      )}

      {/* ---------------- Promociones ---------------- */}
      {promociones.length > 0 && (
        <section className="mt-16">
          <h2 className="font-titulo text-3xl font-light text-crema-100">Promociones</h2>
          <div className="mt-7 grid gap-6 sm:grid-cols-2">
            {promociones.map((p) => (
              <Tarjeta key={p.id} publicacion={p} destacada />
            ))}
          </div>
        </section>
      )}

      {/* ---------------- Eventos ---------------- */}
      {eventos.length > 0 && (
        <section className="mt-16">
          <h2 className="font-titulo text-3xl font-light text-crema-100">Eventos</h2>
          <div className="mt-7 grid gap-6 sm:grid-cols-2">
            {eventos.map((p) => (
              <Tarjeta key={p.id} publicacion={p} />
            ))}
          </div>
        </section>
      )}

      {/* ---------------- El local ----------------
          Las fotos van al final y sin texto encima: para entonces el cliente ya
          sabe qué se come y qué hay esta semana, y lo que le falta es verse
          sentado ahí. */}
      {galeria.length > 0 && (
        <section className="mt-16">
          <h2 className="font-titulo text-3xl font-light text-crema-100">El local</h2>
          <div className="mt-7 grid gap-4 sm:grid-cols-3">
            {galeria.map((p) => (
              <figure key={p.id} className="overflow-hidden rounded-2xl border border-crema-100/10">
                {p.imagen && (
                  <img
                    src={api.urlImagen(p.imagen, 900)}
                    alt={p.titulo}
                    loading="lazy"
                    className="h-56 w-full object-cover"
                  />
                )}
                <figcaption className="bg-bosque-900/60 px-4 py-3 text-sm text-crema-100/70">
                  {p.titulo}
                </figcaption>
              </figure>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}

/** Una promoción o un evento, con su foto si la tiene. */
function Tarjeta({ publicacion: p, destacada = false }: { publicacion: Publicacion; destacada?: boolean }) {
  return (
    <article
      className={`overflow-hidden rounded-2xl border ${
        destacada ? 'border-ambar-500/30 bg-ambar-500/[0.04]' : 'border-crema-100/10 bg-bosque-900/40'
      }`}
    >
      {p.imagen && (
        <img
          src={api.urlImagen(p.imagen, 900)}
          alt={p.titulo}
          loading="lazy"
          className="h-52 w-full object-cover"
        />
      )}
      <div className="p-6">
        <h3 className="font-titulo text-2xl font-light leading-snug text-crema-100">{p.titulo}</h3>
        {p.cuerpo && (
          <p className="mt-3 whitespace-pre-line text-[0.95rem] leading-relaxed text-crema-100/65">
            {p.cuerpo}
          </p>
        )}
        {/* La vigencia solo se anuncia cuando de verdad termina. Decir «hasta
            siempre» no informa, y una fecha de inicio ya pasada tampoco. */}
        {p.hasta && (
          <p className="mt-4 flex items-center gap-2 text-xs uppercase tracking-wider text-ambar-300">
            <CalendarDays className="h-3.5 w-3.5" aria-hidden />
            Hasta el {formatoFechaLarga(p.hasta)}
          </p>
        )}
      </div>
    </article>
  )
}
