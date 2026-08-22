import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Clock, Flame, MapPin, MessageCircle, Navigation, Sparkles, Wine } from 'lucide-react'
import { RESTAURANTE } from '@/compartido/config'
import { formatoFechaLarga } from '@/compartido/formato'
import * as api from '@/compartido/mockApi'
import type { Publicacion } from '@/compartido/tipos'
import { enlaceWhatsApp } from '@/compartido/whatsapp'
import { enlaceMapaEmbebido, enlaceRutaHacia } from './ubicacion'
import { Filete, Ornamento } from './Ornamento'

const SALUDO = `Hola, quisiera reservar una mesa en ${RESTAURANTE.nombreCompleto}.`

const DISTINTIVOS = [
  {
    icono: Sparkles,
    titulo: 'Cocina de fusión',
    texto: 'Sabores del mundo en presentaciones que se recuerdan, con producto del Caribe.',
  },
  {
    icono: Wine,
    titulo: 'Coctelería de autor',
    texto: 'Corozo, tamarindo y panela ahumada en manos de nuestra barra.',
  },
  {
    icono: Flame,
    titulo: 'Viernes de cocina en vivo',
    texto: 'Cada semana un destino distinto, preparado frente a usted.',
  },
]

export default function Inicio() {
  const whatsapp = enlaceWhatsApp(RESTAURANTE.whatsapp, SALUDO)

  // Lo que el restaurante esta anunciando ahora. Se pide aparte y sin bloquear:
  // la portada tiene que pintarse completa aunque esto no llegue, porque una
  // promocion es un extra y la carta y la reserva son el motivo de la visita.
  const [publicaciones, setPublicaciones] = useState<Publicacion[]>([])
  useEffect(() => {
    let vigente = true
    api
      .publicacionesVisibles()
      .then((datos) => {
        if (vigente) setPublicaciones(datos)
      })
      .catch(() => undefined)
    return () => {
      vigente = false
    }
  }, [])

  // Promociones y eventos van juntos: los dos anuncian algo que pasa. Las fotos
  // del local son otra cosa y tienen su propio espacio mas abajo.
  const anuncios = publicaciones.filter((p) => p.tipo !== 'galeria')
  const galeria = publicaciones.filter((p) => p.tipo === 'galeria' && p.imagen)

  return (
    <>
      {/* ---------------- Portada ---------------- */}
      <section className="relative overflow-hidden">
        {/* Luz cálida detrás del título, como la de un salón de noche. */}
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              'radial-gradient(70% 55% at 50% 30%, rgba(198,116,31,0.16) 0%, rgba(8,23,15,0) 70%)',
          }}
          aria-hidden
        />

        <div className="relative mx-auto flex max-w-3xl flex-col items-center px-5 py-20 text-center sm:py-28">
          <Ornamento className="mb-8 h-16 w-28 text-oro-400/70" />

          <p className="text-[0.7rem] uppercase tracking-[0.4em] text-oro-400">
            {RESTAURANTE.ciudad}
          </p>

          <h1 className="mt-5 font-marca text-5xl font-normal tracking-[0.12em] text-crema-100 sm:text-7xl">
            EL PATIO
          </h1>

          <p className="mt-6 font-titulo text-2xl font-light italic leading-snug text-crema-200 sm:text-3xl">
            Donde la fusión gourmet cobra vida
          </p>

          <Filete className="mt-8 w-40 text-oro-400" />

          <p className="mt-8 max-w-xl text-base leading-relaxed text-crema-100/70">
            Un restaurante de mantel para las noches que importan: cumpleaños, aniversarios,
            cierres de negocio y esas cenas familiares que terminan tarde porque nadie quiere
            levantarse de la mesa.
          </p>

          <div className="mt-10 flex flex-col gap-3 sm:flex-row">
            <Link
              to="/carta"
              className="min-h-[52px] rounded-sm bg-oro-500 px-9 text-sm font-semibold uppercase tracking-[0.16em] leading-[52px] text-onix-950 transition hover:bg-oro-400"
            >
              Ver la carta
            </Link>
            <Link
              to="/reservar"
              className="min-h-[52px] rounded-sm border border-crema-100/30 px-9 text-sm uppercase tracking-[0.16em] leading-[50px] text-crema-100 transition hover:border-oro-400 hover:text-oro-300"
            >
              Reservar mesa
            </Link>
          </div>
        </div>

        {/* Franja de datos prácticos */}
        <div className="relative border-y border-oro-500/15 bg-onix-900/60">
          <div className="mx-auto flex max-w-5xl flex-col gap-3 px-5 py-5 text-sm text-crema-100/70 sm:flex-row sm:items-center sm:justify-center sm:gap-10">
            <span className="flex items-center gap-2">
              <Clock className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
              Martes a domingo, desde las 12:00 m.
            </span>
            <span className="flex items-center gap-2">
              <MapPin className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
              {RESTAURANTE.direccion}, {RESTAURANTE.ciudad}
            </span>
            <a
              href={whatsapp}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 transition hover:text-oro-300"
            >
              <MessageCircle className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
              {RESTAURANTE.telefono}
            </a>
          </div>
        </div>
      </section>

      {/* ---------------- Lo que esta pasando ----------------
          Promociones y eventos. Solo aparece si hay algo que anunciar: una
          seccion vacia con un «no hay promociones» ocuparia el mejor lugar de
          la portada para no decir nada. */}
      {anuncios.length > 0 && (
        <section className="border-t border-oro-500/15 bg-onix-900/40">
          <div className="mx-auto max-w-5xl px-5 py-16">
            <p className="text-[0.7rem] uppercase tracking-[0.35em] text-oro-400">
              Ahora en El Patio
            </p>
            <h2 className="mt-4 font-titulo text-4xl font-light leading-tight text-crema-100">
              Lo que está pasando
            </h2>

            <div className="mt-9 grid gap-6 sm:grid-cols-2">
              {anuncios.map((p) => (
                <article
                  key={p.id}
                  className="overflow-hidden rounded-2xl border border-oro-500/15 bg-onix-950/40"
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
                    <p className="text-[0.65rem] uppercase tracking-[0.3em] text-oro-400">
                      {p.tipo === 'promocion' ? 'Promoción' : 'Evento'}
                    </p>
                    <h3 className="mt-2 font-titulo text-2xl font-light leading-snug text-crema-100">
                      {p.titulo}
                    </h3>
                    {p.cuerpo && (
                      <p className="mt-3 whitespace-pre-line text-[0.95rem] leading-relaxed text-crema-100/65">
                        {p.cuerpo}
                      </p>
                    )}
                    {/* La vigencia solo se anuncia cuando de verdad termina.
                        «Hasta siempre» no informa. */}
                    {p.hasta && (
                      <p className="mt-4 text-xs uppercase tracking-wider text-oro-300">
                        Hasta el {formatoFechaLarga(p.hasta)}
                      </p>
                    )}
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* ---------------- El local, en collage ----------------
          Las fotos no van en una rejilla pareja sino en mosaico: la primera
          manda y las demas la acompanan. Una cuadricula de recuadros iguales
          se lee como un catalogo; un collage se lee como un lugar.

          El alto de la fila es fijo y las fotos se recortan al ocupar su
          casilla. Es a proposito: fotos de celular vienen en proporciones
          distintas, y dejarlas a su aire haria que el mosaico quedara con
          escalones. */}
      {galeria.length > 0 && (
        <section className="border-t border-oro-500/15">
          <div className="mx-auto max-w-5xl px-5 py-20">
            <p className="text-[0.7rem] uppercase tracking-[0.35em] text-oro-400">
              El local
            </p>
            <h2 className="mt-4 font-titulo text-4xl font-light leading-tight text-crema-100">
              Así se ve por dentro
            </h2>

            <div className="mt-9 grid auto-rows-[10rem] grid-cols-2 gap-3 sm:auto-rows-[12rem] sm:grid-cols-4">
              {galeria.map((foto, i) => (
                <figure
                  key={foto.id}
                  className={`group relative overflow-hidden rounded-2xl border border-oro-500/15 ${
                    // La primera manda: ocupa cuatro casillas. Cada cuarta de
                    // las siguientes toma dos de ancho, para que el mosaico no
                    // caiga en un patron repetido y aburrido.
                    i === 0 ? 'col-span-2 row-span-2' : i % 4 === 3 ? 'col-span-2' : ''
                  }`}
                >
                  <img
                    src={api.urlImagen(foto.imagen ?? '', i === 0 ? 1000 : 600)}
                    alt={foto.titulo}
                    loading="lazy"
                    className="h-full w-full object-cover transition duration-500 group-hover:scale-105"
                  />
                  {/* El titulo se lee sobre la foto, no debajo: un pie de foto
                      por cada casilla romperia el mosaico. El degradado existe
                      para que el texto siga leyendose sobre una foto clara. */}
                  <figcaption className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-onix-950 to-transparent px-4 pb-3 pt-10 text-sm text-crema-100">
                    {foto.titulo}
                  </figcaption>
                </figure>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* ---------------- El lugar ---------------- */}
      <section className="mx-auto max-w-3xl px-5 py-20 text-center">
        <p className="text-[0.7rem] uppercase tracking-[0.35em] text-oro-400">El lugar</p>
        <h2 className="mt-4 font-titulo text-4xl font-light leading-tight text-crema-100 sm:text-5xl">
          Mantel largo, aire fresco
          <br />
          <span className="italic text-oro-300">y tiempo para conversar</span>
        </h2>
        <p className="mx-auto mt-7 max-w-2xl text-base leading-relaxed text-crema-100/70">
          El Patio nació para las celebraciones. Salón climatizado, terraza para las noches
          templadas de Turbaco y comedores privados cuando la ocasión pide intimidad. Porciones
          generosas, servicio atento y una carta pensada para compartir.
        </p>
      </section>

      {/* ---------------- Distintivos ---------------- */}
      <section className="border-t border-oro-500/15 bg-onix-900/40">
        <div className="mx-auto grid max-w-5xl gap-10 px-5 py-20 sm:grid-cols-3">
          {DISTINTIVOS.map(({ icono: Icono, titulo, texto }) => (
            <article key={titulo} className="text-center">
              <Icono className="mx-auto h-7 w-7 text-oro-400" strokeWidth={1.25} aria-hidden />
              <h3 className="mt-4 font-titulo text-2xl font-light text-crema-100">{titulo}</h3>
              <p className="mt-3 text-sm leading-relaxed text-crema-100/65">{texto}</p>
            </article>
          ))}
        </div>
      </section>

      {/* ---------------- Ubicación y horario ---------------- */}
      <section className="mx-auto max-w-5xl px-5 py-20">
        <div className="grid gap-12 sm:grid-cols-2">
          <div>
            <p className="text-[0.7rem] uppercase tracking-[0.35em] text-oro-400">Encuéntrenos</p>
            <h2 className="mt-4 font-titulo text-4xl font-light text-crema-100">Dónde estamos</h2>
            <p className="mt-6 text-lg leading-relaxed text-crema-100/80">
              {RESTAURANTE.direccion}
              <br />
              {RESTAURANTE.ciudad}
            </p>
            <div className="mt-6 flex flex-wrap items-center gap-3">
              {/*
                Dos acciones y no una: quien esta en el sofa quiere ver donde
                queda, y quien ya salio quiere que el telefono lo lleve. El
                segundo enlace abre la aplicacion nativa con la ruta empezada
                desde donde este, sin escribir el origen.
              */}
              <a
                href={enlaceRutaHacia(
                  RESTAURANTE.coordenadas.latitud,
                  RESTAURANTE.coordenadas.longitud,
                )}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex min-h-toque items-center gap-2 rounded-full bg-oro-500 px-6 text-sm font-semibold uppercase tracking-[0.16em] text-noche-950 transition hover:bg-oro-400"
              >
                <Navigation className="h-4 w-4" aria-hidden />
                Poner la ruta
              </a>
              <a
                href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                  `${RESTAURANTE.nombreCompleto}, ${RESTAURANTE.direccion}, ${RESTAURANTE.ciudad}`,
                )}`}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex min-h-toque items-center gap-2 rounded-full border border-crema-100/20 px-6 text-sm uppercase tracking-[0.16em] text-oro-300 transition hover:border-oro-400/60 hover:text-oro-400"
              >
                <MapPin className="h-4 w-4" aria-hidden />
                Abrir en Google Maps
              </a>
            </div>
          </div>

          <div>
            <p className="text-[0.7rem] uppercase tracking-[0.35em] text-oro-400">Horario</p>
            <h2 className="mt-4 font-titulo text-4xl font-light text-crema-100">Cuándo abrimos</h2>
            <dl className="mt-6 divide-y divide-crema-100/10">
              {RESTAURANTE.horario.map((franja) => (
                <div key={franja.dias} className="flex justify-between gap-4 py-3">
                  <dt className="text-sm text-crema-100/70">{franja.dias}</dt>
                  <dd
                    className={`text-sm ${
                      franja.horas === 'Cerrado' ? 'text-crema-100/40' : 'text-crema-100'
                    }`}
                  >
                    {franja.horas}
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        </div>

        {/*
          El mapa va embebido sin llave de API: una llave en el paquete
          compilado es una llave publica, y aqui solo hay que enseñar un punto
          que nunca se mueve. `loading="lazy"` para que la portada no espere por
          el a pintarse.
        */}
        <div className="mt-14 overflow-hidden rounded-3xl border border-oro-500/15">
          <iframe
            title={`Ubicación de ${RESTAURANTE.nombreCompleto} en ${RESTAURANTE.ciudad}`}
            src={enlaceMapaEmbebido(
              RESTAURANTE.coordenadas.latitud,
              RESTAURANTE.coordenadas.longitud,
            )}
            loading="lazy"
            referrerPolicy="no-referrer-when-downgrade"
            className="h-[320px] w-full border-0 sm:h-[420px]"
          />
        </div>
      </section>

      {/* ---------------- Cierre ---------------- */}
      <section className="border-t border-oro-500/15">
        <div className="mx-auto max-w-3xl px-5 py-20 text-center">
          <Ornamento className="mx-auto mb-7 h-14 w-24 text-oro-400/60" />
          <h2 className="font-titulo text-4xl font-light leading-tight text-crema-100 sm:text-5xl">
            Reserve su mesa
          </h2>
          <p className="mx-auto mt-5 max-w-lg text-base leading-relaxed text-crema-100/70">
            Cuéntenos la fecha y la ocasión. Le confirmamos por WhatsApp y dejamos todo listo.
          </p>
          <div className="mt-9 flex flex-col justify-center gap-3 sm:flex-row">
            <Link
              to="/reservar"
              className="min-h-[52px] rounded-sm bg-oro-500 px-9 text-sm font-semibold uppercase tracking-[0.16em] leading-[52px] text-onix-950 transition hover:bg-oro-400"
            >
              Solicitar reserva
            </Link>
            <a
              href={whatsapp}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-[52px] items-center justify-center gap-2 rounded-sm border border-crema-100/30 px-9 text-sm uppercase tracking-[0.16em] text-crema-100 transition hover:border-oro-400 hover:text-oro-300"
            >
              <MessageCircle className="h-4 w-4" aria-hidden />
              Escribir por WhatsApp
            </a>
          </div>
        </div>
      </section>
    </>
  )
}
