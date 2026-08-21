import { createRoot, type Root } from 'react-dom/client'
import type { ReactNode } from 'react'

/**
 * Salida a impresora.
 *
 * Toda la impresion pasa por la interfaz `Impresora`. Ninguna pantalla sabe
 * como se imprime: pide `imprimir(documento)` y ya. Es el mismo principio que
 * hizo posible cambiar localStorage por un backend sin tocar componentes.
 *
 * Hoy la unica implementacion es `ImpresoraNavegador`, que usa `window.print()`
 * y depende de que Chrome este configurado con --kiosk-printing para que no
 * aparezca el dialogo en caja. Mas adelante entra una `ImpresoraESCPOS` por Web
 * Serial, que si puede cortar el papel y abrir el cajon monedero; cuando entre,
 * lo unico que cambia es la linea que decide cual se usa.
 */

export interface Impresora {
  /** Manda el documento a la impresora. Resuelve cuando el navegador termino. */
  imprimir(documento: ReactNode): Promise<void>
}

const ID_CONTENEDOR = 'contenedor-impresion'

/** Lo que se espera a que React pinte antes de mandar el papel de todos modos. */
const ESPERA_MAXIMA_PINTADO_MS = 1500

/**
 * Impresora del navegador.
 *
 * El documento se monta en un contenedor aparte del arbol de la aplicacion, se
 * imprime y se desmonta. No se reutiliza el arbol principal porque
 * `window.print()` toma lo que haya en el DOM en ese instante, y montar el
 * ticket dentro de la pantalla obligaria a esconderlo con CSS y a rezar para
 * que ningun estilo heredado se cuele en el papel.
 */
export class ImpresoraNavegador implements Impresora {
  private raiz: Root | null = null

  /**
   * La cola de impresion.
   *
   * Todos los tiquetes comparten un mismo contenedor, asi que dos impresiones
   * a la vez se pisan: la segunda monta su documento encima del de la primera
   * antes de que esta llegue a `print()`. Eso es exactamente lo que pasaba al
   * cobrar una cuenta dividida —un tiquete por comensal, en un bucle— y por eso
   * salia uno con la parte equivocada y los demas en blanco.
   *
   * Encadenar los documentos en una promesa los pone en fila: cada uno se monta,
   * se imprime y se desmonta antes de que empiece el siguiente.
   */
  private cola: Promise<void> = Promise.resolve()

  imprimir(documento: ReactNode): Promise<void> {
    // El `catch` va antes de encadenar para que un tiquete que fallo no deje la
    // cola rota: los que vienen detras tienen que salir igual.
    this.cola = this.cola.catch(() => undefined).then(() => this.imprimirAhora(documento))
    return this.cola
  }

  private async imprimirAhora(documento: ReactNode): Promise<void> {
    if (typeof document === 'undefined') return

    const contenedor = this.contenedor()
    this.raiz ??= createRoot(contenedor)
    this.raiz.render(documento)

    // React pinta de forma asincrona: sin esta espera, `print()` puede salir
    // con el contenedor todavia vacio y sacar una hoja en blanco.
    //
    // El temporizador es la red de seguridad. Una pestana en segundo plano no
    // recibe cuadros de animacion, asi que sin el la espera no terminaria nunca:
    // el documento se quedaria montado y, peor, con la cola detenida detras.
    // Mas vale imprimir tarde que dejar la caja sin poder sacar un papel.
    await new Promise<void>((resolver) => {
      let resuelto = false
      const seguir = () => {
        if (resuelto) return
        resuelto = true
        resolver()
      }
      requestAnimationFrame(() => requestAnimationFrame(seguir))
      setTimeout(seguir, ESPERA_MAXIMA_PINTADO_MS)
    })

    try {
      window.print()
    } finally {
      // Se limpia siempre, incluso si el usuario cancelo el dialogo: dejar el
      // ticket montado haria que la siguiente impresion sacara los dos.
      this.raiz?.render(null)
      // Y se le da a React el respiro de un cuadro para desmontarlo, porque el
      // siguiente de la cola monta sobre este mismo contenedor. Con la misma red
      // por debajo, para que la cola no dependa de que la pestana este a la vista.
      await new Promise<void>((resolver) => {
        let resuelto = false
        const seguir = () => {
          if (resuelto) return
          resuelto = true
          resolver()
        }
        requestAnimationFrame(seguir)
        setTimeout(seguir, ESPERA_MAXIMA_PINTADO_MS)
      })
    }
  }

  private contenedor(): HTMLElement {
    const existente = document.getElementById(ID_CONTENEDOR)
    if (existente) return existente
    const nuevo = document.createElement('div')
    nuevo.id = ID_CONTENEDOR
    document.body.appendChild(nuevo)
    return nuevo
  }
}

/**
 * La impresora en uso.
 *
 * Es el unico punto que habria que tocar el dia que entre la ESCPOS por Web
 * Serial. Se crea una sola vez para no montar una raiz de React por impresion.
 */
const impresora: Impresora = new ImpresoraNavegador()

export function imprimir(documento: ReactNode): void {
  void impresora.imprimir(documento).catch((error) => {
    console.error('[impresion] no se pudo imprimir', error)
  })
}
