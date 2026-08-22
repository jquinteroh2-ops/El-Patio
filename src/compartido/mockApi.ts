import { ErrorApi, borrarCredenciales, guardarCredenciales, pedir, pedirOpcional, subirArchivo, tokenDeRefresco } from './cliente'
import { URL_API } from './config'
import {
  SinConexionError,
  colaOrdenada,
  encolar,
  hayConexion,
  leerCola,
  marcarIntento,
  quitarDeCola,
} from './conexion'
import type { Cuenta } from './calculos'
import type {
  Ajustes,
  CargoAdicional,
  CategoriaCarta,
  CierreCaja,
  CuentasDemostracion,
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
  Publicacion,
  EstadoCanal,
  TipoPublicacion,
  EstadoPedido,
  Reserva,
  Rol,
  Sesion,
  TipoPedido,
  Usuario,
  ZonaDomicilio,
} from './tipos'

/**
 * Unica superficie de datos del sistema.
 *
 * Ningun componente lee ni escribe los datos directamente: todo pasa por aqui.
 * Eso es lo que permitio cambiar localStorage por un backend real sin tocar una
 * sola pantalla: solo cambio el interior de este archivo.
 *
 * El nombre se quedo en `mockApi` a proposito. Renombrarlo obligaria a editar
 * el import de cada componente del sistema, que es justamente lo que esta capa
 * existe para evitar. Lo que hay debajo ya no es una simulacion.
 *
 * Ya no hay latencia artificial: la que se siente es la de la red y la del
 * servidor. La interfaz se diseno contra una demora parecida, asi que no hubo
 * que ajustar ninguna espera.
 */

// ---------------------------------------------------------------------------
// Infraestructura
// ---------------------------------------------------------------------------

/**
 * Nunca sale al servidor un error tecnico crudo.
 *
 * Lo que llega en el mensaje termina en la pantalla del mesero, asi que un
 * fallo de red se traduce a algo que se pueda leer de pie en un salon lleno.
 */
function traducirError(error: unknown): Error {
  if (error instanceof SinConexionError) return error
  if (error instanceof ErrorApi) return new Error(error.message)
  if (error instanceof Error) return error
  return new Error('No se pudo completar la operación')
}

async function contra<T>(operacion: () => Promise<T>): Promise<T> {
  try {
    return await operacion()
  } catch (error) {
    throw traducirError(error)
  }
}

// ---------------------------------------------------------------------------
// Arranque
// ---------------------------------------------------------------------------

/**
 * Antes sembraba los datos de demostracion en el navegador. Ahora los datos ya
 * existen en la base y aqui solo se comprueba que el servidor conteste, para
 * que la aplicacion no arranque mostrando pantallas vacias sin explicacion.
 *
 * No lanza si falla: la aplicacion tiene que abrir igual y dejar que cada
 * pantalla muestre su propio error. Un restaurante no se queda sin sistema
 * porque el pulso tardo un segundo de mas.
 */
export async function inicializar(): Promise<void> {
  try {
    await pedir<unknown>('/salud', { sinSesion: true })
  } catch (error) {
    console.error('[api] el servidor no respondió al arrancar', error)
  }
}

// ---------------------------------------------------------------------------
// Acceso
// ---------------------------------------------------------------------------

interface RespuestaAcceso {
  sesion: Sesion
  acceso: string
  refresco: string
  expiraEnSegundos: number
}

export async function autenticar(usuario: string, clave: string): Promise<Sesion> {
  return contra(async () => {
    const respuesta = await pedir<RespuestaAcceso>('/api/acceso/ingresar', {
      metodo: 'POST',
      cuerpo: { usuario: usuario.trim(), clave },
      sinSesion: true,
    })
    guardarCredenciales(respuesta.acceso, respuesta.refresco)
    return respuesta.sesion
  })
}

/**
 * Que cuentas ensenar en la pantalla de acceso.
 *
 * Lo decide el servidor y no el paquete compilado: el mismo frontend sirve para
 * la demostracion y para el restaurante, y lo que cambia entre los dos es una
 * variable de entorno del backend. Si el servidor no contesta se responde que
 * no hay ninguna, porque una pantalla de acceso sin lista sigue siendo usable
 * y una que se queda cargando no.
 */
