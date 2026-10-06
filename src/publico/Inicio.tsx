import { useEffect, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  Bike,
  CalendarDays,
  Clock,
  Flame,
  MapPin,
  MessageCircle,
  Navigation,
  Sparkles,
  Wine,
  type LucideIcon,
} from 'lucide-react'
import { useFichaSitio } from '@/compartido/sitio'
import { RESTAURANTE } from '@/compartido/config'
import { formatoFechaLarga } from '@/compartido/formato'
import * as api from '@/compartido/mockApi'
import type { Publicacion } from '@/compartido/tipos'
import { enlaceWhatsApp } from '@/compartido/whatsapp'
import { enlaceMapaEmbebido, enlaceRutaHacia } from './ubicacion'
import { Filete, Ornamento } from './Ornamento'
import Institucional from './Institucional'

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

/**
 * Una de las dos puertas de la portada: pedir o reservar. El ícono respira, la
 * tarjeta se levanta al pasar el cursor y la flecha avanza.
 */
function TarjetaDeAccion({
  a,
  icono: Icono,
  rotulo,
  titulo,
  texto,
  className = '',
  demoraIcono = false,
}: {
  a: string
  icono: LucideIcon
  rotulo: string
  titulo: string
  texto: string
  className?: string
  /** Desfasa la respiración del ícono para que las dos tarjetas no vayan al compás. */
  demoraIcono?: boolean
}) {
  return (
    <Link
      to={a}
      className={`tarjeta-viva group flex items-center gap-5 rounded-2xl border border-oro-500/15 bg-onix-900/70 p-5 backdrop-blur-sm sm:p-7 ${className}`}
    >
      <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full border border-oro-500/50 text-oro-300 transition-colors duration-500 group-hover:border-oro-400 group-hover:bg-oro-500/10">
        <Icono
          className={`icono-flota h-6 w-6 ${demoraIcono ? '[animation-delay:1.6s]' : ''}`}
          strokeWidth={1.25}
          aria-hidden
        />
      </span>
      <span className="min-w-0 flex-1 text-left">
        <span className="block text-[0.65rem] font-medium uppercase tracking-[0.3em] text-oro-400">
          {rotulo}
        </span>
        <span className="mt-1.5 block font-titulo text-3xl font-light leading-tight text-crema-100">
          {titulo}
        </span>
        <span className="mt-1 block text-sm text-crema-100/60">{texto}</span>
      </span>
      <ArrowRight className="flecha h-5 w-5 shrink-0 text-oro-400" strokeWidth={1.5} aria-hidden />
    </Link>
  )
}

