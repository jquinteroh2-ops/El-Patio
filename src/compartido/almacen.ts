import { CANAL_SYNC, CLAVE_ALMACEN } from './config'
import type { BaseDatos } from './tipos'

/**
 * Capa de persistencia y sincronizacion entre pestanas.
 *
 * Los datos viven en localStorage, que es compartido por todas las pestanas del
 * mismo origen. Cada escritura se anuncia por un BroadcastChannel para que las
 * demas pestanas relean de inmediato, sin esperar a que el usuario recargue.
 *
 * El evento `storage` del navegador queda como respaldo por si el canal no esta
 * disponible. Ninguno de los dos rebota al emisor, asi que ademas mantenemos una
 * lista de oyentes locales para refrescar la pestana que hizo el cambio.
 *
 * Solo mockApi.ts debe usar este modulo.
 */

export interface EventoSync {
  version: number
  origen: string
  /** Nombres de lo que cambio: 'ordenes', 'mesas'... Sirve para animar. */
  cambios: string[]
}

type Oyente = (evento: EventoSync) => void

/** Identifica esta pestana. Cada pestana recibe uno distinto al cargar. */
export const ID_PESTANA = `p${Math.random().toString(36).slice(2, 8)}`

const oyentes = new Set<Oyente>()
let canal: BroadcastChannel | null = null
let iniciado = false

function obtenerCanal(): BroadcastChannel | null {
  if (canal) return canal
  if (typeof BroadcastChannel === 'undefined') return null
  canal = new BroadcastChannel(CANAL_SYNC)
  return canal
}

function iniciarEscucha(): void {
  if (iniciado || typeof window === 'undefined') return
  iniciado = true

  const canalActivo = obtenerCanal()
  if (canalActivo) {
    canalActivo.onmessage = (mensaje: MessageEvent<EventoSync>) => {
      if (!mensaje.data || mensaje.data.origen === ID_PESTANA) return
      avisarOyentes(mensaje.data)
    }
  }

  // Respaldo: se dispara en las otras pestanas cuando cambia localStorage.
  window.addEventListener('storage', (evento) => {
    if (evento.key !== CLAVE_ALMACEN) return
    avisarOyentes({ version: Date.now(), origen: 'storage', cambios: [] })
  })
}

function avisarOyentes(evento: EventoSync): void {
  for (const oyente of oyentes) {
    try {
      oyente(evento)
    } catch (error) {
      console.error('[almacen] oyente fallo', error)
    }
  }
}

/**
 * Escucha cambios de datos, vengan de esta pestana o de otra.
 * Devuelve la funcion para dejar de escuchar.
 */
export function suscribir(oyente: Oyente): () => void {
  iniciarEscucha()
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}

/** Anuncia un cambio a esta pestana y a todas las demas. */
export function notificar(cambios: string[], version: number): void {
  const evento: EventoSync = { version, origen: ID_PESTANA, cambios }
  avisarOyentes(evento)
  obtenerCanal()?.postMessage(evento)
}

// ---------------------------------------------------------------------------
// Lectura y escritura
// ---------------------------------------------------------------------------

export function leerCrudo<T>(clave: string, respaldo: T): T {
  if (typeof localStorage === 'undefined') return respaldo
  try {
    const texto = localStorage.getItem(clave)
    return texto ? (JSON.parse(texto) as T) : respaldo
  } catch (error) {
    console.error(`[almacen] no se pudo leer ${clave}`, error)
    return respaldo
  }
}

export function escribirCrudo<T>(clave: string, valor: T): void {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(clave, JSON.stringify(valor))
  } catch (error) {
    console.error(`[almacen] no se pudo escribir ${clave}`, error)
  }
}

export function leerBD(): BaseDatos | null {
  return leerCrudo<BaseDatos | null>(CLAVE_ALMACEN, null)
}

/** Guarda la base, sube la version y avisa a todas las pestanas. */
export function guardarBD(bd: BaseDatos, cambios: string[] = []): BaseDatos {
  const siguiente: BaseDatos = { ...bd, version: bd.version + 1 }
  escribirCrudo(CLAVE_ALMACEN, siguiente)
  notificar(cambios, siguiente.version)
  return siguiente
}

export function borrarBD(): void {
  if (typeof localStorage === 'undefined') return
  localStorage.removeItem(CLAVE_ALMACEN)
}
