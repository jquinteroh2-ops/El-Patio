import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { RESTAURANTE } from '@/compartido/config'
import { Filete } from './Ornamento'

/**
 * La pagina que sale cuando la direccion no existe.
 *
 * Antes una direccion equivocada mandaba al inicio sin decir nada, y quien
 * llegaba por un enlace viejo creia que el sitio estaba roto o que la pagina
 * que buscaba era la portada. Aqui se le dice que esa direccion no existe y se
 * le dan los tres caminos que casi siempre buscaba.
 *
 * El servidor estatico igual responde 200 (es una aplicacion de una sola
 * pagina), asi que lo que evita que Google la indexe es el `noindex` que pone
 * `MetaDeRuta` en toda ruta que no es publica.
 */
export default function NoEncontrada() {
  // Va despues del efecto de `MetaDeRuta`, que no conoce esta ruta y le pondria
  // el titulo de las pantallas internas.
  useEffect(() => {
    document.title = `Página no encontrada · ${RESTAURANTE.nombreCompleto}`
  }, [])

  return (
    <section className="mx-auto max-w-2xl px-5 py-24 text-center">
      <p className="text-[0.7rem] uppercase tracking-[0.3em] text-oro-300">Error 404</p>

      <h1 className="subir demora-1 mt-6 font-titulo text-4xl font-light leading-tight text-crema-100 sm:text-5xl">
        Esta página no existe
      </h1>
      <p className="mx-auto mt-5 max-w-md text-lg text-crema-100/75">
        Puede que el enlace esté viejo o mal escrito. Lo que buscaba seguro está en alguno de
        estos lugares.
      </p>

      <Filete className="mx-auto mt-8 w-32 text-oro-400" />

      <div className="mt-10 flex flex-col justify-center gap-3 sm:flex-row">
        <Link
          to="/reservar"
          className="boton-brillo min-h-[52px] rounded-sm bg-oro-500 px-8 text-sm font-semibold uppercase tracking-[0.16em] leading-[52px] text-onix-950 hover:bg-oro-400"
        >
          Reservar mesa
        </Link>
        <Link
          to="/carta"
          className="boton-relleno inline-flex min-h-[52px] items-center justify-center rounded-sm border border-crema-100/30 px-8 text-sm uppercase tracking-[0.16em] text-crema-100"
        >
          Ver la carta
        </Link>
        <Link
          to="/"
          className="boton-relleno inline-flex min-h-[52px] items-center justify-center rounded-sm border border-crema-100/30 px-8 text-sm uppercase tracking-[0.16em] text-crema-100"
        >
          Ir al inicio
        </Link>
      </div>
    </section>
  )
}
