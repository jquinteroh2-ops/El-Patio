import { useState } from 'react'
import { Hand } from 'lucide-react'
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
  /* Si el mapa ya recibe el dedo. Arranca apagado: ver el sector es lo comun,
     moverlo es la excepcion. */
  const [activo, setActivo] = useState(false)

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
      {/*
        El mapa nace SIN capturar el dedo, y se activa al tocarlo.

        Un iframe de Google se traga el arrastre: dentro de una hoja emergente
        —la del comprobante de una venta, por ejemplo— el mapa ocupa media
        superficie desplazable, y quien intenta bajar al detalle mueve el mapa
        en vez de la hoja. La hoja parece atascada.

        Con la capa encima, el arrastre pasa de largo y desplaza la hoja, que es
        lo que se quiere el 95% de las veces. Quien de verdad quiera mover el
        mapa lo toca una vez y ya lo tiene.
      */}
      <div className={`relative overflow-hidden ${className}`}>
        <iframe
          title={titulo}
          src={fuente}
          loading="lazy"
          referrerPolicy="no-referrer-when-downgrade"
          className={`w-full border-0 ${alto} ${activo ? '' : 'pointer-events-none'}`}
        />
        {!activo && (
          <button
            type="button"
            onClick={() => setActivo(true)}
            /* `group` para que el rótulo reaccione al pasar por encima sin
               tapar el mapa mientras tanto: en reposo es casi invisible. */
            className="group absolute inset-0 flex items-end justify-center pb-2"
            aria-label="Activar el mapa para poder moverlo"
          >
            <span className="flex items-center gap-1.5 rounded-full bg-noche-950/75 px-2.5 py-1 text-xs text-crema-100/80 opacity-0 transition group-hover:opacity-100 group-focus-visible:opacity-100">
              <Hand className="h-3.5 w-3.5" aria-hidden />
              Toca para mover el mapa
            </span>
          </button>
        )}
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
