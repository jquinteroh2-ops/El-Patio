/**
 * La marca gráfica del restaurante, junto al nombre.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ESTE SÍMBOLO ES PROVISIONAL.
 *
 * El restaurante todavía no ha entregado su logo. Lo que hay aquí es el motivo
 * del favicon —una hoja de cuatro pétalos— redibujado en la paleta vigente,
 * para que el encabezado no quede a medias mientras llega el archivo real.
 *
 * Cuando llegue el `.svg` o el `.ai` del restaurante, se reemplaza SOLO el
 * contenido de este componente. El encabezado, el pie y cualquier otro sitio
 * que use la marca no se tocan: por eso vive aquí y no dibujado dentro del
 * `LayoutPublico`.
 *
 * Pídalo en vectorial y en versión que contraste sobre fondo oscuro. No lo
 * recorte de una foto ni de una captura: un logo rasterizado desde una imagen
 * se ve sucio en pantalla grande y no se puede reescalar.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Va en SVG y no en imagen para que cargue sin red, se vea nítido en cualquier
 * pantalla y herede el color del texto que lo rodea. Cuando se reemplace por un
 * mapa de bits, hará falta `srcset` con 1x y 2x, y `width`/`height` explícitos
 * para que la página no dé un salto al cargar.
 */
export function Marca({ className = '' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 32 32"
      fill="none"
      role="img"
      /*
       * Vacío y `aria-hidden` a propósito: el nombre «EL PATIO» va como texto
       * justo al lado. Con un alt descriptivo, un lector de pantalla leería
       * «El Patio, El Patio» —el símbolo y el texto—, que es exactamente el
       * error que se comete al poner un logo junto a su propio nombre.
       */
      aria-hidden
      className={`h-7 w-7 shrink-0 text-oro-400 ${className}`}
    >
      <path
        d="M16 3c1.4 4.6 4.1 7.6 8.4 9.3-4.3 1.7-7 4.7-8.4 9.3-1.4-4.6-4.1-7.6-8.4-9.3C11.9 10.6 14.6 7.6 16 3Z"
        fill="currentColor"
      />
      <path
        d="M16 22.5v6.5M11.5 27h9"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
        opacity="0.55"
      />
    </svg>
  )
}
