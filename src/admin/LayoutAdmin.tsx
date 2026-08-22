import { NavLink, Outlet } from 'react-router-dom'
import {
  BarChart3,
  BookOpen,
  CalendarClock,
  LayoutDashboard,
  Receipt,
  Settings,
  Wallet,
} from 'lucide-react'
import { useSesionActiva } from '@/compartido/auth'
import { BarraOperativa } from '@/componentes/BarraOperativa'

/** `soloAdmin` mantiene reportes y configuracion fuera de la vista del cajero. */
const SECCIONES = [
  { ruta: '/admin', etiqueta: 'Inicio', icono: LayoutDashboard, exacta: true, soloAdmin: false },
  { ruta: '/admin/reservas', etiqueta: 'Reservas', icono: CalendarClock, exacta: false, soloAdmin: false },
  { ruta: '/admin/carta', etiqueta: 'Carta', icono: BookOpen, exacta: false, soloAdmin: false },
  { ruta: '/admin/ventas', etiqueta: 'Ventas', icono: Receipt, exacta: false, soloAdmin: false },
  { ruta: '/admin/cierre', etiqueta: 'Cierre de caja', icono: Wallet, exacta: false, soloAdmin: false },
  { ruta: '/admin/reportes', etiqueta: 'Reportes', icono: BarChart3, exacta: false, soloAdmin: true },
  { ruta: '/admin/configuracion', etiqueta: 'Configuración', icono: Settings, exacta: false, soloAdmin: true },
]

export default function LayoutAdmin() {
  const sesion = useSesionActiva()
  const visibles = SECCIONES.filter((s) => !s.soloAdmin || sesion.rol === 'administrador')

  return (
    <div className="flex min-h-dvh flex-col bg-noche-950">
      <BarraOperativa
        titulo="Panel administrativo"
        subtitulo={`${sesion.nombre} · ${sesion.rol === 'administrador' ? 'Administrador' : 'Cajero'}`}
      />

      <nav className="sticky top-16 z-20 border-b border-noche-800 bg-noche-900/95 backdrop-blur">
        <div className="sin-scrollbar flex gap-1 overflow-x-auto px-3 py-2">
          {visibles.map(({ ruta, etiqueta, icono: Icono, exacta }) => (
            <NavLink
              key={ruta}
              to={ruta}
              end={exacta}
              className={({ isActive }) =>
                `flex min-h-[42px] shrink-0 items-center gap-2 rounded-xl px-3.5 text-sm font-medium transition ${
                  isActive
                    ? 'bg-ambar-500/15 text-ambar-300'
                    : 'text-noche-300 hover:bg-noche-800 hover:text-crema-100'
                }`
              }
            >
              <Icono className="h-4 w-4" aria-hidden />
              {etiqueta}
            </NavLink>
          ))}
        </div>
      </nav>

      <main className="flex-1 px-3 py-4 sm:px-4">
        <Outlet />
      </main>

    </div>
  )
}
