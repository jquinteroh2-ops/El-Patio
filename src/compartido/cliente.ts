import { CLAVE_ACCESO, CLAVE_REFRESCO, MARGEN_RENOVACION_SEGUNDOS, URL_API } from './config'
import { SinConexionError, hayConexion } from './conexion'

/**
 * Cliente HTTP contra el backend.
 *
 * Aqui vive la credencial y aqui se renueva. Ninguna pantalla sabe que existe
 * un token: piden datos a mockApi y mockApi los pide por aqui.
 *
 * Los tokens viven en sessionStorage y no en localStorage a proposito, por lo
 * mismo que la sesion: cada pestana puede tener a un usuario distinto, que es
 * lo que permite abrir la comandera en una y la pantalla de cocina en otra
 * durante una demostracion, y lo que evita que cerrar sesion en un dispositivo
 * compartido deje la puerta abierta en la pestana de al lado.
 */

// ---------------------------------------------------------------------------
// Credenciales de la pestana
// ---------------------------------------------------------------------------

interface Credenciales {
  acceso: string
  refresco: string
  /** Instante en milisegundos en que deja de servir el token de acceso. */
  expiraEn: number
}

let enMemoria: Credenciales | null = null

function leerCredenciales(): Credenciales | null {
  if (enMemoria) return enMemoria
  try {
    const acceso = sessionStorage.getItem(CLAVE_ACCESO)
    const refresco = sessionStorage.getItem(CLAVE_REFRESCO)
    if (!acceso || !refresco) return null
    enMemoria = { acceso, refresco, expiraEn: expiracionDe(acceso) }
    return enMemoria
  } catch {
    return null
  }
}

export function guardarCredenciales(acceso: string, refresco: string): void {
  enMemoria = { acceso, refresco, expiraEn: expiracionDe(acceso) }
  try {
    sessionStorage.setItem(CLAVE_ACCESO, acceso)
    sessionStorage.setItem(CLAVE_REFRESCO, refresco)
  } catch {
    // Un navegador con el almacenamiento bloqueado sigue funcionando mientras
    // la pestana este abierta: la credencial en memoria alcanza para el turno.
  }
}

export function borrarCredenciales(): void {
  enMemoria = null
  try {
    sessionStorage.removeItem(CLAVE_ACCESO)
    sessionStorage.removeItem(CLAVE_REFRESCO)
  } catch {
    /* nada que limpiar si no hay almacenamiento */
  }
}

export function tokenDeRefresco(): string | null {
  return leerCredenciales()?.refresco ?? null
}

export function haySesion(): boolean {
  return leerCredenciales() !== null
}

/**
 * Lee la fecha de expiracion del token sin verificar la firma.
 *
 * Verificarla aqui no tendria sentido: la firma la comprueba el backend, que es
 * el unico que conoce el secreto. Esto solo sirve para saber cuando conviene
 * pedir uno nuevo, y si el dato viniera manipulado lo unico que se lograria es
 * renovar antes de tiempo.
 */
function expiracionDe(token: string): number {
  try {
    const cuerpo = token.split('.')[1]
    const json = atob(cuerpo.replace(/-/g, '+').replace(/_/g, '/'))
    const datos = JSON.parse(json) as { exp?: number }
    return datos.exp ? datos.exp * 1000 : 0
  } catch {
    return 0
  }
}

// ---------------------------------------------------------------------------
// Expiracion de la sesion
// ---------------------------------------------------------------------------

type OyenteExpiracion = () => void
const oyentesExpiracion = new Set<OyenteExpiracion>()

/**
 * Avisa cuando la sesion se cayo de forma irrecuperable.
 *
 * Lo escucha auth.tsx para limpiar su estado y mandar a la pantalla de acceso.
 * Esta aqui y no alla porque quien se entera primero es el cliente, que es el
 * que recibe el 401 del backend.
 */
export function alExpirarSesion(oyente: OyenteExpiracion): () => void {
  oyentesExpiracion.add(oyente)
  return () => {
    oyentesExpiracion.delete(oyente)
  }
}

function anunciarExpiracion(): void {
  borrarCredenciales()
  for (const oyente of oyentesExpiracion) {
    try {
      oyente()
    } catch (error) {
      console.error('[cliente] un oyente de expiración falló', error)
    }
  }
}

// ---------------------------------------------------------------------------
// Renovacion silenciosa
// ---------------------------------------------------------------------------

/**
 * Renovacion en curso, si la hay.
 *
 * Se comparte entre todas las peticiones para que diez consultas simultaneas
 * con el token vencido disparen una sola renovacion. Sin esto, cada una
 * canjearia el refresco y, como el backend lo rota de un solo uso, la primera
 * en llegar invalidaria a las otras nueve y la sesion se caeria sola.
 */
let renovacionEnCurso: Promise<string | null> | null = null

