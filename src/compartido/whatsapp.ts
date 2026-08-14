import { RESTAURANTE } from './config'
import { formatoFechaLarga, formatoHora, telefonoWhatsApp } from './formato'
import type { Reserva } from './tipos'

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
  const cierre = `Lo esperamos en ${RESTAURANTE.direccion}, ${RESTAURANTE.ciudad}. Si necesita cambiar algo, respóndanos por aquí.`

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

/** Arma el enlace que abre WhatsApp con el mensaje ya escrito. */
export function enlaceWhatsApp(telefono: string, mensaje: string): string {
  return `https://wa.me/${telefonoWhatsApp(telefono)}?text=${encodeURIComponent(mensaje)}`
}
