import { useEffect, useState } from 'react'
import { CLAVE_COLA } from './config'
import { escribirCrudo, leerBD, leerCrudo, notificar, suscribir } from './almacen'
import type { EnvioPendiente } from './tipos'

/**
 * Resistencia a la perdida de conexion.
 *
 * En un restaurante real la tablet pierde WiFi. Si la comandera deja de
 * funcionar sin senal, el salon se paraliza. Aqui vive el estado de conexion y
 * la cola de envios que quedaron pendientes, persistida en localStorage para
 * que sobreviva incluso a un cierre de la aplicacion.
 */

/** Error que lanza mockApi cuando la operacion necesitaba red y no habia. */
export class SinConexionError extends Error {
  constructor(mensaje = 'Sin conexión') {
    super(mensaje)
    this.name = 'SinConexionError'
  }
}

/** Interruptor de demostracion, guardado con los ajustes de la base. */
export function simulandoSinConexion(): boolean {
  return leerBD()?.ajustes.simularSinConexion ?? false
}

/**
 * Solo damos por caida la senal cuando el navegador lo afirma. Si el entorno no
 * expone `onLine`, se asume conexion: es preferible intentar el envio y fallar
 * que dejar la comandera bloqueada por una propiedad que no existe.
 */
function navegadorEnLinea(): boolean {
  if (typeof navigator === 'undefined') return true
  return navigator.onLine !== false
}

export function hayConexion(): boolean {
  if (simulandoSinConexion()) return false
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

// ---------------------------------------------------------------------------
// Hooks
// ---------------------------------------------------------------------------

export interface EstadoConexion {
  enLinea: boolean
  /** true cuando la caida la provoco el interruptor de demostracion. */
  simulada: boolean
  pendientes: number
}

/**
 * Estado de conexion vivo: reacciona al WiFi real del dispositivo y al
 * interruptor de demostracion accionado desde cualquier pestana.
 */
export function useEstadoConexion(): EstadoConexion {
  const calcular = (): EstadoConexion => {
    const simulada = simulandoSinConexion()
    return {
      simulada,
      enLinea: !simulada && navegadorEnLinea(),
      pendientes: leerCola().length,
    }
  }

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