async function renovar(): Promise<string | null> {
  if (renovacionEnCurso) return renovacionEnCurso

  renovacionEnCurso = (async () => {
    const credenciales = leerCredenciales()
    if (!credenciales) return null
    try {
      const respuesta = await fetch(`${URL_API}/api/acceso/refrescar`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refresco: credenciales.refresco }),
      })
      if (!respuesta.ok) {
        anunciarExpiracion()
        return null
      }
      const datos = (await respuesta.json()) as { acceso: string; refresco: string }
      guardarCredenciales(datos.acceso, datos.refresco)
      return datos.acceso
    } catch {
      // Fallo de red: la sesion no se declara perdida, porque el token puede
      // seguir siendo valido cuando vuelva la senal. Echar al mesero por un
      // corte de WiFi de tres segundos seria peor que dejarlo reintentar.
      return null
    } finally {
      renovacionEnCurso = null
    }
  })()

  return renovacionEnCurso
}

/** Token vigente, renovandolo antes de tiempo si esta por vencer. */
async function tokenVigente(): Promise<string | null> {
  const credenciales = leerCredenciales()
  if (!credenciales) return null

  const margen = MARGEN_RENOVACION_SEGUNDOS * 1000
  if (Date.now() < credenciales.expiraEn - margen) return credenciales.acceso

  return (await renovar()) ?? credenciales.acceso
}

// ---------------------------------------------------------------------------
// Peticiones
// ---------------------------------------------------------------------------

/** Error del backend con el mensaje que se le muestra al usuario. */
export class ErrorApi extends Error {
  readonly estado: number

  constructor(mensaje: string, estado: number) {
    super(mensaje)
    this.name = 'ErrorApi'
    this.estado = estado
  }
}

interface Opciones {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  cuerpo?: unknown
  /** Parametros de consulta. Los `undefined` no se envian. */
  consulta?: Record<string, string | number | boolean | undefined>
  /** Rutas abiertas del sitio publico, que no deben forzar sesion. */
  sinSesion?: boolean
}

function construirUrl(ruta: string, consulta?: Opciones['consulta']): string {
  const url = new URL(`${URL_API}${ruta}`)
  for (const [clave, valor] of Object.entries(consulta ?? {})) {
    if (valor === undefined || valor === '') continue
    url.searchParams.set(clave, String(valor))
  }
  return url.toString()
}

async function mensajeDeError(respuesta: Response): Promise<string> {
  try {
    const cuerpo = (await respuesta.json()) as { mensaje?: string }
    if (cuerpo?.mensaje) return cuerpo.mensaje
  } catch {
    /* el cuerpo no era JSON */
  }
  return 'No se pudo completar la operación'
}

/**
 * Hace la peticion y devuelve el cuerpo ya convertido.
 *
 * Un 204 devuelve `undefined`: el backend lo usa para las operaciones que no
 * tienen nada que responder y para «la mesa no tiene cuenta abierta», que en
 * mockApi.ts era un `null` y aqui se traduce en quien llama.
 */
export async function pedir<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const { metodo = 'GET', cuerpo, consulta, sinSesion = false } = opciones

  // El interruptor de demostracion y la caida real de WiFi se tratan igual: si
  // no hay senal no se intenta la peticion, para que el error llegue de
  // inmediato y la comandera pueda encolar en vez de esperar el tiempo de
  // espera del navegador con el mesero mirando la pantalla.
  if (!hayConexion()) throw new SinConexionError()

  const cabeceras: Record<string, string> = {}
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json'

  if (!sinSesion) {
    const token = await tokenVigente()
    if (token) cabeceras.Authorization = `Bearer ${token}`
  }

  let respuesta: Response
  try {
    respuesta = await fetch(construirUrl(ruta, consulta), {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
  } catch {
    // `fetch` solo rechaza por problemas de red, no por códigos de error.
    throw new SinConexionError('No hay conexión con el servidor')
  }

  // Un 401 con el token recien renovado significa que la sesion ya no vale:
  // se reintenta una sola vez y, si vuelve a fallar, se cierra la sesion en
  // lugar de dejar al usuario en una pantalla que no carga nada.
  if (respuesta.status === 401 && !sinSesion) {
    const nuevo = await renovar()
    if (!nuevo) {
      anunciarExpiracion()
      throw new ErrorApi('La sesión expiró: vuelva a ingresar', 401)
    }
    respuesta = await fetch(construirUrl(ruta, consulta), {
      method: metodo,
      headers: { ...cabeceras, Authorization: `Bearer ${nuevo}` },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
    if (respuesta.status === 401) {
      anunciarExpiracion()
      throw new ErrorApi('La sesión expiró: vuelva a ingresar', 401)
    }
  }

  if (!respuesta.ok) throw new ErrorApi(await mensajeDeError(respuesta), respuesta.status)

  if (respuesta.status === 204) return undefined as T
  const texto = await respuesta.text()
  return (texto ? JSON.parse(texto) : undefined) as T
}

/** Igual que `pedir`, pero un 204 se convierte en `null` para quien lo espera. */
export async function pedirOpcional<T>(ruta: string, opciones: Opciones = {}): Promise<T | null> {
  const resultado = await pedir<T | undefined>(ruta, opciones)
  return resultado ?? null
}
