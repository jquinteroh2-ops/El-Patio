import { agruparPorTurno, calcularCuenta, itemsSinEnviar, proximoTurno, totalCorriente } from './calculos'
import { CLAVE_ALMACEN, LATENCIA_MS } from './config'
import { claveDia, franjaHoraria, turnoDe } from './formato'
import { guardarBD, leerBD } from './almacen'
import { crearBaseInicial } from './datosSemilla'
import { SinConexionError, encolar, hayConexion, leerCola, quitarDeCola } from './conexion'
import type {
  Ajustes,
  BaseDatos,
  CargoAdicional,
  CategoriaCarta,
  CierreCaja,
  Destino,
  DivisionPago,
  EstadoItem,
  EstadoReserva,
  ItemCarta,
  ItemOrden,
  Mesa,
  MetodoPago,
  ModificadorSeleccionado,
  Orden,
  Pago,
  Reserva,
  Rol,
  Sesion,
  Usuario,
} from './tipos'

/**
 * Unica superficie de datos del sistema.
 *
 * Ningun componente lee ni escribe los arreglos directamente: todo pasa por
 * aqui. El dia que exista un backend, este archivo se reemplaza por llamadas
 * HTTP y ninguna pantalla cambia.
 *
 * Cada funcion es asincrona y tarda entre 150 y 300 ms a proposito, para que la
 * interfaz se disene contra la latencia que tendra en produccion.
 */

// ---------------------------------------------------------------------------
// Infraestructura
// ---------------------------------------------------------------------------

const esperar = (): Promise<void> =>
  new Promise((r) =>
    setTimeout(r, LATENCIA_MS.min + Math.random() * (LATENCIA_MS.max - LATENCIA_MS.min)),
  )

const clonar = <T>(valor: T): T => JSON.parse(JSON.stringify(valor)) as T

const nuevoId = (prefijo: string): string =>
  `${prefijo}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`

/** Lee la base y la siembra la primera vez que se abre la aplicacion. */
function base(): BaseDatos {
  const guardada = leerBD()
  if (guardada) return guardada
  const inicial = crearBaseInicial()
  guardarBD(inicial, ['todo'])
  return leerBD() ?? inicial
}

/** Aplica un cambio sobre la base y avisa a todas las pestanas. */
function escribir<T>(cambios: string[], mutador: (bd: BaseDatos) => T): T {
  const bd = clonar(base())
  const resultado = mutador(bd)
  guardarBD(bd, cambios)
  return resultado
}

const buscarOrden = (bd: BaseDatos, ordenId: string): Orden => {
  const orden = bd.ordenes.find((o) => o.id === ordenId)
  if (!orden) throw new Error('La comanda ya no existe')
  return orden
}

const buscarMesa = (bd: BaseDatos, mesaId: string): Mesa => {
  const mesa = bd.mesas.find((m) => m.id === mesaId)
  if (!mesa) throw new Error('La mesa no existe')
  return mesa
}

/** Consecutivo de comandas del dia, que se reinicia cada jornada. */
function siguienteConsecutivo(bd: BaseDatos): number {
  const hoy = claveDia()
  if (bd.ajustes.fechaConsecutivo !== hoy) {
    bd.ajustes.fechaConsecutivo = hoy
    bd.ajustes.consecutivoOrden = 1
  } else {
    bd.ajustes.consecutivoOrden += 1
  }
  return bd.ajustes.consecutivoOrden
}

/** Recalcula el estado de la comanda a partir del estado de sus items. */
function sincronizarEstadoOrden(orden: Orden): void {
  if (orden.estado === 'pagada' || orden.estado === 'anulada' || orden.estado === 'cuenta_pedida') return
  const enviados = orden.items.filter((i) => i.turnoEnvio > 0 && i.estado !== 'anulado')
  if (enviados.length === 0) {
    orden.estado = 'abierta'
    return
  }
  if (enviados.every((i) => i.estado === 'servido')) orden.estado = 'servida'
  else if (enviados.some((i) => i.estado === 'en_preparacion' || i.estado === 'listo'))
    orden.estado = 'en_preparacion'
  else orden.estado = 'enviada'
}

// ---------------------------------------------------------------------------
// Arranque y demostracion
// ---------------------------------------------------------------------------

export async function inicializar(): Promise<void> {
  base()
  await esperar()
}

/** Vuelve a sembrar el salon con datos frescos calculados contra la hora actual. */
export async function reiniciarDemo(): Promise<void> {
  await esperar()
  localStorage.removeItem(CLAVE_ALMACEN)
  guardarBD(crearBaseInicial(), ['todo'])
}

// ---------------------------------------------------------------------------
// Acceso
// ---------------------------------------------------------------------------

export async function autenticar(usuario: string, clave: string): Promise<Sesion> {
  await esperar()
  const encontrado = base().usuarios.find(
    (u) => u.usuario.toLowerCase() === usuario.trim().toLowerCase() && u.clave === clave && u.activo,
  )
  if (!encontrado) throw new Error('Usuario o contraseña incorrectos')
  return {
    usuarioId: encontrado.id,
    nombre: encontrado.nombre,
    rol: encontrado.rol,
    usuario: encontrado.usuario,
    iniciadaEn: new Date().toISOString(),
  }
}

export async function listarUsuarios(): Promise<Usuario[]> {
  await esperar()
  return clonar(base().usuarios)
}

