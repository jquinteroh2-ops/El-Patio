import { useState } from 'react'
import { Bike, Check, MapPin, MessageCircle, Navigation, Phone } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { PedidoEnRecepcion } from '@/compartido/mockApi'
import { formatoCOP, formatoTelefono, minutosDesde } from '@/compartido/formato'
import { useReloj, useSyncedState } from '@/compartido/useSyncedState'
import { enlaceWhatsApp, mensajePedidoEnCamino } from '@/compartido/whatsapp'
import { Boton } from '@/componentes/ui/Boton'
import { Insignia } from '@/componentes/ui/Insignia'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'
import { BarraOperativa } from '@/componentes/BarraOperativa'
import { MapaEntrega } from '@/componentes/MapaEntrega'
import { enlaceMapaPorDireccion, enlaceRutaHacia, enlaceWaze } from '@/publico/ubicacion'

/**
 * La pantalla de quien reparte.
 *
 * Se mira en un celular, a veces con casco puesto y con el motor prendido, así
 * que no es el tablero de recepción en pequeño: es una columna de tarjetas
 * grandes, una por entrega, y en cada una solo lo que hace falta para llegar a
 * esa puerta y cerrarla. Nada de columnas, filtros ni estados.
 *
 * Solo salen los pedidos despachados a su nombre. Ni los de otro repartidor ni
 * los que todavía están en cocina: son direcciones y teléfonos de clientes, y
 * cada quien ve los de las puertas a las que va a tocar. Esa regla la aplica el
 * servidor, no esta pantalla.
 */
