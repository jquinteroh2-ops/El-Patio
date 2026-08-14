import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useSesion } from '@/compartido/auth'
import type { Rol } from '@/compartido/tipos'
import { Cargando } from './ui/Cargando'

interface Props {
  roles: Rol[]
  children: ReactNode
}

/**
 * Deja pasar solo a los roles autorizados. Quien no tenga sesion va a /acceso;
 * quien la tenga pero no le corresponda el area, se devuelve a la suya.
 */
export function GuardaRuta({ roles, children }: Props) {
  const { sesion, cargando, rutaInicial } = useSesion()
  const ubicacion = useLocation()

  if (cargando) return <Cargando pantallaCompleta mensaje="Verificando acceso" />

  if (!sesion) {
    return <Navigate to="/acceso" replace state={{ desde: ubicacion.pathname }} />
  }

  if (!roles.includes(sesion.rol)) {
    return <Navigate to={rutaInicial(sesion.rol)} replace />
  }

  return <>{children}</>
}
