import { create } from 'qrcode'

/**
 * El código QR del documento electrónico.
 *
 * Sale como SVG y no como imagen a propósito. Una térmica de 203 puntos por
 * pulgada convierte cualquier gris en blanco o negro sin avisar, y un PNG
 * escalado pierde el borde de los módulos justo lo suficiente para que el
 * lector del celular no lo enganche. Un SVG de rectángulos enteros imprime
 * cuadrado y nítido a cualquier tamaño.
 *
 * Se genera en el momento de pintar, sin pasos asíncronos: la impresión ocurre
 * apenas React monta el documento, y un QR que llega un instante tarde sale en
 * un papel en blanco.
 */

interface Props {
  /** La URL de consulta en el portal de la DIAN. */
  contenido: string
  /** Lado del cuadro impreso. 25 mm es lo que un celular lee sin acercarse. */
  ladoMm?: number
}

/**
 * Margen obligatorio alrededor del código, medido en módulos.
 *
 * El estándar exige cuatro y no es decorativo: sin esa zona en blanco el lector
 * no distingue dónde empieza el código, y pegado al texto del ticket deja de
 * leerse. Es el error más común al imprimir QR en tiquetes angostos.
 */
const MARGEN_MODULOS = 4

export function CodigoQr({ contenido, ladoMm = 25 }: Props) {
  // Corrección media: aguanta que se pierda cerca de un 15 % del código. En un
  // rollo térmico eso es lo que se gasta en un mes dentro de un bolsillo.
  const qr = create(contenido, { errorCorrectionLevel: 'M' })
  const lado = qr.modules.size
  const total = lado + MARGEN_MODULOS * 2

  // Un solo `path` con todos los módulos oscuros. Miles de `rect` sueltos
  // hacen que el navegador tarde mucho más en pintar, y aquí se pinta contra
  // el reloj de la impresión.
  let trazo = ''
  for (let fila = 0; fila < lado; fila++) {
    for (let columna = 0; columna < lado; columna++) {
      if (qr.modules.data[fila * lado + columna]) {
        trazo += `M${columna + MARGEN_MODULOS} ${fila + MARGEN_MODULOS}h1v1h-1z`
      }
    }
  }

  return (
    <svg
      className="ticket-qr"
      viewBox={`0 0 ${total} ${total}`}
      width={`${ladoMm}mm`}
      height={`${ladoMm}mm`}
      /* Sin esto el navegador suaviza los bordes y el lector falla. */
      shapeRendering="crispEdges"
      role="img"
      aria-label="Código QR para consultar el documento en el portal de la DIAN"
    >
      <rect width={total} height={total} fill="#fff" />
      <path d={trazo} fill="#000" />
    </svg>
  )
}
