import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { RESTAURANTE } from './config'

/**
 * El titulo y la descripcion de cada pantalla, en un solo sitio.
 *
 * Va centralizado y no repartido en cada componente porque asi no hay forma de
 * que una pantalla se quede con el titulo de la anterior: en una aplicacion de
 * una sola pagina el navegador no recarga nada, y un `document.title` que solo
 * se escribe en algunas rutas termina mintiendo en las demas.
 *
 * Lo que hay detras del acceso del personal no se indexa. No es solo que no le
 * sirva a nadie desde un buscador: son pantallas con nombres de clientes,
 * telefonos y movimientos de caja.
 */
interface Ficha {
  titulo: string
  descripcion: string
  indexable: boolean
}

const PUBLICAS: Record<string, Ficha> = {
  '/': {
    titulo: `${RESTAURANTE.nombreCompleto} · Cocina de fusión en ${RESTAURANTE.ciudad}`,
    descripcion:
      'Cocina de fusión y coctelería de autor en Turbaco, Bolívar. Reserve su mesa, vea la carta' +
      ' o pida a domicilio. Abierto de martes a domingo.',
    indexable: true,
  },
  '/carta': {
    titulo: `Carta y precios · ${RESTAURANTE.nombreCompleto}, ${RESTAURANTE.ciudad}`,
    descripcion:
      'Entradas, carnes a la parrilla, pescados del Caribe, pastas, postres y coctelería de autor.' +
      ' Vea la carta completa del Restaurante El Patio en Turbaco.',
    indexable: true,
  },
  '/reservar': {
    titulo: `Reservar mesa · ${RESTAURANTE.nombreCompleto}, ${RESTAURANTE.ciudad}`,
    descripcion:
      'Reserve su mesa en el Restaurante El Patio, Turbaco. Cumpleaños, aniversarios y cenas de' +
      ' negocios. Confirmamos por WhatsApp.',
    indexable: true,
  },
  '/pedir': {
    titulo: `Pedir a domicilio · ${RESTAURANTE.nombreCompleto}, ${RESTAURANTE.ciudad}`,
    descripcion:
      'Pida a domicilio o para llevar en Turbaco. Domicilios al centro, El Cerrito, Bonanza,' +
      ' La Esmeralda y más barrios.',
    indexable: true,
  },
}

const INTERNA: Ficha = {
  titulo: `Sistema de sala · ${RESTAURANTE.nombreCompleto}`,
  descripcion: '',
  indexable: false,
}

/**
 * Escribe o crea una etiqueta del encabezado.
 *
 * Crea la etiqueta si no existe en index.html: asi el archivo estatico se queda
 * con lo que necesita un robot que no ejecuta JavaScript, y aqui solo se
 * corrige lo que cambia al navegar.
 */
function etiqueta(atributo: 'name' | 'property', clave: string, contenido: string): void {
  let elemento = document.head.querySelector<HTMLMetaElement>(`meta[${atributo}="${clave}"]`)
  if (!elemento) {
    elemento = document.createElement('meta')
    elemento.setAttribute(atributo, clave)
    document.head.appendChild(elemento)
  }
  elemento.setAttribute('content', contenido)
}

/** La URL canonica, que evita que la misma pagina cuente como dos. */
function canonica(url: string): void {
  let enlace = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
  if (!enlace) {
    enlace = document.createElement('link')
    enlace.setAttribute('rel', 'canonical')
    document.head.appendChild(enlace)
  }
  enlace.setAttribute('href', url)
}

/**
 * Mantiene el encabezado al dia con la ruta abierta.
 *
 * Se monta una sola vez dentro del enrutador y no pinta nada.
 */
export function MetaDeRuta() {
  const { pathname } = useLocation()

  useEffect(() => {
    const ficha = PUBLICAS[pathname] ?? INTERNA

    document.title = ficha.titulo
    etiqueta('name', 'robots', ficha.indexable ? 'index, follow, max-image-preview:large' : 'noindex, nofollow')
    etiqueta('property', 'og:title', ficha.titulo)

    if (ficha.descripcion) {
      etiqueta('name', 'description', ficha.descripcion)
      etiqueta('property', 'og:description', ficha.descripcion)
      etiqueta('name', 'twitter:description', ficha.descripcion)
    }

    // El origen sale del navegador y no de una variable de compilacion: el
    // dominio del sitio es justamente lo que cambia entre el despliegue de
    // prueba y el definitivo, y una canonica escrita a mano apuntando al
    // dominio equivocado es peor que no tener ninguna.
    if (ficha.indexable) {
      const url = `${window.location.origin}${pathname === '/' ? '/' : pathname}`
      canonica(url)
      etiqueta('property', 'og:url', url)
    }
  }, [pathname])

  return null
}
