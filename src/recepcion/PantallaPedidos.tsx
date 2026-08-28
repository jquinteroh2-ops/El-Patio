import { useMemo, useState } from 'react'
import {
  Bike,
  Check,
  Clock,
  Crosshair,
  MapPin,
  MapPinned,
  MessageCircle,
  Navigation,
  Phone,
  Printer,
  ShoppingBag,
  X,
} from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { PedidoEnRecepcion, RepartidorDisponible } from '@/compartido/mockApi'
import { ESTADOS_PEDIDO, MOTIVOS_RECHAZO, UMBRAL_ALERTA_PEDIDO } from '@/compartido/config'
import { ETIQUETA_PEDIDO, TONO_PEDIDO, fondoEspera } from '@/compartido/estados'
import { formatoCOP, formatoTelefono, minutosDesde, tiempoTranscurrido } from '@/compartido/formato'
import { useReloj, useSyncedState } from '@/compartido/useSyncedState'
import {
  enlaceWhatsApp,
  mensajePedidoConfirmado,
  mensajePedidoEnCamino,
  mensajePedidoListoParaRecoger,
  mensajePedidoNuevoTiempo,
  mensajePedidoRechazado,
} from '@/compartido/whatsapp'
import type { EstadoPedido } from '@/compartido/tipos'
import { useAvisoNuevaComanda } from '@/cocina/avisoNuevaComanda'
import { imprimir } from '@/impresion/impresora'
import { ComprobanteTermico } from '@/impresion/ComprobanteTermico'
import { Boton } from '@/componentes/ui/Boton'
import { Campo, CampoArea } from '@/componentes/ui/Campo'
import { Contador } from '@/componentes/ui/Contador'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Insignia } from '@/componentes/ui/Insignia'
import { useAvisos } from '@/componentes/ui/Avisos'
import { BarraOperativa } from '@/componentes/BarraOperativa'
import { MapaEntrega } from '@/componentes/MapaEntrega'
import { BotonSonido } from './BotonSonido'
import { useSonidoRecepcion } from './sonido'
import { PestanasRecepcion } from './PestanasRecepcion'
import {
  PRECISION_ACEPTABLE_METROS,
  distanciaLegible,
  enlaceMapa,
  enlaceMapaPorDireccion,
  enlaceRutaHacia,
  enlaceWaze,
} from '@/publico/ubicacion'

/**
 * Recepcion de domicilios y para llevar.
 *
 * Es la pantalla que enciende cuando alguien pide desde la calle. Esta pensada
 * para un mostrador con ruido: una columna por estado, el cronometro grande y
 * el aviso sonoro del mismo tono que usa cocina, para que el personal no tenga
 * que aprender dos sonidos distintos.
 *
 * El pedido no sale a la plancha al entrar: se queda en «nuevos» hasta que
 * alguien lo acepta. Cocinar algo que nadie confirmo que se puede entregar es
 * comida perdida.
 */
