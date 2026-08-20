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

  async imprimir(documento: ReactNode): Promise<void> {
    if (typeof document === 'undefined') return

    const contenedor = this.contenedor()
    this.raiz ??= createRoot(contenedor)
    this.raiz.render(documento)

    // React pinta de forma asincrona: sin esta espera, `print()` puede salir
    // con el contenedor todavia vacio y sacar una hoja en blanco.
    await new Promise<void>((resolver) => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolver()))
    })

    try {
      window.print()
    } finally {
      // Se limpia siempre, incluso si el usuario cancelo el dialogo: dejar el
      // ticket montado haria que la siguiente impresion sacara los dos.
      this.raiz?.render(null)
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
