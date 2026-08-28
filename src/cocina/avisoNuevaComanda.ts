import { useEffect, useRef, useState } from 'react'

/**
 * Aviso de comanda nueva.
 *
 * El tono se sintetiza en el navegador: sin archivos, sin descargas y sin
 * depender de la red, que es justo lo que necesita una pantalla colgada en la
 * cocina de un restaurante con WiFi flojo.
 */

let contexto: AudioContext | null = null

type Constructor = typeof AudioContext

function obtenerContexto(): AudioContext | null {
  if (typeof window === 'undefined') return null
  const Clase: Constructor | undefined =
    window.AudioContext ?? (window as unknown as { webkitAudioContext?: Constructor }).webkitAudioContext
  if (!Clase) return null
  contexto ??= new Clase()
  return contexto
}

/**
 * Los navegadores no dejan sonar nada hasta que el usuario toca la pantalla.
 * Esto se llama desde el boton de sonido, que sirve de permiso.
 */
export async function habilitarSonido(): Promise<boolean> {
  const ctx = obtenerContexto()
  if (!ctx) return false
  if (ctx.state === 'suspended') await ctx.resume()
  return ctx.state === 'running'
}

/**
 * Los dos avisos del sistema.
 *
 * `entrada` es algo que ya esta en marcha y espera: una comanda en la plancha,
 * un domicilio que hay que aceptar. Sube y corta seco, para que se oiga por
 * encima del ruido de una cocina.
 *
 * `reserva` es una solicitud para otro dia: importa, pero no corre. Baja, es
 * mas suave y dura mas, que es lo que la distingue sin tener que mirar la
 * pantalla. En un mostrador que atiende las dos cosas, un solo tono para ambas
 * obliga a levantar la vista cada vez.
 */
type Tono = {
  notas: { frecuencia: number; desfase: number }[]
  forma: OscillatorType
  volumen: number
  caida: number
}

const TONOS = {
  entrada: {
    notas: [
      { frecuencia: 880, desfase: 0 },
      { frecuencia: 1174, desfase: 0.16 },
    ],
    forma: 'triangle',
    volumen: 0.22,
    caida: 0.15,
  },
  reserva: {
    notas: [
      { frecuencia: 659, desfase: 0 },
      { frecuencia: 523, desfase: 0.18 },
    ],
    forma: 'sine',
    volumen: 0.18,
    caida: 0.3,
  },
} satisfies Record<string, Tono>

export type NombreTono = keyof typeof TONOS

/** Dos notas cortas, audibles por encima del ruido de una cocina. */
export function sonarAviso(nombre: NombreTono = 'entrada'): void {
  const ctx = obtenerContexto()
  if (!ctx || ctx.state !== 'running') return

  const tono: Tono = TONOS[nombre]
  const inicio = ctx.currentTime

  for (const nota of tono.notas) {
    const oscilador = ctx.createOscillator()
    const volumen = ctx.createGain()

    oscilador.type = tono.forma
    oscilador.frequency.value = nota.frecuencia

    const t = inicio + nota.desfase
    volumen.gain.setValueAtTime(0, t)
    volumen.gain.linearRampToValueAtTime(tono.volumen, t + 0.012)
    volumen.gain.exponentialRampToValueAtTime(0.0001, t + tono.caida)

    oscilador.connect(volumen).connect(ctx.destination)
    oscilador.start(t)
    oscilador.stop(t + tono.caida + 0.03)
  }
}

/**
 * Detecta las comandas que acaban de entrar y devuelve sus claves para que la
 * pantalla las destaque. En el primer render no avisa nada: al abrir la
 * pantalla ya hay comandas en curso y no son novedad.
 *
 * `listo` es lo que separa «todavia no han llegado los datos» de «no hay
 * ninguna». Sin el, la lista vacia del primer render pasa por punto de partida
 * y todo lo que devuelve el servidor un instante despues parece recien
 * llegado: la pantalla se abriria sonando una vez por cada comanda en curso.
 */
export function useAvisoNuevaComanda(
  claves: string[],
  sonidoActivo: boolean,
  tono: NombreTono = 'entrada',
  listo = true,
): Set<string> {
  const conocidas = useRef<Set<string> | null>(null)
  const [recientes, setRecientes] = useState<Set<string>>(new Set())
  const firma = claves.join('|')

  useEffect(() => {
    if (!listo) return
    const actuales = new Set(claves)

    if (conocidas.current === null) {
      conocidas.current = actuales
      return
    }

    const nuevas = claves.filter((c) => !conocidas.current!.has(c))
    conocidas.current = actuales
    if (nuevas.length === 0) return

    setRecientes(new Set(nuevas))
    if (sonidoActivo) sonarAviso(tono)

    const id = window.setTimeout(() => setRecientes(new Set()), 6000)
    return () => window.clearTimeout(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [firma, sonidoActivo, tono, listo])

  return recientes
}
