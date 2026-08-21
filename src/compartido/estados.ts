import { UMBRALES_COCINA } from './config'
import type { EstadoItem, EstadoMesa, EstadoPedido, Rol, TipoPedido, Zona } from './tipos'

/**
 * Tokens de presentacion compartidos por la comandera, la cocina y el panel.
 *
 * Semantica de color, igual en todo el sistema:
 * verde = listo o pagado, ambar = en proceso, rojo = demorado o anulado.
 */

export const ESTILO_MESA: Record<
  EstadoMesa,
  { borde: string; fondo: string; punto: string; texto: string; etiqueta: string }
> = {
  libre: {
    borde: 'border-noche-700',
    fondo: 'bg-noche-900',
    punto: 'bg-noche-500',
    texto: 'text-noche-400',
    etiqueta: 'Libre',
  },
  ocupada: {
    borde: 'border-estado-listo/45',
    fondo: 'bg-estado-listo-suave',
    punto: 'bg-estado-listo',
    texto: 'text-estado-listo',
    etiqueta: 'Ocupada',
  },
  cuenta_pedida: {
    borde: 'border-estado-proceso/50',
    fondo: 'bg-estado-proceso-suave',
    punto: 'bg-estado-proceso',
    texto: 'text-estado-proceso',
    etiqueta: 'Cuenta pedida',
  },
  reservada: {
    borde: 'border-estado-reservada/50',
    fondo: 'bg-estado-reservada-suave',
    punto: 'bg-estado-reservada',
    texto: 'text-estado-reservada',
    etiqueta: 'Reservada',
  },
}

export const ETIQUETA_ITEM: Record<EstadoItem, string> = {
  pendiente: 'En espera',
  en_preparacion: 'Preparando',
  listo: 'Listo',
  servido: 'Servido',
  anulado: 'Anulado',
}

export const TONO_ITEM: Record<EstadoItem, 'neutro' | 'listo' | 'proceso' | 'demorado'> = {
  pendiente: 'neutro',
  en_preparacion: 'proceso',
  listo: 'listo',
  servido: 'neutro',
  anulado: 'demorado',
}

/** Como se nombra cada rol delante de una persona. */
export const NOMBRE_ROL: Record<Rol, string> = {
  mesero: 'Mesero',
  cocina: 'Cocina',
  recepcion: 'Recepción',
  repartidor: 'Repartidor',
  cajero: 'Cajero',
  administrador: 'Administrador',
}

export const NOMBRE_ZONA: Record<Zona, string> = {
  salon: 'Salón',
  terraza: 'Terraza',
  privado: 'Privados',
}

export const ETIQUETA_PEDIDO: Record<EstadoPedido, string> = {
  nuevo: 'Nuevo',
  aceptado: 'Aceptado',
  en_preparacion: 'En preparación',
  listo: 'Listo',
  despachado: 'Despachado',
  entregado: 'Entregado',
  rechazado: 'Rechazado',
  cancelado: 'Cancelado',
}

export const TONO_PEDIDO: Record<
  EstadoPedido,
  'neutro' | 'listo' | 'proceso' | 'demorado' | 'ambar'
> = {
  nuevo: 'ambar',
  aceptado: 'proceso',
  en_preparacion: 'proceso',
  listo: 'listo',
  despachado: 'listo',
  entregado: 'neutro',
  rechazado: 'demorado',
  cancelado: 'demorado',
}

export const ETIQUETA_TIPO_PEDIDO: Record<TipoPedido, string> = {
  mesa: 'Salón',
  domicilio: 'Domicilio',
  llevar: 'Para llevar',
}

/** Color del cronometro segun lo que lleve esperando la comida. */
export function colorEspera(minutos: number): string {
  if (minutos >= UMBRALES_COCINA.demorado) return 'text-estado-demorado'
  if (minutos >= UMBRALES_COCINA.atencion) return 'text-estado-proceso'
  return 'text-estado-listo'
}

/** Fondo y borde del cronometro, para que se lea desde un metro de distancia. */
export function fondoEspera(minutos: number): string {
  if (minutos >= UMBRALES_COCINA.demorado)
    return 'bg-estado-demorado text-noche-950 border-estado-demorado'
  if (minutos >= UMBRALES_COCINA.atencion)
    return 'bg-estado-proceso text-noche-950 border-estado-proceso'
  return 'bg-estado-listo-suave text-estado-listo border-estado-listo/50'
}