export async function cuentasDeDemostracion(): Promise<CuentasDemostracion> {
  const vacio: CuentasDemostracion = { activa: false, clave: '', cuentas: [] }
  try {
    const respuesta = await pedir<CuentasDemostracion>('/api/acceso/demostracion', {
      sinSesion: true,
    })
    return respuesta ?? vacio
  } catch {
    return vacio
  }
}

/**
 * Cierra la sesion de este dispositivo tambien en el servidor.
 *
 * Sin esto el token de refresco seguiria vivo hasta vencer solo, y cerrar
 * sesion en una tablet que se perdio no serviria de nada.
 */
export async function cerrarSesion(): Promise<void> {
  const refresco = tokenDeRefresco()
  borrarCredenciales()
  if (!refresco) return
  try {
    await pedir<void>('/api/acceso/salir', {
      metodo: 'POST',
      cuerpo: { refresco },
      sinSesion: true,
    })
  } catch (error) {
    // Que el servidor no conteste no puede impedir salir: la credencial local
    // ya se borro, que es lo que protege a quien esta frente al aparato.
    console.error('[api] no se pudo avisar el cierre de sesión', error)
  }
}

export async function listarUsuarios(): Promise<Usuario[]> {
  return contra(() => pedir<Usuario[]>('/api/usuarios'))
}

export async function guardarUsuario(usuario: Usuario): Promise<Usuario> {
  return contra(() => pedir<Usuario>('/api/usuarios', { metodo: 'PUT', cuerpo: usuario }))
}

// ---------------------------------------------------------------------------
// Ajustes
// ---------------------------------------------------------------------------

export async function obtenerAjustes(): Promise<Ajustes> {
  return contra(async () => {
    return await pedir<Ajustes>('/api/ajustes')
  })
}

