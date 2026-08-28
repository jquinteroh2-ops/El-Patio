import { useEffect } from 'react'
import * as api from './mockApi'
import { RESTAURANTE } from './config'
import { useSyncedState } from './useSyncedState'
import type { FichaSitio } from './tipos'

/**
 * A qué horas abrimos y cómo nos encuentran.
 *
 * Esto vivía escrito a mano en `config.ts`. Se movió a la base porque es el
 * dato que más se mueve del sitio —un horario de temporada, un festivo, un
 * número nuevo— y corregirlo no puede costar un despliegue. Lo que queda en
 * `config.ts` son los valores de reserva.
 *
 * Hay dos formas de leerlo y no una por capricho:
 *
 * - `useFichaSitio()` en un componente, que se mantiene al día solo.
 * - `fichaSitio()` en un módulo sin React —los mensajes de WhatsApp, un
 *   comprobante que se imprime—, que devuelve lo último que se supo. Empieza
 *   con los valores de reserva y se llena en el arranque con `precargar()`.
 */

/** Lo que se pinta mientras el servidor contesta, y si nunca contesta. */
export const FICHA_POR_DEFECTO: FichaSitio = {
  direccion: RESTAURANTE.direccion,
  ciudad: RESTAURANTE.ciudad,
  telefono: RESTAURANTE.telefono,
  whatsapp: RESTAURANTE.whatsapp,
  instagram: RESTAURANTE.instagram,
  horario: RESTAURANTE.horario.map((f) => ({ dias: f.dias, horas: f.horas })),
}

let ultima: FichaSitio = FICHA_POR_DEFECTO

/**
 * La última ficha conocida, sin pasar por React.
 *
 * Nunca devuelve nulo ni espera: un mensaje de WhatsApp que se arma mientras la
 * ficha viaja sale con la dirección de reserva, que es la que había antes de
 * que esto existiera. Es preferible a no poder armarlo.
 */
export function fichaSitio(): FichaSitio {
  return ultima
}

/** Se llama una vez al arrancar, para que `fichaSitio()` no salga con lo de reserva. */
export async function precargarFichaSitio(): Promise<void> {
  try {
    ultima = await api.obtenerFichaSitio()
  } catch {
    // Sin ficha se sigue con los valores de reserva: que el sitio arranque
    // importa más que tener el horario al día.
  }
}

/** La ficha al día dentro de un componente. */
export function useFichaSitio(): FichaSitio {
  const { datos } = useSyncedState<FichaSitio>(
    () => api.obtenerFichaSitio(),
    ultima,
    [],
    ['sitio', 'todo'],
  )

  // Lo que ve la pantalla es también lo que verá quien lea `fichaSitio()`: si
  // recepción corrige el horario, el mensaje de WhatsApp que se arme después
  // sale con el nuevo sin recargar nada.
  useEffect(() => {
    ultima = datos
  }, [datos])

  return datos
}

/** El enlace de Instagram, armado con lo que haya guardado. */
export function enlaceInstagram(usuario: string): string {
  return `https://instagram.com/${usuario.replace(/^@/, '')}`
}
