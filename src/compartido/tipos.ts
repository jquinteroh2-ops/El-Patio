/**
 * Modelo de datos del sistema. Es la unica definicion de las entidades:
 * si algo cambia aqui, el resto del sistema deja de compilar hasta ajustarse.
 */

// ---------------------------------------------------------------------------
// Personal
// ---------------------------------------------------------------------------

export type Rol = 'mesero' | 'cocina' | 'cajero' | 'administrador'

export interface Usuario {
  id: string
  nombre: string
  rol: Rol
  usuario: string
  clave: string
  activo: boolean
}

/** Lo que se guarda en sessionStorage: nunca incluye la clave. */
export interface Sesion {
  usuarioId: string
  nombre: string
  rol: Rol
  usuario: string
  iniciadaEn: string
}

// ---------------------------------------------------------------------------
// Salon
// ---------------------------------------------------------------------------

export type Zona = 'salon' | 'terraza' | 'privado'

export type EstadoMesa = 'libre' | 'ocupada' | 'cuenta_pedida' | 'reservada'

export interface Mesa {
  id: string
  numero: number
  /** Nombre de sala si lo tiene: "Terraza 3", "Privado 2". */
  nombre?: string
  zona: Zona
  capacidad: number
  estado: EstadoMesa
  /** Mesero que la atiende. */
  meseroId?: string
  ordenActivaId?: string
}

// ---------------------------------------------------------------------------
// Carta
// ---------------------------------------------------------------------------

/** A donde se imprime la comanda: cocina o bar. */
export type Destino = 'cocina' | 'bar'

export interface CategoriaCarta {
  id: string
  nombre: string
  orden: number
}

export type TipoModificador = 'seleccion_unica' | 'seleccion_multiple' | 'texto_libre'

export interface OpcionModificador {
  nombre: string
  precioAdicional: number
}

export interface Modificador {
  id: string
  nombre: string
  tipo: TipoModificador
  opciones?: OpcionModificador[]
  obligatorio: boolean
}

export interface ItemCarta {
  id: string
  categoriaId: string
  nombre: string
  descripcion: string
  precio: number
  /** Agotar un plato debe ser un solo clic desde /admin/carta. */
  disponible: boolean
  tiempoPreparacionMin: number
  destino: Destino
  modificadores?: Modificador[]
}

// ---------------------------------------------------------------------------
// Comandas
// ---------------------------------------------------------------------------

export type EstadoOrden =
  | 'abierta'
  | 'enviada'
  | 'en_preparacion'
  | 'servida'
  | 'cuenta_pedida'
  | 'pagada'
  | 'anulada'

export type EstadoItem = 'pendiente' | 'en_preparacion' | 'listo' | 'servido' | 'anulado'

export interface ModificadorSeleccionado {
  nombre: string
  valor: string
  precioAdicional: number
}

export interface ItemOrden {
  id: string
  itemCartaId: string
  nombre: string
  precioUnitario: number
  cantidad: number
  modificadoresSeleccionados: ModificadorSeleccionado[]
  /** "sin sal", "para compartir", "termino tres cuartos". */
  notaCocina?: string
  estado: EstadoItem
  destino: Destino
  enviadoEn?: string
  listoEn?: string
  /** Entradas van en turno 1, fuertes en turno 2. 0 = todavia sin enviar. */
  turnoEnvio: number
}

export interface CargoAdicional {
  id: string
  nombre: string
  valor: number
  /** Nombre del usuario que lo agrego: nada entra a la cuenta sin responsable. */
  agregadoPor: string
  agregadoEn: string
}

export interface Orden {
  id: string
  mesaId: string
  meseroId: string
  /** Consecutivo del dia. */
  numero: number
  estado: EstadoOrden
  items: ItemOrden[]
  cargosAdicionales: CargoAdicional[]
  comensales: number
  abiertaEn: string
  cerradaEn?: string
  notas?: string
}

// ---------------------------------------------------------------------------
// Cobro
// ---------------------------------------------------------------------------

export type MetodoPago = 'efectivo' | 'tarjeta' | 'transferencia' | 'mixto'

export interface DivisionPago {
  nombre: string
  valor: number
  metodo: Exclude<MetodoPago, 'mixto'>
}

export interface Pago {
  id: string
  ordenId: string
  subtotal: number
  inc: number
  propina: number
  cargosAdicionales: number
  total: number
  metodo: MetodoPago
  divisiones?: DivisionPago[]
  recibidoPor: string
  fechaHora: string
}

// ---------------------------------------------------------------------------
// Reservas
// ---------------------------------------------------------------------------

export type Ocasion = 'cumpleanos' | 'aniversario' | 'negocios' | 'ninguna'

export type EstadoReserva = 'solicitada' | 'confirmada' | 'cancelada' | 'cumplida' | 'no_asistio'

export interface Reserva {
  id: string
  nombreCliente: string
  telefono: string
  fechaHora: string
  personas: number
  ocasion?: Ocasion
  estado: EstadoReserva
  notas?: string
  mesaAsignadaId?: string
}

// ---------------------------------------------------------------------------
// Caja
// ---------------------------------------------------------------------------

export type Turno = 'almuerzo' | 'cena'

export interface CierreCaja {
  id: string
  /** aaaa-mm-dd */
  fecha: string
  turno: Turno
  ventaTotal: number
  totalEfectivo: number
  totalTarjeta: number
  totalTransferencia: number
  propinasTotales: number
  incTotal: number
  ordenesAtendidas: number
  ticketPromedio: number
  cerradoPor: string
  fechaHora: string
}

// ---------------------------------------------------------------------------
// Estado global persistido
// ---------------------------------------------------------------------------

/**
 * Todo lo que vive en localStorage y se comparte entre pestanas.
 * Solo mockApi lo lee y lo escribe.
 */
export interface BaseDatos {
  version: number
  usuarios: Usuario[]
  mesas: Mesa[]
  categorias: CategoriaCarta[]
  carta: ItemCarta[]
  ordenes: Orden[]
  pagos: Pago[]
  reservas: Reserva[]
  cierres: CierreCaja[]
  ajustes: Ajustes
}

export interface Ajustes {
  /** Impuesto Nacional al Consumo, en porcentaje. Configurable por establecimiento. */
  porcentajeInc: number
  /** Interruptor de demostracion: simula perdida de WiFi en la comandera. */
  simularSinConexion: boolean
  /** Consecutivo de comandas del dia. */
  consecutivoOrden: number
  /** Fecha del consecutivo, para reiniciarlo cada dia. */
  fechaConsecutivo: string
}

/** Elemento de la cola local de envios pendientes cuando no hay conexion. */
export interface EnvioPendiente {
  id: string
  ordenId: string
  mesaId: string
  turnoEnvio: number
  itemIds: string[]
  encoladoEn: string
  intentos: number
}
