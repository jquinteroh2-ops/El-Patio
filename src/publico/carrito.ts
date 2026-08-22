import { useCallback, useEffect, useState } from 'react'
import { escribirCrudo, leerCrudo } from '@/compartido/almacen'
import { precioVigente } from '@/compartido/calculos'
import type { ItemCarta, ModificadorSeleccionado } from '@/compartido/tipos'

/**
 * El carrito del cliente.
 *
 * Vive en el navegador del cliente y no en el servidor, y eso si es correcto:
 * hasta que toca «Confirmar», el pedido no existe para el restaurante. Guardarlo
 * en la base seria llenarla de carritos que nadie termino.
 *
 * Persiste en localStorage porque en un celular es normal que llegue una
 * llamada a mitad del pedido: al volver, lo que habia escogido sigue ahi.
 */

const CLAVE_CARRITO = 'elpatio.carrito.v1'

export interface LineaCarrito {
  /** Identifica la linea, no el producto: el mismo plato puede ir dos veces. */
  id: string
  itemCartaId: string
  nombre: string
  precioUnitario: number
  cantidad: number
  modificadores: ModificadorSeleccionado[]
  nota?: string
}

export interface Carrito {
  lineas: LineaCarrito[]
  /** Suma de los productos. Sin impuesto ni envio: eso lo calcula el servidor. */
  subtotal: number
  unidades: number
  agregar: (item: ItemCarta, cantidad: number, modificadores: ModificadorSeleccionado[], nota?: string) => void
  cambiarCantidad: (lineaId: string, cantidad: number) => void
  quitar: (lineaId: string) => void
  vaciar: () => void
}

const precioLinea = (linea: LineaCarrito): number => {
  const adicionales = linea.modificadores.reduce((s, m) => s + m.precioAdicional, 0)
  return (linea.precioUnitario + adicionales) * linea.cantidad
}

function leer(): LineaCarrito[] {
  return leerCrudo<LineaCarrito[]>(CLAVE_CARRITO, [])
}

/**
 * Oyentes locales del carrito.
 *
 * El carrito cambia desde la carta y se lee desde el boton flotante y desde la
 * pantalla de confirmacion, que son componentes hermanos. Sin este aviso, tocar
 * «Agregar» no actualizaria el contador.
 */
const oyentes = new Set<() => void>()

function guardar(lineas: LineaCarrito[]): void {
  escribirCrudo(CLAVE_CARRITO, lineas)
  for (const oyente of oyentes) oyente()
}

export function useCarrito(): Carrito {
  const [lineas, setLineas] = useState<LineaCarrito[]>(leer)

  useEffect(() => {
    const actualizar = () => setLineas(leer())
    oyentes.add(actualizar)
    // El evento `storage` cubre el caso de dos pestanas del mismo celular.
    window.addEventListener('storage', actualizar)
    actualizar()
    return () => {
      oyentes.delete(actualizar)
      window.removeEventListener('storage', actualizar)
    }
  }, [])

  const agregar = useCallback(
    (item: ItemCarta, cantidad: number, modificadores: ModificadorSeleccionado[], nota?: string) => {
      const actuales = leer()

      // Un mismo producto con la misma eleccion se acumula en una sola linea,
      // igual que en la comandera: el resumen tiene que ser legible.
      const firma = JSON.stringify(modificadores)
      const existente = actuales.find(
        (l) =>
          l.itemCartaId === item.id &&
          (l.nota ?? '') === (nota ?? '') &&
          JSON.stringify(l.modificadores) === firma,
      )

      if (existente) {
        existente.cantidad += cantidad
        guardar([...actuales])
        return
      }

      actuales.push({
        id: `lc_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`,
        itemCartaId: item.id,
        nombre: item.nombre,
        // El precio que rige hoy, que puede ser el de promocion. Es el mismo
        // calculo que hace el backend al armar la comanda: si aqui se copiara
        // el de lista, el cliente veria un precio en la carta y otro al pagar.
        precioUnitario: precioVigente(item),
        cantidad,
        modificadores,
        nota,
      })
      guardar(actuales)
    },
    [],
  )

  const cambiarCantidad = useCallback((lineaId: string, cantidad: number) => {
    const actuales = leer()
    if (cantidad <= 0) {
      guardar(actuales.filter((l) => l.id !== lineaId))
      return
    }
    guardar(actuales.map((l) => (l.id === lineaId ? { ...l, cantidad } : l)))
  }, [])

  const quitar = useCallback((lineaId: string) => {
    guardar(leer().filter((l) => l.id !== lineaId))
  }, [])

  const vaciar = useCallback(() => guardar([]), [])

  return {
    lineas,
    subtotal: lineas.reduce((s, l) => s + precioLinea(l), 0),
    unidades: lineas.reduce((s, l) => s + l.cantidad, 0),
    agregar,
    cambiarCantidad,
    quitar,
    vaciar,
  }
}

export { precioLinea }