export async function guardarUsuario(usuario: Usuario): Promise<Usuario> {
  await esperar()
  return escribir(['usuarios'], (bd) => {
    const indice = bd.usuarios.findIndex((u) => u.id === usuario.id)
    if (indice >= 0) {
      bd.usuarios[indice] = usuario
      return usuario
    }
    const nuevo = { ...usuario, id: nuevoId('u') }
    bd.usuarios.push(nuevo)
    return nuevo
  })
}

// ---------------------------------------------------------------------------
// Ajustes
// ---------------------------------------------------------------------------

export async function obtenerAjustes(): Promise<Ajustes> {
  await esperar()
  return clonar(base().ajustes)
}

export async function actualizarAjustes(cambios: Partial<Ajustes>): Promise<Ajustes> {
  await esperar()
  return escribir(['ajustes'], (bd) => {
    bd.ajustes = { ...bd.ajustes, ...cambios }
    return clonar(bd.ajustes)
  })
}

/** Interruptor de demostracion de la caida de WiFi. Sin latencia: debe ser instantaneo. */
export function alternarSinConexion(activo: boolean): void {
  escribir(['ajustes', 'conexion'], (bd) => {
    bd.ajustes.simularSinConexion = activo
  })
}

// ---------------------------------------------------------------------------
// Salon
// ---------------------------------------------------------------------------

/** Mesa con lo que la comandera necesita pintar en el mapa. */
export interface MesaEnMapa extends Mesa {
  ordenNumero?: number
  abiertaEn?: string
  total: number
  comensales: number
  itemsPendientes: number
  itemsListos: number
  meseroNombre?: string
}

export async function listarMesas(): Promise<MesaEnMapa[]> {
  await esperar()
  const bd = base()
  return bd.mesas.map((mesa) => {
    const orden = mesa.ordenActivaId ? bd.ordenes.find((o) => o.id === mesa.ordenActivaId) : undefined
    const vigentes = orden?.items.filter((i) => i.estado !== 'anulado') ?? []
    return {
      ...mesa,
      ordenNumero: orden?.numero,
      abiertaEn: orden?.abiertaEn,
      comensales: orden?.comensales ?? 0,
      total: orden ? totalCorriente(orden, bd.ajustes.porcentajeInc) : 0,
      itemsPendientes: vigentes.filter((i) => i.estado === 'pendiente' || i.estado === 'en_preparacion').length,
      itemsListos: vigentes.filter((i) => i.estado === 'listo').length,
      meseroNombre: bd.usuarios.find((u) => u.id === mesa.meseroId)?.nombre,
    }
  })
}

export async function guardarMesa(mesa: Mesa): Promise<Mesa> {
  await esperar()
  return escribir(['mesas'], (bd) => {
    const indice = bd.mesas.findIndex((m) => m.id === mesa.id)
    if (indice >= 0) bd.mesas[indice] = mesa
    else bd.mesas.push({ ...mesa, id: nuevoId('m') })
    return mesa
  })
}

export async function eliminarMesa(mesaId: string): Promise<void> {
  await esperar()
  escribir(['mesas'], (bd) => {
    const mesa = buscarMesa(bd, mesaId)
    if (mesa.ordenActivaId) throw new Error('No se puede eliminar una mesa con cuenta abierta')
    bd.mesas = bd.mesas.filter((m) => m.id !== mesaId)
  })
}

// ---------------------------------------------------------------------------
// Carta
// ---------------------------------------------------------------------------

export async function listarCategorias(): Promise<CategoriaCarta[]> {
  await esperar()
  return clonar(base().categorias).sort((a, b) => a.orden - b.orden)
}

export async function listarCarta(): Promise<ItemCarta[]> {
  await esperar()
  return clonar(base().carta)
}

/** Carta agrupada por categoria, tal como la lee el cliente y el mesero. */
export interface CategoriaConItems extends CategoriaCarta {
  items: ItemCarta[]
}

export async function cartaAgrupada(soloDisponibles = false): Promise<CategoriaConItems[]> {
  await esperar()
  const bd = base()
  return clonar(bd.categorias)
    .sort((a, b) => a.orden - b.orden)
    .map((categoria) => ({
      ...categoria,
      items: bd.carta.filter(
        (i) => i.categoriaId === categoria.id && (!soloDisponibles || i.disponible),
      ),
    }))
    .filter((c) => c.items.length > 0)
}

export async function cambiarDisponibilidad(itemId: string, disponible: boolean): Promise<void> {
  await esperar()
  escribir(['carta'], (bd) => {
    const item = bd.carta.find((i) => i.id === itemId)
    if (!item) throw new Error('El producto no existe')
    item.disponible = disponible
  })
}

export async function guardarItemCarta(item: ItemCarta): Promise<ItemCarta> {
  await esperar()
  return escribir(['carta'], (bd) => {
    const indice = bd.carta.findIndex((i) => i.id === item.id)
    if (indice >= 0) {
      bd.carta[indice] = item
      return item
    }
    const nuevo = { ...item, id: nuevoId('p') }
    bd.carta.push(nuevo)
    return nuevo
  })
}

export async function eliminarItemCarta(itemId: string): Promise<void> {
  await esperar()
  escribir(['carta'], (bd) => {
    bd.carta = bd.carta.filter((i) => i.id !== itemId)
  })
}

export async function guardarCategoria(categoria: CategoriaCarta): Promise<CategoriaCarta> {
  await esperar()
  return escribir(['carta'], (bd) => {
    const indice = bd.categorias.findIndex((c) => c.id === categoria.id)
    if (indice >= 0) {
      bd.categorias[indice] = categoria
      return categoria
    }
    const nueva = { ...categoria, id: nuevoId('c') }
    bd.categorias.push(nueva)
    return nueva
  })
}

