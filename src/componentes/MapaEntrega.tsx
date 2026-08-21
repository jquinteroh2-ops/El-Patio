import type { UbicacionEntrega } from '@/compartido/tipos'
import { enlaceMapaEmbebido, enlaceMapaEmbebidoPorDireccion } from '@/publico/ubicacion'

interface Props {
  /** El punto que compartió el cliente. Manda sobre la dirección escrita. */
  ubicacion?: UbicacionEntrega | null
  /** Respaldo cuando no hay punto: se busca la dirección en el mapa. */
  direccion?: string
  barrio?: string
  /** Alto del mapa, como clase de Tailwind. Cambia según dónde se muestre. */
  alto?: string
  /** Va al `title` del iframe: es lo que anuncia un lector de pantalla. */
  titulo: string
  /** Borde y redondeo, que no son iguales en el sitio público y en operación. */
  className?: string
}

/**
 * El mapa de la entrega, igual en todas las pantallas.
 *
 * Un enlace a Google Maps obliga a salirse de lo que se está haciendo para
 * comprobar algo que se responde de un vistazo: el cliente quiere ver que el
 * punto cayó en su casa antes de enviar el pedido, y quien lo lleva quiere ver
 * el sector antes de arrancar. Por eso el mapa se enseña donde se necesita en
 * vez de esconderlo detrás de un enlace.
 *
 * Va embebido sin llave de API —una llave en el paquete compilado es una llave
 * pública— y con `loading="lazy"`, que importa de verdad en recepción: son
 * varias tarjetas a la vez y el mapa no puede retrasar la que trae el pedido.
 *
 * Si no hay coordenada se cae a la dirección escrita, y entonces lo dice: un
 * mapa que apunta al centro del pueblo parece exacto sin serlo.
 */
export function MapaEntrega({
  ubicacion,
  direccion,
  barrio,
  alto = 'h-52',
  titulo,
  className = '',
}: Props) {
  const escrita = direccion?.trim()
  const fuente = ubicacion
    ? enlaceMapaEmbebido(ubicacion.latitud, ubicacion.longitud)
    : escrita
      ? enlaceMapaEmbebidoPorDireccion(escrita, barrio)
      : null

  // Sin punto y sin dirección no hay nada que enseñar: para llevar, por ejemplo.
  if (!fuente) return null

  return (
    <div>
      <div className={`overflow-hidden ${className}`}>
        <iframe
          title={titulo}
          src={fuente}
          loading="lazy"
          referrerPolicy="no-referrer-when-downgrade"
          className={`w-full border-0 ${alto}`}
        />
      </div>
      {!ubicacion && (
        <p className="mt-1 text-xs text-noche-400">
          El cliente no compartió su ubicación: el mapa busca la dirección escrita y puede quedar
          cerca, no exacto.
        </p>
      )}
    </div>
  )
}
