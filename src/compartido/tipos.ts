/**
 * Modelo de datos del sistema. Es la unica definicion de las entidades:
 * si algo cambia aqui, el resto del sistema deja de compilar hasta ajustarse.
 */

// ---------------------------------------------------------------------------
// Personal
// ---------------------------------------------------------------------------

export type Rol =
  | 'mesero'
  | 'cocina'
  | 'recepcion'
  /** Sale a la calle con el pedido y confirma la entrega desde su telefono. */
  | 'repartidor'
  | 'cajero'
  | 'administrador'

export interface Usuario {
  id: string
  nombre: string
  rol: Rol
  usuario: string
  /** Solo se escribe. El backend nunca devuelve el hash, asi que llega vacia. */
  clave: string
  /**
   * Correo de contacto, opcional. No es la credencial: se entra con `usuario`,
   * que es corto y se escribe rapido en una tablet.
   */
  correo?: string
  activo: boolean
}

/** Lo que se guarda en sessionStorage: nunca incluye la clave. */
/**
 * Una cuenta que la pantalla de acceso ofrece durante una demostracion.
 *
 * La clave no viaja aqui sino una sola vez en `CuentasDemostracion`: las seis
 * cuentas comparten la misma, que es justo lo que hace comoda una demostracion
 * e inaceptable un turno de verdad.
 */
export interface CuentaDemostracion {
  usuario: string
  nombre: string
  rol: Rol
  /** A que pantalla lleva: se lee bajo el nombre del rol. */
  destino: string
}

export interface CuentasDemostracion {
  activa: boolean
  clave: string
  cuentas: CuentaDemostracion[]
}

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

/** Que esta publicando el restaurante. Decide donde sale en el sitio. */
export type TipoPublicacion = 'promocion' | 'evento' | 'galeria'

/**
 * Una promocion, un evento o una foto del local.
 *
 * `publicada` y la vigencia son cosas distintas: el dueno prepara el martes la
 * promocion del viernes y la deja sin publicar, y una vencida deja de mostrarse
 * sola. Al cliente solo le llega lo que cumple las dos, y ese filtro lo hace el
 * servidor: lo que no esta vigente no viaja.
 */
export interface Publicacion {
  id: string
  tipo: TipoPublicacion
  titulo: string
  cuerpo: string
  /** Nombre del archivo en el almacen. Puede no haber foto. */
  imagen?: string | null
  /** aaaa-mm-dd */
  desde?: string | null
  hasta?: string | null
  publicada: boolean
  orden: number
  creadaEn?: string
}

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
  /** El precio de lista. Sigue siendo este aunque haya promocion. */
  precio: number
  /**
   * Precio de promocion, o nulo si el plato se vende al de lista.
   *
   * Es un PRECIO y no un descuento, y esa diferencia sostiene todo lo demas:
   * la venta ocurre a este valor, y el INC, la propina y el documento ante la
   * DIAN se calculan sobre el sin ninguna regla aparte. Una linea de descuento
   * en la cuenta obligaria a repartir la rebaja entre las lineas gravadas para
   * que la base gravable declarada siguiera cuadrando.
   */
  precioPromocional?: number | null
  /** aaaa-mm-dd. Nulo en los dos extremos: mientras el precio este puesto. */
  promocionDesde?: string | null
  promocionHasta?: string | null
  /** Agotar un plato debe ser un solo clic desde /admin/carta. */
  disponible: boolean
  tiempoPreparacionMin: number
  destino: Destino
  modificadores?: Modificador[]
}

// ---------------------------------------------------------------------------
// Canal del pedido
// ---------------------------------------------------------------------------

/**
 * Por donde entro la venta.
 *
 * Un domicilio no es una entidad distinta de una comanda: es una comanda con
 * otro canal. Por eso cocina lo ve igual que el de una mesa, la caja lo suma
 * sin logica aparte y el INC se calcula con la misma regla.
 */
export type TipoPedido = 'mesa' | 'domicilio' | 'llevar'

/**
 * El recorrido de un pedido externo, en el orden en que ocurre.
 *
 * Es distinto de EstadoOrden: aquel sigue la cocina, este sigue al cliente. Un
 * pedido puede estar `despachado` mientras su comanda ya esta `servida`.
 */
export type EstadoPedido =
  | 'nuevo'
  | 'aceptado'
  | 'en_preparacion'
  | 'listo'
  | 'despachado'
  | 'entregado'
  | 'rechazado'
  | 'cancelado'