// ---------------------------------------------------------------------------
// Comandas
// ---------------------------------------------------------------------------

export interface OrdenDetallada {
  orden: Orden
  mesa: Mesa
  meseroNombre: string
  porcentajeInc: number
}

export async function obtenerOrdenDeMesa(mesaId: string): Promise<OrdenDetallada | null> {
  await esperar()
  const bd = base()
  const mesa = buscarMesa(bd, mesaId)
  if (!mesa.ordenActivaId) return null
  const orden = bd.ordenes.find((o) => o.id === mesa.ordenActivaId)
  if (!orden) return null
  return {
    orden: clonar(orden),
    mesa: clonar(mesa),
    meseroNombre: bd.usuarios.find((u) => u.id === orden.meseroId)?.nombre ?? 'Sin asignar',
    porcentajeInc: bd.ajustes.porcentajeInc,
  }
}

export async function abrirMesa(mesaId: string, meseroId: string, comensales: number): Promise<Orden> {
  await esperar()
  return escribir(['mesas', 'ordenes'], (bd) => {
    const mesa = buscarMesa(bd, mesaId)
    if (mesa.ordenActivaId) throw new Error('La mesa ya tiene una cuenta abierta')

    const orden: Orden = {
      id: nuevoId('ord'),
      mesaId,
      meseroId,
      numero: siguienteConsecutivo(bd),
      estado: 'abierta',
      items: [],
      cargosAdicionales: [],
      comensales,
      abiertaEn: new Date().toISOString(),
    }
    bd.ordenes.push(orden)
    mesa.estado = 'ocupada'
    mesa.meseroId = meseroId
    mesa.ordenActivaId = orden.id
    return clonar(orden)
  })
}

export async function cambiarComensales(ordenId: string, comensales: number): Promise<void> {
  await esperar()
  escribir(['ordenes'], (bd) => {
    buscarOrden(bd, ordenId).comensales = Math.max(1, comensales)
  })
}

export interface NuevoItem {
  itemCartaId: string
  cantidad: number
  modificadoresSeleccionados?: ModificadorSeleccionado[]
  notaCocina?: string
}

export async function agregarItems(ordenId: string, nuevos: NuevoItem[]): Promise<Orden> {
  await esperar()
  return escribir(['ordenes', 'mesas'], (bd) => {
    const orden = buscarOrden(bd, ordenId)
    for (const nuevo of nuevos) {
      const carta = bd.carta.find((i) => i.id === nuevo.itemCartaId)
      if (!carta) throw new Error('El producto no existe en la carta')
      if (!carta.disponible) throw new Error(`${carta.nombre} está agotado`)

      const modificadores = nuevo.modificadoresSeleccionados ?? []
      // Un mismo producto sin modificadores ni nota se acumula en una sola linea.
      const existente = orden.items.find(
        (i) =>
          i.itemCartaId === carta.id &&
          i.turnoEnvio === 0 &&
          i.estado !== 'anulado' &&
          (i.notaCocina ?? '') === (nuevo.notaCocina ?? '') &&
          JSON.stringify(i.modificadoresSeleccionados) === JSON.stringify(modificadores),
      )

      if (existente) {
        existente.cantidad += nuevo.cantidad
        continue
      }

      orden.items.push({
        id: nuevoId('io'),
        itemCartaId: carta.id,
        nombre: carta.nombre,
        precioUnitario: carta.precio,
        cantidad: nuevo.cantidad,
        modificadoresSeleccionados: modificadores,
        notaCocina: nuevo.notaCocina,
        estado: 'pendiente',
        destino: carta.destino,
        turnoEnvio: 0,
      })
    }
    return clonar(orden)
  })
}

export async function cambiarCantidad(ordenId: string, itemId: string, cantidad: number): Promise<void> {
  await esperar()
  escribir(['ordenes'], (bd) => {
    const orden = buscarOrden(bd, ordenId)
    const item = orden.items.find((i) => i.id === itemId)
    if (!item) throw new Error('El producto ya no está en la comanda')
    if (item.turnoEnvio > 0) throw new Error('Ese producto ya se envió: pídele anulación al administrador')
    if (cantidad <= 0) orden.items = orden.items.filter((i) => i.id !== itemId)
    else item.cantidad = cantidad
  })
}

export async function quitarItem(ordenId: string, itemId: string): Promise<void> {
  await cambiarCantidad(ordenId, itemId, 0)
}

/** Anula un producto ya enviado. Queda registrado, nunca desaparece de la comanda. */
export async function anularItem(ordenId: string, itemId: string, motivo: string): Promise<void> {
  await esperar()
  escribir(['ordenes', 'mesas'], (bd) => {
    const orden = buscarOrden(bd, ordenId)
    const item = orden.items.find((i) => i.id === itemId)
    if (!item) throw new Error('El producto ya no está en la comanda')
    item.estado = 'anulado'
    item.notaCocina = [item.notaCocina, `Anulado: ${motivo}`].filter(Boolean).join(' · ')
    sincronizarEstadoOrden(orden)
  })
}

export interface ResultadoEnvio {
  /** true cuando no habia conexion y la comanda quedo en la cola local. */
  encolado: boolean
  turno: number
  cantidadItems: number
}

/**
 * Manda a cocina y barra lo que todavia no se ha enviado.
 *
 * Sin conexion no se pierde nada: la comanda entra a la cola local y sale sola
 * apenas vuelve la senal. El mesero puede seguir tomando pedidos mientras tanto.
 */
