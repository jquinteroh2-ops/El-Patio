import { RESTAURANTE } from '@/compartido/config'
import type { UbicacionEntrega } from '@/compartido/tipos'

/**
 * Ubicación del cliente para la entrega.
 *
 * En Turbaco las direcciones escritas fallan seguido —«casa esquinera»,
 * «portón verde», calles sin nomenclatura— y el domiciliario termina llamando
 * para que lo guíen. Con el permiso del cliente, el navegador entrega el punto
 * y quien lleva el pedido abre Waze o Google Maps directamente ahí.
 *
 * Tres reglas que no se negocian:
 *
 *  1. Es opcional. Si el cliente niega el permiso, el pedido entra igual.
 *  2. Se pide con un toque explícito, nunca al abrir la página.
 *  3. La dirección escrita sigue siendo la que manda. La coordenada ayuda.
 */

/** Por encima de esto la coordenada ubica un barrio, no una casa. */
export const PRECISION_ACEPTABLE_METROS = 100

export type ResultadoUbicacion =
  | { estado: 'lista'; ubicacion: UbicacionEntrega }
  | { estado: 'negada' }
  | { estado: 'sin_soporte' }
  | { estado: 'fallo'; mensaje: string }

/**
 * Pide la ubicación al navegador.
 *
 * `enableHighAccuracy` enciende el GPS del celular en vez de conformarse con la
 * posición de la red, que en un pueblo puede errar por kilómetros. Cuesta unos
 * segundos y algo de batería, y para esto vale la pena.
 *
 * El tiempo de espera es generoso porque un GPS bajo techo tarda: mejor esperar
 * doce segundos que devolver una posición mala.
 */
export async function pedirUbicacion(): Promise<ResultadoUbicacion> {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    return { estado: 'sin_soporte' }
  }

  return new Promise((resolver) => {
    navigator.geolocation.getCurrentPosition(
      (posicion) =>
        resolver({
          estado: 'lista',
          ubicacion: {
            latitud: posicion.coords.latitude,
            longitud: posicion.coords.longitude,
            precisionMetros: Number.isFinite(posicion.coords.accuracy)
              ? Math.round(posicion.coords.accuracy)
              : undefined,
          },
        }),
      (error) => {
        if (error.code === error.PERMISSION_DENIED) {
          resolver({ estado: 'negada' })
          return
        }
        resolver({
          estado: 'fallo',
          mensaje:
            error.code === error.TIMEOUT
              ? 'La señal del GPS tardó demasiado. Puede intentarlo de nuevo o seguir sin ubicación.'
              : 'No se pudo obtener la ubicación. Puede seguir sin ella.',
        })
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 },
    )
  })
}

// ---------------------------------------------------------------------------
// Enlaces a mapas
// ---------------------------------------------------------------------------

/**
 * Enlace al punto exacto.
 *
 * Se usa el esquema universal de Google Maps y no el de la aplicación nativa:
 * funciona igual en Android, en iPhone y en un computador de escritorio, que es
 * lo que hace falta cuando el domiciliario usa su propio teléfono.
 */
export function enlaceMapa(ubicacion: UbicacionEntrega): string {
  return `https://www.google.com/maps/search/?api=1&query=${ubicacion.latitud},${ubicacion.longitud}`
}

/** Waze, que es lo que de verdad usa un domiciliario para navegar. */
export function enlaceWaze(ubicacion: UbicacionEntrega): string {
  return `https://waze.com/ul?ll=${ubicacion.latitud},${ubicacion.longitud}&navigate=yes`
}

/**
 * Respaldo cuando no hay coordenadas: buscar la dirección escrita.
 *
 * Es notablemente menos preciso —depende de que la calle esté bien mapeada—,
 * pero es mejor que nada y no necesita permiso de nadie.
 */
export function enlaceMapaPorDireccion(direccion: string, barrio?: string): string {
  const consulta = [direccion, barrio, RESTAURANTE.ciudad].filter(Boolean).join(', ')
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(consulta)}`
}

/** «a 1,2 km» o «a 400 m», para decir de un vistazo qué tan lejos pidió. */
export function distanciaLegible(metros: number): string {
  if (metros < 1000) return `a ${Math.round(metros / 10) * 10} m`
  return `a ${(metros / 1000).toFixed(1).replace('.', ',')} km`
}
