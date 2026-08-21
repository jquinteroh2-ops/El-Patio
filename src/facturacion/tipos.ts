/**
 * El documento electrónico, tal como la DIAN lo entiende.
 *
 * Es deliberadamente distinto del modelo de `compartido/tipos.ts`. Aquel
 * describe cómo opera el restaurante: mesas, turnos de envío, comandas. Este
 * describe cómo se le rinde cuentas a la DIAN, y son dos vocabularios que no
 * conviene mezclar. La traducción de uno al otro ocurre en un solo lugar,
 * `documento.ts`, y es lo único que hay que revisar si la norma cambia.
 */

import type { Ambiente, TipoDocumento, TipoIdentificacion } from './catalogos'

// ---------------------------------------------------------------------------
// Las dos partes
// ---------------------------------------------------------------------------

/**
 * Quien vende. Sale del RUT del restaurante, nunca de lo que se vea bonito en
 * el papel: la razón social es la que está inscrita, con NIT y dígito de
 * verificación.
 */
export interface Emisor {
  razonSocial: string
  /** Sin puntos ni guion: así lo quiere el XML. El papel sí lo formatea. */
  nit: string
  /** Dígito de verificación, separado. La DIAN lo pide en su propio campo. */
  digitoVerificacion: string
  direccion: string
  municipio: string
  departamento: string
  /** Código DANE del municipio. Turbaco es 13836. */
  codigoMunicipio: string
  /** Códigos de la casilla 53 del RUT. */
  responsabilidades: string[]
  /** Texto del régimen, para el pie del documento. */
  regimen: string
  correo: string
  telefono: string
}

/**
 * Quien compra.
 *
 * Cuando el cliente no se identifica se usa el consumidor final del catálogo.
 * Esa es la razón por la que casi todos los campos son opcionales: exigirlos
 * obligaría a pedir la cédula en cada almuerzo, y la norma no lo pide.
 */
export interface Adquiriente {
  tipoIdentificacion: TipoIdentificacion
  numeroIdentificacion: string
  /** Nombre completo o razón social, como aparece en el RUT o en la cédula. */
  nombre: string
  /** Solo si el adquiriente es NIT. */
  digitoVerificacion?: string
  /**
   * La DIAN exige enviarle el documento al adquiriente identificado. Sin correo
   * no hay a dónde mandarlo, así que en factura con cliente real es obligatorio.
   */
  correo?: string
  direccion?: string
  municipio?: string
  codigoMunicipio?: string
  telefono?: string
  responsabilidades?: string[]
}

// ---------------------------------------------------------------------------
// Las líneas
// ---------------------------------------------------------------------------

/** Un impuesto sobre una línea, con su base. La DIAN los quiere así, no global. */
export interface TributoLinea {
  /** Código del catálogo: 01 IVA, 04 INC, 03 ICA. */
  codigo: string
  /** Sobre cuánto se calculó. */
  base: number
  /** Tarifa en porcentaje. */
  tarifa: number
  /** Lo que resultó, en pesos. */
  valor: number
}

/**
 * Una línea del documento.
 *
 * Un plato con sus modificadores es UNA línea, no varias: el cliente pidió un
 * lomo término medio, no un lomo y un término medio. Los modificadores entran
 * en la descripción y su precio ya está dentro del valor unitario.
 */
export interface LineaDocumento {
  /** Número de orden dentro del documento, empezando en 1. */
  numero: number
  /** Código del producto en la carta del restaurante. */
  codigo: string
  descripcion: string
  /**
   * Los modificadores escogidos, para imprimirlos debajo del plato.
   *
   * Su precio ya está dentro de `valorUnitario`: aquí van solo como texto, que
   * es lo que le permite al cliente reconocer lo que se comió.
   */
  detalles: string[]
  cantidad: number
  /** Código UN/ECE 20. */
  unidadMedida: string
  /** Precio de uno, con modificadores incluidos y sin impuesto. */
  valorUnitario: number
  /** cantidad × valorUnitario. Base gravable de la línea. */
  valorBruto: number
  tributos: TributoLinea[]
  /** valorBruto + suma de tributos. */
  valorTotal: number
  /**
   * Si esta línea causa impuesto al consumo.
   *
   * El domicilio y los cargos adicionales viajan como líneas para que el
   * documento cuadre, pero no son consumo de alimentos y no se gravan.
   */
  gravada: boolean
}

/** Un impuesto totalizado para todo el documento, agrupado por tarifa. */
export interface ResumenTributo {
  codigo: string
  base: number
  tarifa: number
  valor: number
}

