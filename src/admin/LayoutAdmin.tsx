import { NavLink, Outlet } from 'react-router-dom'
import { BarChart3, BookOpen, CalendarClock, FileCheck2, LayoutDashboard, Megaphone, Receipt, FileText, MessageSquare, Settings, Users, Wallet } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import { useSesionActiva } from '@/compartido/auth'
import { useSyncedState } from '@/compartido/useSyncedState'
import { BarraOperativa } from '@/componentes/BarraOperativa'

/**
 * Lo que espera respuesta, contado en la pestana.
 *
 * Una hoja de vida o una queja sin responder no avisan de nada: hay que
 * acordarse de entrar a mirarlas. El numero encima de la pestana es lo que
 * convierte «entrar por si acaso» en «entrar porque hay tres».
 *
 * El servidor no publica evento cuando entra una nueva -ni PQR ni postulaciones
 * lo hacen-, asi que esto se pone al dia con la reconsulta periodica de
 * useSyncedState, del orden del minuto. Para una bandeja que se atiende por
 * dias, sobra.
 */
function Pendientes({ consultar }: { consultar: () => Promise<number> }) {
  const { datos } = useSyncedState<number>(consultar, 0, [], ['todo'])
  if (datos <= 0) return null
  return (
    <span className="ml-0.5 inline-flex min-w-[1.25rem] items-center justify-center rounded-full bg-estado-proceso px-1.5 text-xs font-bold text-noche-950">
      {datos}
    </span>
  )
}

/** `soloAdmin` mantiene reportes y configuracion fuera de la vista del cajero. */
const SECCIONES = [
  { ruta: '/admin', etiqueta: 'Inicio', icono: LayoutDashboard, exacta: true, soloAdmin: false },
  { ruta: '/admin/reservas', etiqueta: 'Reservas', icono: CalendarClock, exacta: false, soloAdmin: false },
  { ruta: '/admin/carta', etiqueta: 'Carta', icono: BookOpen, exacta: false, soloAdmin: false },
  // Solo el dueno: lo que se publica aqui lo ve cualquiera que pase por el sitio.
  { ruta: '/admin/publicaciones', etiqueta: 'Publicaciones', icono: Megaphone, exacta: false, soloAdmin: true },
  { ruta: '/admin/ventas', etiqueta: 'Ventas', icono: Receipt, exacta: false, soloAdmin: false },
  { ruta: '/admin/cierre', etiqueta: 'Cierre de caja', icono: Wallet, exacta: false, soloAdmin: false },
  // Muestra la venta completa del periodo: es informacion de cierre contable.
  { ruta: '/admin/conciliacion', etiqueta: 'Conciliación', icono: FileCheck2, exacta: false, soloAdmin: true },
  // Hojas de vida: datos personales de gente que confio en el restaurante. Solo el dueno.
  { ruta: '/admin/postulaciones', etiqueta: 'Postulaciones', icono: Users, exacta: false, soloAdmin: true, pendientes: api.postulacionesSinRevisar },
  // PQR: lleva nombre, correo y a veces telefono de un cliente, ademas de lo
  // que opina del servicio. No es informacion para cualquiera con acceso.
  { ruta: '/admin/pqr', etiqueta: 'PQR', icono: MessageSquare, exacta: false, soloAdmin: true, pendientes: api.pqrAbiertas },
  { ruta: '/admin/institucional', etiqueta: 'Quiénes somos', icono: FileText, exacta: false, soloAdmin: true },
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
          {visibles.map(({ ruta, etiqueta, icono: Icono, exacta, pendientes }) => (
            <NavLink
              key={ruta}
              to={ruta}
              end={exacta}
              className={({ isActive }) =>
                `flex min-h-[42px] shrink-0 items-center gap-2 rounded-xl px-3.5 text-sm font-medium transition ${
                  isActive
                    ? 'bg-oro-500/15 text-oro-300'
                    : 'text-noche-300 hover:bg-noche-800 hover:text-crema-100'
                }`
              }
            >
              <Icono className="h-4 w-4" aria-hidden />
              {etiqueta}
              {pendientes && <Pendientes consultar={pendientes} />}
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