export default function PantallaRepartidor() {
  const { mostrar } = useAvisos()
  const [entregando, setEntregando] = useState<string | null>(null)

  const { datos: entregas, cargando } = useSyncedState<PedidoEnRecepcion[]>(
    () => api.listarMisEntregas(),
    [],
    [],
    ['pedidos', 'ordenes', 'todo'],
  )

  // El cronómetro de cada tarjeta sin un intervalo por tarjeta.
  useReloj(15000)

  const confirmar = async (pedido: PedidoEnRecepcion) => {
    setEntregando(pedido.orden.id)
    try {
      await api.entregarMiPedido(pedido.orden.id)
      mostrar(`Pedido n.º ${pedido.orden.numero} entregado`, 'exito')
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo confirmar', 'error')
    } finally {
      setEntregando(null)
    }
  }

  return (
    <div className="min-h-dvh bg-noche-950">
      <BarraOperativa
        titulo="Mis entregas"
        subtitulo={
          entregas.length === 0
            ? 'Nada en la calle'
            : `${entregas.length} ${entregas.length === 1 ? 'pedido en la calle' : 'pedidos en la calle'}`
        }
        mostrarConexion
      />

      <div className="mx-auto max-w-md space-y-4 p-4">
        {cargando ? (
          <p className="py-10 text-center text-sm text-noche-400">Cargando sus entregas…</p>
        ) : entregas.length === 0 ? (
          <Vacio
            icono={Bike}
            titulo="No tiene entregas"
            descripcion="Cuando recepción despache un domicilio a su nombre, le aparece aquí."
          />
        ) : (
          entregas.map((pedido) => (
            <TarjetaEntrega
              key={pedido.orden.id}
              pedido={pedido}
              trabajando={entregando === pedido.orden.id}
              onEntregar={() => void confirmar(pedido)}
            />
          ))
        )}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Una entrega
// ---------------------------------------------------------------------------

interface PropsTarjeta {
  pedido: PedidoEnRecepcion
  trabajando: boolean
  onEntregar: () => void
}

function TarjetaEntrega({ pedido, trabajando, onEntregar }: PropsTarjeta) {
  const { orden, cuenta, etiqueta, zonaNombre } = pedido
  const telefono = orden.cliente?.telefono ?? ''
  const espera = minutosDesde(orden.recibidoEn ?? orden.abiertaEn)

  const chip =
    'inline-flex flex-1 items-center justify-center gap-1.5 rounded-xl border border-noche-700 bg-noche-850 px-3 py-3 text-sm text-crema-100 transition hover:border-oro-500/50'

  return (
    <article className="rounded-2xl border border-noche-800 bg-noche-900 p-4">
      <header className="mb-3 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-lg font-semibold text-crema-100">{etiqueta}</p>
          <p className="truncate text-sm text-noche-300">{orden.cliente?.nombre}</p>
        </div>
        <Insignia tono="oro">{espera} min</Insignia>
      </header>

      <p className="mb-2 flex items-start gap-1.5 text-sm text-crema-100">
        <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-oro-400" aria-hidden />
        <span>
          {orden.cliente?.direccion ?? 'Sin dirección anotada'}
          {zonaNombre && <span className="text-noche-400"> · {zonaNombre}</span>}
        </span>
      </p>

      {/*
        El mapa manda en esta pantalla: es lo que resuelve una dirección que no
        se entiende, que en Turbaco es la mitad de ellas. Va grande porque se
        mira de un vistazo antes de arrancar, no se estudia.
      */}
      <MapaEntrega
        ubicacion={orden.ubicacion}
        direccion={orden.cliente?.direccion}
        barrio={orden.cliente?.barrio}
        alto="h-56"
        titulo={`Dónde entregar el pedido n.º ${orden.numero}`}
        className="rounded-xl border border-noche-700"
      />

      <div className="mt-3 flex gap-2">
        {orden.ubicacion ? (
          <>
            <a href={enlaceWaze(orden.ubicacion)} target="_blank" rel="noreferrer" className={chip}>
              <Navigation className="h-4 w-4" aria-hidden />
              Waze
            </a>
            <a
              href={enlaceRutaHacia(orden.ubicacion.latitud, orden.ubicacion.longitud)}
              target="_blank"
              rel="noreferrer"
              className={chip}
            >
              <MapPin className="h-4 w-4" aria-hidden />
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
              Buscar la dirección
            </a>
          )
        )}
      </div>

      {orden.notas && (
        <p className="mt-3 rounded-xl bg-noche-850 px-3 py-2 text-sm italic text-noche-300">
          {orden.notas}
        </p>
      )}

      {/*
        Cuánto se cobra y con qué dijo que iba a pagar. Es lo que decide si hay
        que salir con vueltas: llegar sin cambio a un pago en efectivo es el
        problema más viejo del reparto.
      */}
      <div className="mt-3 flex items-center justify-between rounded-xl border border-noche-700 px-3 py-2">
        <div>
          <p className="text-xs uppercase tracking-wide text-noche-400">Cobrar</p>
          {orden.metodoPagoPrevisto && (
            <p className="text-xs text-noche-400">Dijo que pagaba con {orden.metodoPagoPrevisto}</p>
          )}
        </div>
        <p className="text-xl font-bold tabular-nums text-crema-100">{formatoCOP(cuenta.total)}</p>
      </div>

      {telefono && (
        <div className="mt-3 flex gap-2">
          <a href={`tel:+57${telefono}`} className={chip}>
            <Phone className="h-4 w-4" aria-hidden />
            {formatoTelefono(telefono)}
          </a>
          <a
            href={enlaceWhatsApp(telefono, mensajePedidoEnCamino(orden))}
            target="_blank"
            rel="noreferrer"
            className={chip}
          >
            <MessageCircle className="h-4 w-4" aria-hidden />
            WhatsApp
          </a>
        </div>
      )}

      <div className="mt-4">
        <Boton
          variante="exito"
          tamano="grande"
          bloque
          cargando={trabajando}
          onClick={onEntregar}
          icono={<Check className="h-5 w-5" aria-hidden />}
        >
          Confirmar entrega
        </Boton>
      </div>
    </article>
  )
}