// ---------------------------------------------------------------------------
// La numeración
// ---------------------------------------------------------------------------

/**
 * El rango de numeración que la DIAN autoriza por resolución.
 *
 * No tiene nada que ver con `orden.numero`, que es el consecutivo diario de las
 * comandas y se reinicia cada jornada. Este es continuo, no se salta, no se
 * repite y se acaba: cuando `hasta` se agota hay que pedir otra resolución, y
 * quedarse sin numeración un sábado por la noche significa no poder cobrar.
 */
export interface NumeracionAutorizada {
  /** Número de la resolución que la autoriza. */
  resolucion: string
  /** aaaa-mm-dd */
  fechaResolucion: string
  prefijo: string
  desde: number
  hasta: number
  /** aaaa-mm-dd. Vencida la vigencia, el rango deja de servir aunque sobren números. */
  vigenteHasta: string
  /**
   * La clave técnica que entrega la DIAN al habilitarse. Entra en el cálculo
   * del CUFE y es un secreto: no se muestra ni se imprime nunca.
   */
  claveTecnica: string
}

// ---------------------------------------------------------------------------
// El documento
// ---------------------------------------------------------------------------

/** Dónde va el documento en su camino hacia la DIAN. */
export type EstadoDocumento =
  /** Generado, todavía sin salir. Es el estado de la contingencia. */
  | 'pendiente'
  /** Entregado al proveedor, esperando respuesta. */
  | 'enviado'
  /** La DIAN lo validó. Solo aquí el documento existe para efectos fiscales. */
  | 'aceptado'
  /** La DIAN lo rechazó. Hay que corregir y reemitir. */
  | 'rechazado'

/**
 * El documento electrónico completo.
 *
 * Una vez emitido no se modifica. Corregir una factura ya transmitida se hace
 * con una nota crédito, igual que en contabilidad no se borra un asiento.
 */
export interface DocumentoElectronico {
  id: string
  /**
   * La venta de la que salió.
   *
   * No es adorno ni trazabilidad opcional: es lo que impide que una misma venta
   * termine con dos documentos. Quien emite consulta primero por esta llave, y
   * si ya hay documento lo devuelve en vez de gastar otro consecutivo.
   */
  ordenId: string
  tipo: TipoDocumento
  ambiente: Ambiente

  /**
   * Prefijo y número pegados, sin guion: «FE1042».
   *
   * Se imprime exactamente igual que como entra al código único y como queda
   * en el portal de la DIAN. Un guion de adorno en el papel obliga al cliente
   * —o al auditor— a adivinar que «FE-1042» y «FE1042» son el mismo documento.
   */
  numeroCompleto: string
  prefijo: string
  numero: number

  /** ISO 8601 con la hora del restaurante. */
  emitidoEn: string

  emisor: Emisor
  adquiriente: Adquiriente

  lineas: LineaDocumento[]
  tributos: ResumenTributo[]

  /** Suma de las líneas gravadas y no gravadas, antes de impuestos. */
  subtotal: number
  /** Solo lo que sirve de base al INC. */
  baseGravable: number
  /** Suma de todos los tributos. */
  totalTributos: number
  /**
   * La propina, aparte de todo.
   *
   * No es base gravable, no es ingreso del restaurante y la DIAN la trata como
   * un cargo no gravado. Va identificada para que nadie la confunda con venta.
   */
  propina: number
  /** Lo que el cliente paga. */
  total: number

  formaPago: string
  medioPago: string
  moneda: string

  /** CUFE o CUDE. Vacío mientras el documento no se haya generado de verdad. */
  codigoUnico: string
  /** Contenido del QR: la URL de consulta en el portal de la DIAN. */
  contenidoQr: string

  estado: EstadoDocumento
  /** Lo que respondió la DIAN, para poder explicar un rechazo. */
  respuestaDian?: string

  /** La resolución con que se numeró, para imprimirla al pie. */
  numeracion: NumeracionAutorizada

  /** Si es nota crédito: a qué documento corrige y por qué. */
  documentoReferenciado?: string
  motivo?: string

  /**
   * Verdadero mientras esto no sea un documento fiscal real.
   *
   * Manda sobre la impresión: con esto en `true` el papel sale con una banda
   * que dice que no tiene valor fiscal. Es lo único que impide que una prueba
   * se confunda con una factura, así que no se apaga «para ver cómo queda».
   */
  esPrueba: boolean
}