export async function enviarACocina(ordenId: string): Promise<ResultadoEnvio> {
  await esperar()
  const bd = base()
  const orden = buscarOrden(bd, ordenId)

  // Lo que ya espera en la cola no se vuelve a encolar: sin esto, un mesero sin
  // senal que toca "Enviar" dos veces deja la misma comanda repetida.
  const yaEnCola = new Set(itemsEnCola(ordenId))
  const pendientes = itemsSinEnviar(orden).filter((i) => !yaEnCola.has(i.id))

  if (pendientes.length === 0) {
    throw new Error(
      yaEnCola.size > 0
        ? 'Esos productos ya están en cola y salen solos al volver la señal'
        : 'No hay productos nuevos para enviar',
    )
  }

  // La cola conserva el turno con el que se tomo el pedido, para que las
  // entradas no terminen saliendo despues de los fuertes al reconectar.
  const turnosEnCola = leerCola()
    .filter((e) => e.ordenId === ordenId)
    .map((e) => e.turnoEnvio)
  const turno = Math.max(proximoTurno(orden), ...turnosEnCola.map((t) => t + 1))

  if (!hayConexion()) {
    encolar({
      ordenId,
      mesaId: orden.mesaId,
      turnoEnvio: turno,
      itemIds: pendientes.map((i) => i.id),
    })
    return { encolado: true, turno, cantidadItems: pendientes.length }
  }

  aplicarEnvio(ordenId, pendientes.map((i) => i.id), turno)
  return { encolado: false, turno, cantidadItems: pendientes.length }
}

/** Productos de una comanda que esperan en la cola local por falta de senal. */
export function itemsEnCola(ordenId: string): string[] {
  return leerCola()
    .filter((e) => e.ordenId === ordenId)
    .flatMap((e) => e.itemIds)
}

function aplicarEnvio(ordenId: string, itemIds: string[], turno: number): number {
  return escribir(['ordenes', 'mesas', 'cocina'], (bd) => {
    const orden = buscarOrden(bd, ordenId)
    const ahora = new Date().toISOString()
    let enviados = 0
    for (const item of orden.items) {
      if (!itemIds.includes(item.id) || item.turnoEnvio > 0 || item.estado === 'anulado') continue
      item.turnoEnvio = turno
      item.enviadoEn = ahora
      item.estado = 'pendiente'
      enviados++
    }
    sincronizarEstadoOrden(orden)
    return enviados
  })
}

/** Vacia la cola local cuando vuelve la conexion. */
export async function procesarCola(): Promise<number> {
  if (!hayConexion()) return 0
  const cola = leerCola()
  let enviadas = 0
  for (const envio of cola) {
    try {
      aplicarEnvio(envio.ordenId, envio.itemIds, envio.turnoEnvio)
      quitarDeCola(envio.id)
      enviadas++
    } catch (error) {
      // La comanda ya no existe o cambio: se descarta para no bloquear la cola.
      console.error('[mockApi] no se pudo enviar una comanda encolada', error)
      quitarDeCola(envio.id)
    }
  }
  return enviadas
}

export async function agregarCargo(
  ordenId: string,
  nombre: string,
  valor: number,
  agregadoPor: string,
): Promise<CargoAdicional> {
  await esperar()
  if (!nombre.trim()) throw new Error('El cargo necesita un nombre visible para el cliente')
  return escribir(['ordenes', 'mesas'], (bd) => {
    const orden = buscarOrden(bd, ordenId)
    const cargo: CargoAdicional = {
      id: nuevoId('cg'),
      nombre: nombre.trim(),
      valor: Math.round(valor),
      agregadoPor,
      agregadoEn: new Date().toISOString(),
    }
    orden.cargosAdicionales.push(cargo)
    return cargo
  })
}

export async function quitarCargo(ordenId: string, cargoId: string): Promise<void> {
  await esperar()
  escribir(['ordenes', 'mesas'], (bd) => {
    const orden = buscarOrden(bd, ordenId)
    orden.cargosAdicionales = orden.cargosAdicionales.filter((c) => c.id !== cargoId)
  })
}

export async function pedirCuenta(ordenId: string): Promise<void> {
  await esperar()
  escribir(['ordenes', 'mesas'], (bd) => {
    const orden = buscarOrden(bd, ordenId)
    orden.estado = 'cuenta_pedida'
    buscarMesa(bd, orden.mesaId).estado = 'cuenta_pedida'
  })
}

export async function trasladarMesa(ordenId: string, mesaDestinoId: string): Promise<void> {
  await esperar()
  escribir(['ordenes', 'mesas'], (bd) => {
    const orden = buscarOrden(bd, ordenId)
    const destino = buscarMesa(bd, mesaDestinoId)
    if (destino.ordenActivaId) throw new Error('La mesa de destino ya está ocupada')

    const origen = buscarMesa(bd, orden.mesaId)
    destino.estado = origen.estado
    destino.meseroId = origen.meseroId
    destino.ordenActivaId = orden.id

    origen.estado = 'libre'
    origen.meseroId = undefined
    origen.ordenActivaId = undefined

    orden.mesaId = mesaDestinoId
  })
}

export async function agregarNota(ordenId: string, notas: string): Promise<void> {
  await esperar()
  escribir(['ordenes'], (bd) => {
    buscarOrden(bd, ordenId).notas = notas
  })
}

// ---------------------------------------------------------------------------
// Cocina y barra
// ---------------------------------------------------------------------------

export interface TurnoEnCocina {
  ordenId: string
  numeroOrden: number
  mesaId: string
  mesaEtiqueta: string
  zona: Mesa['zona']
  meseroNombre: string
  turno: number
  enviadoEn: string
  estado: 'pendiente' | 'en_preparacion' | 'listo' | 'servido'
  items: ItemOrden[]
  notas?: string
}

