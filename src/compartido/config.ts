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
// Domicilios y para llevar
// ---------------------------------------------------------------------------

/**
 * Las zonas de domicilio NO estan aqui.
 *
 * Viven en la base y se administran desde /admin/configuracion, porque el dueno
 * sube la tarifa de una zona cuando sube la gasolina y no puede necesitar un
 * despliegue para eso. Lo que queda en este archivo es lo que la interfaz
 * necesita saber sin preguntarle al servidor.
 */

/** Minutos que se le promete al cliente cuando la zona no dice otra cosa. */
export const MINUTOS_ESTIMADOS_POR_DEFECTO = 40

/**
 * Monto minimo de referencia que se muestra en el sitio publico antes de que
 * el cliente escoja barrio. El que manda es el de la zona, que valida el
 * servidor: este solo evita que la pagina prometa algo distinto.
 */
export const MONTO_MINIMO_DOMICILIO = 30000

/** Minutos que lleva un pedido esperando antes de que recepcion deba mirarlo. */
export const UMBRAL_ALERTA_PEDIDO = 10

/** Un celular colombiano tiene diez digitos y empieza por 3. */
export const DIGITOS_TELEFONO = 10

/** Como se nombra cada canal en pantalla. */
export const ETIQUETA_CANAL = {
  mesa: 'Salón',
  domicilio: 'Domicilio',
  llevar: 'Para llevar',
} as const

/** Los estados de un pedido, en el orden en que ocurren. */
export const ESTADOS_PEDIDO = [
  { estado: 'nuevo', etiqueta: 'Nuevos' },
  { estado: 'aceptado', etiqueta: 'Aceptados' },
  { estado: 'en_preparacion', etiqueta: 'En preparación' },
  { estado: 'listo', etiqueta: 'Listos' },
  { estado: 'despachado', etiqueta: 'Despachados' },
] as const

/** Motivos frecuentes de rechazo. Siempre se puede escribir otro. */
export const MOTIVOS_RECHAZO = [
  'La cocina está saturada en este momento',
  'No tenemos disponible lo que pidió',
  'No llegamos a esa dirección',
  'No pudimos confirmar el pedido por teléfono',
] as const

// ---------------------------------------------------------------------------
// Impresion
// ---------------------------------------------------------------------------

/**
 * Datos del establecimiento que van en el comprobante.
 *
 * `resolucion` y `prefijo` estan vacios a proposito: este documento NO es una
 * factura electronica ante la DIAN, es un comprobante interno de venta. El
 * espacio queda previsto para cuando el restaurante se habilite; hasta que la
 * DIAN entregue una resolucion real, aqui no puede aparecer un numero.
 */
export const DATOS_FISCALES = {
  razonSocial: 'Restaurante El Patio S.A.S.',
  nit: '901.234.567-8',
  regimen: 'Responsable de impuesto al consumo',
  responsabilidad: 'No responsable de IVA',
  /** Lo entrega la DIAN al habilitarse. Nunca se inventa. */
  resolucion: '',
  prefijo: '',
  /** Texto que la ley exige mientras no haya facturacion electronica. */
  leyenda: 'Este documento no es una factura electrónica de venta. Comprobante interno.',
} as const

/** Ancho del rollo de la impresora termica, en milimetros. */
export const ANCHO_TICKET_MM = 80

/** Si la comanda sale sola a cocina al enviar el turno. */
export const IMPRIMIR_COMANDA_AUTOMATICO = false

/** Copias del comprobante de venta. Una para el cliente, otra para la caja. */
export const COPIAS_COMPROBANTE = 1

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