/**
 * Donde hay que llevar el pedido, en coordenadas.
 *
 * Complementa la direccion escrita, no la reemplaza. `precisionMetros` es el
 * radio dentro del cual el navegador afirma que esta el punto: un GPS da unos
 * quince metros y una posicion deducida de la IP da dos kilometros, y la
 * segunda parece precisa sin serlo.
 */
export interface UbicacionEntrega {
  latitud: number
  longitud: number
  precisionMetros?: number
}

/** Quien pidio desde fuera del salon. */
export interface ClienteExterno {
  nombre: string
  telefono: string
  /** Obligatoria en domicilio; en para llevar no aplica. */
  direccion?: string
  barrio?: string
}

/** Un barrio al que se lleva, con lo que cuesta y lo que tarda llegar. */
export interface ZonaDomicilio {
  id: string
  nombre: string
  /** Lo que se le cobra al cliente por el envio. No causa INC ni propina. */
  tarifa: number
  /** Por debajo de esto el envio se comeria el margen del pedido. */
  montoMinimo: number
  minutosEstimados: number
  activa: boolean
  orden: number
}

/** Lo que el sitio publico necesita saber antes de dejar pedir. */
export interface EstadoCanal {
  abierto: boolean
  pausado: boolean
  /** hh:mm:ss en la hora del restaurante. */
  desde: string
  hasta: string
  zonas: ZonaDomicilio[]
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
  /**
   * Los pedidos de domicilio y para llevar no tienen mesa, y hasta que
   * recepcion los acepta tampoco tienen a quien atribuirselos. Por eso los dos
   * campos son opcionales: no romper las ordenes de mesa era la condicion.
   */
  mesaId?: string
  meseroId?: string
  /** Consecutivo del dia. */
  numero: number
  estado: EstadoOrden
  items: ItemOrden[]
  cargosAdicionales: CargoAdicional[]
  comensales: number
  abiertaEn: string
  cerradaEn?: string
  notas?: string

  // --- Canal ---------------------------------------------------------------

  /** 'mesa' en todo lo que viene del salon. */
  tipo: TipoPedido
  /** Solo en pedidos externos: es el recorrido que ve recepcion. */
  estadoPedido?: EstadoPedido
  cliente?: ClienteExterno
  /** Solo si el cliente autorizo compartirla. Nunca es obligatoria. */
  ubicacion?: UbicacionEntrega
  zonaDomicilioId?: string
  /** Linea aparte DESPUES del impuesto: el envio no causa INC ni propina. */
  costoEnvio?: number
  /** Lo que el cliente dijo que pensaba pagar. El cobro real vive en Pago. */
  metodoPagoPrevisto?: MetodoPago
  minutosEstimados?: number
  repartidor?: string
  /** Solo si quien lo lleva tiene cuenta: es lo que le muestra sus entregas. */
  repartidorId?: string
  motivoRechazo?: string
  /** Cuando entro el pedido. El cronometro de recepcion cuenta desde aqui. */
  recibidoEn?: string

  /** Anulacion auditable de una comanda ya cobrada. */
  motivoAnulacion?: string
  ordenReemplazoId?: string
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
  /** Lo cobrado por llevarlo. Se guarda con el pago y no se recalcula. */
  costoEnvio?: number
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
  /** Venta por canal. La suma de los tres es `ventaTotal`. */
  totalSalon: number
  totalDomicilio: number
  totalLlevar: number
  /** Lo cobrado por envios, ya incluido dentro de `totalDomicilio`. */
  totalEnvios: number
  /**
   * Lo que el contador necesita para declarar, guardado con el cierre.
   *
   * Se guarda y no se recalcula: el INC se declara cada dos meses, y para
   * entonces las comandas de ese turno pueden haber cambiado de estado.
   */
  baseGravable: number
  baseNoGravada: number
  totalCargos: number
  porcentajeInc: number
  descuentos: number
  comensales: number
  lineasAnuladas: number
  valorAnulado: number
  cerradoPor: string
  fechaHora: string
}

// ---------------------------------------------------------------------------
// Ajustes del establecimiento
// ---------------------------------------------------------------------------

export interface Ajustes {
  /** Impuesto Nacional al Consumo, en porcentaje. Configurable por establecimiento. */
  porcentajeInc: number
  /** Consecutivo de comandas del dia. */
  consecutivoOrden: number
  /** Fecha del consecutivo, para reiniciarlo cada dia. */
  fechaConsecutivo: string
  /** Cierra el canal de pedidos cuando la cocina esta saturada. */
  domiciliosPausados: boolean
  /** Franja en que se reciben pedidos, hh:mm:ss en la hora del restaurante. */
  domiciliosDesde: string
  domiciliosHasta: string
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