export async function actualizarAjustes(cambios: Partial<Ajustes>): Promise<Ajustes> {
  return contra(async () => {
    return await pedir<Ajustes>('/api/ajustes', {
      metodo: 'PUT',
      cuerpo: {
        porcentajeInc: cambios.porcentajeInc,
        domiciliosPausados: cambios.domiciliosPausados,
        domiciliosDesde: cambios.domiciliosDesde,
        domiciliosHasta: cambios.domiciliosHasta,
      },
    })
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
  return contra(() => pedir<MesaEnMapa[]>('/api/comandera/mesas'))
}

export async function guardarMesa(mesa: Mesa): Promise<Mesa> {
  return contra(() => pedir<Mesa>('/api/salon/mesas', { metodo: 'PUT', cuerpo: mesa }))
}

export async function eliminarMesa(mesaId: string): Promise<void> {
  return contra(() => pedir<void>(`/api/salon/mesas/${mesaId}`, { metodo: 'DELETE' }))
}

// ---------------------------------------------------------------------------
// Carta
// ---------------------------------------------------------------------------

export async function listarCategorias(): Promise<CategoriaCarta[]> {
  return contra(() => pedir<CategoriaCarta[]>('/api/carta/categorias', { sinSesion: true }))
}

export async function listarCarta(): Promise<ItemCarta[]> {
  return contra(() => pedir<ItemCarta[]>('/api/carta', { sinSesion: true }))
}

/** Carta agrupada por categoria, tal como la lee el cliente y el mesero. */
export interface CategoriaConItems extends CategoriaCarta {
  items: ItemCarta[]
}

export async function cartaAgrupada(soloDisponibles = false): Promise<CategoriaConItems[]> {
  // El sitio publico la consulta sin sesion: es el menu que cualquiera puede
  // leer desde la calle.
  return contra(() =>
    pedir<CategoriaConItems[]>('/api/carta/agrupada', {
      consulta: { soloDisponibles },
      sinSesion: true,
    }),
  )
}

export async function cambiarDisponibilidad(itemId: string, disponible: boolean): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/carta/${itemId}/disponibilidad`, { metodo: 'PATCH', cuerpo: { disponible } }),
  )
}

export async function guardarItemCarta(item: ItemCarta): Promise<ItemCarta> {
  return contra(() => pedir<ItemCarta>('/api/carta', { metodo: 'PUT', cuerpo: item }))
}

export async function eliminarItemCarta(itemId: string): Promise<void> {
  return contra(() => pedir<void>(`/api/carta/${itemId}`, { metodo: 'DELETE' }))
}

export async function guardarCategoria(categoria: CategoriaCarta): Promise<CategoriaCarta> {
  return contra(() =>
    pedir<CategoriaCarta>('/api/carta/categorias', { metodo: 'PUT', cuerpo: categoria }),
  )
}

// ---------------------------------------------------------------------------
// Publicaciones: promociones, eventos y fotos del local
// ---------------------------------------------------------------------------

/**
 * Lo que esta publicado y vigente hoy. Sin sesion: es lo que el restaurante
 * quiere que vea quien todavia no es cliente.
 */
export async function publicacionesVisibles(tipo?: TipoPublicacion): Promise<Publicacion[]> {
  return contra(() =>
    pedir<Publicacion[]>('/api/publicaciones/visibles', {
      consulta: tipo ? { tipo } : undefined,
      sinSesion: true,
    }),
  )
}

/** Todas, con borradores y vencidas. Es la pantalla del dueno. */
export async function listarPublicaciones(): Promise<Publicacion[]> {
  return contra(() => pedir<Publicacion[]>('/api/publicaciones'))
}

export async function guardarPublicacion(publicacion: Publicacion): Promise<Publicacion> {
  return contra(() =>
    pedir<Publicacion>('/api/publicaciones', { metodo: 'PUT', cuerpo: publicacion }),
  )
}

export async function eliminarPublicacion(id: string): Promise<void> {
  return contra(() => pedir<void>(`/api/publicaciones/${id}`, { metodo: 'DELETE' }))
}

/**
 * Sube una foto y devuelve el nombre con que quedo guardada.
 *
 * Se sube antes de guardar la publicacion, no junto con ella: asi el dueno ve
 * la foto en pantalla antes de decidir, y si se arrepiente del texto no tiene
 * que volver a subir los megas desde el celular.
 */
export async function subirImagenPublicacion(archivo: File): Promise<string> {
  const { imagen } = await contra(() =>
    subirArchivo<{ imagen: string }>('/api/publicaciones/imagenes', 'archivo', archivo),
  )
  return imagen
}

/**
 * La direccion desde la que el navegador pide una foto ya guardada.
 *
 * Hay dos formas de guardar y las dos pasan por aqui:
 *
 *  - Cloudinary devuelve una direccion completa. En ese caso se le pide la
 *    version que sirve para el ancho que va a ocupar en pantalla, y el formato
 *    lo escoge Cloudinary segun el navegador: `f_auto` entrega WebP a quien lo
 *    entienda. La misma foto pesa unos 60 KB en un celular y se ve nitida en un
 *    computador, sin guardar dos archivos.
 *  - El almacen en disco devuelve solo un nombre, y lo sirve este backend en un
 *    unico tamano.
 *
 * Por eso `ancho` es una peticion, no una promesa: con Cloudinary se cumple, en
 * disco se ignora porque solo hay un archivo.
 */
export function urlImagen(nombre: string, ancho = 800): string {
  if (!nombre.startsWith('http')) return `${URL_API}/api/publicaciones/imagenes/${nombre}`
  return nombre.replace('/upload/', `/upload/f_auto,q_auto,w_${ancho}/`)
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
  // El backend responde 204 cuando la mesa esta libre, que es el `null` que la
  // comandera espera para pintar el boton de abrir cuenta.
  return contra(() => pedirOpcional<OrdenDetallada>(`/api/comandera/mesas/${mesaId}/orden`))
}

export async function abrirMesa(mesaId: string, meseroId: string, comensales: number): Promise<Orden> {
  return contra(() =>
    pedir<Orden>(`/api/comandera/mesas/${mesaId}/abrir`, {
      metodo: 'POST',
      cuerpo: { meseroId, comensales },
    }),
  )
}

export async function cambiarComensales(ordenId: string, comensales: number): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/comandera/ordenes/${ordenId}/comensales`, {
      metodo: 'PATCH',
      cuerpo: { comensales },
    }),
  )
}

