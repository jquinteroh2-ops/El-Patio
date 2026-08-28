import { RESTAURANTE } from './config'
import { fichaSitio } from './sitio'
import { formatoCOP, formatoFechaLarga, formatoHora, telefonoWhatsApp } from './formato'
import type { Orden, Reserva } from './tipos'

/**
 * Mensajes de WhatsApp para responder reservas.
 *
 * El texto se arma aqui y no en los componentes, para que el dueno pueda
 * cambiar el tono de la casa en un solo archivo. Siempre se muestra al usuario
 * antes de enviarlo: nada sale a nombre del restaurante sin que alguien lo lea.
 */

const primerNombre = (nombre: string): string => nombre.trim().split(' ')[0]

const cuando = (fechaHora: string): string =>
  `el ${formatoFechaLarga(fechaHora)} a las ${formatoHora(fechaHora)}`

const personas = (cantidad: number): string =>
  cantidad === 1 ? '1 persona' : `${cantidad} personas`

/** "a las 9:00 p. m.." queda con dos puntos: la hora ya trae el suyo. */
const sinPuntoDoble = (texto: string): string => texto.replace(/\.\.+/g, '.')

export function mensajeConfirmacion(reserva: Reserva, mesaEtiqueta?: string): string {
  const saludo = `Hola ${primerNombre(reserva.nombreCliente)}, le escribimos de ${RESTAURANTE.nombreCompleto}.`
  const cuerpo = `Su reserva quedó confirmada para ${personas(reserva.personas)} ${cuando(reserva.fechaHora)}.`
  const mesa = mesaEtiqueta ? ` Le separamos ${mesaEtiqueta}.` : ''
  const ocasion =
    reserva.ocasion === 'cumpleanos'
      ? ' Tenemos anotado que es un cumpleaños, así que lo preparamos todo.'
      : reserva.ocasion === 'aniversario'
        ? ' Tenemos anotado que es un aniversario, así que lo preparamos todo.'
        : ''
  const cierre = `Lo esperamos en ${fichaSitio().direccion}, ${fichaSitio().ciudad}. Si necesita cambiar algo, respóndanos por aquí.`

  return sinPuntoDoble(`${saludo}\n\n${cuerpo}${mesa}${ocasion}\n\n${cierre}`)
}

export function mensajePropuesta(reserva: Reserva, nuevaFechaHora: string): string {
  const saludo = `Hola ${primerNombre(reserva.nombreCliente)}, le escribimos de ${RESTAURANTE.nombreCompleto}.`
  const cuerpo =
    `Gracias por escribirnos. Para ${personas(reserva.personas)} ${cuando(reserva.fechaHora)} ya tenemos el salón comprometido, ` +
    `pero podemos recibirlo ${cuando(nuevaFechaHora)}.`
  const cierre = '¿Le sirve ese horario? Con su confirmación le separamos la mesa.'

  return sinPuntoDoble(`${saludo}\n\n${cuerpo}\n\n${cierre}`)
}

export function mensajeRechazo(reserva: Reserva): string {
  const saludo = `Hola ${primerNombre(reserva.nombreCliente)}, le escribimos de ${RESTAURANTE.nombreCompleto}.`
  const cuerpo = `Lamentablemente no tenemos disponibilidad para ${personas(reserva.personas)} ${cuando(reserva.fechaHora)}.`
  const cierre = 'Nos encantaría recibirlo otro día. Escríbanos y buscamos el horario que mejor le sirva.'

  return sinPuntoDoble(`${saludo}\n\n${cuerpo}\n\n${cierre}`)
}

// ---------------------------------------------------------------------------
// Domicilios y para llevar
// ---------------------------------------------------------------------------

/**
 * Mensajes del canal de pedidos.
 *
 * Mismo criterio que con las reservas: el texto se arma aqui para que el dueno
 * pueda cambiar el tono de la casa en un solo archivo, y siempre se le muestra
 * al usuario antes de enviarlo. Nada sale a nombre del restaurante sin que
 * alguien lo lea.
 */

const nombreDelCliente = (orden: Orden): string =>
  primerNombre(orden.cliente?.nombre ?? '')