/**
 * Lo que ve la pantalla de cocina: un bloque por turno de envio, para que las
 * entradas y los fuertes de una misma mesa no se mezclen.
 */
export async function comandasActivas(destino: Destino): Promise<TurnoEnCocina[]> {
  await esperar()
  const bd = base()
  const bloques: TurnoEnCocina[] = []

  for (const orden of bd.ordenes) {
    if (orden.estado === 'pagada' || orden.estado === 'anulada') continue
    const mesa = bd.mesas.find((m) => m.id === orden.mesaId)
    if (!mesa) continue

    const propios = orden.items.filter(
      (i) => i.destino === destino && i.turnoEnvio > 0 && i.estado !== 'anulado',
    )

    for (const grupo of agruparPorTurno(propios)) {
      // Un turno servido por completo ya no ocupa espacio en la pantalla.
      if (grupo.items.every((i) => i.estado === 'servido')) continue

      const estado: TurnoEnCocina['estado'] = grupo.items.some((i) => i.estado === 'pendiente')
        ? 'pendiente'
        : grupo.items.some((i) => i.estado === 'en_preparacion')
          ? 'en_preparacion'
          : 'listo'

      bloques.push({
        ordenId: orden.id,
        numeroOrden: orden.numero,
        mesaId: mesa.id,
        mesaEtiqueta: mesa.nombre ?? `Mesa ${mesa.numero}`,
        zona: mesa.zona,
        meseroNombre: bd.usuarios.find((u) => u.id === orden.meseroId)?.nombre ?? '',
        turno: grupo.turno,
        enviadoEn: grupo.items[0].enviadoEn ?? orden.abiertaEn,
        estado,
        items: clonar(grupo.items),
        notas: orden.notas,
      })
    }
  }

  // El mas antiguo primero: lo que lleva mas tiempo esperando manda.
  return bloques.sort((a, b) => new Date(a.enviadoEn).getTime() - new Date(b.enviadoEn).getTime())
}

export async function cambiarEstadoItem(
  ordenId: string,
  itemId: string,
  estado: EstadoItem,
): Promise<void> {
  await esperar()
  escribir(['ordenes', 'cocina', 'mesas'], (bd) => {
    const orden = buscarOrden(bd, ordenId)
    const item = orden.items.find((i) => i.id === itemId)
    if (!item) throw new Error('El producto ya no está en la comanda')
    item.estado = estado
    if (estado === 'listo') item.listoEn = new Date().toISOString()
    sincronizarEstadoOrden(orden)
  })
}

export async function cambiarEstadoTurno(
  ordenId: string,
  turno: number,
  destino: Destino,
  estado: EstadoItem,
): Promise<void> {
  await esperar()
  escribir(['ordenes', 'cocina', 'mesas'], (bd) => {
    const orden = buscarOrden(bd, ordenId)
    const ahora = new Date().toISOString()
    for (const item of orden.items) {
      if (item.turnoEnvio !== turno || item.destino !== destino || item.estado === 'anulado') continue
      item.estado = estado
      if (estado === 'listo') item.listoEn = ahora
    }
    sincronizarEstadoOrden(orden)
  })
}

// ---------------------------------------------------------------------------
// Cobro
// ---------------------------------------------------------------------------

export interface DatosPago {
  ordenId: string
  porcentajePropina: number
  propina: number
  metodo: MetodoPago
  divisiones?: DivisionPago[]
  recibidoPor: string
}

export async function registrarPago(datos: DatosPago): Promise<Pago> {
  await esperar()
  return escribir(['ordenes', 'mesas', 'pagos'], (bd) => {
    const orden = buscarOrden(bd, datos.ordenId)
    if (orden.estado === 'pagada') throw new Error('Esta cuenta ya fue cobrada')

    const cuenta = calcularCuenta(orden, bd.ajustes.porcentajeInc, datos.porcentajePropina, datos.propina)

    // Un pago mixto sin desglose dejaria el cierre de caja descuadrado.
    if (datos.metodo === 'mixto') {
      const partes = datos.divisiones ?? []
      const suma = partes.reduce((s, d) => s + d.valor, 0)
      if (partes.length === 0) throw new Error('Un pago mixto necesita el desglose por medio de pago')
      if (suma !== cuenta.total)
        throw new Error('Las partes del pago no suman el total de la cuenta')
    }

    const pago: Pago = {
      id: nuevoId('pg'),
      ordenId: orden.id,
      subtotal: cuenta.subtotal,
      inc: cuenta.inc,
      propina: cuenta.propina,
      cargosAdicionales: cuenta.cargosAdicionales,
      total: cuenta.total,
      metodo: datos.metodo,
      divisiones: datos.divisiones,
      recibidoPor: datos.recibidoPor,
      fechaHora: new Date().toISOString(),
    }

    bd.pagos.push(pago)
    orden.estado = 'pagada'
    orden.cerradaEn = pago.fechaHora
    for (const item of orden.items) if (item.estado !== 'anulado') item.estado = 'servido'

    const mesa = buscarMesa(bd, orden.mesaId)
    mesa.estado = 'libre'
    mesa.meseroId = undefined
    mesa.ordenActivaId = undefined

    return clonar(pago)
  })
}

export interface ComprobanteDetallado {
  pago: Pago
  orden: Orden
  mesaEtiqueta: string
  meseroNombre: string
}