export interface NuevoItem {
  itemCartaId: string
  cantidad: number
  modificadoresSeleccionados?: ModificadorSeleccionado[]
  notaCocina?: string
}

export async function agregarItems(ordenId: string, nuevos: NuevoItem[]): Promise<Orden> {
  return contra(() =>
    pedir<Orden>(`/api/comandera/ordenes/${ordenId}/items`, {
      metodo: 'POST',
      cuerpo: { items: nuevos },
    }),
  )
}

export async function cambiarCantidad(ordenId: string, itemId: string, cantidad: number): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/comandera/ordenes/${ordenId}/items/${itemId}/cantidad`, {
      metodo: 'PATCH',
      cuerpo: { cantidad },
    }),
  )
}

export async function quitarItem(ordenId: string, itemId: string): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/comandera/ordenes/${ordenId}/items/${itemId}`, { metodo: 'DELETE' }),
  )
}

/** Anula un producto ya enviado. Queda registrado, nunca desaparece de la comanda. */
export async function anularItem(ordenId: string, itemId: string, motivo: string): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/comandera/ordenes/${ordenId}/items/${itemId}/anular`, {
      metodo: 'POST',
      cuerpo: { motivo },
    }),
  )
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
 *
 * La diferencia con el prototipo es que ahora la cola tiene sentido: antes todo
 * era local y nada podia fallar, asi que encolar era una puesta en escena.
 */
export async function enviarACocina(ordenId: string): Promise<ResultadoEnvio> {
  // Lo que ya espera en la cola no se vuelve a encolar: sin esto, un mesero sin
  // senal que toca "Enviar" dos veces deja la misma comanda repetida.
  const yaEnCola = new Set(itemsEnCola(ordenId))

  // Sin senal no se puede consultar que falta por mandar, asi que se encola lo
  // que la pantalla ya tenia cargado. Es el mismo camino que seguia el
  // prototipo, con la diferencia de que ahora la cola de verdad hace falta.
  const orden = hayConexion() ? await obtenerOrden(ordenId) : null
  const pendientes = (orden?.items ?? []).filter(
    (i) => i.turnoEnvio === 0 && i.estado !== 'anulado' && !yaEnCola.has(i.id),
  )

  if (orden && pendientes.length === 0) {
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
    .map((e) => e.turnoEnvio + 1)
  const turnoBase = Math.max(0, ...(orden?.items ?? []).map((i) => i.turnoEnvio)) + 1
  const turno = Math.max(turnoBase, ...turnosEnCola)

  const aLaCola = (): ResultadoEnvio => {
    encolar({
      ordenId,
      mesaId: orden?.mesaId ?? '',
      turnoEnvio: turno,
      itemIds: pendientes.map((i) => i.id),
    })
    return { encolado: true, turno, cantidadItems: pendientes.length }
  }

  if (!hayConexion()) return aLaCola()

  try {
    return await pedir<ResultadoEnvio>(`/api/comandera/ordenes/${ordenId}/enviar`, {
      metodo: 'POST',
      cuerpo: { itemIds: pendientes.map((i) => i.id), turno },
    })
  } catch (error) {
    // Si la senal se cayo justo al enviar, la comanda no se pierde: entra a la
    // cola igual que si nunca hubiera habido conexion. Cualquier otro error es
    // del negocio y tiene que llegarle al mesero tal cual.
    if (!(error instanceof SinConexionError)) throw traducirError(error)
    return aLaCola()
  }
}

/** Comanda por su identificador, para lo que no parte de la mesa. */
export async function obtenerOrden(ordenId: string): Promise<Orden> {
  return contra(() => pedir<Orden>(`/api/comandera/ordenes/${ordenId}`))
}

/** Productos de una comanda que esperan en la cola local por falta de senal. */
export function itemsEnCola(ordenId: string): string[] {
  return leerCola()
    .filter((e) => e.ordenId === ordenId)
    .flatMap((e) => e.itemIds)
}

/**
 * Vacia la cola local cuando vuelve la conexion.
 *
 * Se reenvia en el orden en que se dicto, uno a uno y esperando cada respuesta:
 * mandarlos en paralelo haria que los turnos llegaran a cocina desordenados.
 *
 * Los conflictos los resuelve el servidor, que es el unico que sabe que paso
 * mientras el aparato estuvo incomunicado. Si un producto ya habia salido, el
 * backend lo ignora; si la comanda se cobro o se anulo entre tanto, responde
 * que no y el envio se descarta en vez de quedarse trabando la cola para
 * siempre. Lo que no se descarta es un fallo de red: eso se reintenta.
 */
export async function procesarCola(): Promise<number> {
  if (!hayConexion()) return 0
  let enviadas = 0

  for (const envio of colaOrdenada()) {
    try {
      await pedir<ResultadoEnvio>(`/api/comandera/ordenes/${envio.ordenId}/enviar`, {
        metodo: 'POST',
        cuerpo: { itemIds: envio.itemIds, turno: envio.turnoEnvio },
      })
      quitarDeCola(envio.id)
      enviadas++
    } catch (error) {
      if (error instanceof SinConexionError) {
        // Se volvio a caer: se deja la cola como esta y se reintenta luego.
        marcarIntento(envio.id)
        break
      }
      console.error('[api] no se pudo enviar una comanda encolada', error)
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
  if (!nombre.trim()) throw new Error('El cargo necesita un nombre visible para el cliente')
  // `agregadoPor` ya no viaja: el backend lo toma del token, para que nadie
  // pueda firmar un descorche a nombre de otro. El parametro se conserva
  // porque la pantalla lo pasa y quitarlo obligaria a editarla.
  void agregadoPor
  return contra(() =>
    pedir<CargoAdicional>(`/api/comandera/ordenes/${ordenId}/cargos`, {
      metodo: 'POST',
      cuerpo: { nombre: nombre.trim(), valor: Math.round(valor) },
    }),
  )
}

export async function quitarCargo(ordenId: string, cargoId: string): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/comandera/ordenes/${ordenId}/cargos/${cargoId}`, { metodo: 'DELETE' }),
  )
}

