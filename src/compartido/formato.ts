import { format, formatDistanceToNowStrict, differenceInMinutes, isToday, isYesterday } from 'date-fns'
import { es } from 'date-fns/locale'

/**
 * Peso colombiano, sin decimales y con punto de miles: $45.000
 * Es la unica forma de mostrar dinero en todo el sistema.
 */
export function formatoCOP(valor: number): string {
  const redondeado = Math.round(valor)
  const signo = redondeado < 0 ? '-' : ''
  const absoluto = Math.abs(redondeado).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  return `${signo}$${absoluto}`
}

/** Version compacta para ejes y tarjetas estrechas: $1,2 M · $450 mil */
export function formatoCOPCorto(valor: number): string {
  const absoluto = Math.abs(valor)
  const signo = valor < 0 ? '-' : ''
  if (absoluto >= 1000000) {
    const millones = (absoluto / 1000000).toFixed(absoluto >= 10000000 ? 0 : 1)
    return `${signo}$${millones.replace('.', ',')} M`
  }
  if (absoluto >= 1000) return `${signo}$${Math.round(absoluto / 1000)} mil`
  return formatoCOP(valor)
}

/** Igual que formatoCOP pero sin el signo, para tablas densas. */
export function formatoCOPSinSigno(valor: number): string {
  return formatoCOP(valor).replace('$', '')
}

/** Telefono colombiano legible: +57 300 123 4567 */
export function formatoTelefono(numero: string): string {
  const digitos = numero.replace(/\D/g, '')
  const sinPais = digitos.startsWith('57') && digitos.length > 10 ? digitos.slice(2) : digitos
  if (sinPais.length !== 10) return numero
  return `+57 ${sinPais.slice(0, 3)} ${sinPais.slice(3, 6)} ${sinPais.slice(6)}`
}

/** Deja el telefono como lo exige wa.me: 573001234567 */
export function telefonoWhatsApp(numero: string): string {
  const digitos = numero.replace(/\D/g, '')
  if (digitos.startsWith('57')) return digitos
  return `57${digitos}`
}

const aFecha = (valor: string | Date): Date => (valor instanceof Date ? valor : new Date(valor))

/** dd/mm/aaaa */
export function formatoFecha(valor: string | Date): string {
  return format(aFecha(valor), 'dd/MM/yyyy')
}

/**
 * date-fns entrega "p.m." y en Colombia se escribe con espacio: "p. m.".
 * El token `a` devolveria "PM" en ingles, que no puede salir en pantalla.
 */
const conEspacioEnMeridiano = (texto: string): string =>
  texto.replace('a.m.', 'a. m.').replace('p.m.', 'p. m.')

/** 8:45 p. m. */
export function formatoHora(valor: string | Date): string {
  return conEspacioEnMeridiano(format(aFecha(valor), 'h:mm aaaa', { locale: es }))
}

/** dd/mm/aaaa 8:45 p. m. */
export function formatoFechaHora(valor: string | Date): string {
  return `${formatoFecha(valor)} ${formatoHora(valor)}`
}

/** Para texto corrido: "sábado 13 de agosto" */
export function formatoFechaLarga(valor: string | Date): string {
  return format(aFecha(valor), "EEEE d 'de' MMMM", { locale: es })
}

/** "hoy", "ayer" o la fecha corta. */
export function formatoDiaRelativo(valor: string | Date): string {
  const fecha = aFecha(valor)
  if (isToday(fecha)) return 'hoy'
  if (isYesterday(fecha)) return 'ayer'
  return formatoFecha(fecha)
}

/** "hace 12 minutos" */
export function hace(valor: string | Date): string {
  return `hace ${formatDistanceToNowStrict(aFecha(valor), { locale: es })}`
}

/** Minutos enteros transcurridos desde un instante. */
export function minutosDesde(valor: string | Date): number {
  return Math.max(0, differenceInMinutes(new Date(), aFecha(valor)))
}

/**
 * Cronometro compacto para mesas y comandas: "8 min", "1 h 12 min".
 * Se lee de un vistazo desde un metro de distancia.
 */
export function tiempoTranscurrido(valor: string | Date): string {
  const minutos = minutosDesde(valor)
  if (minutos < 60) return `${minutos} min`
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  return resto === 0 ? `${horas} h` : `${horas} h ${resto} min`
}

/** aaaa-mm-dd, la llave con la que agrupamos por dia. */
export function claveDia(valor: string | Date = new Date()): string {
  return format(aFecha(valor), 'yyyy-MM-dd')
}

/** Turno operativo segun la hora: antes de las 5 p. m. es almuerzo. */
export function turnoDe(valor: string | Date = new Date()): 'almuerzo' | 'cena' {
  return aFecha(valor).getHours() < 17 ? 'almuerzo' : 'cena'
}

/** Franja horaria para los reportes: "7 p. m." */
export function franjaHoraria(valor: string | Date): string {
  return conEspacioEnMeridiano(format(aFecha(valor), 'h aaaa', { locale: es }))
}

export function pluralizar(cantidad: number, singular: string, plural: string): string {
  return `${cantidad} ${cantidad === 1 ? singular : plural}`
}
