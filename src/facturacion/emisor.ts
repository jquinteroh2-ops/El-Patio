/**
 * Quien emite el documento electrónico.
 *
 * Es un puerto, igual que `Impresora`: las pantallas piden «emite esta venta» y
 * no saben si detrás hay un proveedor tecnológico, la DIAN directamente o nada.
 * Eso es lo que permite construir y probar todo el formato hoy, sin proveedor
 * contratado, y enchufar el real el día que se firme sin tocar la caja.
 *
 * Hoy hay una sola implementación, `EmisorDePruebas`, y es honesta sobre lo que
 * es: arma el documento completo y correcto, pero no lo transmite a nadie y lo
 * marca como prueba. El día que entre `EmisorFactus` —o el proveedor que sea—
 * lo único que cambia es la línea del final de este archivo.
 */

import type { Cuenta } from '@/compartido/calculos'
import {
  AMBIENTE_DIAN,
  DATOS_FISCALES,
  NUMERACION_DIAN,
  TIPO_DOCUMENTO_VENTA,
  facturacionHabilitada,
} from '@/compartido/config'
import type { MetodoPago, Orden } from '@/compartido/tipos'
import {
  CONSUMIDOR_FINAL,
  FORMA_PAGO_CONTADO,
  MONEDA,
  TRIBUTO,
  type TipoDocumento,
} from './catalogos'
import { calcularCodigoUnico, contenidoQr } from './cufe'
import { codigoMedioPago, construirLineas, cuadra, resumirTributos } from './documento'
import type {
  Adquiriente,
  DocumentoElectronico,
  Emisor,
  NumeracionAutorizada,
} from './tipos'

// ---------------------------------------------------------------------------

export interface PeticionEmision {
  orden: Orden
  cuenta: Cuenta
  metodo: MetodoPago
  /** Si el cliente se identificó. Si no, se emite a consumidor final. */
  adquiriente?: Adquiriente
  /** Por defecto, el que diga la configuración. */
  tipo?: TipoDocumento
}

export interface EmisorDocumentos {
  emitir(peticion: PeticionEmision): Promise<DocumentoElectronico>
}

// ---------------------------------------------------------------------------
// El emisor del restaurante, armado desde el RUT
// ---------------------------------------------------------------------------

function emisorDelRestaurante(): Emisor {
  return {
    razonSocial: DATOS_FISCALES.razonSocial,
    nit: DATOS_FISCALES.nit,
    digitoVerificacion: DATOS_FISCALES.digitoVerificacion,
    direccion: DATOS_FISCALES.direccion,
    municipio: DATOS_FISCALES.municipio,
    departamento: DATOS_FISCALES.departamento,
    codigoMunicipio: DATOS_FISCALES.codigoMunicipio,
    responsabilidades: [...DATOS_FISCALES.responsabilidades],
    regimen: DATOS_FISCALES.regimen,
    correo: DATOS_FISCALES.correo,
    telefono: '',
  }
}

/**
 * La clave técnica que se usa mientras no haya una real.
 *
 * Está escrita para que sea imposible confundirla con la de la DIAN: si algún
 * día aparece este texto en un documento de producción, el error salta a la
 * vista en vez de esconderse dentro de un hash que parece válido.
 */
const CLAVE_DE_PRUEBA = 'CLAVE-TECNICA-DE-PRUEBA-SIN-VALOR-FISCAL'

/** Prefijo de los documentos que no son fiscales. No se parece a uno real. */
const PREFIJO_DE_PRUEBA = 'PRU'

/** Dónde lleva la cuenta el emisor de pruebas, que no tiene servidor detrás. */
const LLAVE_CONSECUTIVO = 'elpatio.facturacion.consecutivo.pruebas'

/** Los documentos ya emitidos, por orden, para no emitir dos veces la misma venta. */
const LLAVE_DOCUMENTOS = 'elpatio.facturacion.documentos.pruebas'

// ---------------------------------------------------------------------------
// Emisor de pruebas
// ---------------------------------------------------------------------------

/**
 * Arma el documento completo sin transmitirlo.
 *
 * Sirve para dos cosas y ninguna es fingir: ver el formato real impreso antes
 * de contratar proveedor, y tener contra qué probar las pantallas de caja. Todo
 * lo que produce sale con `esPrueba` en verdadero.
 *
 * El consecutivo vive en el navegador porque este emisor no tiene servidor
 * detrás. Eso es aceptable en pruebas y NO lo es en producción: la numeración
 * fiscal no puede depender de un `localStorage` que el cajero puede borrar. El
 * emisor real la pide al backend, bajo el mismo bloqueo de fila que ya usa el
 * consecutivo diario de las comandas.
 */