export async function pedirCuenta(ordenId: string): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/comandera/ordenes/${ordenId}/pedir-cuenta`, { metodo: 'POST' }),
  )
}

export async function trasladarMesa(ordenId: string, mesaDestinoId: string): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/comandera/ordenes/${ordenId}/trasladar`, {
      metodo: 'POST',
      cuerpo: { mesaDestinoId },
    }),
  )
}

export async function agregarNota(ordenId: string, notas: string): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/comandera/ordenes/${ordenId}/nota`, { metodo: 'PUT', cuerpo: { notas } }),
  )
}

// ---------------------------------------------------------------------------
// Domicilios y para llevar
// ---------------------------------------------------------------------------

/**
 * Lo que el sitio publico necesita antes de dejar pedir: si el canal esta
 * abierto, a que zonas se lleva y cuanto cuesta cada una. Sin sesion, porque
 * lo consulta el cliente desde la calle.
 */
export async function estadoCanal(): Promise<EstadoCanal> {
  return contra(() => pedir<EstadoCanal>('/api/pedidos/canal', { sinSesion: true }))
}

export interface NuevoPedidoExterno {
  tipo: Exclude<TipoPedido, 'mesa'>
  nombre: string
  telefono: string
  direccion?: string
  barrio?: string
  zonaDomicilioId?: string
  metodoPagoPrevisto?: MetodoPago
  notas?: string
  /** Ubicacion del cliente, si autorizo compartirla. Los tres son opcionales. */
  latitud?: number
  longitud?: number
  precisionMetros?: number
  items: NuevoItem[]
}

export interface PedidoCreado {
  id: string
  numero: number
  minutosEstimados?: number
  cuenta: Cuenta
}

/**
 * ESTE ES EL PUNTO donde un pedido hecho desde la calle entra al restaurante.
 *
 * En el prototipo esta funcion escribia en el localStorage del propio celular
 * del cliente, asi que el pedido no llegaba a ninguna parte: la pantalla de
 * recepcion es otro aparato. Ahora es un POST de verdad, y de el sale el evento
 * que enciende la pantalla de recepcion sin que nadie recargue.
 *
 * Las validaciones se repiten en el servidor: el formulario protege al cliente
 * de equivocarse, el servidor protege al restaurante de pedidos imposibles.
 */
export async function crearPedidoExterno(datos: NuevoPedidoExterno): Promise<PedidoCreado> {
  return contra(() =>
    pedir<PedidoCreado>('/api/pedidos', { metodo: 'POST', cuerpo: datos, sinSesion: true }),
  )
}

/** Un pedido tal como lo pinta la pantalla de recepcion. */
export interface PedidoEnRecepcion {
  orden: Orden
  etiqueta: string
  zonaNombre?: string
  cuenta: Cuenta
  /**
   * Metros entre el punto que compartio el cliente y el local. Sirve para
   * detectar al que pidio desde el trabajo para que le lleven a la casa: si el
   * punto esta lejos, quien despacha tiene que fiarse de la direccion escrita.
   */
  metrosDelLocal?: number
}

export async function listarPedidos(incluirCerrados = false): Promise<PedidoEnRecepcion[]> {
  return contra(() =>
    pedir<PedidoEnRecepcion[]>('/api/pedidos', { consulta: { incluirCerrados } }),
  )
}

/**
 * Recepcion acepta el pedido y con eso entra a cocina.
 *
 * Los productos salen por el mismo camino que los de una mesa, asi que cada uno
 * cae en su destino segun lo que diga la carta: cocina no tiene una pantalla
 * aparte para domicilios ni tiene que saber de donde vino.
 */
export async function aceptarPedido(ordenId: string, minutosEstimados: number): Promise<Orden> {
  return contra(() =>
    pedir<Orden>(`/api/pedidos/${ordenId}/aceptar`, {
      metodo: 'POST',
      cuerpo: { minutosEstimados },
    }),
  )
}

/**
 * Corrige el tiempo prometido de un pedido que ya se acepto.
 *
 * No mueve el pedido de columna ni vuelve a mandar nada a cocina: lo unico que
 * cambia es lo que se le dijo al cliente. El aviso se manda aparte, por
 * WhatsApp, con el texto a la vista de quien lo envia.
 */
export async function cambiarTiempoPedido(
  ordenId: string,
  minutosEstimados: number,
): Promise<Orden> {
  return contra(() =>
    pedir<Orden>(`/api/pedidos/${ordenId}/tiempo`, {
      metodo: 'PATCH',
      cuerpo: { minutosEstimados },
    }),
  )
}

export async function rechazarPedido(ordenId: string, motivo: string): Promise<Orden> {
  return contra(() =>
    pedir<Orden>(`/api/pedidos/${ordenId}/rechazar`, { metodo: 'POST', cuerpo: { motivo } }),
  )
}

export async function cancelarPedido(ordenId: string, motivo: string): Promise<Orden> {
  return contra(() =>
    pedir<Orden>(`/api/pedidos/${ordenId}/cancelar`, { metodo: 'POST', cuerpo: { motivo } }),
  )
}

export async function cambiarEstadoPedido(ordenId: string, estado: EstadoPedido): Promise<Orden> {
  return contra(() =>
    pedir<Orden>(`/api/pedidos/${ordenId}/estado`, { metodo: 'PATCH', cuerpo: { estado } }),
  )
}

/**
 * En domicilio se exige quien lo lleva: el cliente puede llamar a preguntar.
 *
 * `repartidorId` solo viaja cuando quien lo lleva tiene cuenta en el sistema, y
 * es lo que hace que el pedido le aparezca a el en su pantalla. Sin el, el
 * nombre sigue saliendo en el papel y en el mensaje al cliente igual que antes:
 * un motorizado de turno no deja de poder llevar un pedido por no tener usuario.
 */
export async function despacharPedido(
  ordenId: string,
  repartidor: string,
  repartidorId?: string,
): Promise<Orden> {
  return contra(() =>
    pedir<Orden>(`/api/pedidos/${ordenId}/despachar`, {
      metodo: 'POST',
      cuerpo: { repartidor, repartidorId },
    }),
  )
}

/** Quienes pueden llevar un domicilio: las cuentas con rol de repartidor. */
export async function listarRepartidores(): Promise<RepartidorDisponible[]> {
  return contra(() => pedir<RepartidorDisponible[]>('/api/pedidos/repartidores'))
}

export interface RepartidorDisponible {
  id: string
  nombre: string
}

// --- La calle: lo que ve quien reparte -------------------------------------

/**
 * Los pedidos que este repartidor lleva encima ahora mismo.
 *
 * Quien pregunta sale del token y no de un parametro: son direcciones y
 * telefonos de clientes, y cada quien ve los de las puertas a las que va.
 */
export async function listarMisEntregas(): Promise<PedidoEnRecepcion[]> {
  return contra(() => pedir<PedidoEnRecepcion[]>('/api/pedidos/mios'))
}

/** El repartidor cierra la entrega en la puerta, y solo la suya. */
export async function entregarMiPedido(ordenId: string): Promise<Orden> {
  return contra(() => pedir<Orden>(`/api/pedidos/mios/${ordenId}/entregar`, { metodo: 'POST' }))
}

export async function entregarPedido(ordenId: string): Promise<Orden> {
  return contra(() => pedir<Orden>(`/api/pedidos/${ordenId}/entregar`, { metodo: 'POST' }))
}

// --- Zonas ------------------------------------------------------------------

export async function listarZonasDomicilio(): Promise<ZonaDomicilio[]> {
  return contra(() => pedir<ZonaDomicilio[]>('/api/pedidos/zonas'))
}

export async function guardarZonaDomicilio(zona: ZonaDomicilio): Promise<ZonaDomicilio> {
  return contra(() => pedir<ZonaDomicilio>('/api/pedidos/zonas', { metodo: 'PUT', cuerpo: zona }))
}

export async function eliminarZonaDomicilio(zonaId: string): Promise<void> {
  return contra(() => pedir<void>(`/api/pedidos/zonas/${zonaId}`, { metodo: 'DELETE' }))
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
  return contra(() => pedir<TurnoEnCocina[]>('/api/cocina/comandas', { consulta: { destino } }))
}

export async function cambiarEstadoItem(
  ordenId: string,
  itemId: string,
  estado: EstadoItem,
): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/cocina/ordenes/${ordenId}/items/${itemId}/estado`, {
      metodo: 'PATCH',
      cuerpo: { estado },
    }),
  )
}

