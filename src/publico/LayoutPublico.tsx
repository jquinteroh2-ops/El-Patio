import { Link, NavLink, Outlet } from 'react-router-dom'
import { Instagram, MapPin, MessageCircle, Phone } from 'lucide-react'
import { ETIQUETA_DEMO, RESTAURANTE } from '@/compartido/config'
import { enlaceWhatsApp } from '@/compartido/whatsapp'

const SALUDO_WHATSAPP = `Hola, quisiera información sobre ${RESTAURANTE.nombreCompleto}.`

export default function LayoutPublico() {
  const whatsapp = enlaceWhatsApp(RESTAURANTE.whatsapp, SALUDO_WHATSAPP)

  return (
    <div className="flex min-h-screen flex-col bg-bosque-950 text-crema-100">
      <header className="sticky top-0 z-40 border-b border-crema-100/10 bg-bosque-950/90 backdrop-blur">
        {/* Altura fija: la barra de categorías de la carta se pega debajo (top-16). */}
        <nav className="mx-auto flex h-16 max-w-5xl items-center justify-between gap-4 px-5">
          <Link
            to="/"
            className="font-marca text-base tracking-[0.3em] text-crema-100 transition hover:text-ambar-300 sm:text-lg"
          >
            EL PATIO
          </Link>

          <div className="flex items-center gap-5 text-xs uppercase tracking-[0.16em] sm:gap-7">
            <NavLink
              to="/carta"
              className={({ isActive }) =>
                `transition hover:text-ambar-300 ${isActive ? 'text-ambar-300' : 'text-crema-100/70'}`
              }
            >
              Carta
            </NavLink>
            <NavLink
              to="/reservar"
              className={({ isActive }) =>
                `rounded-sm border px-3.5 py-2 transition ${
                  isActive
                    ? 'border-ambar-400 text-ambar-300'
                    : 'border-crema-100/25 text-crema-100 hover:border-ambar-400 hover:text-ambar-300'
                }`
              }
            >
              Reservar
            </NavLink>
          </div>
        </nav>
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="border-t border-crema-100/10 bg-bosque-900">
        <div className="mx-auto grid max-w-5xl gap-8 px-5 py-12 sm:grid-cols-3">
          <div>
            <p className="font-marca text-lg tracking-[0.28em] text-crema-100">EL PATIO</p>
            <p className="mt-3 text-sm leading-relaxed text-crema-100/60">
              {RESTAURANTE.descripcionCorta} en el corazón de {RESTAURANTE.ciudad}.
            </p>
          </div>

          <div>
            <p className="mb-3 text-xs uppercase tracking-[0.2em] text-ambar-400">Visítanos</p>
            <p className="flex items-start gap-2 text-sm text-crema-100/70">
              <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-ambar-400" aria-hidden />
              {RESTAURANTE.direccion}
              <br />
              {RESTAURANTE.ciudad}
            </p>
            <p className="mt-3 flex items-center gap-2 text-sm text-crema-100/70">
              <Phone className="h-4 w-4 shrink-0 text-ambar-400" aria-hidden />
              {RESTAURANTE.telefono}
            </p>
          </div>

          <div>
            <p className="mb-3 text-xs uppercase tracking-[0.2em] text-ambar-400">Escríbenos</p>
            <a
              href={whatsapp}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 text-sm text-crema-100/70 transition hover:text-ambar-300"
            >
              <MessageCircle className="h-4 w-4 shrink-0 text-ambar-400" aria-hidden />
              WhatsApp
            </a>
            <a
              href={`https://instagram.com/${RESTAURANTE.instagram}`}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-3 flex items-center gap-2 text-sm text-crema-100/70 transition hover:text-ambar-300"
            >
              <Instagram className="h-4 w-4 shrink-0 text-ambar-400" aria-hidden />
              @{RESTAURANTE.instagram}
            </a>
          </div>
        </div>

        <div className="border-t border-crema-100/10">
          <div className="mx-auto flex max-w-5xl flex-col items-center gap-2 px-5 py-5 text-center sm:flex-row sm:justify-between">
            <span className="text-xs text-crema-100/35">{ETIQUETA_DEMO}</span>
            {/* Acceso del personal: existe, pero no compite con la carta ni la reserva. */}
            <Link
              to="/acceso"
              className="text-xs uppercase tracking-[0.18em] text-crema-100/35 transition hover:text-ambar-300"
            >
              Acceso personal
            </Link>
          </div>
        </div>
      </footer>
    </div>
  )
}