export class EmisorDePruebas implements EmisorDocumentos {
  async emitir({ orden, cuenta, metodo, adquiriente, tipo }: PeticionEmision): Promise<DocumentoElectronico> {
    // Una venta, un documento. Recepción puede volver a darle a «Imprimir»
    // cuantas veces quiera —el papel se atasca, sale en blanco, el cliente pide
    // copia— y ninguna de esas veces es una venta nueva. Sin esto, cada clic
    // gastaría un consecutivo y dejaría dos documentos declarando el mismo
    // pedido, que es exactamente lo que la DIAN cobra caro.
    const yaEmitido = this.buscarEmitido(orden.id)
    if (yaEmitido) return yaEmitido

    const tipoDocumento = tipo ?? TIPO_DOCUMENTO_VENTA
    const emitidoEn = new Date()
    const numero = this.siguienteNumero()
    const prefijo = PREFIJO_DE_PRUEBA
    const numeroCompleto = `${prefijo}${numero}`

    const lineas = construirLineas(orden, cuenta)
    const tributos = resumirTributos(lineas)

    const subtotal = lineas.reduce((s, l) => s + l.valorBruto, 0)
    const baseGravable = lineas.filter((l) => l.gravada).reduce((s, l) => s + l.valorBruto, 0)
    const totalTributos = tributos.reduce((s, t) => s + t.valor, 0)
    const total = subtotal + totalTributos + cuenta.propina

    // Antes de emitir nada se comprueba que las partes sumen. Un documento que
    // no cuadra lo rechaza la DIAN, y para entonces el cliente ya se fue.
    if (!cuadra(lineas, tributos, cuenta.propina, cuenta.total)) {
      throw new Error(
        `El documento no cuadra: las lineas suman ${total} y la cuenta dice ${cuenta.total}`,
      )
    }

    const cliente: Adquiriente = adquiriente ?? {
      tipoIdentificacion: CONSUMIDOR_FINAL.tipo,
      numeroIdentificacion: CONSUMIDOR_FINAL.numero,
      nombre: CONSUMIDOR_FINAL.nombre,
    }

    const codigoUnico = await calcularCodigoUnico({
      numeroCompleto,
      fecha: emitidoEn,
      valorBruto: subtotal,
      valorIva: 0,
      valorInc: tributos.find((t) => t.codigo === TRIBUTO.inc)?.valor ?? 0,
      valorIca: 0,
      valorTotal: total,
      nitEmisor: DATOS_FISCALES.nit,
      documentoAdquiriente: cliente.numeroIdentificacion,
      claveSecreta: CLAVE_DE_PRUEBA,
      ambiente: AMBIENTE_DIAN,
    })

    const documento: DocumentoElectronico = {
      id: crypto.randomUUID(),
      ordenId: orden.id,
      tipo: tipoDocumento,
      ambiente: AMBIENTE_DIAN,
      numeroCompleto,
      prefijo,
      numero,
      emitidoEn: emitidoEn.toISOString(),
      emisor: emisorDelRestaurante(),
      adquiriente: cliente,
      lineas,
      tributos,
      subtotal,
      baseGravable,
      totalTributos,
      propina: cuenta.propina,
      total,
      formaPago: FORMA_PAGO_CONTADO,
      medioPago: codigoMedioPago(metodo),
      moneda: MONEDA,
      codigoUnico,
      contenidoQr: contenidoQr(codigoUnico, AMBIENTE_DIAN),
      estado: 'pendiente',
      numeracion: this.numeracionDePruebas(),
      // Y aquí está la línea que impide que una prueba se confunda con una
      // venta declarada. No se toca a mano: depende del trámite, no del ánimo.
      esPrueba: !facturacionHabilitada(),
    }

    this.guardarEmitido(documento)
    return documento
  }

  /**
   * El documento que ya se emitió para esta venta, si lo hay.
   *
   * Vive en el navegador porque este emisor no tiene servidor. Eso alcanza para
   * que reimprimir no emita de nuevo, y NO alcanza para producción: si el
   * cajero borra los datos del navegador o abre la caja en otro equipo, aquí no
   * hay memoria de nada. El emisor real le pregunta esto al backend, que es
   * quien tiene una sola respuesta para todos los puestos.
   */
  private buscarEmitido(ordenId: string): DocumentoElectronico | null {
    try {
      const guardados = JSON.parse(localStorage.getItem(LLAVE_DOCUMENTOS) ?? '{}')
      return (guardados as Record<string, DocumentoElectronico>)[ordenId] ?? null
    } catch {
      // Un almacenamiento corrupto no puede impedir cobrar: se emite de nuevo.
      return null
    }
  }

  private guardarEmitido(documento: DocumentoElectronico): void {
    try {
      const guardados = JSON.parse(localStorage.getItem(LLAVE_DOCUMENTOS) ?? '{}')
      const mapa = guardados as Record<string, DocumentoElectronico>
      mapa[documento.ordenId] = documento
      localStorage.setItem(LLAVE_DOCUMENTOS, JSON.stringify(mapa))
    } catch {
      // Si no se pudo guardar, el documento ya existe y sale impreso igual. Lo
      // que se pierde es la protección contra reimprimir, no la venta.
    }
  }

  private siguienteNumero(): number {
    const actual = Number(localStorage.getItem(LLAVE_CONSECUTIVO) ?? '0')
    const siguiente = Number.isFinite(actual) && actual > 0 ? actual + 1 : 1
    localStorage.setItem(LLAVE_CONSECUTIVO, String(siguiente))
    return siguiente
  }

  private numeracionDePruebas(): NumeracionAutorizada {
    return {
      resolucion: NUMERACION_DIAN.resolucion,
      fechaResolucion: NUMERACION_DIAN.fechaResolucion,
      prefijo: PREFIJO_DE_PRUEBA,
      desde: 1,
      hasta: 0,
      vigenteHasta: NUMERACION_DIAN.vigenteHasta,
      claveTecnica: '',
    }
  }
}

/**
 * El emisor en uso.
 *
 * Es el único punto que cambia el día que se contrate proveedor, igual que
 * `impresora.ts` es el único que cambiaría al pasar a ESC/POS.
 */
export const emisor: EmisorDocumentos = new EmisorDePruebas()