export async function cambiarEstadoTurno(
  ordenId: string,
  turno: number,
  destino: Destino,
  estado: EstadoItem,
): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/cocina/ordenes/${ordenId}/turnos/${turno}/estado`, {
      metodo: 'PATCH',
      consulta: { destino },
      cuerpo: { estado },
    }),
  )
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
  // `recibidoPor` lo reescribe el backend con el nombre del token: es el dato
  // con el que despues se le reclama a alguien un faltante de caja.
  return contra(() => pedir<Pago>('/api/cobro/pagos', { metodo: 'POST', cuerpo: datos }))
}

export interface ComprobanteDetallado {
  pago: Pago
  orden: Orden
  mesaEtiqueta: string
  meseroNombre: string
}

/** Todo lo que hace falta para reimprimir un comprobante desde el histórico. */
export async function obtenerComprobante(pagoId: string): Promise<ComprobanteDetallado | null> {
  return contra(() => pedirOpcional<ComprobanteDetallado>(`/api/cobro/comprobantes/${pagoId}`))
}

// ---------------------------------------------------------------------------
// Reservas
// ---------------------------------------------------------------------------

export async function listarReservas(): Promise<Reserva[]> {
  return contra(() => pedir<Reserva[]>('/api/reservas'))
}

export async function crearReserva(
  datos: Omit<Reserva, 'id' | 'estado' | 'mesaAsignadaId'>,
): Promise<Reserva> {
  // La crea el cliente desde el sitio publico, sin sesion.
  return contra(() =>
    pedir<Reserva>('/api/reservas', { metodo: 'POST', cuerpo: datos, sinSesion: true }),
  )
}

export async function cambiarEstadoReserva(
  reservaId: string,
  estado: EstadoReserva,
  mesaAsignadaId?: string,
): Promise<Reserva> {
  return contra(() =>
    pedir<Reserva>(`/api/reservas/${reservaId}/estado`, {
      metodo: 'PATCH',
      cuerpo: { estado, mesaAsignadaId: mesaAsignadaId ?? null },
    }),
  )
}

export async function reprogramarReserva(reservaId: string, fechaHora: string): Promise<void> {
  return contra(() =>
    pedir<void>(`/api/reservas/${reservaId}/fecha`, { metodo: 'PATCH', cuerpo: { fechaHora } }),
  )
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

export async function indicadoresDia(): Promise<IndicadoresDia> {
  return contra(() => pedir<IndicadoresDia>('/api/caja/indicadores'))
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
  return contra(() =>
    pedir<VentaHistorica[]>('/api/caja/ventas', {
      consulta: {
        desde: filtro.desde,
        hasta: filtro.hasta,
        meseroId: filtro.meseroId,
        metodo: filtro.metodo,
      },
    }),
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
  /** Venta por canal. La suma de los tres es `ventaTotal`. */
  totalSalon: number
  totalDomicilio: number
  totalLlevar: number
  /** Lo cobrado por envios, ya incluido dentro de `totalDomicilio`. */
  totalEnvios: number
  /** Mismo turno del dia anterior, para comparar. */
  ventaDiaAnterior: number
  ordenesDiaAnterior: number

  // --- Lo que el contador necesita para declarar ---------------------------
  //
  // No son adorno del cierre: son las lineas que se llevan a la declaracion.
  // Estaban implicitas dentro del total y habia que deducirlas a mano, que es
  // justo donde aparecen las diferencias.

  /** Alimentos y bebidas, antes de impuesto y de propina. Base del INC. */
  baseGravable: number
  /** Cargos y domicilios: no causan INC y por eso van aparte. */
  baseNoGravada: number
  totalCargos: number
  /** La tarifa que rigio el turno, tal como quedo configurada. */
  porcentajeInc: number
  /** Lo que se dejo de cobrar por promociones, contra el precio de lista. */
  descuentos: number
  comensales: number
  /** Lo anulado: no entra en la venta, pero un cierre sin esto no se audita. */
  lineasAnuladas: number
  valorAnulado: number
}

export async function resumenTurnoActual(): Promise<ResumenTurno> {
  return contra(() => pedir<ResumenTurno>('/api/caja/turno'))
}

export async function listarCierres(): Promise<CierreCaja[]> {
  return contra(() => pedir<CierreCaja[]>('/api/caja/cierres'))
}

export async function cerrarTurno(cerradoPor: string): Promise<CierreCaja> {
  // Igual que con el cobro, quien cierra la caja lo determina el token: el
  // cierre es el documento con el que se entrega el dinero.
  void cerradoPor
  return contra(() => pedir<CierreCaja>('/api/caja/cierres', { metodo: 'POST' }))
}

export interface Reportes {
  masVendidos: { nombre: string; unidades: number; ingreso: number }[]
  porFranja: { franja: string; hora: number; ventas: number; ordenes: number }[]
  porMesero: { nombre: string; ordenes: number; ventas: number; ticketPromedio: number; propinas: number }[]
  tiemposPorProducto: { nombre: string; minutos: number; muestras: number }[]
  ventasPorDia: { dia: string; total: number }[]
  /** Cuanto pesa cada canal. Es la pregunta que el dueno hace primero. */
  porCanal: { canal: TipoPedido; ordenes: number; ventas: number; ticketPromedio: number }[]
}

export async function reportes(dias = 10): Promise<Reportes> {
  return contra(() => pedir<Reportes>('/api/reportes', { consulta: { dias } }))
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
  return contra(() => pedir<Alerta[]>('/api/caja/alertas', { consulta: { umbralMinutos } }))
}

/** Roles autorizados en cada area. Lo consulta la guarda de rutas. */
export const ACCESO_POR_AREA: Record<string, Rol[]> = {
  comandera: ['mesero', 'administrador'],
  cocina: ['cocina', 'administrador'],
  // El cajero y el administrador tambien entran a recepcion: en un restaurante
  // de este tamano la misma persona atiende el telefono y la caja.
  recepcion: ['recepcion', 'cajero', 'administrador'],
  admin: ['cajero', 'administrador'],
  reportes: ['administrador'],
}

export { SinConexionError }

/** Cola de envios pendientes, para el indicador de la comandera. */
export function pendientesDeEnvio(): number {
  return leerCola().length
}
