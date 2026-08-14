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

/** Dos notas cortas, audibles por encima del ruido de una cocina. */
export function sonarAviso(): void {
  const ctx = obtenerContexto()
  if (!ctx || ctx.state !== 'running') return

  const inicio = ctx.currentTime
  const notas = [
    { frecuencia: 880, desfase: 0 },
    { frecuencia: 1174, desfase: 0.16 },
  ]

  for (const nota of notas) {
    const oscilador = ctx.createOscillator()
    const volumen = ctx.createGain()

    oscilador.type = 'triangle'
    oscilador.frequency.value = nota.frecuencia

    const t = inicio + nota.desfase
    volumen.gain.setValueAtTime(0, t)
    volumen.gain.linearRampToValueAtTime(0.22, t + 0.012)
    volumen.gain.exponentialRampToValueAtTime(0.0001, t + 0.15)

    oscilador.connect(volumen).connect(ctx.destination)
    oscilador.start(t)
    oscilador.stop(t + 0.18)
  }
}

/**
 * Detecta las comandas que acaban de entrar y devuelve sus claves para que la
 * pantalla las destaque. En el primer render no avisa nada: al abrir la
 * pantalla ya hay comandas en curso y no son novedad.
 */
export function useAvisoNuevaComanda(claves: string[], sonidoActivo: boolean): Set<string> {
  const conocidas = useRef<Set<string> | null>(null)
  const [recientes, setRecientes] = useState<Set<string>>(new Set())
  const firma = claves.join('|')

  useEffect(() => {
    const actuales = new Set(claves)

    if (conocidas.current === null) {
      conocidas.current = actuales
      return
    }

    const nuevas = claves.filter((c) => !conocidas.current!.has(c))
    conocidas.current = actuales
    if (nuevas.length === 0) return

    setRecientes(new Set(nuevas))
    if (sonidoActivo) sonarAviso()

    const id = window.setTimeout(() => setRecientes(new Set()), 6000)
    return () => window.clearTimeout(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [firma, sonidoActivo])

  return recientes
}
