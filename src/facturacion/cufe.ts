/**
 * El código único del documento: CUFE en la factura, CUDE en lo demás.
 *
 * Es lo que convierte un papel en un documento verificable. Quien escanea el QR
 * llega al portal de la DIAN y encuentra este código; si el que está impreso no
 * coincide con el que se transmitió, el documento no sirve.
 *
 * No es un identificador que uno invente ni un aleatorio: es el resultado de
 * pasar por SHA-384 una cadena con los datos de la venta, en un orden fijo, más
 * una clave técnica que la DIAN entrega solo al habilitarse. Por eso no se
 * puede fabricar un CUFE creíble sin estar habilitado, y por eso mismo este
 * archivo no tiene ningún camino que lo simule en producción.
 */

import { CODIGO_AMBIENTE, TRIBUTO, URL_CONSULTA, type Ambiente } from './catalogos'

/**
 * Colombia no cambia la hora en todo el año, así que el desfase es siempre este
 * y no hay que calcularlo. Si el país adoptara horario de verano, esta línea es
 * lo primero que dejaría de ser cierto.
 */
const DESFASE_COLOMBIA = '-05:00'

/** Las partes de una fecha en la hora de Colombia, sin depender del reloj del equipo. */
function partesEnBogota(fecha: Date): Record<string, string> {
  const formateador = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Bogota',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
  const partes: Record<string, string> = {}
  for (const parte of formateador.formatToParts(fecha)) partes[parte.type] = parte.value
  // A medianoche, `en-CA` devuelve «24» donde la DIAN espera «00».
  if (partes.hour === '24') partes.hour = '00'
  return partes
}

/** aaaa-mm-dd, como lo quiere el XML. */
export function fechaDian(fecha: Date): string {
  const p = partesEnBogota(fecha)
  return `${p.year}-${p.month}-${p.day}`
}

/** hh:mm:ss-05:00, como lo quiere el XML. */
export function horaDian(fecha: Date): string {
  const p = partesEnBogota(fecha)
  return `${p.hour}:${p.minute}:${p.second}${DESFASE_COLOMBIA}`
}

/** Los montos viajan con dos decimales y punto, aunque el peso no tenga centavos. */
const monto = (valor: number): string => valor.toFixed(2)

// ---------------------------------------------------------------------------

/**
 * Todo lo que entra en el cálculo.
 *
 * Las tres casillas de impuesto son fijas y en este orden —IVA, INC, ICA—
 * incluso cuando valen cero: la DIAN no las omite, las declara en cero. Esa es
 * la razón por la que el impuesto al consumo de un restaurante tiene el
 * código 04 y ocupa la segunda casilla.
 */
export interface DatosCodigoUnico {
  /** Prefijo y número pegados, sin guion: «FE1042». */
  numeroCompleto: string
  fecha: Date
  /** Suma de las líneas antes de impuestos. */
  valorBruto: number
  valorIva: number
  valorInc: number
  valorIca: number
  /** Lo que paga el cliente. */
  valorTotal: number
  /** NIT del emisor, sin puntos, sin guion y sin dígito de verificación. */
  nitEmisor: string
  /** Documento del adquiriente. En consumidor final, el valor reservado. */
  documentoAdquiriente: string
  /**
   * La clave técnica de la resolución (para CUFE) o el PIN del software (para
   * CUDE). Es un secreto: no se imprime, no se registra y no sale del servidor.
   */
  claveSecreta: string
  ambiente: Ambiente
}

/**
 * Arma la cadena que se va a resumir.
 *
 * Va aparte del hash a propósito: cuando la DIAN rechaza un documento por CUFE
 * inválido, lo primero que hay que mirar es esta cadena, campo por campo,
 * contra el Anexo Técnico. Poder imprimirla sin calcular el hash ahorra horas.
 *
 * NUNCA la registre en consola tal cual: termina con la clave técnica dentro.
 */
export function cadenaCodigoUnico(datos: DatosCodigoUnico): string {
  return [
    datos.numeroCompleto,
    fechaDian(datos.fecha),
    horaDian(datos.fecha),
    monto(datos.valorBruto),
    TRIBUTO.iva,
    monto(datos.valorIva),
    TRIBUTO.inc,
    monto(datos.valorInc),
    TRIBUTO.ica,
    monto(datos.valorIca),
    monto(datos.valorTotal),
    datos.nitEmisor,
    datos.documentoAdquiriente,
    datos.claveSecreta,
    CODIGO_AMBIENTE[datos.ambiente],
  ].join('')
}

/**
 * Calcula el código único.
 *
 * Usa la criptografía del navegador, que solo está disponible en contextos
 * seguros: https o localhost. En caja eso se cumple siempre; si algún día no,
 * es preferible que esto falle a que salga un documento con un código a medias.
 */
export async function calcularCodigoUnico(datos: DatosCodigoUnico): Promise<string> {
  const cripto = globalThis.crypto?.subtle
  if (!cripto) {
    throw new Error(
      'No hay criptografía disponible: el código único exige https o localhost',
    )
  }

  const bytes = new TextEncoder().encode(cadenaCodigoUnico(datos))
  const resumen = await cripto.digest('SHA-384', bytes)

  return [...new Uint8Array(resumen)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

/**
 * Lo que se codifica en el QR.
 *
 * Es la URL de consulta del portal de la DIAN con el código único. El cliente
 * escanea, abre el portal y ve la misma venta que tiene en la mano: eso es lo
 * que hace verificable el documento y lo que distingue una factura electrónica
 * de un papel con un cuadrito impreso.
 */
export function contenidoQr(codigoUnico: string, ambiente: Ambiente): string {
  return `${URL_CONSULTA[ambiente]}${codigoUnico}`
}
