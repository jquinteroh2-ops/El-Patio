/**
 * Traducción de una venta de El Patio a un documento de la DIAN.
 *
 * Este archivo es la frontera. A la izquierda queda el vocabulario del
 * restaurante —comandas, modificadores, domicilio, propina—; a la derecha, el
 * de la DIAN —líneas, tributos por línea, base gravable—. Si algún día cambia
 * la norma, se cambia aquí y no en las pantallas.
 */

import type { Cuenta } from '@/compartido/calculos'
import { precioItem } from '@/compartido/calculos'
import type { MetodoPago, Orden } from '@/compartido/tipos'
import {
  CODIGO_MEDIO_PAGO,
  MEDIO_PAGO_NO_DEFINIDO,
  TRIBUTO,
  UNIDAD_MEDIDA,
} from './catalogos'
import type { LineaDocumento, ResumenTributo } from './tipos'

// ---------------------------------------------------------------------------
// Reparto de impuesto sin perder un peso
// ---------------------------------------------------------------------------

/**
 * Reparte un total entre varias líneas en proporción a su peso.
 *
 * Existe por una razón muy concreta: hoy el INC se calcula una sola vez sobre
 * el subtotal, pero la DIAN lo quiere línea por línea. Redondear cada línea por
 * su cuenta da una suma que casi nunca coincide con el total —tres líneas de
 * 8% sobre valores impares y ya sobra o falta un peso—, y la DIAN rechaza el
 * documento cuando las líneas no suman exactamente el total declarado.
 *
 * El método es el del residuo mayor: se reparte la parte entera y los pesos que
 * sobran se le dan, uno a uno, a las líneas cuya fracción quedó más cerca del
 * siguiente peso. Es el mismo criterio que reparte curules, y garantiza que la
 * suma dé exacta sin castigar siempre a la misma línea.
 */
export function repartirProporcional(total: number, pesos: number[]): number[] {
  const sumaPesos = pesos.reduce((s, p) => s + p, 0)
  if (sumaPesos <= 0 || total === 0) return pesos.map(() => 0)

  const exactos = pesos.map((p) => (total * p) / sumaPesos)
  const enteros = exactos.map((v) => Math.floor(v))
  let restante = total - enteros.reduce((s, v) => s + v, 0)

  // Las líneas ordenadas por lo que se les quedó debiendo al truncar.
  const porFraccion = exactos
    .map((v, i) => ({ i, fraccion: v - Math.floor(v) }))
    .sort((a, b) => b.fraccion - a.fraccion)

  for (const { i } of porFraccion) {
    if (restante <= 0) break
    enteros[i] += 1
    restante -= 1
  }

  return enteros
}

// ---------------------------------------------------------------------------
// Las líneas del documento
// ---------------------------------------------------------------------------

/** Código con que se identifican en el documento las líneas que no son carta. */
const CODIGO_CARGO = 'CARGO'
const CODIGO_DOMICILIO = 'DOMICILIO'

/**
 * Arma las líneas de la venta.
 *
 * Tres clases de línea, y la diferencia entre ellas es fiscal, no cosmética:
 *
 *  - Los platos y bebidas, que causan INC.
 *  - Los cargos adicionales —descorche, decoración—, que son servicios y no
 *    consumo de alimentos: van en el documento pero sin impuesto.
 *  - El domicilio, que tampoco es consumo.
 *
 * La propina no es línea. No es venta del restaurante y no puede aparecer como
 * si lo fuera; va aparte, en el total.
 */
