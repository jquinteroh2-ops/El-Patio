/**
 * Catálogos de la DIAN.
 *
 * Esto es, literalmente, «el formato que exige la DIAN»: la entidad no acepta
 * los nombres que usa el restaurante sino códigos de sus propias tablas. Un
 * pago en efectivo no es «efectivo», es el medio de pago 10; el impuesto al
 * consumo no es «INC», es el tributo 04.
 *
 * Los códigos salen del Anexo Técnico de Factura Electrónica de Venta.
 *
 * ADVERTENCIA: el Anexo Técnico tiene versiones y la DIAN las cambia. Antes de
 * salir a producción, confirme cada tabla de este archivo contra la versión
 * vigente y contra lo que documente el proveedor tecnológico que se contrate.
 * Están aquí, juntos y en un solo archivo, precisamente para que esa revisión
 * sea leer una página y no rastrear constantes por todo el código.
 */

import type { MetodoPago } from '@/compartido/tipos'

// ---------------------------------------------------------------------------
// Ambiente
// ---------------------------------------------------------------------------

/**
 * Los dos mundos de la DIAN, que no se tocan.
 *
 * En «pruebas» (habilitación) se emite contra un ambiente de ensayo: los
 * documentos existen, tienen código único y se pueden consultar, pero no tienen
 * efecto fiscal. En «produccion» cada documento emitido es una venta declarada.
 */
export type Ambiente = 'pruebas' | 'produccion'

/** Código de ambiente que viaja dentro del CUFE. No es decorativo: lo cambia. */
export const CODIGO_AMBIENTE: Record<Ambiente, string> = {
  pruebas: '2',
  produccion: '1',
}

/** Dónde consulta el cliente el documento al escanear el QR. */
export const URL_CONSULTA: Record<Ambiente, string> = {
  pruebas: 'https://catalogo-vpfe-hab.dian.gov.co/document/searchqr?documentkey=',
  produccion: 'https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=',
}

// ---------------------------------------------------------------------------
// Tipo de documento
// ---------------------------------------------------------------------------

/**
 * Qué documento se está emitiendo.
 *
 * El Patio necesita los tres. «factura» cuando el cliente se identifica o la
 * cuenta supera el tope legal del tiquete; «tiquete_pos» para el consumo que sí
 * cabe en el documento equivalente; «nota_credito» para anular una venta ya
 * emitida, porque una factura transmitida a la DIAN no se borra: se
 * contra-documenta.
 */
export type TipoDocumento = 'factura' | 'tiquete_pos' | 'nota_credito'

export const CODIGO_TIPO_DOCUMENTO: Record<TipoDocumento, string> = {
  factura: '01',
  tiquete_pos: '20',
  nota_credito: '91',
}

/** Cómo se titula el documento en el papel. La DIAN exige la denominación. */
export const DENOMINACION: Record<TipoDocumento, string> = {
  factura: 'Factura electrónica de venta',
  tiquete_pos: 'Documento equivalente electrónico · Tiquete P.O.S.',
  nota_credito: 'Nota crédito electrónica',
}

/**
 * Cómo se llama el código único según el documento.
 *
 * Es el mismo algoritmo con distinta entrada, pero el nombre impreso cambia y
 * la DIAN revisa que sea el correcto: CUFE en la factura, CUDE en lo demás.
 */
export const NOMBRE_CODIGO_UNICO: Record<TipoDocumento, 'CUFE' | 'CUDE'> = {
  factura: 'CUFE',
  tiquete_pos: 'CUDE',
  nota_credito: 'CUDE',
}

// ---------------------------------------------------------------------------
// Tipo de documento de identidad del adquiriente
// ---------------------------------------------------------------------------

export type TipoIdentificacion = 'CC' | 'NIT' | 'CE' | 'PASAPORTE' | 'TI' | 'PEP' | 'EXTRANJERO'

/** Tabla de tipos de documento de identidad del Anexo Técnico. */
export const CODIGO_IDENTIFICACION: Record<TipoIdentificacion, string> = {
  TI: '12',
  CC: '13',
  CE: '22',
  NIT: '31',
  PASAPORTE: '41',
  EXTRANJERO: '42',
  PEP: '47',
}

