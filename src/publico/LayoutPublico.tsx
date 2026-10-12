import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { ArrowUpRight, Clock, Instagram, Lock, MapPin, MessageCircle, Phone } from 'lucide-react'
import { RESTAURANTE } from '@/compartido/config'
import { enlaceInstagram, useFichaSitio } from '@/compartido/sitio'
import { enlaceWhatsApp } from '@/compartido/whatsapp'
import { Emblema, MarcaConNombre } from './Marca'
import { enlaceRutaHacia } from './ubicacion'
import { OtroRestaurante } from './OtroRestaurante'
import { AvisoCookies, EVENTO_ABRIR_AVISO } from './AvisoCookies'

/**
 * Lo que se busca al final de la página. PQR va aquí y no en el encabezado:
 * es un canal de servicio, no un llamado a la acción comercial.
 */
const ENLACES_PIE = [
  { ruta: '/carta', texto: 'La carta' },
  { ruta: '/reservar', texto: 'Reservar mesa' },
  { ruta: '/pedir', texto: 'Pedir a domicilio' },
  { ruta: '/trabaja-con-nosotros', texto: 'Trabaja con nosotros' },
  { ruta: '/pqr', texto: 'Peticiones, quejas y reclamos' },
]

const CLASE_RED =
  'flex h-10 w-10 items-center justify-center rounded-full border border-oro-500/30 text-oro-300 transition hover:border-oro-400 hover:bg-oro-500 hover:text-onix-950'

/** Título de columna del pie: versalitas de oro con un filete corto debajo. */
function TituloPie({ children }: { children: string }) {
  return (
    <p className="mb-5 text-[0.7rem] font-semibold uppercase tracking-[0.28em] text-oro-400">
      {children}
      <span className="mt-2.5 block h-px w-8 bg-oro-500/50" aria-hidden />
    </p>
  )
}

const SALUDO_WHATSAPP = `Hola, quisiera información sobre ${RESTAURANTE.nombreCompleto}.`

