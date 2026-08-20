import { useEffect, useState } from 'react'
import { CLAVE_COLA } from './config'
import { escribirCrudo, leerCrudo, notificar, suscribir } from './almacen'
import type { EnvioPendiente } from './tipos'

/**
 * Resistencia a la perdida de conexion.
 *
 * En un restaurante real la tablet pierde WiFi. Si la comandera deja de
 * funcionar sin senal, el salon se paraliza. Aqui vive el estado de conexion y
 * la cola de envios que quedaron pendientes, guardada en el navegador para que
 * sobreviva incluso a un cierre de la aplicacion.
 *
 * Hasta ahora la cola era una simulacion: todo estaba en localStorage y nada
 * podia fallar de verdad. Con el backend, la cola cumple su proposito: guarda
 * los envios a cocina que no alcanzaron a salir y los reenvia solos, en el
 * mismo orden en que el mesero los dicto, apenas vuelve la senal.
 *
 * Alcance de lo que cubre: el envio a cocina. Tomar la comanda ya necesita
 * servidor, porque el identificador de cada producto lo asigna la base. Un
 * mesero completamente sin senal no puede empezar una mesa nueva; lo que si
 * queda protegido es el corte a mitad de servicio, que es la falla frecuente.
 */

/** Error que lanza el cliente cuando la operacion necesitaba red y no habia. */
export class SinConexionError extends Error {
  constructor(mensaje = 'Sin conexión') {
    super(mensaje)
    this.name = 'SinConexionError'
  }
}

// ---------------------------------------------------------------------------
// Estado de conexion
// ---------------------------------------------------------------------------

/**
 * Solo damos por caida la senal cuando el navegador lo afirma. Si el entorno no
 * expone `onLine`, se asume conexion: es preferible intentar el envio y fallar
 * que dejar la comandera bloqueada por una propiedad que no existe.
 */
function navegadorEnLinea(): boolean {
  if (typeof navigator === 'undefined') return true
  return navigator.onLine !== false
}

/**
 * Si vale la pena intentar hablar con el servidor.
 *
 * No se consulta el estado del WebSocket para decidirlo: el canal puede estar
 * reconectandose mientras el API responde perfectamente, y bloquear las
 * peticiones por eso dejaria la comandera muerta sin motivo.
 */
export function hayConexion(): boolean {
  return navegadorEnLinea()
}

// ---------------------------------------------------------------------------
// Cola de envios pendientes
// ---------------------------------------------------------------------------

export function leerCola(): EnvioPendiente[] {
  return leerCrudo<EnvioPendiente[]>(CLAVE_COLA, [])
}

function guardarCola(cola: EnvioPendiente[]): void {
  escribirCrudo(CLAVE_COLA, cola)
  notificar(['cola'], Date.now())
}

export function encolar(envio: Omit<EnvioPendiente, 'id' | 'encoladoEn' | 'intentos'>): EnvioPendiente {
  const pendiente: EnvioPendiente = {
    ...envio,
    id: `env_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`,
    encoladoEn: new Date().toISOString(),
    intentos: 0,
  }
  guardarCola([...leerCola(), pendiente])
  return pendiente
}

export function quitarDeCola(id: string): void {
  guardarCola(leerCola().filter((e) => e.id !== id))
}

export function marcarIntento(id: string): void {
  guardarCola(leerCola().map((e) => (e.id === id ? { ...e, intentos: e.intentos + 1 } : e)))
}

export function vaciarCola(): void {
  guardarCola([])
}

/**
 * Envios en el orden en que se dictaron.
 *
 * El orden importa: si las entradas quedaron en cola antes que los fuertes,
 * tienen que salir a cocina en esa secuencia o el turno se invierte y los
 * platos llegan a la mesa al reves.
 */
export function colaOrdenada(): EnvioPendiente[] {
  return [...leerCola()].sort((a, b) => a.encoladoEn.localeCompare(b.encoladoEn))
}

// ---------------------------------------------------------------------------
// Hooks
// ---------------------------------------------------------------------------

export interface EstadoConexion {
  enLinea: boolean
  pendientes: number
}

/** Estado de conexion vivo: reacciona al WiFi real del dispositivo. */
export function useEstadoConexion(): EstadoConexion {
  const calcular = (): EstadoConexion => ({
    // A proposito NO se exige que el canal de tiempo real este vivo. Con el
    // canal caido las pantallas dejan de refrescarse solas, pero el mesero
    // sigue pudiendo comandar y cobrar contra el API; pintarle un aviso rojo
    // seria una falsa alarma, y ademas parpadearia en cada carga mientras el
    // socket termina de abrir.
    enLinea: navegadorEnLinea(),
    pendientes: leerCola().length,
  })

  const [estado, setEstado] = useState<EstadoConexion>(calcular)

  useEffect(() => {
    const actualizar = () => setEstado(calcular())

    window.addEventListener('online', actualizar)
    window.addEventListener('offline', actualizar)
    const cancelarSync = suscribir(actualizar)

    actualizar()
    return () => {
      window.removeEventListener('online', actualizar)
      window.removeEventListener('offline', actualizar)
      cancelarSync()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return estado
}

/**
 * Ejecuta el procesador de la cola cuando vuelve la conexion y cada cierto
 * tiempo mientras queden pendientes. El procesador real lo aporta mockApi.
 */
export function useReintentoAutomatico(procesar: () => Promise<void>, intervaloMs = 8000): void {
  useEffect(() => {
    let activo = true

    const intentar = () => {
      if (!activo || !hayConexion() || leerCola().length === 0) return
      void procesar()
    }

    window.addEventListener('online', intentar)
    const cancelarSync = suscribir(intentar)
    const id = window.setInterval(intentar, intervaloMs)
    intentar()

    return () => {
      activo = false
      window.removeEventListener('online', intentar)
      window.clearInterval(id)
      cancelarSync()
    }
  }, [procesar, intervaloMs])
}