export default function Inicio() {
  // Horario y contacto salen de la base, no del codigo: los edita el panel.
  const ficha = useFichaSitio()
  const whatsapp = enlaceWhatsApp(ficha.whatsapp, SALUDO)

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
      {/* ---------------- Portada ----------------

          El nombre a la izquierda y, al lado, lo que la gente viene a hacer:
          PEDIR y RESERVAR. Van dentro de la portada y no más abajo porque son
          el motivo de casi todas las visitas; nadie debería tener que bajar
          para encontrarlos. En el celular quedan justo debajo del nombre, en
          la primera pantalla.

          Todo entra en cadena: el ornamento se dibuja, el nombre aparece letra
          por letra, luego el lema, el filete y las tarjetas. Detrás, dos luces
          cálidas muy tenues se desplazan despacio, como las de un salón de
          noche. */}
      <section className="relative overflow-hidden">
        <div
          aria-hidden
          className="luz-dorada pointer-events-none absolute -left-40 -top-48 h-[38rem] w-[38rem]"
        />
        <div
          aria-hidden
          className="luz-dorada a-destiempo pointer-events-none absolute -bottom-56 -right-40 h-[32rem] w-[32rem] opacity-70"
        />

        <div className="relative mx-auto grid max-w-6xl gap-12 px-5 pb-16 pt-12 sm:px-6 sm:pt-16 lg:grid-cols-[1.1fr_1fr] lg:items-center lg:gap-16 lg:pb-24 lg:pt-20">
          <div className="flex flex-col items-center text-center lg:items-start lg:text-left">
            {/* El arco del dibujo empieza a un cuarto de su ancho: alineado a la
                izquierda se vería sangrado respecto al texto, y el margen
                negativo lo pone a ras. */}
            <Ornamento className="ornamento-vivo mb-7 h-16 w-28 text-oro-400/70 lg:-ml-7" />

            <p className="subir text-[0.7rem] uppercase tracking-[0.4em] text-oro-400">
              {ficha.ciudad}
            </p>

            {/* El nombre se escribe letra por letra. Las letras sueltas van
                ocultas a los lectores de pantalla, que leen el aria-label
                entero en vez de «E, L, P…». El espacio se queda en su sitio
                con un ancho propio: un inline-block vacío se encogería a cero. */}
            <h1
              aria-label="El Patio"
              className="mt-5 font-marca text-5xl font-normal tracking-[0.12em] text-crema-100 sm:text-7xl"
            >
              {[...'EL PATIO'].map((letra, i) => (
                <span
                  key={i}
                  aria-hidden
                  className={letra === ' ' ? 'inline-block w-[0.35em]' : 'letra'}
                  style={{ '--i': i } as CSSProperties}
                >
                  {letra === ' ' ? ' ' : letra}
                </span>
              ))}
            </h1>

            <p className="subir demora-4 mt-6 font-titulo text-2xl font-light italic leading-snug text-crema-200 sm:text-3xl">
              Donde la fusión gourmet cobra vida
            </p>

            <Filete className="subir demora-4 mt-8 w-40 text-oro-400" />

            <p className="subir demora-5 mt-8 max-w-md text-base leading-relaxed text-crema-100/70">
              Un restaurante de mantel para las noches que importan: cumpleaños, aniversarios,
              cierres de negocio y esas cenas familiares que terminan tarde porque nadie quiere
              levantarse de la mesa.
            </p>
          </div>

          {/* ---- Lo que se viene a hacer ---- */}
          <div className="grid gap-3">
            <TarjetaDeAccion
              a="/carta"
              icono={Bike}
              rotulo="Domicilio o para llevar"
              titulo="Hacer un pedido"
              texto="Escoja en la carta y se lo llevamos."
              className="subir demora-3"
            />
            <TarjetaDeAccion
              a="/reservar"
              icono={CalendarDays}
              rotulo="Su mesa lista"
              titulo="Reservar mesa"
              texto="Le confirmamos por WhatsApp."
              className="subir demora-4"
              demoraIcono
            />

            <a
              href={whatsapp}
              target="_blank"
              rel="noopener noreferrer"
              className="subir demora-5 boton-relleno group flex min-h-[52px] items-center justify-between rounded-2xl border border-crema-100/20 px-5 text-xs font-semibold uppercase tracking-[0.2em] text-crema-100 sm:px-7"
            >
              <span className="flex items-center gap-3">
                <MessageCircle className="h-4 w-4" strokeWidth={1.5} aria-hidden />
                Escríbanos por WhatsApp
              </span>
              <ArrowRight className="flecha h-4 w-4" strokeWidth={1.5} aria-hidden />
            </a>
          </div>
        </div>

        {/* Franja de datos prácticos */}
        <div className="subir demora-6 relative border-y border-oro-500/15 bg-onix-900/60">
          <div className="mx-auto flex max-w-6xl flex-col gap-3 px-5 py-5 text-sm text-crema-100/70 sm:flex-row sm:items-center sm:justify-center sm:gap-10 sm:px-6">
            {/*
              La primera franja del horario, no una frase escrita a mano: si
              alguien corrige el horario en el panel y esta linea se quedara
              fija, la portada anunciaria unas horas y la seccion de abajo otras.
            */}
            {ficha.horario[0] && (
              <span className="flex items-center gap-2">
                <Clock className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
                {ficha.horario[0].dias}, {ficha.horario[0].horas}
              </span>
            )}
            <span className="flex items-center gap-2">
              <MapPin className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
              {ficha.direccion}, {ficha.ciudad}
            </span>
            <a
              href={whatsapp}
              target="_blank"
              rel="noopener noreferrer"
              className="subrayado flex items-center gap-2 self-center transition hover:text-oro-300"
            >
              <MessageCircle className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
              {ficha.telefono}
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
          <div className="revelar mx-auto max-w-5xl px-5 py-16">
            <p className="text-[0.7rem] uppercase tracking-[0.35em] text-oro-400">
              Ahora en El Patio
            </p>
            <h2 className="mt-4 font-titulo text-4xl font-light leading-tight text-crema-100">
              Lo que está pasando
            </h2>

            <div className="escalonar mt-9 grid gap-6 sm:grid-cols-2">
              {anuncios.map((p) => (
                <article
                  key={p.id}
                  className="tarjeta-viva overflow-hidden rounded-2xl border border-oro-500/15 bg-onix-950/40"
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
          <div className="revelar mx-auto max-w-5xl px-5 py-20">
            <p className="text-[0.7rem] uppercase tracking-[0.35em] text-oro-400">
              El local
            </p>
            <h2 className="mt-4 font-titulo text-4xl font-light leading-tight text-crema-100">
              Así se ve por dentro
            </h2>

            {/* `grid-flow-row-dense` deja que una foto pequeña suba a tapar el
                hueco que deja una ancha al no caber al final de su fila. */}
            <div className="escalonar mt-9 grid grid-flow-row-dense auto-rows-[10rem] grid-cols-2 gap-3 sm:auto-rows-[12rem] sm:grid-cols-4">
              {galeria.map((foto, i) => (
                <figure
                  key={foto.id}
                  className={`tarjeta-viva group relative overflow-hidden rounded-2xl border border-oro-500/15 ${
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
                    className="h-full w-full object-cover"
                  />
                  {/* El titulo se lee sobre la foto, no debajo: un pie de foto
                      por cada casilla romperia el mosaico. El degradado existe
                      para que el texto siga leyendose sobre una foto clara.
                      Al pasar el cursor el titulo sube un poco y se aclara. */}
                  <figcaption className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-onix-950 to-transparent px-4 pb-3 pt-10 font-titulo text-lg italic text-crema-100/85 transition duration-500 group-hover:-translate-y-1 group-hover:text-crema-50">
                    {foto.titulo}
                  </figcaption>
                </figure>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* ---------------- El lugar ---------------- */}
      <section className="revelar mx-auto max-w-3xl px-5 py-20 text-center">
        <Filete className="mx-auto mb-8 w-32 text-oro-400" />
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

      {/* ---------------- Quiénes somos ----------------
          Va después de «el lugar» y antes de los distintivos: quien llega al
          sitio busca primero la carta y la reserva; lo institucional se lee
          cuando ya decidió mirar. Ponerlo arriba estorbaría a la mayoría. */}
      <Institucional />

      {/* ---------------- Distintivos ---------------- */}
      <section className="border-t border-oro-500/15 bg-onix-900/40">
        <div className="revelar escalonar mx-auto grid max-w-5xl gap-4 px-5 py-20 sm:grid-cols-3">
          {DISTINTIVOS.map(({ icono: Icono, titulo, texto }, i) => (
            <article
              key={titulo}
              className="tarjeta-viva group rounded-2xl border border-transparent px-6 py-10 text-center hover:bg-onix-900/60"
            >
              <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full border border-oro-500/30 transition-colors duration-500 group-hover:border-oro-400 group-hover:bg-oro-500/10">
                <Icono
                  className="h-6 w-6 text-oro-400 transition-transform duration-500 group-hover:-translate-y-0.5 group-hover:scale-110"
                  strokeWidth={1.25}
                  aria-hidden
                />
              </span>
              <span className="mt-4 block font-titulo text-sm italic tracking-[0.2em] text-oro-400/80">
                0{i + 1}
              </span>
              <h3 className="mt-2 font-titulo text-2xl font-light text-crema-100">{titulo}</h3>
              <p className="mt-3 text-sm leading-relaxed text-crema-100/65">{texto}</p>
            </article>
          ))}
        </div>
      </section>

      {/* ---------------- Ubicación y horario ---------------- */}
      <section className="mx-auto max-w-5xl px-5 py-20">
        <div className="revelar escalonar grid gap-12 sm:grid-cols-2">
          <div>
            <p className="text-[0.7rem] uppercase tracking-[0.35em] text-oro-400">Encuéntrenos</p>
            <h2 className="mt-4 font-titulo text-4xl font-light text-crema-100">Dónde estamos</h2>
            <p className="mt-6 text-lg leading-relaxed text-crema-100/80">
              {ficha.direccion}
              <br />
              {ficha.ciudad}
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
                className="boton-brillo inline-flex min-h-toque items-center gap-2 rounded-full bg-oro-500 px-6 text-sm font-semibold uppercase tracking-[0.16em] text-noche-950 hover:bg-oro-400"
              >
                <Navigation className="h-4 w-4" aria-hidden />
                Poner la ruta
              </a>
              <a
                href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                  `${RESTAURANTE.nombreCompleto}, ${ficha.direccion}, ${ficha.ciudad}`,
                )}`}
                target="_blank"
                rel="noopener noreferrer"
                className="boton-relleno inline-flex min-h-toque items-center gap-2 rounded-full border border-crema-100/20 px-6 text-sm uppercase tracking-[0.16em] text-oro-300"
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
              {ficha.horario.map((franja) => (
                <div
                  key={franja.dias}
                  className="flex justify-between gap-4 rounded-lg py-3 transition-[padding,background-color] duration-300 hover:bg-onix-900/70 hover:px-3"
                >
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
        <div className="revelar mt-14 overflow-hidden rounded-3xl border border-oro-500/15">
          <iframe
            title={`Ubicación de ${RESTAURANTE.nombreCompleto} en ${ficha.ciudad}`}
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
        <div className="revelar mx-auto max-w-3xl px-5 py-20 text-center">
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
              className="boton-brillo min-h-[52px] rounded-sm bg-oro-500 px-9 text-sm font-semibold uppercase tracking-[0.16em] leading-[52px] text-onix-950 hover:bg-oro-400"
            >
              Solicitar reserva
            </Link>
            <a
              href={whatsapp}
              target="_blank"
              rel="noopener noreferrer"
              className="boton-relleno inline-flex min-h-[52px] items-center justify-center gap-2 rounded-sm border border-crema-100/30 px-9 text-sm uppercase tracking-[0.16em] text-crema-100"
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