export default function LayoutPublico() {
  const { pathname } = useLocation()
  // Direccion, telefono y redes salen de la base: los edita el panel.
  const ficha = useFichaSitio()
  const whatsapp = enlaceWhatsApp(ficha.whatsapp, SALUDO_WHATSAPP)

  // Arriba del todo el encabezado se funde con la portada; al bajar aparece
  // su fondo y una sombra, para que se separe de lo que pasa por debajo.
  const [bajo, setBajo] = useState(false)
  useEffect(() => {
    const medir = () => setBajo(window.scrollY > 12)
    medir()
    window.addEventListener('scroll', medir, { passive: true })
    return () => window.removeEventListener('scroll', medir)
  }, [])

  return (
    <div className="flex min-h-dvh flex-col bg-onix-950 text-crema-100">
      <header
        className={`sticky top-0 z-40 border-b backdrop-blur transition-[background-color,border-color,box-shadow] duration-500 ${
          bajo
            ? 'border-oro-500/15 bg-onix-950/95 shadow-[0_10px_30px_-12px_rgba(0,0,0,0.9)]'
            : 'border-transparent bg-onix-950/40'
        }`}
      >
        {/* Altura fija: la barra de categorías de la carta se pega debajo (top-16). */}
        <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-5 sm:px-6">
          {/* El símbolo y el nombre son UN solo enlace al inicio, no dos: dos
              enlaces contiguos al mismo sitio obligan a un lector de pantalla a
              anunciarlo dos veces, y con el teclado hay que pasar dos veces por
              lo mismo. El símbolo va marcado como decorativo porque el nombre
              ya está ahí como texto. */}
          <Link
            to="/"
            className="group flex items-center gap-2.5 text-crema-100 transition hover:text-oro-300 sm:gap-3"
          >
            <MarcaConNombre />
          </Link>

          <div className="flex items-center gap-5 text-xs uppercase tracking-[0.16em] sm:gap-7">
            <NavLink to="/carta" className={enlace}>
              Carta
            </NavLink>
            <NavLink to="/pedir" className={enlace}>
              Pedir
            </NavLink>
            {/* «Trabaja con nosotros» va en el menú pero SIN el borde del botón:
                el llamado a la acción del restaurante es reservar, no contratar.
                En móvil se esconde y queda en el pie, donde lo busca quien lo
                busca a propósito. */}
            <NavLink
              to="/trabaja-con-nosotros"
              className={(estado) => `hidden sm:inline ${enlace(estado)}`}
            >
              Trabaja con nosotros
            </NavLink>
            <NavLink
              to="/reservar"
              className={({ isActive }) =>
                `boton-brillo rounded-sm px-3.5 py-2 font-semibold ${
                  isActive ? 'bg-oro-400 text-onix-950' : 'bg-oro-500 text-onix-950 hover:bg-oro-400'
                }`
              }
            >
              Reservar
            </NavLink>
          </div>
        </nav>
      </header>

      <main key={pathname} className="entrada-de-panel flex-1">
        <Outlet />
      </main>

      {/*
        El pie, en tres pisos: una franja de oro que lo separa de la página, el
        cuerpo con la marca y las columnas que un cliente busca al final de
        cualquier sitio (dónde, cuándo, a dónde ir), y la línea legal. Cada
        columna lleva su título en versalitas con un filete corto debajo: es lo
        que ordena el bloque sin cajas ni bordes.
      */}
      <footer className="relative bg-onix-900">
        <span
          className="block h-1 w-full bg-gradient-to-r from-oro-600 via-oro-400 to-oro-600"
          aria-hidden
        />

        <div className="mx-auto max-w-6xl px-5 pb-12 pt-14 sm:px-6">
          <div className="grid gap-12 lg:grid-cols-[1.2fr_2fr]">
            {/* ---------- Marca ---------- */}
            <div>
              <Link
                to="/"
                className="inline-flex items-center gap-3.5 text-crema-100 transition hover:text-oro-300"
              >
                <Emblema tamano={48} />
                <span>
                  <span className="block font-marca text-2xl tracking-[0.3em]">EL PATIO</span>
                  <span className="mt-1 block text-[0.65rem] uppercase tracking-[0.32em] text-oro-400">
                    Restaurante
                  </span>
                </span>
              </Link>
              <span className="mt-6 block h-px w-24 bg-oro-500/60" aria-hidden />
              <p className="mt-5 max-w-sm text-sm leading-relaxed text-crema-100/60">
                {RESTAURANTE.descripcionCorta} en el corazón de {ficha.ciudad}.
              </p>
              <div className="mt-6 flex gap-2.5">
                <a
                  href={whatsapp}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label="WhatsApp"
                  title="WhatsApp"
                  className={CLASE_RED}
                >
                  <MessageCircle className="h-4 w-4" aria-hidden />
                </a>
                <a
                  href={enlaceInstagram(ficha.instagram)}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label="Instagram"
                  title={`@${ficha.instagram}`}
                  className={CLASE_RED}
                >
                  <Instagram className="h-4 w-4" aria-hidden />
                </a>
                <a
                  href={`tel:${ficha.telefono.replace(/[^+\d]/g, '')}`}
                  aria-label="Llamar"
                  title={ficha.telefono}
                  className={CLASE_RED}
                >
                  <Phone className="h-4 w-4" aria-hidden />
                </a>
              </div>
            </div>

            <div className="grid gap-10 sm:grid-cols-3">
              {/* ---------- Visítanos ---------- */}
              <div>
                <TituloPie>Visítanos</TituloPie>
                <p className="flex items-start gap-2.5 text-sm leading-relaxed text-crema-100/70">
                  <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-oro-400" aria-hidden />
                  <span>
                    {ficha.direccion}
                    <br />
                    {ficha.ciudad}
                  </span>
                </p>
                <p className="mt-3 flex items-center gap-2.5 text-sm text-crema-100/70">
                  <Phone className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
                  {ficha.telefono}
                </p>
                <a
                  href={enlaceRutaHacia(
                    RESTAURANTE.coordenadas.latitud,
                    RESTAURANTE.coordenadas.longitud,
                  )}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="subrayado mt-4 inline-flex items-center gap-1.5 text-xs uppercase tracking-[0.18em] text-oro-300 transition hover:text-oro-200"
                >
                  Cómo llegar
                  <ArrowUpRight className="h-3.5 w-3.5" aria-hidden />
                </a>
              </div>

              {/* ---------- Horario ---------- */}
              <div>
                <TituloPie>Horario</TituloPie>
                <dl className="space-y-3">
                  {ficha.horario.map((franja) => (
                    <div key={franja.dias} className="text-sm">
                      <dt className="flex items-center gap-2 text-crema-100/50">
                        <Clock className="h-3.5 w-3.5 shrink-0 text-oro-400/80" aria-hidden />
                        {franja.dias}
                      </dt>
                      <dd
                        className={`ml-[1.375rem] ${
                          franja.horas === 'Cerrado' ? 'text-crema-100/40' : 'text-crema-100/80'
                        }`}
                      >
                        {franja.horas}
                      </dd>
                    </div>
                  ))}
                </dl>
              </div>

              {/* ---------- Explora ---------- */}
              <nav aria-label="Enlaces del sitio">
                <TituloPie>Explora</TituloPie>
                <ul className="space-y-2.5 text-sm">
                  {ENLACES_PIE.map((e) => (
                    <li key={e.ruta}>
                      <Link
                        to={e.ruta}
                        className="subrayado text-crema-100/70 transition hover:text-oro-300"
                      >
                        {e.texto}
                      </Link>
                    </li>
                  ))}
                </ul>
              </nav>
            </div>
          </div>
        </div>

        <OtroRestaurante />

        {/* ---------- Línea legal ---------- */}
        <div className="border-t border-oro-500/15 bg-onix-950/60">
          <div className="mx-auto flex max-w-6xl flex-col items-center gap-3 px-5 py-5 text-center sm:px-6 md:flex-row md:justify-between md:text-left">
            <span className="text-xs text-crema-100/40">
              © {new Date().getFullYear()} {RESTAURANTE.nombreCompleto}. Todos los derechos
              reservados.
            </span>
            <div className="flex flex-wrap items-center justify-center gap-x-5 gap-y-2">
              <Link
                to="/politica-de-datos"
                className="text-xs text-crema-100/40 transition hover:text-oro-300"
              >
                Política de datos
              </Link>
              <button
                type="button"
                onClick={() => window.dispatchEvent(new Event(EVENTO_ABRIR_AVISO))}
                className="text-xs text-crema-100/40 transition hover:text-oro-300"
              >
                Cookies
              </button>
              {/* Acceso del personal: existe, pero no compite con la carta ni la reserva. */}
              <Link
                to="/acceso"
                className="flex items-center gap-1.5 text-xs text-crema-100/40 transition hover:text-oro-300"
              >
                <Lock className="h-3 w-3" aria-hidden />
                Acceso personal
              </Link>
            </div>
          </div>
        </div>
      </footer>

      <AvisoCookies />
    </div>
  )
}

function enlace({ isActive }: { isActive: boolean }) {
  // El subrayado de oro crece al pasar el cursor y se queda en la activa.
  return `subrayado py-1 transition ${
    isActive ? 'activo text-oro-300' : 'text-crema-100/70 hover:text-crema-50'
  }`
}