/** Confirmacion: el pedido se acepto y se le promete un tiempo. */
export function mensajePedidoConfirmado(orden: Orden, total: number): string {
  const saludo = `Hola ${nombreDelCliente(orden)}, le escribimos de ${RESTAURANTE.nombreCompleto}.`
  const cuerpo = `Confirmamos su pedido n.º ${orden.numero} por ${formatoCOP(total)}.`
  const tiempo = orden.minutosEstimados
    ? orden.tipo === 'domicilio'
      ? ` Se lo estamos llevando en unos ${orden.minutosEstimados} minutos.`
      : ` Puede pasar a recogerlo en unos ${orden.minutosEstimados} minutos.`
    : ''
  const cierre =
    orden.tipo === 'domicilio'
      ? `Lo llevamos a ${orden.cliente?.direccion ?? 'la dirección indicada'}. Si necesita cambiar algo, respóndanos por aquí.`
      : `Lo esperamos en ${fichaSitio().direccion}, ${fichaSitio().ciudad}.`

  return sinPuntoDoble(`${saludo}\n\n${cuerpo}${tiempo}\n\n${cierre}`)
}

/**
 * El tiempo cambió después de confirmarlo.
 *
 * Tres textos y no uno, porque las tres situaciones se le dicen distinto a la
 * persona que está esperando: si se demora, se pide disculpas, que es lo mínimo
 * cuando alguien ya organizó su hora alrededor de lo prometido; si sale antes
 * hay que avisar igual —en un para llevar la persona puede salir ya— y ahí una
 * disculpa sobra; y si el tiempo no cambió, el mensaje es solo para tranquilizar
 * al que llamó a preguntar, sin inventar una demora que no hubo.
 */
export function mensajePedidoNuevoTiempo(orden: Orden, minutos: number): string {
  const anterior = orden.minutosEstimados
  const seDemora = anterior === undefined || minutos > anterior
  const seAdelanta = anterior !== undefined && minutos < anterior

  const saludo = `Hola ${nombreDelCliente(orden)}, le escribimos de ${RESTAURANTE.nombreCompleto}.`
  const cuerpo = seDemora
    ? `Su pedido n.º ${orden.numero} se nos está demorando un poco más de lo que le dijimos.`
    : seAdelanta
      ? `Su pedido n.º ${orden.numero} va a estar antes de lo que le dijimos.`
      : `Le contamos cómo va su pedido n.º ${orden.numero}.`
  const tiempo =
    orden.tipo === 'domicilio'
      ? ` Se lo estamos llevando en unos ${minutos} minutos.`
      : ` Puede pasar a recogerlo en unos ${minutos} minutos.`
  const cierre = seDemora
    ? 'Le pedimos disculpas por la espera. Cualquier cosa, respóndanos por aquí.'
    : 'Cualquier cosa, respóndanos por aquí.'

  return sinPuntoDoble(`${saludo}\n\n${cuerpo}${tiempo}\n\n${cierre}`)
}

/** El domicilio ya salio del local. */
export function mensajePedidoEnCamino(orden: Orden): string {
  const saludo = `Hola ${nombreDelCliente(orden)}, le escribimos de ${RESTAURANTE.nombreCompleto}.`
  const quien = orden.repartidor ? ` Se lo lleva ${orden.repartidor}.` : ''
  const cuerpo = `Su pedido n.º ${orden.numero} ya va en camino.${quien}`
  const cierre = 'Cualquier cosa, respóndanos por aquí.'

  return sinPuntoDoble(`${saludo}\n\n${cuerpo}\n\n${cierre}`)
}

/** El para llevar esta listo en el mostrador. */
export function mensajePedidoListoParaRecoger(orden: Orden): string {
  const saludo = `Hola ${nombreDelCliente(orden)}, le escribimos de ${RESTAURANTE.nombreCompleto}.`
  const cuerpo = `Su pedido n.º ${orden.numero} ya está listo y lo tenemos empacado.`
  const cierre = `Lo esperamos en ${fichaSitio().direccion}, ${fichaSitio().ciudad}.`

  return sinPuntoDoble(`${saludo}\n\n${cuerpo}\n\n${cierre}`)
}

/** No se le puede atender. Se le dice por que: el cliente merece una razon. */
export function mensajePedidoRechazado(orden: Orden, motivo: string): string {
  const saludo = `Hola ${nombreDelCliente(orden)}, le escribimos de ${RESTAURANTE.nombreCompleto}.`
  const cuerpo = `Lamentablemente no podemos atender su pedido n.º ${orden.numero}: ${motivo.toLowerCase()}.`
  const cierre = 'Le pedimos disculpas. Nos encantaría atenderlo en otro momento.'

  return sinPuntoDoble(`${saludo}\n\n${cuerpo}\n\n${cierre}`)
}

/** Arma el enlace que abre WhatsApp con el mensaje ya escrito. */
export function enlaceWhatsApp(telefono: string, mensaje: string): string {
  return `https://wa.me/${telefonoWhatsApp(telefono)}?text=${encodeURIComponent(mensaje)}`
}