/** Todo lo que hace falta para reimprimir un comprobante desde el histórico. */
export async function obtenerComprobante(pagoId: string): Promise<ComprobanteDetallado | null> {
  await esperar()
  const bd = base()
  const pago = bd.pagos.find((p) => p.id === pagoId)
  if (!pago) return null
  const orden = bd.ordenes.find((o) => o.id === pago.ordenId)
  if (!orden) return null
  const mesa = bd.mesas.find((m) => m.id === orden.mesaId)

  return {
    pago: clonar(pago),
    orden: clonar(orden),
    mesaEtiqueta: mesa ? (mesa.nombre ?? `Mesa ${mesa.numero}`) : 'Mesa retirada',
    meseroNombre: bd.usuarios.find((u) => u.id === orden.meseroId)?.nombre ?? '',
  }
}

// ---------------------------------------------------------------------------
// Reservas
// ---------------------------------------------------------------------------

export async function listarReservas(): Promise<Reserva[]> {
  await esperar()
  return clonar(base().reservas).sort(
    (a, b) => new Date(a.fechaHora).getTime() - new Date(b.fechaHora).getTime(),
  )
}

export async function crearReserva(
  datos: Omit<Reserva, 'id' | 'estado' | 'mesaAsignadaId'>,
): Promise<Reserva> {
  await esperar()
  return escribir(['reservas'], (bd) => {
    const reserva: Reserva = { ...datos, id: nuevoId('r'), estado: 'solicitada' }
    bd.reservas.push(reserva)
    return clonar(reserva)
  })
}

export async function cambiarEstadoReserva(
  reservaId: string,
  estado: EstadoReserva,
  mesaAsignadaId?: string,
): Promise<Reserva> {
  await esperar()
  return escribir(['reservas', 'mesas'], (bd) => {
    const reserva = bd.reservas.find((r) => r.id === reservaId)
    if (!reserva) throw new Error('La reserva no existe')
    reserva.estado = estado
    if (mesaAsignadaId !== undefined) reserva.mesaAsignadaId = mesaAsignadaId || undefined

    if (estado === 'confirmada' && reserva.mesaAsignadaId) {
      const mesa = bd.mesas.find((m) => m.id === reserva.mesaAsignadaId)
      if (mesa && mesa.estado === 'libre') mesa.estado = 'reservada'
    }
    if ((estado === 'cancelada' || estado === 'cumplida' || estado === 'no_asistio') && reserva.mesaAsignadaId) {
      const mesa = bd.mesas.find((m) => m.id === reserva.mesaAsignadaId)
      if (mesa && mesa.estado === 'reservada') mesa.estado = 'libre'
    }
    return clonar(reserva)
  })
}

export async function reprogramarReserva(reservaId: string, fechaHora: string): Promise<void> {
  await esperar()
  escribir(['reservas'], (bd) => {
    const reserva = bd.reservas.find((r) => r.id === reservaId)
    if (!reserva) throw new Error('La reserva no existe')
    reserva.fechaHora = fechaHora
  })
}

// ---------------------------------------------------------------------------
// Panel administrativo
// ---------------------------------------------------------------------------

export interface IndicadoresDia {
  ventaTotal: number
  ordenes: number
  ticketPromedio: number
  mesasOcupadas: number
  mesasTotales: number
  propinas: number
  inc: number
  minutosPromedioPreparacion: number
  comensales: number
}

const esDeHoy = (fecha: string): boolean => claveDia(fecha) === claveDia()

/**
 * Reparte los pagos por medio de cobro. Un pago mixto se abre en sus partes,
 * porque de lo contrario el efectivo declarado no cuadraria con la caja.
 */
function totalesPorMetodo(pagos: Pago[]): {
  efectivo: number
  tarjeta: number
  transferencia: number
} {
  const totales = { efectivo: 0, tarjeta: 0, transferencia: 0 }
  for (const pago of pagos) {
    if (pago.metodo === 'mixto') {
      for (const parte of pago.divisiones ?? []) totales[parte.metodo] += parte.valor
    } else {
      totales[pago.metodo] += pago.total
    }
  }
  return totales
}

export async function indicadoresDia(): Promise<IndicadoresDia> {
  await esperar()
  const bd = base()
  const pagosHoy = bd.pagos.filter((p) => esDeHoy(p.fechaHora))
  const ventaTotal = pagosHoy.reduce((s, p) => s + p.total, 0)

  const tiempos: number[] = []
  for (const orden of bd.ordenes) {
    for (const item of orden.items) {
      if (!item.enviadoEn || !item.listoEn) continue
      if (!esDeHoy(item.listoEn)) continue
      tiempos.push((new Date(item.listoEn).getTime() - new Date(item.enviadoEn).getTime()) / 60000)
    }
  }

  const ordenesHoy = bd.ordenes.filter((o) => esDeHoy(o.abiertaEn))

  return {
    ventaTotal,
    ordenes: pagosHoy.length,
    ticketPromedio: pagosHoy.length ? Math.round(ventaTotal / pagosHoy.length) : 0,
    mesasOcupadas: bd.mesas.filter((m) => m.estado !== 'libre').length,
    mesasTotales: bd.mesas.length,
    propinas: pagosHoy.reduce((s, p) => s + p.propina, 0),
    inc: pagosHoy.reduce((s, p) => s + p.inc, 0),
    minutosPromedioPreparacion: tiempos.length
      ? Math.round(tiempos.reduce((s, t) => s + t, 0) / tiempos.length)
      : 0,
    comensales: ordenesHoy.reduce((s, o) => s + o.comensales, 0),
  }
}

