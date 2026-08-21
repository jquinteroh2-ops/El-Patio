import { Client, type IMessage } from '@stomp/stompjs'
import { TOPICOS, URL_WS } from './config'

/**
 * Canal de sincronizacion entre dispositivos.
 *
 * Antes esto era un BroadcastChannel sobre localStorage, que solo alcanzaba a
 * las pestanas del mismo navegador. Por eso una comanda tomada en la tablet del
 * mesero nunca aparecia en la pantalla de cocina, que es otro aparato: el
 * sistema se veia funcionar en una demostracion y no servia en un salon.
 *
 * Ahora los avisos llegan por WebSocket desde el backend. La interfaz de
 * `suscribir()` no cambio: las pantallas siguen escuchando igual, sin saber que
 * debajo hay una red en vez de un canal local.
 *
 * Por el canal no viaja ningun dato del negocio, solo el aviso de que algo
 * cambio y en que area. Quien lo recibe vuelve a pedir los datos por el API,
 * que si exige credencial.
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

// ---------------------------------------------------------------------------
// Conexion con el canal
// ---------------------------------------------------------------------------

let cliente: Client | null = null
let conectado = false

/**
 * Ultima version recibida.
 *
 * El backend numera los eventos de forma creciente. Al reconectar puede llegar
 * un aviso viejo que estaba en vuelo; descartarlo evita una recarga inutil de
 * todas las pantallas del salon.
 */
let ultimaVersion = 0

/** Si el canal de tiempo real esta vivo en este momento. */
export function canalConectado(): boolean {
  return conectado
}

function alRecibir(mensaje: IMessage): void {
  try {
    const evento = JSON.parse(mensaje.body) as EventoSync
    if (!evento || !Array.isArray(evento.cambios)) return
    if (evento.version <= ultimaVersion) return
    ultimaVersion = evento.version
    avisarOyentes(evento)
  } catch (error) {
    console.error('[almacen] evento ilegible', error)
  }
}

function crearCliente(): Client {
  const nuevo = new Client({
    brokerURL: URL_WS,
    // El latido detecta un cable cortado o un punto de acceso caido, que es lo
    // que pasa en un restaurante. Sin el, el socket queda abierto contra nadie
    // y las pantallas se congelan sin dar senal de que algo va mal.
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    reconnectDelay: 3000,
    onConnect: () => {
      conectado = true
      for (const topico of Object.values(TOPICOS)) {
        nuevo.subscribe(topico, alRecibir)
      }
      // Al recuperar el canal las pantallas pueden llevar minutos desfasadas:
      // se les pide que relean todo. Todas las suscripciones incluyen 'todo',
      // asi que este unico aviso las alcanza a todas.
      avisarOyentes({ version: Date.now(), origen: 'reconexion', cambios: ['todo', 'canal'] })
    },
    onWebSocketClose: () => {
      if (!conectado) return
      conectado = false
      // Queda escrito en la consola a proposito: si el canal no levanta en un
      // despliegue, esta es la unica pista de que las pantallas se estan
      // manteniendo al dia por consulta y no por aviso.
      console.warn('[almacen] se cayó el canal de tiempo real; reintentando')
      // 'canal' no lo observa ninguna pantalla de datos: solo despierta al
      // indicador de conexion para que el mesero vea que perdio la senal.
      avisarOyentes({ version: Date.now(), origen: 'desconexion', cambios: ['canal'] })
    },
    onStompError: (trama) => {
      console.error('[almacen] el canal rechazó la conexión', trama.headers.message)
    },
  })
  return nuevo
}

/** Abre el canal. Es idempotente: llamarla dos veces no abre dos sockets. */
export function conectarCanal(): void {
  if (cliente || typeof window === 'undefined') return
  cliente = crearCliente()
  cliente.activate()
}

/** Cierra el canal. Solo hace falta al desmontar la aplicacion entera. */
export function desconectarCanal(): void {
  conectado = false
  void cliente?.deactivate()
  cliente = null
}

function avisarOyentes(evento: EventoSync): void {
  for (const oyente of oyentes) {
    try {
      oyente(evento)
    } catch (error) {
      console.error('[almacen] oyente falló', error)
    }
  }
}

/**
 * Escucha cambios de datos, vengan de este dispositivo o de cualquier otro.
 * Devuelve la funcion para dejar de escuchar.
 */
export function suscribir(oyente: Oyente): () => void {
  conectarCanal()
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}

/**
 * Anuncia un cambio hecho en este dispositivo.
 *
 * Los cambios de datos ya los anuncia el backend a todo el salon; esto solo
 * sirve para lo que es propio de este aparato y el servidor no puede conocer,
 * como la cola local de envios pendientes.
 */
export function notificar(cambios: string[], version: number): void {
  avisarOyentes({ version, origen: ID_PESTANA, cambios })
}

// ---------------------------------------------------------------------------
// Almacenamiento local
// ---------------------------------------------------------------------------

/**
 * Lo unico que sigue viviendo en el navegador es la cola de envios pendientes,
 * porque es justamente lo que hay que conservar cuando no se puede hablar con
 * el servidor. Los datos del negocio ya no se guardan aqui: la fuente de verdad
 * es PostgreSQL.
 */

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
