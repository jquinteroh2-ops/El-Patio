import { Link, NavLink, Outlet } from 'react-router-dom'
import { Instagram, MapPin, MessageCircle, Phone } from 'lucide-react'
import { DATOS_FISCALES, RESTAURANTE } from '@/compartido/config'
import { enlaceInstagram, useFichaSitio } from '@/compartido/sitio'
import { enlaceWhatsApp } from '@/compartido/whatsapp'
import { Emblema, MarcaConNombre } from './Marca'

const SALUDO_WHATSAPP = `Hola, quisiera información sobre ${RESTAURANTE.nombreCompleto}.`

export default function LayoutPublico() {
  // Direccion, telefono y redes salen de la base: los edita el panel.
  const ficha = useFichaSitio()
  const whatsapp = enlaceWhatsApp(ficha.whatsapp, SALUDO_WHATSAPP)

  return (
    <div className="flex min-h-dvh flex-col bg-onix-950 text-crema-100">
      <header className="sticky top-0 z-40 border-b border-oro-500/15 bg-onix-950/90 backdrop-blur">
        {/* Altura fija: la barra de categorías de la carta se pega debajo (top-16). */}
        <nav className="mx-auto flex h-16 max-w-5xl items-center justify-between gap-4 px-5">
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
            <NavLink
              to="/carta"
              className={({ isActive }) =>
                `transition hover:text-oro-300 ${isActive ? 'text-oro-300' : 'text-crema-100/70'}`
              }
            >
              Carta
            </NavLink>
            <NavLink
              to="/pedir"
              className={({ isActive }) =>
                `transition hover:text-oro-300 ${isActive ? 'text-oro-300' : 'text-crema-100/70'}`
              }
            >
              Pedir
            </NavLink>
            {/* «Trabaja con nosotros» va en el menú pero SIN el borde del botón:
                el llamado a la acción del restaurante es reservar, no contratar.
                En móvil se esconde y queda en el pie, donde lo busca quien lo
                busca a propósito. */}
            <NavLink
              to="/trabaja-con-nosotros"
              className={({ isActive }) =>
                `hidden transition hover:text-oro-300 sm:inline ${
                  isActive ? 'text-oro-300' : 'text-crema-100/70'
                }`
              }
            >
              Trabaja con nosotros
            </NavLink>
            <NavLink
              to="/reservar"
              className={({ isActive }) =>
                `rounded-sm border px-3.5 py-2 transition ${
                  isActive
                    ? 'border-oro-400 text-oro-300'
                    : 'border-crema-100/25 text-crema-100 hover:border-oro-400 hover:text-oro-300'
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

      <footer className="border-t border-oro-500/15 bg-onix-900">
        <div className="mx-auto grid max-w-5xl gap-8 px-5 py-12 sm:grid-cols-3">
          <div>
            <p className="flex items-center gap-2.5 font-marca text-lg tracking-[0.28em] text-crema-100">
              <Emblema tamano={28} />
              EL PATIO
            </p>
            <p className="mt-3 text-sm leading-relaxed text-crema-100/60">
              {RESTAURANTE.descripcionCorta} en el corazón de {ficha.ciudad}.
            </p>
          </div>

          <div>
            <p className="mb-3 text-xs uppercase tracking-[0.2em] text-oro-400">Visítanos</p>
            <p className="flex items-start gap-2 text-sm text-crema-100/70">
              <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-oro-400" aria-hidden />
              {ficha.direccion}
              <br />
              {ficha.ciudad}
            </p>
            <p className="mt-3 flex items-center gap-2 text-sm text-crema-100/70">
              <Phone className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
              {ficha.telefono}
            </p>
          </div>

          <div>
            <p className="mb-3 text-xs uppercase tracking-[0.2em] text-oro-400">Escríbenos</p>
            <a
              href={whatsapp}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 text-sm text-crema-100/70 transition hover:text-oro-300"
            >
              <MessageCircle className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
              WhatsApp
            </a>
            <a
              href={enlaceInstagram(ficha.instagram)}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-3 flex items-center gap-2 text-sm text-crema-100/70 transition hover:text-oro-300"
            >
              <Instagram className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
              @{ficha.instagram}
            </a>
          </div>
        </div>

        <div className="border-t border-oro-500/15">
          <div className="mx-auto flex max-w-5xl flex-col items-center gap-2 px-5 py-5 text-center sm:flex-row sm:justify-between">
            <span className="text-xs text-crema-100/35">
              {RESTAURANTE.nombreCompleto} · NIT {DATOS_FISCALES.nitCompleto}
            </span>
            <div className="flex items-center gap-4">
              <Link
                to="/trabaja-con-nosotros"
                className="text-xs uppercase tracking-[0.18em] text-crema-100/50 transition hover:text-oro-300"
              >
                Trabaja con nosotros
              </Link>
              {/* PQR va en el pie y NO en el encabezado: es un canal de
                  servicio, no un llamado a la accion comercial. */}
              <Link
                to="/pqr"
                className="text-xs uppercase tracking-[0.18em] text-crema-100/50 transition hover:text-oro-300"
              >
                PQR
              </Link>
              <Link
                to="/politica-de-datos"
                className="text-xs uppercase tracking-[0.18em] text-crema-100/35 transition hover:text-oro-300"
              >
                Política de datos
              </Link>
            </div>
            {/* Acceso del personal: existe, pero no compite con la carta ni la reserva. */}
            <Link
              to="/acceso"
              className="text-xs uppercase tracking-[0.18em] text-crema-100/35 transition hover:text-oro-300"
            >
              Acceso personal
            </Link>
          </div>
        </div>
      </footer>
    </div>
  )
}
