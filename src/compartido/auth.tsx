import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { CLAVE_SESION } from './config'
import * as api from './mockApi'
import type { Rol, Sesion } from './tipos'

/**
 * Sesion por pestana.
 *
 * A diferencia de los datos, que viven en localStorage y son comunes a todas
 * las pestanas, la sesion vive en sessionStorage: cada pestana puede tener un
 * usuario distinto. Sin eso no se puede tener al mesero en una pestana y a
 * cocina en otra, que es justo lo que hay que mostrar en la reunion.
 */

interface ContextoValor {
  sesion: Sesion | null
  cargando: boolean
  ingresar: (usuario: string, clave: string) => Promise<Sesion>
  salir: () => void
  /** Ruta inicial que le corresponde al rol. */
  rutaInicial: (rol: Rol) => string
}

const Contexto = createContext<ContextoValor | null>(null)

const RUTA_POR_ROL: Record<Rol, string> = {
  mesero: '/comandera',
  cocina: '/cocina',
  cajero: '/admin',
  administrador: '/admin',
}

function leerSesion(): Sesion | null {
  try {
    const texto = sessionStorage.getItem(CLAVE_SESION)
    return texto ? (JSON.parse(texto) as Sesion) : null
  } catch {
    return null
  }
}

export function ProveedorSesion({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion | null>(null)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    setSesion(leerSesion())
    setCargando(false)
  }, [])

  const ingresar = useCallback(async (usuario: string, clave: string) => {
    const nueva = await api.autenticar(usuario, clave)
    sessionStorage.setItem(CLAVE_SESION, JSON.stringify(nueva))
    setSesion(nueva)
    return nueva
  }, [])

  const salir = useCallback(() => {
    sessionStorage.removeItem(CLAVE_SESION)
    setSesion(null)
  }, [])

  const valor = useMemo<ContextoValor>(
    () => ({ sesion, cargando, ingresar, salir, rutaInicial: (rol) => RUTA_POR_ROL[rol] }),
    [sesion, cargando, ingresar, salir],
  )

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>
}

export function useSesion(): ContextoValor {
  const valor = useContext(Contexto)
  if (!valor) throw new Error('useSesion debe usarse dentro de ProveedorSesion')
  return valor
}

/** Sesion activa o error: para pantallas que ya pasaron por la guarda. */
export function useSesionActiva(): Sesion {
  const { sesion } = useSesion()
  if (!sesion) throw new Error('Esta pantalla exige una sesión iniciada')
  return sesion
}

export { RUTA_POR_ROL }
