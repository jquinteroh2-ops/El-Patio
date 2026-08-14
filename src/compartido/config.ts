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

/** Retardo artificial de mockApi, para que la interfaz se comporte como con red real. */
export const LATENCIA_MS = { min: 150, max: 300 } as const

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

export const CLAVE_ALMACEN = 'elpatio.bd.v1'
export const CLAVE_SESION = 'elpatio.sesion.v1'
export const CLAVE_COLA = 'elpatio.cola.v1'
export const CANAL_SYNC = 'elpatio.sync.v1'

export const ETIQUETA_DEMO = 'Versión de demostración'