export default function PantallaPedidos() {
  const { mostrar } = useAvisos()
  // El interruptor es del mostrador entero, no de esta pantalla: lo comparte
  // con reservas y lo enciende el boton de la cabecera.
  const { activo: sonidoActivo, alternar: alternarSonido } = useSonidoRecepcion()

  const activarSonido = async () => {
    if (!(await alternarSonido())) mostrar('El navegador no dejó activar el sonido', 'error')
  }

  const { datos: pedidos, cargando } = useSyncedState<PedidoEnRecepcion[]>(
    () => api.listarPedidos(),
    [],
    [],
    ['pedidos', 'ordenes', 'todo'],
  )

  // Mantiene vivos los cronometros sin un intervalo por tarjeta.
  useReloj(15000)

  // Quienes pueden llevarlo. Se escogen de una lista y no se escriben: un
  // nombre tecleado con una letra de mas no le aparece a nadie en su pantalla.
  const { datos: repartidores } = useSyncedState<RepartidorDisponible[]>(
    () => api.listarRepartidores(),
    [],
    [],
    ['usuarios', 'todo'],
  )

  // Mismo aviso que cocina: reutilizado, no reescrito.
  const clavesNuevas = useMemo(
    () => pedidos.filter((p) => p.orden.estadoPedido === 'nuevo').map((p) => p.orden.id),
    [pedidos],
  )
  const recientes = useAvisoNuevaComanda(clavesNuevas, sonidoActivo, 'entrada', !cargando)

  const [aceptando, setAceptando] = useState<PedidoEnRecepcion | null>(null)
  const [minutos, setMinutos] = useState(30)
  const [rechazando, setRechazando] = useState<PedidoEnRecepcion | null>(null)
  const [motivo, setMotivo] = useState('')
  const [despachando, setDespachando] = useState<PedidoEnRecepcion | null>(null)
  const [repartidor, setRepartidor] = useState('')
  const [repartidorId, setRepartidorId] = useState('')
  const [cambiandoTiempo, setCambiandoTiempo] = useState<PedidoEnRecepcion | null>(null)
  const [minutosNuevos, setMinutosNuevos] = useState(30)
  const [trabajando, setTrabajando] = useState(false)

  const porEstado = useMemo(() => {
    const mapa = new Map<EstadoPedido, PedidoEnRecepcion[]>()
    for (const columna of ESTADOS_PEDIDO) mapa.set(columna.estado, [])
    for (const pedido of pedidos) {
      const lista = mapa.get(pedido.orden.estadoPedido as EstadoPedido)
      if (lista) lista.push(pedido)
    }
    return mapa
  }, [pedidos])

  const conError = async (accion: () => Promise<unknown>, exito: string) => {
    setTrabajando(true)
    try {
      await accion()
      mostrar(exito, 'exito')
      return true
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo completar', 'error')
      return false
    } finally {
      setTrabajando(false)
    }
  }

  // ---------------------------------------------------------------------------
  // Acciones
  // ---------------------------------------------------------------------------

  const abrirAceptar = (pedido: PedidoEnRecepcion) => {
    // El tiempo que propone la zona es un punto de partida; quien conoce la
    // carga real de la cocina es la persona que esta mirando esta pantalla.
    setMinutos(pedido.orden.minutosEstimados ?? 30)
    setAceptando(pedido)
  }

  const confirmarAceptar = async () => {
    if (!aceptando) return
    const ok = await conError(
      () => api.aceptarPedido(aceptando.orden.id, minutos),
      `Pedido n.º ${aceptando.orden.numero} aceptado y enviado a cocina`,
    )
    if (ok) setAceptando(null)
  }

  const confirmarRechazo = async () => {
    if (!rechazando || !motivo.trim()) return
    const ok = await conError(
      () => api.rechazarPedido(rechazando.orden.id, motivo.trim()),
      `Pedido n.º ${rechazando.orden.numero} rechazado`,
    )
    if (ok) {
      setRechazando(null)
      setMotivo('')
    }
  }

  const confirmarDespacho = async () => {
    if (!despachando) return
    const ok = await conError(
      () => api.despacharPedido(despachando.orden.id, repartidor.trim(), repartidorId || undefined),
      `Pedido n.º ${despachando.orden.numero} despachado`,
    )
    if (ok) {
      setDespachando(null)
      setRepartidor('')
      setRepartidorId('')
    }
  }

  const abrirCambioTiempo = (pedido: PedidoEnRecepcion) => {
    setMinutosNuevos(pedido.orden.minutosEstimados ?? 30)
    setCambiandoTiempo(pedido)
  }

  const confirmarCambioTiempo = async () => {
    if (!cambiandoTiempo) return
    const ok = await conError(
      () => api.cambiarTiempoPedido(cambiandoTiempo.orden.id, minutosNuevos),
      `Pedido n.º ${cambiandoTiempo.orden.numero}: ahora en ${minutosNuevos} min`,
    )
    if (ok) setCambiandoTiempo(null)
  }

  const avanzar = (pedido: PedidoEnRecepcion, estado: EstadoPedido) =>
    conError(
      () => api.cambiarEstadoPedido(pedido.orden.id, estado),
      `Pedido n.º ${pedido.orden.numero}: ${ETIQUETA_PEDIDO[estado].toLowerCase()}`,
    )

  const entregar = (pedido: PedidoEnRecepcion) =>
    conError(
      () => api.entregarPedido(pedido.orden.id),
      `Pedido n.º ${pedido.orden.numero} entregado`,
    )

  /**
   * Saca el comprobante interno que se va con el pedido.
   *
   * Un domicilio es una venta como la de una mesa y le corresponde el mismo
   * papel: si el que viaja en la bolsa fuera distinto del que se entrega en el
   * salón, el restaurante tendría dos formatos según el canal y uno de los dos
   * estaría mal.
   *
   * No es un documento fiscal. El de esta venta lo emite Globalsoft, y no se
   * espera aquí: la comida se enfría mientras un ERP responde.
   */
  const imprimirComprobante = (pedido: PedidoEnRecepcion) => {
    // Lo que el cliente dijo que iba a pagar, que es todo lo que se sabe cuando
    // el papel se imprime: el cobro ocurre en la puerta, media hora despues. Si
    // al final paga distinto, lo que corrige eso es el cierre de caja.
    imprimir(
      <ComprobanteTermico
        orden={pedido.orden}
        cuenta={pedido.cuenta}
        etiqueta={pedido.etiqueta}
        atendidoPor="Recepción"
      />,
    )
  }

  if (cargando) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-noche-950 text-noche-400">
        Cargando pedidos…
      </div>
    )
  }

  return (
    <div className="flex min-h-dvh flex-col bg-noche-950">
      {/*
        El mismo encabezado que cocina, comandera y el panel. Recepcion tenia
        uno propio y por eso era la unica pantalla sin boton de salir: quien
        recibe domicilios se quedaba dentro de su usuario hasta cerrar el
        navegador.
      */}
      <BarraOperativa
        titulo="Recepción"
        subtitulo={
          pedidos.length === 0
            ? 'Sin pedidos en curso'
            : `${pedidos.length} ${pedidos.length === 1 ? 'pedido activo' : 'pedidos activos'}`
        }
        mostrarConexion
        acciones={
          <div className="flex items-center gap-1.5">
            <PestanasRecepcion activa="pedidos" />
            <BotonSonido />
          </div>
        }
      />

      {!sonidoActivo && (
        <p className="border-b border-noche-800 bg-noche-900 px-4 py-2 text-xs text-noche-400">
          El navegador exige un toque para permitir sonido.{' '}
          <button
            type="button"
            onClick={activarSonido}
            className="font-medium text-oro-400 underline"
          >
            Activar aviso sonoro
          </button>
        </p>
      )}

      {/* ---------- Columnas por estado ---------- */}
      <div className="flex-1 overflow-x-auto">
        <div className="flex min-h-full min-w-max">
          {ESTADOS_PEDIDO.map((columna) => {
            const lista = porEstado.get(columna.estado) ?? []
            return (
              <section
                key={columna.estado}
                className="flex w-[320px] shrink-0 flex-col border-r border-noche-800"
              >
                <header className="flex items-center justify-between border-b border-noche-800 px-3 py-2">
                  <h2 className="text-sm font-semibold uppercase tracking-wider text-noche-300">
                    {columna.etiqueta}
                  </h2>
                  <span className="rounded-lg bg-noche-800 px-2 py-0.5 text-xs font-semibold text-crema-100">
                    {lista.length}
                  </span>
                </header>

                <div className="flex-1 space-y-3 p-3">
                  {lista.length === 0 ? (
                    <p className="pt-6 text-center text-xs text-noche-600">Nada por aquí</p>
                  ) : (
                    lista.map((pedido) => (
                      <TarjetaPedido
                        key={pedido.orden.id}
                        pedido={pedido}
                        reciente={recientes.has(pedido.orden.id)}
                        trabajando={trabajando}
                        onAceptar={() => abrirAceptar(pedido)}
                        onRechazar={() => setRechazando(pedido)}
                        onAvanzar={(estado) => void avanzar(pedido, estado)}
                        onDespachar={() => {
                          setRepartidor(pedido.orden.repartidor ?? '')
                          setRepartidorId(pedido.orden.repartidorId ?? '')
                          setDespachando(pedido)
                        }}
                        onCambiarTiempo={() => abrirCambioTiempo(pedido)}
                        onEntregar={() => void entregar(pedido)}
                        onImprimir={() => void imprimirComprobante(pedido)}
                      />
                    ))
                  )}
                </div>
              </section>
            )
          })}
        </div>
      </div>


      {/* ---------- Aceptar ---------- */}
      <HojaInferior
        abierta={aceptando !== null}
        titulo={aceptando ? `Aceptar pedido n.º ${aceptando.orden.numero}` : ''}
        descripcion="Al aceptar, los productos entran a cocina y a barra según su destino."
        onCerrar={() => setAceptando(null)}
        pie={
          <Boton
            variante="exito"
            tamano="grande"
            bloque
            cargando={trabajando}
            onClick={confirmarAceptar}
            icono={<Check className="h-5 w-5" aria-hidden />}
          >
            Aceptar y mandar a cocina
          </Boton>
        }
      >
        <div className="space-y-4">
          <div>
            <p className="mb-2 text-sm text-noche-300">¿En cuántos minutos lo tenemos?</p>
            <Contador valor={minutos} onCambiar={setMinutos} minimo={5} maximo={180} />
            <p className="mt-2 text-xs text-noche-500">
              Es lo que se le promete al cliente. Prefiera quedarse corto antes que incumplir.
            </p>
          </div>

          {aceptando && (
            <EnlacesCliente
              pedido={aceptando}
              mensaje={mensajePedidoConfirmado(aceptando.orden, aceptando.cuenta.total)}
            />
          )}
        </div>
      </HojaInferior>

      {/* ---------- Rechazar ---------- */}
      <HojaInferior
        abierta={rechazando !== null}
        titulo={rechazando ? `Rechazar pedido n.º ${rechazando.orden.numero}` : ''}
        descripcion="El motivo queda registrado y es lo que se le explica al cliente."
        onCerrar={() => {
          setRechazando(null)
          setMotivo('')
        }}
        pie={
          <Boton
            variante="peligro"
            tamano="grande"
            bloque
            cargando={trabajando}
            disabled={!motivo.trim()}
            onClick={confirmarRechazo}
            icono={<X className="h-5 w-5" aria-hidden />}
          >
            Rechazar el pedido
          </Boton>
        }
      >
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            {MOTIVOS_RECHAZO.map((texto) => (
              <button
                key={texto}
                type="button"
                onClick={() => setMotivo(texto)}
                className={`rounded-xl border px-3 py-2 text-left text-xs transition ${
                  motivo === texto
                    ? 'border-oro-500 bg-oro-500/10 text-oro-300'
                    : 'border-noche-700 bg-noche-850 text-noche-300 hover:border-noche-600'
                }`}
              >
                {texto}
              </button>
            ))}
          </div>
          <CampoArea
            etiqueta="Motivo"
            rows={2}
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="Escriba el motivo o toque uno de arriba"
          />
          {rechazando && motivo.trim() && (
            <EnlacesCliente
              pedido={rechazando}
              mensaje={mensajePedidoRechazado(rechazando.orden, motivo.trim())}
            />
          )}
        </div>
      </HojaInferior>

      {/* ---------- Despachar ---------- */}
      <HojaInferior
        abierta={despachando !== null}
        titulo={despachando ? `Despachar pedido n.º ${despachando.orden.numero}` : ''}
        descripcion={
          despachando?.orden.tipo === 'domicilio'
            ? 'Anote quién lo lleva: si el cliente llama, hay a quién preguntarle.'
            : 'El pedido sale del local.'
        }
        onCerrar={() => setDespachando(null)}
        pie={
          <Boton
            variante="principal"
            tamano="grande"
            bloque
            cargando={trabajando}
            disabled={despachando?.orden.tipo === 'domicilio' && !repartidor.trim()}
            onClick={confirmarDespacho}
            icono={<Bike className="h-5 w-5" aria-hidden />}
          >
            Despachar
          </Boton>
        }
      >
        <div className="space-y-3">
          {/*
            Primero a dónde va, después quién sale: se mira el mapa para saber
            si uno se sabe llegar y solo entonces se anota el nombre.
          */}
          {despachando?.orden.tipo === 'domicilio' && (
            <>
              <EntregaDelDomicilio pedido={despachando} />

              {repartidores.length > 0 && (
                <div>
                  <p className="mb-2 text-sm text-noche-300">¿Quién lo lleva?</p>
                  <div className="flex flex-wrap gap-2">
                    {repartidores.map((quien) => (
                      <button
                        key={quien.id}
                        type="button"
                        onClick={() => {
                          setRepartidorId(quien.id)
                          setRepartidor(quien.nombre)
                        }}
                        className={`min-h-toque rounded-xl border px-4 text-sm transition ${
                          repartidorId === quien.id
                            ? 'border-oro-500 bg-oro-500/10 text-oro-300'
                            : 'border-noche-700 bg-noche-850 text-crema-100 hover:border-noche-600'
                        }`}
                      >
                        {quien.nombre}
                      </button>
                    ))}
                    {/* El que no tiene cuenta tiene que poder llevar un pedido
                        igual: el hijo del dueño, un motorizado de turno. */}
                    <button
                      type="button"
                      onClick={() => {
                        setRepartidorId('')
                        setRepartidor('')
                      }}
                      className={`min-h-toque rounded-xl border px-4 text-sm transition ${
                        repartidorId === ''
                          ? 'border-oro-500 bg-oro-500/10 text-oro-300'
                          : 'border-noche-700 bg-noche-850 text-noche-300 hover:border-noche-600'
                      }`}
                    >
                      Otra persona
                    </button>
                  </div>
                </div>
              )}

              {repartidorId === '' && (
                <Campo
                  etiqueta={repartidores.length > 0 ? 'Nombre de quien lo lleva' : '¿Quién lo lleva?'}
                  value={repartidor}
                  onChange={(e) => setRepartidor(e.target.value)}
                  placeholder="Nombre del domiciliario"
                />
              )}

              {repartidorId !== '' && (
                <p className="text-xs text-noche-500">
                  {repartidor} lo ve en su pantalla apenas se despache, y confirma la entrega desde
                  la puerta.
                </p>
              )}
            </>
          )}
          {despachando && (
            <EnlacesCliente
              pedido={despachando}
              mensaje={
                despachando.orden.tipo === 'domicilio'
                  ? mensajePedidoEnCamino({ ...despachando.orden, repartidor })
                  : mensajePedidoListoParaRecoger(despachando.orden)
              }
            />
          )}
        </div>
      </HojaInferior>

      {/* ---------- Cambiar el tiempo ---------- */}
      <HojaInferior
        abierta={cambiandoTiempo !== null}
        titulo={cambiandoTiempo ? `Tiempo del pedido n.º ${cambiandoTiempo.orden.numero}` : ''}
        descripcion="Corrige lo que se le prometió al cliente. El pedido no se mueve de columna."
        onCerrar={() => setCambiandoTiempo(null)}
        pie={
          <Boton
            variante="principal"
            tamano="grande"
            bloque
            cargando={trabajando}
            onClick={confirmarCambioTiempo}
            icono={<Clock className="h-5 w-5" aria-hidden />}
          >
            Guardar el tiempo
          </Boton>
        }
      >
        <div className="space-y-4">
          <div>
            <p className="mb-2 text-sm text-noche-300">
              ¿En cuántos minutos lo tenemos, contados desde ahora?
            </p>
            <Contador valor={minutosNuevos} onCambiar={setMinutosNuevos} minimo={5} maximo={180} />
            {cambiandoTiempo && (
              <p className="mt-2 text-xs text-noche-500">
                {cambiandoTiempo.orden.minutosEstimados !== undefined &&
                  `Se le habían prometido ${cambiandoTiempo.orden.minutosEstimados} min. `}
                Lleva esperando{' '}
                {minutosDesde(cambiandoTiempo.orden.recibidoEn ?? cambiandoTiempo.orden.abiertaEn)}{' '}
                min.
              </p>
            )}
          </div>

          {/*
            Cambiar el numero en la pantalla no le sirve de nada al cliente si
            nadie le avisa: por eso el mensaje sale aqui mismo, escrito y listo
            para enviar, como en todas las demas decisiones de esta pantalla.
          */}
          {cambiandoTiempo && (
            <EnlacesCliente
              pedido={cambiandoTiempo}
              mensaje={mensajePedidoNuevoTiempo(cambiandoTiempo.orden, minutosNuevos)}
            />
          )}
        </div>
      </HojaInferior>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Tarjeta de un pedido
// ---------------------------------------------------------------------------

interface PropsTarjeta {
  pedido: PedidoEnRecepcion
  reciente: boolean
  trabajando: boolean
  onAceptar: () => void
  onRechazar: () => void
  onAvanzar: (estado: EstadoPedido) => void
  onDespachar: () => void
  onCambiarTiempo: () => void
  onEntregar: () => void
  onImprimir: () => void
}

function TarjetaPedido({
  pedido,
  reciente,
  trabajando,
  onAceptar,
  onRechazar,
  onAvanzar,
  onDespachar,
  onCambiarTiempo,
  onEntregar,
  onImprimir,
}: PropsTarjeta) {
  const { orden, cuenta, etiqueta, zonaNombre, metrosDelLocal } = pedido
  // El reloj corre desde que el cliente pidio, no desde que alguien lo miro.
  const espera = minutosDesde(orden.recibidoEn ?? orden.abiertaEn)
  const estado = orden.estadoPedido as EstadoPedido
  // El mapa arranca abierto: es lo que se mira antes de mandar a alguien a la
  // calle. Se puede plegar porque en una noche con la columna llena, diez mapas
  // vivos a la vez pesan más de lo que ayudan.
  const [mapaAbierto, setMapaAbierto] = useState(true)

  return (
    <article
      className={`rounded-2xl border bg-noche-900 p-3 transition ${
        reciente ? 'border-oro-500 ring-2 ring-oro-500/40' : 'border-noche-800'
      }`}
    >
      <header className="mb-2 flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="flex items-center gap-1.5 truncate font-semibold text-crema-100">
            {orden.tipo === 'domicilio' ? (
              <Bike className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
            ) : (
              <ShoppingBag className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />
            )}
            <span className="truncate">{etiqueta}</span>
          </p>
          <p className="truncate text-xs text-noche-400">{orden.cliente?.nombre}</p>
        </div>
        <span
          className={`shrink-0 rounded-lg border px-2 py-1 text-sm font-bold tabular-nums ${fondoEspera(
            espera,
          )}`}
          title={`Entró hace ${tiempoTranscurrido(orden.recibidoEn ?? orden.abiertaEn)}`}
        >
          {espera}
          <span className="ml-0.5 text-[0.65rem] font-medium">min</span>
        </span>
      </header>

      {espera >= UMBRAL_ALERTA_PEDIDO && estado === 'nuevo' && (
        <p className="mb-2 rounded-lg bg-estado-demorado/10 px-2 py-1 text-xs font-medium text-estado-demorado">
          Lleva {espera} min sin respuesta
        </p>
      )}

      {/*
        Lo que se le prometio al cliente, al lado del reloj que corre. Sin esto
        el numero grande no dice nada: veinte minutos son pocos si se prometio
        media hora y son un problema si se prometio un cuarto.
      */}
      {orden.minutosEstimados !== undefined && estado !== 'nuevo' && (
        <p className="mb-2 flex items-center gap-1.5 text-xs text-noche-400">
          <Clock className="h-3.5 w-3.5 shrink-0 text-noche-500" aria-hidden />
          Prometido en {orden.minutosEstimados} min
          {espera > orden.minutosEstimados && estado !== 'entregado' && (
            <span className="font-medium text-estado-demorado">· ya se pasó</span>
          )}
        </p>
      )}

      {/* --- Entrega --- */}
      {orden.tipo === 'domicilio' && (
        <div className="mb-2">
          <p className="flex items-start gap-1.5 text-xs text-noche-300">
            <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-noche-500" aria-hidden />
            <span>
              {orden.cliente?.direccion}
              {zonaNombre && <span className="text-noche-500"> · {zonaNombre}</span>}
            </span>
          </p>

          {/*
            La dirección escrita manda; la coordenada es la ayuda. Si el cliente
            la compartió se ofrece navegación directa, y en cualquier caso el
            mapa de abajo enseña dónde queda: con el punto exacto si lo hay, y
            si no con la dirección escrita, que es menos preciso pero mejor que
            salir a adivinar.
          */}
          {orden.ubicacion && (
            <div className="mt-1.5">
              <div className="flex gap-1.5">
                <a
                  href={enlaceWaze(orden.ubicacion)}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 rounded-lg border border-noche-700 bg-noche-850 px-2 py-1 text-xs text-crema-100 transition hover:border-oro-500/50"
                >
                  <Navigation className="h-3 w-3" aria-hidden />
                  Waze
                </a>
                <a
                  href={enlaceMapa(orden.ubicacion)}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 rounded-lg border border-noche-700 bg-noche-850 px-2 py-1 text-xs text-crema-100 transition hover:border-oro-500/50"
                >
                  <Crosshair className="h-3 w-3" aria-hidden />
                  Mapa
                </a>
              </div>

              {/* Una coordenada de dos kilómetros de radio parece precisa y no
                  lo es: hay que decirlo antes de que alguien salga confiado. */}
              {orden.ubicacion.precisionMetros !== undefined &&
                orden.ubicacion.precisionMetros > PRECISION_ACEPTABLE_METROS && (
                  <p className="mt-1 text-xs text-estado-proceso">
                    Ubicación aproximada (±{orden.ubicacion.precisionMetros} m): guíese por la
                    dirección.
                  </p>
                )}

              {/* Pidió desde lejos: casi siempre significa que estaba en otro
                  sitio y la coordenada no es la de la entrega. */}
              {metrosDelLocal !== undefined && metrosDelLocal > 5000 && (
                <p className="mt-1 text-xs text-estado-proceso">
                  Pidió {distanciaLegible(metrosDelLocal)} del local: confirme la dirección antes de
                  despachar.
                </p>
              )}
            </div>
          )}

          {/*
            El mapa a la vista y no detrás de un enlace: en el mostrador se
            confirma el sector de un vistazo, sin salirse de la pantalla ni
            perder de vista el resto de los pedidos.
          */}
          {(orden.ubicacion || orden.cliente?.direccion) && (
            <>
              {mapaAbierto && (
                <MapaEntrega
                  ubicacion={orden.ubicacion}
                  direccion={orden.cliente?.direccion}
                  barrio={orden.cliente?.barrio}
                  alto="h-40"
                  titulo={`Dónde entregar el pedido n.º ${orden.numero}`}
                  className="mt-2 rounded-xl border border-noche-700"
                />
              )}
              <button
                type="button"
                onClick={() => setMapaAbierto((abierto) => !abierto)}
                aria-expanded={mapaAbierto}
                className="mt-1.5 inline-flex items-center gap-1 rounded-lg border border-noche-700 bg-noche-850 px-2 py-1 text-xs text-noche-300 transition hover:border-oro-500/50"
              >
                <MapPinned className="h-3 w-3" aria-hidden />
                {mapaAbierto ? 'Ocultar el mapa' : 'Ver el mapa'}
              </button>
            </>
          )}
        </div>
      )}

      {/* --- Productos --- */}
      <ul className="mb-2 space-y-1">
        {orden.items
          .filter((i) => i.estado !== 'anulado')
          .map((item) => (
            <li key={item.id} className="text-sm text-crema-100">
              <span className="mr-1.5 font-semibold text-oro-400">{item.cantidad}×</span>
              {item.nombre}
              {item.modificadoresSeleccionados.length > 0 && (
                <span className="block pl-5 text-xs text-noche-400">
                  {item.modificadoresSeleccionados.map((m) => m.valor).join(' · ')}
                </span>
              )}
              {item.notaCocina && (
                <span className="block pl-5 text-xs italic text-estado-proceso">
                  {item.notaCocina}
                </span>
              )}
            </li>
          ))}
      </ul>

      {orden.notas && (
        <p className="mb-2 rounded-lg bg-noche-850 px-2 py-1 text-xs italic text-noche-300">
          {orden.notas}
        </p>
      )}

      {/* --- Cuenta --- */}
      <div className="mb-3 border-t border-noche-800 pt-2 text-xs">
        <div className="flex justify-between text-noche-400">
          <span>Subtotal</span>
          <span className="tabular-nums">{formatoCOP(cuenta.subtotal)}</span>
        </div>
        <div className="flex justify-between text-noche-400">
          <span>INC {cuenta.porcentajeInc}%</span>
          <span className="tabular-nums">{formatoCOP(cuenta.inc)}</span>
        </div>
        {cuenta.costoEnvio > 0 && (
          <div className="flex justify-between text-noche-400">
            <span>Envío</span>
            <span className="tabular-nums">{formatoCOP(cuenta.costoEnvio)}</span>
          </div>
        )}
        <div className="mt-1 flex justify-between font-semibold text-crema-100">
          <span>Total</span>
          <span className="tabular-nums">{formatoCOP(cuenta.total)}</span>
        </div>
        {orden.metodoPagoPrevisto && (
          <p className="mt-1 text-noche-500">Pagaría con {orden.metodoPagoPrevisto}</p>
        )}
      </div>

      {/* --- Contacto --- */}
      <div className="mb-2 flex gap-2">
        <a
          href={`tel:+57${orden.cliente?.telefono ?? ''}`}
          className="flex flex-1 items-center justify-center gap-1.5 rounded-xl border border-noche-700 bg-noche-850 px-2 py-2 text-xs text-crema-100 transition hover:border-noche-600"
        >
          <Phone className="h-3.5 w-3.5" aria-hidden />
          {formatoTelefono(orden.cliente?.telefono ?? '')}
        </a>
      </div>

      {/* --- Acciones --- */}
      <div className="flex flex-wrap gap-2">
        {estado === 'nuevo' && (
          <>
            <Boton variante="exito" tamano="compacto" onClick={onAceptar} disabled={trabajando}>
              Aceptar
            </Boton>
            <Boton variante="peligro" tamano="compacto" onClick={onRechazar} disabled={trabajando}>
              Rechazar
            </Boton>
          </>
        )}

        {estado === 'aceptado' && (
          <Boton
            variante="principal"
            tamano="compacto"
            onClick={() => onAvanzar('en_preparacion')}
            disabled={trabajando}
          >
            En preparación
          </Boton>
        )}

        {estado === 'en_preparacion' && (
          <Boton
            variante="exito"
            tamano="compacto"
            onClick={() => onAvanzar('listo')}
            disabled={trabajando}
          >
            Marcar listo
          </Boton>
        )}

        {estado === 'listo' && (
          <Boton variante="principal" tamano="compacto" onClick={onDespachar} disabled={trabajando}>
            {orden.tipo === 'domicilio' ? 'Despachar' : 'Entregar al cliente'}
          </Boton>
        )}

        {estado === 'despachado' && (
          <Boton variante="exito" tamano="compacto" onClick={onEntregar} disabled={trabajando}>
            Confirmar entrega
          </Boton>
        )}

        {/*
          El tiempo prometido deja de ser verdad en cuanto la cocina se atrasa.
          Corregirlo desde la misma tarjeta es lo que evita que el cliente se
          entere esperando en la puerta.
        */}
        {(estado === 'aceptado' || estado === 'en_preparacion' || estado === 'listo') && (
          <Boton
            variante="fantasma"
            tamano="compacto"
            onClick={onCambiarTiempo}
            disabled={trabajando}
            icono={<Clock className="h-3.5 w-3.5" aria-hidden />}
          >
            Cambiar el tiempo
          </Boton>
        )}

        <Boton
          variante="fantasma"
          tamano="compacto"
          onClick={onImprimir}
          icono={<Printer className="h-3.5 w-3.5" aria-hidden />}
          aria-label="Imprimir comprobante"
        >
          Imprimir
        </Boton>
      </div>

      {orden.repartidor && (
        <p className="mt-2 text-xs text-noche-400">
          Lo lleva <span className="text-crema-100">{orden.repartidor}</span>
        </p>
      )}

      <div className="mt-2">
        <Insignia tono={TONO_PEDIDO[estado]}>{ETIQUETA_PEDIDO[estado]}</Insignia>
      </div>
    </article>
  )
}

// ---------------------------------------------------------------------------
// Contacto por WhatsApp
// ---------------------------------------------------------------------------

/**
 * Muestra el texto antes de abrir WhatsApp.
 *
 * Nada sale a nombre del restaurante sin que alguien lo lea: es la misma regla
 * que se sigue con las reservas.
 */
function EnlacesCliente({ pedido, mensaje }: { pedido: PedidoEnRecepcion; mensaje: string }) {
  const telefono = pedido.orden.cliente?.telefono ?? ''
  if (!telefono) return null

  return (
    <div className="rounded-2xl border border-noche-700 bg-noche-850 p-3">
      <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-oro-400">
        Mensaje para el cliente
      </p>
      <p className="mb-3 whitespace-pre-line text-sm leading-relaxed text-noche-200">{mensaje}</p>
      <div className="flex gap-2">
        <a
          href={enlaceWhatsApp(telefono, mensaje)}
          target="_blank"
          rel="noreferrer"
          className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-estado-listo px-3 py-2 text-sm font-semibold text-noche-950 transition hover:brightness-110"
        >
          <MessageCircle className="h-4 w-4" aria-hidden />
          Enviar por WhatsApp
        </a>
        <a
          href={`tel:+57${telefono}`}
          className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-noche-700 px-3 py-2 text-sm text-crema-100 transition hover:border-noche-600"
        >
          <Phone className="h-4 w-4" aria-hidden />
          Llamar
        </a>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Lo que ve quien sale a repartir
// ---------------------------------------------------------------------------

/**
 * A dónde va el domicilio, en mapa y con la navegación a un toque.
 *
 * Se enseña al despachar porque es el último momento en que alguien puede
 * notar que la dirección no cuadra con el mapa, cuando la comida todavía está
 * en el local y no dando vueltas por el barrio. Los botones abren Waze o
 * Google Maps en el teléfono de quien sale, sin copiar direcciones a mano.
 */
function EntregaDelDomicilio({ pedido }: { pedido: PedidoEnRecepcion }) {
  const { orden, zonaNombre } = pedido
  const chip =
    'inline-flex items-center gap-1.5 rounded-xl border border-noche-700 px-3 py-2 text-sm text-crema-100 transition hover:border-oro-500/50'

  return (
    <div className="rounded-2xl border border-noche-700 bg-noche-850 p-3">
      <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-oro-400">
        Para quien lo lleva
      </p>

      <p className="flex items-start gap-1.5 text-sm text-crema-100">
        <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-noche-400" aria-hidden />
        <span>
          {orden.cliente?.direccion ?? 'Sin dirección anotada'}
          {zonaNombre && <span className="text-noche-400"> · {zonaNombre}</span>}
        </span>
      </p>

      <MapaEntrega
        ubicacion={orden.ubicacion}
        direccion={orden.cliente?.direccion}
        barrio={orden.cliente?.barrio}
        alto="h-52"
        titulo={`Dónde entregar el pedido n.º ${orden.numero}`}
        className="mt-2 rounded-xl border border-noche-700"
      />

      <div className="mt-2 flex flex-wrap gap-2">
        {orden.ubicacion ? (
          <>
            <a href={enlaceWaze(orden.ubicacion)} target="_blank" rel="noreferrer" className={chip}>
              <Navigation className="h-4 w-4" aria-hidden />
              Abrir en Waze
            </a>
            <a
              href={enlaceRutaHacia(orden.ubicacion.latitud, orden.ubicacion.longitud)}
              target="_blank"
              rel="noreferrer"
              className={chip}
            >
              <Crosshair className="h-4 w-4" aria-hidden />
              Cómo llegar
            </a>
          </>
        ) : (
          orden.cliente?.direccion && (
            <a
              href={enlaceMapaPorDireccion(orden.cliente.direccion, orden.cliente.barrio)}
              target="_blank"
              rel="noreferrer"
              className={chip}
            >
              <MapPin className="h-4 w-4" aria-hidden />
              Buscar la dirección en el mapa
            </a>
          )
        )}
      </div>
    </div>
  )
}
