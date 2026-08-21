import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { CLAVE_SESION } from './config'
import { alExpirarSesion, haySesion } from './cliente'
import * as api from './mockApi'
import type { Rol, Sesion } from './tipos'

/**
 * Sesion por pestana.
 *
 * A diferencia de los datos, que ahora viven en el servidor y son comunes a
 * todo el salon, la sesion vive en sessionStorage: cada pestana puede tener un
 * usuario distinto. Sin eso no se puede tener al mesero en una pestana y a
 * cocina en otra, que es justo como se opera en un mismo equipo cuando falta
 * una tablet.
 *
 * El token de acceso y el de refresco viven en cliente.ts, que es quien los
 * usa. Aqui solo se guarda quien es la persona, que es lo unico que las
 * pantallas necesitan saber.
 *
 * La renovacion del token es silenciosa y la maneja cliente.ts antes de cada
 * peticion. Este contexto solo se entera cuando la sesion se pierde de verdad,
 * para sacar al usuario a la pantalla de acceso en vez de dejarlo frente a
 * pantallas que no cargan nada.
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
  recepcion: '/recepcion',
  repartidor: '/reparto',
  cajero: '/admin',
  administrador: '/admin',
}

function leerSesion(): Sesion | null {
  try {
    const texto = sessionStorage.getItem(CLAVE_SESION)
    if (!texto) return null
    // Una sesion guardada sin credencial no sirve para nada: pasa si alguien
    // limpio el almacenamiento a medias o si la pestana se restauro despues de
    // que el token ya se borro. Se descarta para que la pantalla de acceso
    // aparezca de una vez en lugar de una comandera que falla en cada consulta.
    if (!haySesion()) {
      sessionStorage.removeItem(CLAVE_SESION)
      return null
    }
    return JSON.parse(texto) as Sesion
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

  // Cuando el refresco ya no sirve, el cliente avisa y aqui se limpia. La
  // guarda de rutas se encarga del resto: sin sesion, manda a /acceso.
  useEffect(
    () =>
      alExpirarSesion(() => {
        sessionStorage.removeItem(CLAVE_SESION)
        setSesion(null)
      }),
    [],
  )

  const ingresar = useCallback(async (usuario: string, clave: string) => {
    const nueva = await api.autenticar(usuario, clave)
    sessionStorage.setItem(CLAVE_SESION, JSON.stringify(nueva))
    setSesion(nueva)
    return nueva
  }, [])

  const salir = useCallback(() => {
    // La sesion local se corta de inmediato para que la pantalla responda al
    // instante; el aviso al servidor, que revoca el refresco, sale detras.
    sessionStorage.removeItem(CLAVE_SESION)
    setSesion(null)
    void api.cerrarSesion()
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
