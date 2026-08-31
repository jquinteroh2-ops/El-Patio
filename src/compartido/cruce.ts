import { CLAVE_AVISO_CRUCE } from './config'

/**
 * El paso desde el panel del otro restaurante del dueño.
 *
 * El Patio y La Carreta Gourmet son dos despliegues separados, con sus propias
 * bases y sus propias sesiones. Para que el dueño no tenga que escribir la clave cada
 * vez que compara las dos cajas, el sistema de origen le firma un pase de
 * treinta segundos y de un solo uso, y el destino lo canjea por una sesión
 * propia.
 *
 * ── Por qué el pase viaja en el fragmento ────────────────────────────────────
 * Son dos dominios distintos: nada de lo que hay en uno —ni `sessionStorage`,
 * ni una cookie— es visible desde el otro. Lo único que cruza es la barra de
 * direcciones.
 *
 * Y dentro de la barra, el fragmento —lo que va después del `#`— y no la
 * consulta —lo que va después del `?`—, porque el navegador NO manda el
 * fragmento al servidor: no aparece en los registros de acceso, no viaja en la
 * cabecera `Referer` hacia terceros, y no queda escrito en los registros de
 * ningún proxy por el que pase la petición.
 *
 * Lo que sí queda, un instante, es el historial del navegador. Por eso lo
 * primero que hace este módulo es borrarlo de la URL, y por eso el pase dura
 * treinta segundos y sirve una sola vez: lo que quede ahí ya no abre nada.
 * ─────────────────────────────────────────────────────────────────────────────
 */

/** El nombre del parámetro dentro del fragmento: `#pase=…`. */
const PARAMETRO = 'pase'

/**
 * Saca el pase de la URL y lo borra de la barra de direcciones.
 *
 * Devuelve `null` si no venía ninguno, que es el caso normal: casi todas las
 * visitas a este sitio no vienen del otro restaurante.
 *
 * El borrado va ANTES de devolverlo y no después de canjearlo, a propósito. Si
 * el canje falla —un pase vencido, una red que se cae— y el pase siguiera en la
 * barra, recargar la página lo reintentaría en bucle contra un pase que ya no
 * sirve, y quedaría a la vista en la pantalla de quien esté mirando.
 */
export function tomarPaseDeLaUrl(): string | null {
  const bruto = window.location.hash
  if (!bruto || !bruto.includes(PARAMETRO)) return null

  const parametros = new URLSearchParams(bruto.replace(/^#/, ''))
  const pase = parametros.get(PARAMETRO)
  if (!pase) return null

  // `replaceState` y no `pushState`: sustituye la entrada del historial en vez
  // de agregar otra. Con `pushState`, el botón de atrás devolvería a la URL con
  // el pase dentro.
  window.history.replaceState(null, '', window.location.pathname + window.location.search)

  return pase
}

/**
 * Deja dicho por qué no se pudo entrar, para que la pantalla de acceso lo diga.
 *
 * Sin esto, un pase rechazado deja al dueño frente a un formulario de acceso
 * sin ninguna explicación, y lo que parece es que el botón no funciona.
 */
export function anotarFalloDelCruce(mensaje: string): void {
  try {
    sessionStorage.setItem(CLAVE_AVISO_CRUCE, mensaje)
  } catch {
    // Un navegador con el almacenamiento bloqueado se queda sin el aviso. El
    // formulario de acceso sigue estando ahí, que es lo que importa.
  }
}

/** Lee el aviso pendiente y lo consume: solo se enseña una vez. */
export function tomarAvisoDelCruce(): string | null {
  try {
    const mensaje = sessionStorage.getItem(CLAVE_AVISO_CRUCE)
    if (mensaje) sessionStorage.removeItem(CLAVE_AVISO_CRUCE)
    return mensaje
  } catch {
    return null
  }
}

/**
 * Arma la dirección a la que salta el selector de restaurante.
 *
 * Conserva la sección: quien está mirando el cierre de caja de un local quiere
 * el cierre de caja del otro, no su portada. Las dos aplicaciones son el mismo
 * código, así que la ruta existe igual en las dos.
 *
 * Sin pase también funciona: lleva a la misma sección y el otro sistema pedirá
 * la clave. Es lo que pasa cuando el cruce no está configurado, o cuando pedir
 * el pase falla y no vale la pena dejar al dueño sin ir a ninguna parte.
 */
export function urlDelCruce(base: string, ruta: string, pase?: string | null): string {
  const destino = `${base}${ruta}`
  return pase ? `${destino}#${PARAMETRO}=${encodeURIComponent(pase)}` : destino
}
