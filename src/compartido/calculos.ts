import type { CargoAdicional, ItemOrden, Orden } from './tipos'

/**
 * Desglose de una cuenta. El orden de los campos es el mismo en que se le
 * muestra al cliente, para que nadie vea un cargo por primera vez al pagar.
 */
export interface Cuenta {
  /** Alimentos y bebidas, con modificadores incluidos. */
  subtotal: number
  /** Impuesto Nacional al Consumo sobre el subtotal, antes de propina. */
  inc: number
  porcentajeInc: number
  /** Decoracion, descorche, servicios especiales. No causan INC. */
  cargosAdicionales: number
  /**
   * Domicilio. Linea aparte DESPUES del impuesto.
   *
   * El INC grava el consumo de alimentos y bebidas, y llevar un pedido hasta
   * una casa no es consumo: por eso el envio no causa impuesto. Tampoco entra
   * en la base de la propina, que retribuye el servicio de mesa y en un
   * domicilio no existe.
   */
  costoEnvio: number
  /** Voluntaria. Cero mientras el cliente no la autorice. */
  propina: number
  porcentajePropina: number
  total: number
}

export const precioItem = (item: ItemOrden): number => {
  const adicionales = item.modificadoresSeleccionados.reduce((s, m) => s + m.precioAdicional, 0)
  return (item.precioUnitario + adicionales) * item.cantidad
}

const cuentaItems = (items: ItemOrden[]): ItemOrden[] => items.filter((i) => i.estado !== 'anulado')

/** Subtotal de alimentos y bebidas. Los items anulados no suman. */
export function calcularSubtotal(items: ItemOrden[]): number {
  return cuentaItems(items).reduce((s, i) => s + precioItem(i), 0)
}

export function calcularCargos(cargos: CargoAdicional[]): number {
  return cargos.reduce((s, c) => s + c.valor, 0)
}

/**
 * Arma la cuenta completa.
 *
 * Reglas colombianas aplicadas aqui, en un solo lugar:
 *  - El INC se calcula sobre el subtotal de alimentos y bebidas, antes de la
 *    propina, y no sobre los cargos adicionales, que no son consumo.
 *  - La propina se calcula sobre el mismo subtotal y por defecto es 0: solo
 *    entra si el mesero la agrega despues de consultarla con el cliente.
 *  - EL DOMICILIO NO LLEVA INC NI PROPINA. El envio entra como una linea
 *    aparte, despues del impuesto, y queda fuera de las dos bases de calculo.
 */
export function calcularCuenta(
  orden: Pick<Orden, 'items' | 'cargosAdicionales'> & Pick<Partial<Orden>, 'costoEnvio'>,
  porcentajeInc: number,
  porcentajePropina = 0,
  propinaManual?: number,
): Cuenta {
  const subtotal = calcularSubtotal(orden.items)
  const inc = Math.round((subtotal * porcentajeInc) / 100)
  const cargosAdicionales = calcularCargos(orden.cargosAdicionales)
  const costoEnvio = orden.costoEnvio ?? 0

  // El envio queda fuera de la base de la propina, igual que queda fuera de la
  // del impuesto: nadie propina por el domicilio que ya esta pagando.
  const propina =
    propinaManual !== undefined
      ? Math.max(0, Math.round(propinaManual))
      : Math.round((subtotal * porcentajePropina) / 100)

  return {
    subtotal,
    inc,
    porcentajeInc,
    cargosAdicionales,
    costoEnvio,
    propina,
    porcentajePropina,
    total: subtotal + inc + cargosAdicionales + costoEnvio + propina,
  }
}

/** Total corriente de una mesa, para mostrarlo en el mapa de mesas. */
export function totalCorriente(orden: Orden, porcentajeInc: number): number {
  return calcularCuenta(orden, porcentajeInc).total
}

// ---------------------------------------------------------------------------
// Division de cuenta
// ---------------------------------------------------------------------------

/**
 * Reparte el total en partes iguales. El residuo de la division en pesos se
 * carga a la primera parte para que la suma cuadre exacta con el total.
 */
export function dividirEnPartesIguales(total: number, partes: number): number[] {
  if (partes < 1) return [total]
  const base = Math.floor(total / partes)
  const residuo = total - base * partes
  return Array.from({ length: partes }, (_, i) => (i === 0 ? base + residuo : base))
}

/**
 * Division por items seleccionados: a la seleccion se le aplica su parte
 * proporcional de INC, cargos y propina, para que ninguna parte quede sin
 * impuesto y la suma de las partes sea el total.
 */
export function dividirPorItems(
  items: ItemOrden[],
  seleccionIds: string[],
  cuenta: Cuenta,
): { valorSeleccion: number; valorResto: number } {
  const vigentes = cuentaItems(items)
  const subtotalSeleccion = vigentes
    .filter((i) => seleccionIds.includes(i.id))
    .reduce((s, i) => s + precioItem(i), 0)

  if (cuenta.subtotal === 0) return { valorSeleccion: 0, valorResto: cuenta.total }

  const proporcion = subtotalSeleccion / cuenta.subtotal
  const valorSeleccion = Math.round(cuenta.total * proporcion)
  return { valorSeleccion, valorResto: cuenta.total - valorSeleccion }
}

// ---------------------------------------------------------------------------
// Turnos de envio
// ---------------------------------------------------------------------------

/** Items que el mesero todavia no ha mandado a cocina o barra. */
export const itemsSinEnviar = (orden: Orden): ItemOrden[] =>
  orden.items.filter((i) => i.turnoEnvio === 0 && i.estado !== 'anulado')

/** Numero del proximo turno de envio de la mesa. */
export function proximoTurno(orden: Orden): number {
  const turnos = orden.items.map((i) => i.turnoEnvio)
  return Math.max(0, ...turnos) + 1
}

/** Agrupa los items ya enviados por turno, en orden de envio. */
export function agruparPorTurno(items: ItemOrden[]): { turno: number; items: ItemOrden[] }[] {
  const mapa = new Map<number, ItemOrden[]>()
  for (const item of items) {
    if (item.turnoEnvio === 0) continue
    const lista = mapa.get(item.turnoEnvio) ?? []
    lista.push(item)
    mapa.set(item.turnoEnvio, lista)
  }
  return [...mapa.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([turno, items]) => ({ turno, items }))
}

/**
 * Estado agregado de un turno: se toma el menos avanzado de sus items, porque
 * un turno no esta listo hasta que salga el ultimo plato.
 */
export function estadoDeTurno(items: ItemOrden[]): 'pendiente' | 'en_preparacion' | 'listo' | 'servido' {
  const vigentes = items.filter((i) => i.estado !== 'anulado')
  if (vigentes.length === 0) return 'servido'
  if (vigentes.some((i) => i.estado === 'pendiente')) return 'pendiente'
  if (vigentes.some((i) => i.estado === 'en_preparacion')) return 'en_preparacion'
  if (vigentes.some((i) => i.estado === 'listo')) return 'listo'
  return 'servido'
}