export interface VentaHistorica {
  orden: Orden
  pago: Pago
  mesaEtiqueta: string
  meseroNombre: string
}

export interface FiltroVentas {
  desde?: string
  hasta?: string
  meseroId?: string
  metodo?: MetodoPago
}

export async function historicoVentas(filtro: FiltroVentas = {}): Promise<VentaHistorica[]> {
  await esperar()
  const bd = base()
  const resultado: VentaHistorica[] = []

  for (const pago of bd.pagos) {
    const dia = claveDia(pago.fechaHora)
    if (filtro.desde && dia < filtro.desde) continue
    if (filtro.hasta && dia > filtro.hasta) continue
    if (filtro.metodo && pago.metodo !== filtro.metodo) continue

    const orden = bd.ordenes.find((o) => o.id === pago.ordenId)
    if (!orden) continue
    if (filtro.meseroId && orden.meseroId !== filtro.meseroId) continue

    const mesa = bd.mesas.find((m) => m.id === orden.mesaId)
    resultado.push({
      orden: clonar(orden),
      pago: clonar(pago),
      mesaEtiqueta: mesa ? (mesa.nombre ?? `Mesa ${mesa.numero}`) : 'Mesa retirada',
      meseroNombre: bd.usuarios.find((u) => u.id === orden.meseroId)?.nombre ?? '',
    })
  }

  return resultado.sort(
    (a, b) => new Date(b.pago.fechaHora).getTime() - new Date(a.pago.fechaHora).getTime(),
  )
}

export interface ResumenTurno {
  fecha: string
  turno: 'almuerzo' | 'cena'
  ventaTotal: number
  totalEfectivo: number
  totalTarjeta: number
  totalTransferencia: number
  propinasTotales: number
  incTotal: number
  ordenesAtendidas: number
  ticketPromedio: number
  /** Mismo turno del dia anterior, para comparar. */
  ventaDiaAnterior: number
  ordenesDiaAnterior: number
}

export async function resumenTurnoActual(): Promise<ResumenTurno> {
  await esperar()
  const bd = base()
  const turno = turnoDe()
  const hoy = claveDia()
  const ayer = claveDia(new Date(Date.now() - 86400000))

  const delTurno = bd.pagos.filter((p) => claveDia(p.fechaHora) === hoy && turnoDe(p.fechaHora) === turno)
  const deAyer = bd.pagos.filter((p) => claveDia(p.fechaHora) === ayer && turnoDe(p.fechaHora) === turno)

  const porMetodo = totalesPorMetodo(delTurno)
  const ventaTotal = delTurno.reduce((s, p) => s + p.total, 0)

  return {
    fecha: hoy,
    turno,
    ventaTotal,
    totalEfectivo: porMetodo.efectivo,
    totalTarjeta: porMetodo.tarjeta,
    totalTransferencia: porMetodo.transferencia,
    propinasTotales: delTurno.reduce((s, p) => s + p.propina, 0),
    incTotal: delTurno.reduce((s, p) => s + p.inc, 0),
    ordenesAtendidas: delTurno.length,
    ticketPromedio: delTurno.length ? Math.round(ventaTotal / delTurno.length) : 0,
    ventaDiaAnterior: deAyer.reduce((s, p) => s + p.total, 0),
    ordenesDiaAnterior: deAyer.length,
  }
}

export async function listarCierres(): Promise<CierreCaja[]> {
  await esperar()
  return clonar(base().cierres).sort((a, b) => b.fechaHora.localeCompare(a.fechaHora))
}

export async function cerrarTurno(cerradoPor: string): Promise<CierreCaja> {
  const resumen = await resumenTurnoActual()
  return escribir(['cierres'], (bd) => {
    const cierre: CierreCaja = {
      id: nuevoId('cc'),
      fecha: resumen.fecha,
      turno: resumen.turno,
      ventaTotal: resumen.ventaTotal,
      totalEfectivo: resumen.totalEfectivo,
      totalTarjeta: resumen.totalTarjeta,
      totalTransferencia: resumen.totalTransferencia,
      propinasTotales: resumen.propinasTotales,
      incTotal: resumen.incTotal,
      ordenesAtendidas: resumen.ordenesAtendidas,
      ticketPromedio: resumen.ticketPromedio,
      cerradoPor,
      fechaHora: new Date().toISOString(),
    }
    bd.cierres.push(cierre)
    return clonar(cierre)
  })
}

export interface Reportes {
  masVendidos: { nombre: string; unidades: number; ingreso: number }[]
  porFranja: { franja: string; hora: number; ventas: number; ordenes: number }[]
  porMesero: { nombre: string; ordenes: number; ventas: number; ticketPromedio: number; propinas: number }[]
  tiemposPorProducto: { nombre: string; minutos: number; muestras: number }[]
  ventasPorDia: { dia: string; total: number }[]
}