/** Cómo se abrevia en el papel, que es angosto. */
export const ETIQUETA_IDENTIFICACION: Record<TipoIdentificacion, string> = {
  TI: 'T.I.',
  CC: 'C.C.',
  CE: 'C.E.',
  NIT: 'NIT',
  PASAPORTE: 'Pasaporte',
  EXTRANJERO: 'Doc. extranjero',
  PEP: 'PEP',
}

/**
 * El cliente que no se identifica.
 *
 * La DIAN previó el caso: quien entra a almorzar, paga y se va no tiene por qué
 * dar la cédula. Se le emite igual, a nombre de este adquiriente genérico, que
 * es un valor reservado por la entidad y no un relleno inventado.
 */
export const CONSUMIDOR_FINAL = {
  tipo: 'CC' as TipoIdentificacion,
  numero: '222222222222',
  nombre: 'CONSUMIDOR FINAL',
} as const

// ---------------------------------------------------------------------------
// Forma y medio de pago
// ---------------------------------------------------------------------------

/**
 * Forma de pago: 1 contado, 2 crédito.
 *
 * En un restaurante siempre es contado. Se deja explícito porque el XML lo
 * exige y porque un día puede haber cuenta abierta a una empresa.
 */
export const FORMA_PAGO_CONTADO = '1'

/**
 * Medios de pago. La tabla de la DIAN es la UN/ECE 4461, que es más fina de lo
 * que la caja distingue hoy.
 *
 * OJO CON «tarjeta»: la DIAN separa crédito (48) de débito (49) y el sistema no
 * lo pregunta. Aquí va crédito por ser el caso más común, pero si el contador
 * quiere el desglose real hay que agregar esa pregunta en la pantalla de cobro;
 * no se arregla en este archivo.
 */
export const CODIGO_MEDIO_PAGO: Record<Exclude<MetodoPago, 'mixto'>, string> = {
  efectivo: '10',
  tarjeta: '48',
  transferencia: '42',
}

/** Cuando la cuenta se pagó con varios medios y no hay uno solo que la describa. */
export const MEDIO_PAGO_NO_DEFINIDO = '1'

export const NOMBRE_MEDIO_PAGO: Record<MetodoPago, string> = {
  efectivo: 'Efectivo',
  tarjeta: 'Tarjeta',
  transferencia: 'Transferencia',
  mixto: 'Pago mixto',
}

// ---------------------------------------------------------------------------
// Tributos
// ---------------------------------------------------------------------------

/**
 * Códigos de tributo.
 *
 * Los tres no son una elección de diseño: son las tres casillas fijas que el
 * algoritmo del CUFE reserva, en este orden, para IVA, INC e ICA. Por eso el
 * impuesto al consumo de un restaurante es 04 y no 02.
 */
export const TRIBUTO = {
  iva: '01',
  inc: '04',
  ica: '03',
} as const

export const NOMBRE_TRIBUTO: Record<string, string> = {
  '01': 'IVA',
  '04': 'INC',
  '03': 'ICA',
}

/**
 * Unidad de medida de cada línea, según la recomendación UN/ECE 20.
 *
 * 94 es la unidad genérica y sirve para un plato o un cóctel: nadie factura un
 * lomo en gramos. Se deja como constante porque algunos proveedores esperan
 * C62 para lo mismo; es el primer valor que hay que confirmar al integrar.
 */
export const UNIDAD_MEDIDA = '94'

/**
 * Tipo de operación. 10 es la venta estándar, que es todo lo que hace un
 * restaurante: no hay AIU, ni mandato, ni exportación.
 */
export const TIPO_OPERACION = '10'

/** Moneda, en ISO 4217. */
export const MONEDA = 'COP'

// ---------------------------------------------------------------------------
// Responsabilidades fiscales
// ---------------------------------------------------------------------------

/**
 * Códigos de responsabilidad fiscal del RUT.
 *
 * NO se escogen leyendo esta lista: se copian de la casilla 53 del RUT del
 * restaurante. Poner uno que no está en el RUT es declarar algo falso.
 */
export const RESPONSABILIDAD_FISCAL: Record<string, string> = {
  'O-13': 'Gran contribuyente',
  'O-15': 'Autorretenedor',
  'O-23': 'Agente de retención IVA',
  'O-47': 'Régimen simple de tributación',
  'R-99-PN': 'No responsable',
}