export function construirLineas(orden: Orden, cuenta: Cuenta): LineaDocumento[] {
  const items = orden.items.filter((i) => i.estado !== 'anulado')

  // El INC total ya está calculado y cobrado. Lo que hace falta es repartirlo
  // entre las líneas gravadas sin que la suma se corra ni un peso.
  const basesGravadas = items.map((i) => precioItem(i))
  const incPorLinea = repartirProporcional(cuenta.inc, basesGravadas)

  const lineas: LineaDocumento[] = items.map((item, indice) => {
    const adicionales = item.modificadoresSeleccionados.reduce((s, m) => s + m.precioAdicional, 0)
    const valorUnitario = item.precioUnitario + adicionales
    const valorBruto = basesGravadas[indice]
    const inc = incPorLinea[indice]

    return {
      numero: indice + 1,
      codigo: item.itemCartaId,
      descripcion: item.nombre,
      detalles: item.modificadoresSeleccionados.map((m) => m.valor),
      cantidad: item.cantidad,
      unidadMedida: UNIDAD_MEDIDA,
      valorUnitario,
      valorBruto,
      tributos: [
        {
          codigo: TRIBUTO.inc,
          base: valorBruto,
          tarifa: cuenta.porcentajeInc,
          valor: inc,
        },
      ],
      valorTotal: valorBruto + inc,
      gravada: true,
    }
  })

  // Los cargos adicionales entran uno por uno y con su nombre: el cliente tiene
  // derecho a ver que le cobraron el descorche, no un «otros» sin explicar.
  for (const cargo of orden.cargosAdicionales) {
    lineas.push({
      numero: lineas.length + 1,
      codigo: CODIGO_CARGO,
      descripcion: cargo.nombre,
      detalles: [],
      cantidad: 1,
      unidadMedida: UNIDAD_MEDIDA,
      valorUnitario: cargo.valor,
      valorBruto: cargo.valor,
      tributos: [],
      valorTotal: cargo.valor,
      gravada: false,
    })
  }

  if (cuenta.costoEnvio > 0) {
    lineas.push({
      numero: lineas.length + 1,
      codigo: CODIGO_DOMICILIO,
      descripcion: 'Servicio de domicilio',
      detalles: [],
      cantidad: 1,
      unidadMedida: UNIDAD_MEDIDA,
      valorUnitario: cuenta.costoEnvio,
      valorBruto: cuenta.costoEnvio,
      tributos: [],
      valorTotal: cuenta.costoEnvio,
      gravada: false,
    })
  }

  return lineas
}

// ---------------------------------------------------------------------------
// El resumen de tributos
// ---------------------------------------------------------------------------

/**
 * Totaliza los impuestos agrupándolos por código y tarifa.
 *
 * Hoy solo hay INC al 8% y esto parece exagerado para un solo grupo. No lo es:
 * si mañana el restaurante vende una botella cerrada al 19% de IVA o cambia la
 * tarifa del consumo, el documento tiene que mostrar los dos grupos separados,
 * y ese cambio no debería obligar a reescribir esta función.
 */
export function resumirTributos(lineas: LineaDocumento[]): ResumenTributo[] {
  const grupos = new Map<string, ResumenTributo>()

  for (const linea of lineas) {
    for (const tributo of linea.tributos) {
      const llave = `${tributo.codigo}-${tributo.tarifa}`
      const acumulado = grupos.get(llave)
      if (acumulado) {
        acumulado.base += tributo.base
        acumulado.valor += tributo.valor
      } else {
        grupos.set(llave, {
          codigo: tributo.codigo,
          base: tributo.base,
          tarifa: tributo.tarifa,
          valor: tributo.valor,
        })
      }
    }
  }

  return [...grupos.values()].sort((a, b) => a.codigo.localeCompare(b.codigo))
}

// ---------------------------------------------------------------------------
// Medio de pago
// ---------------------------------------------------------------------------

/**
 * Traduce el medio de cobro al código de la DIAN.
 *
 * Un pago mixto no tiene un código propio en la tabla: el cliente pagó parte en
 * efectivo y parte con tarjeta, y la DIAN no tiene una casilla para eso en un
 * documento con un solo medio. Se declara «no definido», que es el valor
 * previsto para el caso, y el desglose real sigue vivo en `pagos.divisiones`
 * para el cierre de caja.
 */
export function codigoMedioPago(metodo: MetodoPago): string {
  if (metodo === 'mixto') return MEDIO_PAGO_NO_DEFINIDO
  return CODIGO_MEDIO_PAGO[metodo]
}

// ---------------------------------------------------------------------------
// Comprobación de que el documento cuadra
// ---------------------------------------------------------------------------

/**
 * Verifica que las partes sumen el total antes de mandar nada.
 *
 * Es barato y evita el error más caro de todos: un documento rechazado por la
 * DIAN después de que el cliente ya se fue del restaurante. Si esto falla, la
 * culpa está en el cálculo, no en la DIAN, y hay que arreglarlo antes de emitir.
 */
export function cuadra(
  lineas: LineaDocumento[],
  tributos: ResumenTributo[],
  propina: number,
  totalEsperado: number,
): boolean {
  const bruto = lineas.reduce((s, l) => s + l.valorBruto, 0)
  const impuestos = tributos.reduce((s, t) => s + t.valor, 0)
  return bruto + impuestos + propina === totalEsperado
}
