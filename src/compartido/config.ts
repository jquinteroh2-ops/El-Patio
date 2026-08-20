/**
 * Todo lo que un dueno distinto querria cambiar sin tocar componentes.
 */

export const RESTAURANTE = {
  nombre: 'El Patio',
  nombreCompleto: 'Restaurante El Patio',
  descripcionCorta: 'Cocina de fusion y coctelería de autor',
  direccion: 'Calle 26 #31-2',
  ciudad: 'Turbaco, Bolívar',
  /** Numero de contacto del restaurante. Cambiar por el real antes de publicar. */
  telefono: '+57 300 000 0000',
  /** Formato internacional sin signos, como lo exige wa.me */
  whatsapp: '573000000000',
  instagram: 'elpatiorestaurante_turbaco',
  horario: [
    { dias: 'Martes a Jueves', horas: '12:00 m. – 10:00 p. m.' },
    { dias: 'Viernes y Sábado', horas: '12:00 m. – 12:00 a. m.' },
    { dias: 'Domingo', horas: '12:00 m. – 9:00 p. m.' },
    { dias: 'Lunes', horas: 'Cerrado' },
  ],
} as const

/**
 * A los restaurantes en Colombia les aplica el Impuesto Nacional al Consumo,
 * no IVA. Se calcula sobre el subtotal de alimentos y bebidas, antes de propina.
 * Configurable porque no todos los establecimientos lo cobran igual.
 */
export const INC_POR_DEFECTO = 8

/**
 * La propina en Colombia es voluntaria y debe consultarse antes de incluirla.
 * Estos son solo sugerencias que el mesero ofrece; nunca se aplican solas.
 */
export const PROPINAS_SUGERIDAS = [0, 5, 10] as const

/** Umbrales de demora en la pantalla de cocina, en minutos. */
export const UMBRALES_COCINA = {
  atencion: 10,
  demorado: 20,
} as const

/** Minutos sin cobrar que disparan alerta en el panel administrativo. */
export const UMBRAL_ALERTA_ADMIN = 20

/** Notas rapidas de un toque en la comandera. */
export const NOTAS_RAPIDAS = [
  'Sin sal',
  'Sin cebolla',
  'Sin picante',
  'Para compartir',
  'Sin hielo',
  'Servir al final',
  'Alergia: mariscos',
  'Alergia: gluten',
] as const

/** Cargos adicionales frecuentes. Siempre los agrega el mesero a mano. */
export const CARGOS_FRECUENTES = [
  { nombre: 'Decoración de cumpleaños', valor: 45000 },
  { nombre: 'Servicio de descorche', valor: 35000 },
  { nombre: 'Torta porcionada', valor: 12000 },
  { nombre: 'Reserva de zona privada', valor: 80000 },
] as const

// ---------------------------------------------------------------------------
// Conexion con el backend
// ---------------------------------------------------------------------------

/** Quita la barra final para que al concatenar rutas no queden dos seguidas. */
const sinBarraFinal = (url: string): string => url.replace(/\/+$/, '')

/**
 * URL del API.
 *
 * Nunca se escribe una URL en un componente. En desarrollo apunta al backend
 * local; en Railway la define la variable de entorno del servicio del frontend,
 * porque el dominio cambia entre entornos y el codigo no puede saberlo.
 */
export const URL_API = sinBarraFinal(import.meta.env.VITE_URL_API ?? 'http://localhost:8080')

/**
 * URL del WebSocket.
 *
 * Si no viene declarada se deduce de la del API cambiando el esquema: sobre
 * HTTPS tiene que ser wss, porque un navegador en una pagina segura rechaza
 * abrir un socket en claro y el salon se quedaria sin tiempo real.
 */
export const URL_WS =
  import.meta.env.VITE_URL_WS ?? `${URL_API.replace(/^http/, 'ws')}/ws`

/**
 * Topicos del canal de tiempo real. Tienen que coincidir con Topicos.java: si
 * uno cambia de nombre, la pantalla que lo escuchaba se queda muda sin que nada
 * falle de forma visible.
 */
export const TOPICOS = {
  comandas: '/topic/comandas',
  mesas: '/topic/mesas',
  pedidos: '/topic/pedidos',
  general: '/topic/general',
} as const

/**
 * Margen con el que se renueva el token antes de que expire.
 *
 * Se renueva antes y no al fallar para que el mesero nunca vea un error de
 * sesion en medio de una comanda: cuando el token esta por vencer, ya hay otro.
 */
export const MARGEN_RENOVACION_SEGUNDOS = 120

export const CLAVE_SESION = 'elpatio.sesion.v1'
export const CLAVE_ACCESO = 'elpatio.acceso.v1'
export const CLAVE_REFRESCO = 'elpatio.refresco.v1'
export const CLAVE_COLA = 'elpatio.cola.v1'

export const ETIQUETA_DEMO = 'Versión de demostración'
