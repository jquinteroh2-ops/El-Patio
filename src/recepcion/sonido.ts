import { useCallback, useEffect, useSyncExternalStore } from 'react'
import { escribirCrudo, leerCrudo } from '@/compartido/almacen'
import { CLAVE_SONIDO_RECEPCION } from '@/compartido/config'
import { habilitarSonido } from '@/cocina/avisoNuevaComanda'

/**
 * El interruptor de sonido del mostrador.
 *
 * Vive fuera de React y no dentro de una pantalla porque recepcion son dos:
 * pedidos y reservas. Con el estado en cada una, pasar de una pestana a la otra
 * apagaba el aviso sin que nadie lo tocara, y el pedido que entraba mientras
 * tanto no sonaba.
 *
 * Se recuerda en el navegador, pero recordarlo no basta para que suene: el
 * navegador exige un gesto de la persona antes de dejar sonar nada. Al volver a
 * abrir se intenta reanudar, y si el navegador no deja, el boton vuelve a decir
 * «activar sonido» en vez de mentir diciendo que esta encendido.
 */

let activo = false
let restaurado = false
const oyentes = new Set<() => void>()

function avisar(): void {
  for (const oyente of oyentes) oyente()
}

function suscribir(oyente: () => void): () => void {
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}

const leer = (): boolean => activo

/** Devuelve si quedo sonando: falso tambien al apagarlo a proposito. */
async function alternar(): Promise<boolean> {
  if (activo) {
    activo = false
    escribirCrudo(CLAVE_SONIDO_RECEPCION, false)
    avisar()
    return false
  }

  const listo = await habilitarSonido()
  activo = listo
  if (listo) escribirCrudo(CLAVE_SONIDO_RECEPCION, true)
  avisar()
  return listo
}

export interface Sonido {
  activo: boolean
  /**
   * Enciende pidiendo permiso al navegador, o apaga si ya sonaba. Devuelve si
   * quedo sonando; un `false` tras encender es que el navegador no dejo.
   */
  alternar: () => Promise<boolean>
}

export function useSonidoRecepcion(): Sonido {
  const estado = useSyncExternalStore(suscribir, leer, () => false)

  // Un solo intento por carga de pagina, y solo si quedo encendido la vez
  // anterior: crear el contexto de audio sin que nadie lo haya pedido llena la
  // consola de advertencias y no enciende nada.
  useEffect(() => {
    if (restaurado || activo) return
    restaurado = true
    if (!leerCrudo(CLAVE_SONIDO_RECEPCION, false)) return
    void habilitarSonido().then((listo) => {
      if (!listo) return
      activo = true
      avisar()
    })
  }, [])

  return { activo: estado, alternar: useCallback(alternar, []) }
}
