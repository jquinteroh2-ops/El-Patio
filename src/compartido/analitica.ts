import { ID_ANALITICA } from './config'

/**
 * Google Analytics, solo con permiso y solo en el sitio publico.
 *
 * Tres reglas, y las tres son a proposito:
 *
 * 1. **Nada se carga sin que el visitante acepte.** Hasta que no dice que si en
 *    el aviso de cookies, el script de Google ni siquiera se descarga: no hay
 *    cookie que borrar despues. Lo exige la Ley 1581 de 2012 para un dato que
 *    no es necesario para prestar el servicio.
 * 2. **Las pantallas del personal no se miden nunca.** Tienen nombres de
 *    clientes, telefonos y movimientos de caja en la direccion y en el titulo.
 *    Si alguien pasa del sitio a /admin en la misma pestana, se activa el
 *    interruptor oficial de Google (`ga-disable-<ID>`) y no sale ningun dato.
 * 3. **Solo mide el sitio de verdad.** En desarrollo o en una copia en otro
 *    dominio no se manda nada: las visitas de prueba ensuciarian las cifras.
 */

type Decision = 'aceptada' | 'rechazada'

const CLAVE = 'elpatio-cookies-analitica'

/**
 * Rutas que NO se miden: todo lo que hay detras del acceso del personal.
 *
 * Al personal se llega siempre pasando por /acceso, y es ahi donde se corta:
 * cuando el panel abre ya no sale ningun dato. Lo maximo que puede llegar a
 * Google es la visita a /acceso misma, que no muestra nada de nadie.
 */
const PREFIJOS_INTERNOS = ['/acceso', '/comandera', '/cocina', '/recepcion', '/reparto', '/admin']

declare global {
  interface Window {
    dataLayer?: unknown[]
    gtag?: (...args: unknown[]) => void
  }
}

const habilitada = (): boolean =>
  import.meta.env.PROD && /(^|\.)restauranteelpatio\.com$/.test(window.location.hostname)

export function esRutaInterna(pathname: string): boolean {
  return PREFIJOS_INTERNOS.some((p) => pathname === p || pathname.startsWith(`${p}/`))
}

/** La decision guardada, o null si el visitante todavia no ha elegido. */
export function decisionGuardada(): Decision | null {
  try {
    const valor = localStorage.getItem(CLAVE)
    return valor === 'aceptada' || valor === 'rechazada' ? valor : null
  } catch {
    // Sin almacenamiento (navegacion privada estricta): se vuelve a preguntar.
    return null
  }
}

export function guardarDecision(decision: Decision): void {
  try {
    localStorage.setItem(CLAVE, decision)
  } catch {
    // Si no se puede guardar, la decision vale para esta visita y nada mas.
  }
  if (decision === 'aceptada') {
    cargar()
  } else {
    apagar()
  }
}

let cargada = false

/** Descarga el script de Google y lo configura. Una sola vez por pagina. */
export function cargar(): void {
  if (cargada || !habilitada()) return
  cargada = true

  window.dataLayer = window.dataLayer ?? []
  window.gtag = function gtag() {
    // gtag exige recibir el objeto `arguments`, no un arreglo.
    // eslint-disable-next-line prefer-rest-params
    window.dataLayer!.push(arguments)
  }
  window.gtag('js', new Date())
  // La medicion mejorada de la propiedad ya cuenta los cambios de pagina de
  // una aplicacion de una sola pagina: no hace falta mandarlos a mano.
  window.gtag('config', ID_ANALITICA)

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${ID_ANALITICA}`
  document.head.appendChild(script)
}

/** Corta el envio de datos sin recargar la pagina. */
function apagar(): void {
  ;(window as unknown as Record<string, boolean>)[`ga-disable-${ID_ANALITICA}`] = true
}

/**
 * Se llama en cada cambio de ruta: en las del personal corta el envio y en
 * las publicas lo deja seguir si el visitante acepto.
 */
export function ajustarARuta(pathname: string): void {
  const interna = esRutaInterna(pathname)
  ;(window as unknown as Record<string, boolean>)[`ga-disable-${ID_ANALITICA}`] =
    interna || decisionGuardada() !== 'aceptada'
  if (!interna && decisionGuardada() === 'aceptada') cargar()
}
