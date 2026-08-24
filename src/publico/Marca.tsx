/**
 * La marca del restaurante: el emblema junto al nombre.
 *
 * El emblema es el arco con la monstera y la orquídea del logo real, recortado
 * sin el texto. El logo completo sí lleva «EL PATIO» escrito, pero aquí el
 * nombre ya va al lado como texto: usar el logo entero pondría el nombre dos
 * veces, una junto a otra.
 *
 * ── Por qué no se usa el archivo tal cual ────────────────────────────────────
 * El logo viene dibujado en negro sobre crema y el sitio es oscuro. Pegado tal
 * cual, es un recuadro claro sobre fondo casi negro: se ve como una foto pegada
 * encima, no como un logo. `scripts/PrepararLogo.java` deriva del original la
 * versión en negativo —trazo claro, sin fondo—, que es la que se usa aquí. El
 * arco flota sobre el encabezado en vez de recortarse contra él.
 *
 * El resplandor dorado detrás del emblema y del nombre es lo que los ata al
 * fondo y hace que se lean como una sola marca.
 *
 * Sigue haciendo falta el archivo VECTORIAL. Este sale de un JPG de 150 px:
 * alcanza para el encabezado y no para nada más grande.
 * ─────────────────────────────────────────────────────────────────────────────
 */

interface Props {
  /** Alto del emblema en píxeles. El disco y el halo se calculan a partir de él. */
  tamano?: number
  className?: string
}

export function Emblema({ tamano = 34, className = '' }: Props) {
  return (
    <span
      className={`relative inline-flex shrink-0 items-center justify-center ${className}`}
      style={{ width: tamano, height: tamano }}
    >
      {/* El halo. Va detrás y desbordando el emblema, con `blur` y sin borde
          duro, para que se lea como luz y no como un segundo elemento. */}
      <span
        aria-hidden
        className="pointer-events-none absolute rounded-full bg-oro-400/30 blur-lg"
        style={{ width: tamano * 1.5, height: tamano * 1.5 }}
      />
      <img
        src="/emblema.png"
        alt=""
        /*
         * `alt` vacío y `aria-hidden`: el nombre «EL PATIO» va como texto justo
         * al lado. Con un alt descriptivo, un lector de pantalla leería «El
         * Patio, El Patio» —el emblema y el texto—, que es el error clásico al
         * poner un logo junto a su propio nombre.
         */
        aria-hidden
        /*
         * `width` y `height` explícitos: sin ellos el navegador no reserva el
         * espacio hasta que la imagen carga, y el encabezado da un salto con
         * la página ya a la vista.
         */
        width={tamano}
        height={tamano}
        /*
         * Sin recuadro, sin borde y sin recorte: el emblema ya viene sin fondo,
         * así que se dibuja directamente sobre el encabezado. Meterlo en un
         * disco o en una caja sería devolverle el marco que se le quitó.
         */
        className="relative"
        style={{ width: tamano, height: tamano }}
      />
    </span>
  )
}

/**
 * El bloque completo del encabezado: emblema y nombre, con el halo detrás.
 *
 * Va junto en un componente porque el resplandor abarca las dos cosas: el
 * usuario lo pidió así y además es lo que hace que se lean como una sola marca
 * y no como una imagen al lado de unas letras.
 */
export function MarcaConNombre({ className = '' }: { className?: string }) {
  return (
    <span className={`relative flex items-center gap-2.5 sm:gap-3 ${className}`}>
      {/* El halo del conjunto: una mancha ancha y muy tenue detrás de todo.
          Es distinta del halo del emblema —aquella es un punto de luz, esta es
          el ambiente— y juntas evitan que el emblema se vea como el único
          elemento iluminado. */}
      <span
        aria-hidden
        className="pointer-events-none absolute -inset-x-3 -inset-y-2 rounded-2xl bg-oro-500/10 blur-lg"
      />
      <Emblema tamano={30} className="sm:hidden" />
      <Emblema tamano={38} className="hidden sm:inline-flex" />
      <span
        className="relative font-marca text-base tracking-[0.3em] sm:text-lg"
        /* Un poco de luz también en las letras, en el mismo dorado. Discreta:
           un texto con demasiada sombra de color deja de leerse como texto. */
        style={{ textShadow: '0 0 14px rgba(212, 178, 85, 0.45)' }}
      >
        EL PATIO
      </span>
    </span>
  )
}
