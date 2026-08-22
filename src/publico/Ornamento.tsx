/**
 * Motivo botanico de la casa: un arco de patio con hojas, dibujado en linea.
 *
 * Va en SVG y no en imagen para que cargue sin red, se vea nitido en cualquier
 * pantalla y herede el color del texto que lo rodea.
 */
/**
 * Tamano y color de partida.
 *
 * Un SVG sin alto ni ancho se estira hasta llenar su contenedor. Sin este
 * valor por defecto, olvidar la clase no da un error ni se nota al programar:
 * da un ornamento de pantalla completa que empuja el contenido real fuera de
 * la vista, y parece que la pagina estuviera vacia. Va primero en el atributo
 * para que cualquier clase que llegue lo pueda pisar.
 */
const MEDIDA = 'h-14 w-24 text-oro-400/60'

export function Ornamento({ className = '' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 120 64"
      fill="none"
      stroke="currentColor"
      strokeWidth="1"
      strokeLinecap="round"
      className={`${MEDIDA} ${className}`}
      aria-hidden
    >
      {/* Arco */}
      <path d="M32 62 V34 a28 28 0 0 1 56 0 V62" opacity="0.5" />

      {/* Rama izquierda */}
      <path d="M60 56 V24" />
      <path d="M60 46 C52 44 48 38 47 32 C54 33 59 38 60 46 Z" opacity="0.85" />
      <path d="M60 38 C68 36 72 30 73 24 C66 25 61 30 60 38 Z" opacity="0.85" />
      <path d="M60 30 C52 28 48 22 47 16 C54 17 59 22 60 30 Z" opacity="0.85" />

      {/* Remate */}
      <circle cx="60" cy="20" r="1.6" fill="currentColor" stroke="none" />
    </svg>
  )
}

/** Separador fino entre secciones. */
export function Filete({ className = '' }: { className?: string }) {
  return (
    <span className={`flex items-center gap-3 ${className}`} aria-hidden>
      <span className="h-px flex-1 bg-current opacity-25" />
      <svg viewBox="0 0 24 8" width="24" height="8" fill="none" stroke="currentColor" strokeWidth="1">
        <path d="M0 4 L8 1 L12 4 L16 1 L24 4" />
      </svg>
      <span className="h-px flex-1 bg-current opacity-25" />
    </span>
  )
}