export async function reportes(dias = 10): Promise<Reportes> {
  await esperar()
  const bd = base()
  const desde = Date.now() - dias * 86400000
  const pagos = bd.pagos.filter((p) => new Date(p.fechaHora).getTime() >= desde)
  const ordenesPorId = new Map(bd.ordenes.map((o) => [o.id, o]))

  const productos = new Map<string, { unidades: number; ingreso: number }>()
  const franjas = new Map<number, { ventas: number; ordenes: number }>()
  const meseros = new Map<string, { ordenes: number; ventas: number; propinas: number }>()
  const porDia = new Map<string, number>()

  for (const pago of pagos) {
    const orden = ordenesPorId.get(pago.ordenId)
    if (!orden) continue

    for (const item of orden.items) {
      if (item.estado === 'anulado') continue
      const actual = productos.get(item.nombre) ?? { unidades: 0, ingreso: 0 }
      const adicionales = item.modificadoresSeleccionados.reduce((s, m) => s + m.precioAdicional, 0)
      actual.unidades += item.cantidad
      actual.ingreso += (item.precioUnitario + adicionales) * item.cantidad
      productos.set(item.nombre, actual)
    }

    const hora = new Date(pago.fechaHora).getHours()
    const franja = franjas.get(hora) ?? { ventas: 0, ordenes: 0 }
    franja.ventas += pago.total
    franja.ordenes += 1
    franjas.set(hora, franja)

    const mesero = meseros.get(orden.meseroId) ?? { ordenes: 0, ventas: 0, propinas: 0 }
    mesero.ordenes += 1
    mesero.ventas += pago.total
    mesero.propinas += pago.propina
    meseros.set(orden.meseroId, mesero)

    const dia = claveDia(pago.fechaHora)
    porDia.set(dia, (porDia.get(dia) ?? 0) + pago.total)
  }

  // Tiempo real de preparacion por producto, desde el envio hasta que sale listo.
  const tiempos = new Map<string, { total: number; muestras: number }>()
  for (const orden of bd.ordenes) {
    for (const item of orden.items) {
      if (!item.enviadoEn || !item.listoEn) continue
      const minutos = (new Date(item.listoEn).getTime() - new Date(item.enviadoEn).getTime()) / 60000
      if (minutos <= 0 || minutos > 180) continue
      const actual = tiempos.get(item.nombre) ?? { total: 0, muestras: 0 }
      actual.total += minutos
      actual.muestras += 1
      tiempos.set(item.nombre, actual)
    }
  }

  return {
    masVendidos: [...productos.entries()]
      .map(([nombre, d]) => ({ nombre, ...d }))
      .sort((a, b) => b.unidades - a.unidades)
      .slice(0, 12),
    porFranja: [...franjas.entries()]
      .map(([hora, d]) => ({
        hora,
        franja: franjaHoraria(new Date(2026, 0, 1, hora)),
        ventas: d.ventas,
        ordenes: d.ordenes,
      }))
      .sort((a, b) => a.hora - b.hora),
    porMesero: [...meseros.entries()]
      .map(([id, d]) => ({
        nombre: bd.usuarios.find((u) => u.id === id)?.nombre ?? 'Sin asignar',
        ordenes: d.ordenes,
        ventas: d.ventas,
        propinas: d.propinas,
        ticketPromedio: Math.round(d.ventas / d.ordenes),
      }))
      .sort((a, b) => b.ventas - a.ventas),
    tiemposPorProducto: [...tiempos.entries()]
      .map(([nombre, d]) => ({
        nombre,
        minutos: Math.round(d.total / d.muestras),
        muestras: d.muestras,
      }))
      .sort((a, b) => b.minutos - a.minutos)
      .slice(0, 12),
    ventasPorDia: [...porDia.entries()]
      .map(([dia, total]) => ({ dia, total }))
      .sort((a, b) => a.dia.localeCompare(b.dia)),
  }
}

export interface Alerta {
  id: string
  tipo: 'demora' | 'cobro'
  mensaje: string
  minutos: number
  mesaId: string
}

/** Mesas esperando comida hace rato y cuentas pedidas sin cobrar. */
export async function alertas(umbralMinutos: number): Promise<Alerta[]> {
  await esperar()
  const bd = base()
  const lista: Alerta[] = []
  const ahora = Date.now()

  for (const orden of bd.ordenes) {
    if (orden.estado === 'pagada' || orden.estado === 'anulada') continue
    const mesa = bd.mesas.find((m) => m.id === orden.mesaId)
    if (!mesa) continue
    const etiqueta = mesa.nombre ?? `Mesa ${mesa.numero}`

    const enEspera = orden.items.filter(
      (i) => i.turnoEnvio > 0 && (i.estado === 'pendiente' || i.estado === 'en_preparacion'),
    )
    const masAntiguo = enEspera
      .map((i) => new Date(i.enviadoEn ?? orden.abiertaEn).getTime())
      .sort((a, b) => a - b)[0]

    if (masAntiguo) {
      const minutos = Math.floor((ahora - masAntiguo) / 60000)
      if (minutos >= umbralMinutos) {
        lista.push({
          id: `al_${orden.id}_demora`,
          tipo: 'demora',
          mesaId: mesa.id,
          minutos,
          mensaje: `${etiqueta} lleva ${minutos} min esperando comida`,
        })
      }
    }

    if (orden.estado === 'cuenta_pedida') {
      const minutos = Math.floor((ahora - new Date(orden.abiertaEn).getTime()) / 60000)
      lista.push({
        id: `al_${orden.id}_cobro`,
        tipo: 'cobro',
        mesaId: mesa.id,
        minutos,
        mensaje: `${etiqueta} pidió la cuenta y sigue sin cobrar`,
      })
    }
  }

  return lista.sort((a, b) => b.minutos - a.minutos)
}

/** Roles autorizados en cada area. Lo consulta la guarda de rutas. */
export const ACCESO_POR_AREA: Record<string, Rol[]> = {
  comandera: ['mesero', 'administrador'],
  cocina: ['cocina', 'administrador'],
  admin: ['cajero', 'administrador'],
  reportes: ['administrador'],
}

export { SinConexionError }

/** Cola de envios pendientes, para el indicador de la comandera. */
export function pendientesDeEnvio(): number {
  return leerCola().length
}
