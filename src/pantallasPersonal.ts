/**
 * Todas las pantallas del personal, en un solo paquete aparte.
 *
 * El cliente que abre el sitio desde Google no tiene por que descargar la
 * comandera, la cocina, la caja y el panel administrativo: era mas de la mitad
 * del codigo y lo pagaba en cada visita, en datos y en espera. `App.tsx` carga
 * este modulo solo cuando alguien entra a una ruta del personal.
 *
 * Va todo junto y no pantalla por pantalla a proposito: la tablet del mesero
 * tiene que poder abrir cualquier pantalla sin WiFi, y eso solo pasa si el
 * service worker ya guardo su codigo. Con un solo paquete, la primera pantalla
 * del personal que se abre deja guardadas todas las demas.
 */
export { default as MapaMesas } from '@/comandera/MapaMesas'
export { default as OrdenMesa } from '@/comandera/OrdenMesa'
export { default as SelectorProductos } from '@/comandera/SelectorProductos'
export { default as CuentaMesa } from '@/comandera/CuentaMesa'
export { default as PantallaCocina } from '@/cocina/PantallaCocina'
export { default as PantallaPedidos } from '@/recepcion/PantallaPedidos'
export { default as PantallaReservas } from '@/recepcion/PantallaReservas'
export { default as PantallaAjustes } from '@/recepcion/PantallaAjustes'
export { default as PantallaRepartidor } from '@/repartidor/PantallaRepartidor'
export { default as LayoutAdmin } from '@/admin/LayoutAdmin'
export { default as InicioAdmin } from '@/admin/InicioAdmin'
export { default as Reservas } from '@/admin/Reservas'
export { default as CartaAdmin } from '@/admin/CartaAdmin'
export { default as Publicaciones } from '@/admin/Publicaciones'
export { default as Ventas } from '@/admin/Ventas'
export { default as Cierre } from '@/admin/Cierre'
export { default as Caja } from '@/admin/Caja'
export { default as Conciliacion } from '@/admin/Conciliacion'
export { default as Papelera } from '@/admin/Papelera'
export { default as Postulaciones } from '@/admin/Postulaciones'
export { default as PqrAdmin } from '@/admin/Pqr'
export { default as InstitucionalAdmin } from '@/admin/Institucional'
export { default as Reportes } from '@/admin/Reportes'
export { default as Configuracion } from '@/admin/Configuracion'
