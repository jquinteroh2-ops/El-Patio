import { Loader2 } from 'lucide-react'
import { Emblema } from '@/publico/Marca'

interface Props {
  mensaje?: string
  pantallaCompleta?: boolean
}

/**
 * Frases del arranque.
 *
 * Se escoge una al azar en cada apertura, y no se van rotando mientras carga:
 * el arranque dura menos de un segundo cuando el servidor está despierto, y un
 * texto que cambia solo en ese rato se lee como un parpadeo. El azar da la
 * variedad; la duración manda que sea una sola.
 *
 * Todas hablan de lo mismo —abrir el local— y ninguna promete un tiempo. «Casi
 * listo» envejece mal a los diez segundos de un servidor dormido.
 */
const FRASES = [
  'Encendiendo las velas',
  'Poniendo los manteles',
  'Puliendo las copas',
  'Abriendo el salón',
  'Sacando la vajilla',
  'Enfriando la barra',
] as const

/** Una frase distinta en cada apertura, elegida al montar y no en cada pintada. */
function fraseDeLaCasa(): string {
  return FRASES[Math.floor(Math.random() * FRASES.length)]
}

/**
 * Lo que se ve mientras algo carga.
 *
 * Dos formas muy distintas según dónde va:
 *
 * **En pantalla completa** es la puerta del sistema: lo primero que ve quien
 * abre la aplicación, antes de que exista ninguna pantalla. Ahí se firma con el
 * emblema de la casa y una frase, porque es el único momento en que la marca
 * aparece sola y en grande.
 *
 * **Dentro de una pantalla** es un rueda pequeña y muda. Donde de verdad se
 * espera por datos casi siempre hay algo mejor —un esqueleto con la forma de lo
 * que viene, en `Esqueleto.tsx`— y esto queda para los sitios donde la forma de
 * lo que llega no se sabe de antemano.
 */
export function Cargando({ mensaje, pantallaCompleta = false }: Props) {
  if (!pantallaCompleta) {
    return (
      <div className="flex justify-center py-10" role="status" aria-live="polite">
        <div className="flex flex-col items-center gap-3 text-noche-400">
          <Loader2 className="h-6 w-6 animate-spin" aria-hidden />
          <span className="text-sm">{mensaje ?? 'Cargando'}</span>
        </div>
      </div>
    )
  }

  return <PantallaDeArranque mensaje={mensaje} />
}

/**
 * La puerta: emblema, nombre y una barra que va y viene.
 *
 * La barra NO mide progreso, y por eso va y viene en vez de llenarse. Nadie
 * sabe cuánto falta —depende de una red y de un servidor que puede estar
 * despertando— y una barra que se llena hasta el borde y ahí se queda quieta
 * miente sobre lo que va a pasar en el segundo siguiente.
 */
function PantallaDeArranque({ mensaje }: { mensaje?: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex min-h-dvh flex-col items-center justify-center gap-6 bg-onix-950 px-6"
    >
      {/* El emblema respira. Es lo que dice «esto sigue vivo» sin una rueda
          girando, que es el gesto de cualquier página y no el de esta casa. */}
      <div className="relative flex items-center justify-center">
        <span
          aria-hidden
          className="pointer-events-none absolute h-32 w-32 rounded-full bg-oro-500/15 blur-2xl"
        />
        <Emblema tamano={76} className="animate-respirar" />
      </div>

      <div className="flex flex-col items-center gap-1.5">
        <span className="font-marca text-lg tracking-[0.3em] text-crema-100">EL PATIO</span>
      </div>

      {/* La barra. `overflow-hidden` en el carril y la luz corriéndose dentro:
          así el barrido entra y sale por los bordes en vez de aparecer de la
          nada en el medio. */}
      <div className="h-px w-44 overflow-hidden rounded-full bg-oro-500/15">
        <div className="h-full w-1/3 animate-vaiven rounded-full bg-oro-400/70" aria-hidden />
      </div>

      <span className="font-titulo text-base italic text-crema-100/60">
        {mensaje ?? fraseDeLaCasa()}
      </span>
    </div>
  )
}
