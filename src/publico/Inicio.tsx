import { Link } from 'react-router-dom'
import { Clock, Flame, MapPin, MessageCircle, Navigation, Sparkles, Wine } from 'lucide-react'
import { RESTAURANTE } from '@/compartido/config'
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
          <Ornamento className="mb-8 h-16 w-28 text-ambar-400/70" />

          <p className="text-[0.7rem] uppercase tracking-[0.4em] text-ambar-400">
            {RESTAURANTE.ciudad}
          </p>

          <h1 className="mt-5 font-marca text-5xl font-normal tracking-[0.12em] text-crema-100 sm:text-7xl">
            EL PATIO
          </h1>

          <p className="mt-6 font-titulo text-2xl font-light italic leading-snug text-crema-200 sm:text-3xl">
            Donde la fusión gourmet cobra vida
          </p>

          <Filete className="mt-8 w-40 text-ambar-400" />

          <p className="mt-8 max-w-xl text-base leading-relaxed text-crema-100/70">
            Un restaurante de mantel para las noches que importan: cumpleaños, aniversarios,
            cierres de negocio y esas cenas familiares que terminan tarde porque nadie quiere
            levantarse de la mesa.
          </p>

          <div className="mt-10 flex flex-col gap-3 sm:flex-row">
            <Link
              to="/carta"
              className="min-h-[52px] rounded-sm bg-ambar-500 px-9 text-sm font-semibold uppercase tracking-[0.16em] leading-[52px] text-bosque-950 transition hover:bg-ambar-400"
            >
              Ver la carta
            </Link>
            <Link
              to="/reservar"
              className="min-h-[52px] rounded-sm border border-crema-100/30 px-9 text-sm uppercase tracking-[0.16em] leading-[50px] text-crema-100 transition hover:border-ambar-400 hover:text-ambar-300"
            >
              Reservar mesa
            </Link>
          </div>
        </div>

        {/* Franja de datos prácticos */}
        <div className="relative border-y border-crema-100/10 bg-bosque-900/60">
          <div className="mx-auto flex max-w-5xl flex-col gap-3 px-5 py-5 text-sm text-crema-100/70 sm:flex-row sm:items-center sm:justify-center sm:gap-10">
            <span className="flex items-center gap-2">
              <Clock className="h-4 w-4 shrink-0 text-ambar-400" aria-hidden />
              Martes a domingo, desde las 12:00 m.
            </span>
            <span className="flex items-center gap-2">
              <MapPin className="h-4 w-4 shrink-0 text-ambar-400" aria-hidden />
              {RESTAURANTE.direccion}, {RESTAURANTE.ciudad}
            </span>
            <a
              href={whatsapp}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 transition hover:text-ambar-300"
            >
              <MessageCircle className="h-4 w-4 shrink-0 text-ambar-400" aria-hidden />
              {RESTAURANTE.telefono}
            </a>
          </div>
        </div>
      </section>

      {/* ---------------- El lugar ---------------- */}
      <section className="mx-auto max-w-3xl px-5 py-20 text-center">
        <p className="text-[0.7rem] uppercase tracking-[0.35em] text-ambar-400">El lugar</p>
        <h2 className="mt-4 font-titulo text-4xl font-light leading-tight text-crema-100 sm:text-5xl">
          Mantel largo, aire fresco
          <br />
          <span className="italic text-ambar-300">y tiempo para conversar</span>
        </h2>
        <p className="mx-auto mt-7 max-w-2xl text-base leading-relaxed text-crema-100/70">
          El Patio nació para las celebraciones. Salón climatizado, terraza para las noches
          templadas de Turbaco y comedores privados cuando la ocasión pide intimidad. Porciones
          generosas, servicio atento y una carta pensada para compartir.
        </p>
      </section>

      {/* ---------------- Distintivos ---------------- */}
      <section className="border-t border-crema-100/10 bg-bosque-900/40">
        <div className="mx-auto grid max-w-5xl gap-10 px-5 py-20 sm:grid-cols-3">
          {DISTINTIVOS.map(({ icono: Icono, titulo, texto }) => (
            <article key={titulo} className="text-center">
              <Icono className="mx-auto h-7 w-7 text-ambar-400" strokeWidth={1.25} aria-hidden />
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
            <p className="text-[0.7rem] uppercase tracking-[0.35em] text-ambar-400">Encuéntrenos</p>
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
                className="inline-flex min-h-toque items-center gap-2 rounded-full bg-ambar-500 px-6 text-sm font-semibold uppercase tracking-[0.16em] text-noche-950 transition hover:bg-ambar-400"
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
                className="inline-flex min-h-toque items-center gap-2 rounded-full border border-crema-100/20 px-6 text-sm uppercase tracking-[0.16em] text-ambar-300 transition hover:border-ambar-400/60 hover:text-ambar-400"
              >
                <MapPin className="h-4 w-4" aria-hidden />
                Abrir en Google Maps
              </a>
            </div>
          </div>

          <div>
            <p className="text-[0.7rem] uppercase tracking-[0.35em] text-ambar-400">Horario</p>
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
        <div className="mt-14 overflow-hidden rounded-3xl border border-crema-100/10">
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
      <section className="border-t border-crema-100/10">
        <div className="mx-auto max-w-3xl px-5 py-20 text-center">
          <Ornamento className="mx-auto mb-7 h-14 w-24 text-ambar-400/60" />
          <h2 className="font-titulo text-4xl font-light leading-tight text-crema-100 sm:text-5xl">
            Reserve su mesa
          </h2>
          <p className="mx-auto mt-5 max-w-lg text-base leading-relaxed text-crema-100/70">
            Cuéntenos la fecha y la ocasión. Le confirmamos por WhatsApp y dejamos todo listo.
          </p>
          <div className="mt-9 flex flex-col justify-center gap-3 sm:flex-row">
            <Link
              to="/reservar"
              className="min-h-[52px] rounded-sm bg-ambar-500 px-9 text-sm font-semibold uppercase tracking-[0.16em] leading-[52px] text-bosque-950 transition hover:bg-ambar-400"
            >
              Solicitar reserva
            </Link>
            <a
              href={whatsapp}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-[52px] items-center justify-center gap-2 rounded-sm border border-crema-100/30 px-9 text-sm uppercase tracking-[0.16em] text-crema-100 transition hover:border-ambar-400 hover:text-ambar-300"
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
