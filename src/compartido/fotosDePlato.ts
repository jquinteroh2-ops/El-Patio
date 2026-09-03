import type { ItemCarta } from './tipos'

/**
 * Las fotos de un plato como UNA sola lista, con la portada de primera.
 *
 * Por dentro el plato las guarda repartidas —`imagen` es la portada y
 * `galeria` las demas— porque asi nada tiene que decidir cual de un arreglo
 * manda, y quitar una foto de la ficha no cambia cual identifica al plato en el
 * listado. Pero de cara a quien las mira y a quien las sube son una lista y ya,
 * y estas dos funciones son el unico sitio donde se pasa de una forma a la
 * otra.
 *
 * Se filtran los vacios: un `imagen` en blanco —lo que deja un formulario mal
 * guardado— le pediria al servidor una ruta sin nombre y dejaria un hueco roto
 * en medio del carrusel.
 */
export function fotosDePlato(item: ItemCarta): string[] {
  return [item.imagen, ...(item.galeria ?? [])].filter(
    (foto): foto is string => typeof foto === 'string' && foto.trim() !== '',
  )
}

/** El camino de vuelta: una lista, y el reparto que espera el servidor. */
export function conFotosDePlato(item: ItemCarta, fotos: string[]): ItemCarta {
  return { ...item, imagen: fotos[0] ?? null, galeria: fotos.slice(1) }
}
