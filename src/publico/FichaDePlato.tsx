import { useRef, useState } from 'react'
import * as api from '@/compartido/mockApi'
import { enPromocion, precioVigente } from '@/compartido/calculos'
import { formatoCOP } from '@/compartido/formato'
import { fotosDePlato } from '@/compartido/fotosDePlato'
import type { ItemCarta } from '@/compartido/tipos'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { HojaModificadores, type SeleccionProducto } from '@/comandera/HojaModificadores'

/** El carrusel: se arrastra de lado, con un punto por foto. */
function Fotos({ item, fotos }: { item: ItemCarta; fotos: string[] }) {
  const pista = useRef<HTMLDivElement>(null)
  const [visible, setVisible] = useState(0)

  const irA = (i: number) => {
    const el = pista.current
    if (!el) return
    el.scrollTo({ left: i * el.clientWidth, behavior: 'smooth' })
  }

  return (
    <div className="-mx-4 -mt-4">
      {/*
        Alto fijo y `object-contain`, no un recorte.

        Las fotos son verticales y estan tomadas para que se vea el plato
        ENTERO con su fondo —la madera, el jardin, el salon—, que es justo lo
        que un recorte cuadrado se lleva por delante. Y el alto fijo va en el
        contenedor, no en la imagen: asi el sitio ya esta reservado antes de
        que la foto baje, y la descripcion no da un salto cuando termina de
        cargar.
      */}
      <div
        ref={pista}
        onScroll={(e) => setVisible(Math.round(e.currentTarget.scrollLeft / e.currentTarget.clientWidth))}
        role="group"
        aria-label={`Fotos de ${item.nombre}`}
        tabIndex={0}
        className="sin-scrollbar flex h-[42dvh] snap-x snap-mandatory overflow-x-auto overscroll-x-contain bg-onix-950 outline-none"
      >
        {fotos.map((foto, i) => (
          <div
            key={foto}
            className="flex h-full w-full shrink-0 snap-center items-center justify-center"
          >
            <img
              src={api.urlImagenCarta(foto, 900)}
              alt={
                fotos.length === 1
                  ? item.nombre
                  : `${item.nombre}, foto ${i + 1} de ${fotos.length}`
              }
              /* La primera se pide ya; las demas, solo si el cliente arrastra.
                 En datos moviles bajar cinco fotos de una para que mire una es
                 gastarle el plan por adelantado. */
              loading={i === 0 ? 'eager' : 'lazy'}
              className="h-full w-auto max-w-full object-contain"
            />
          </div>
        ))}
      </div>

      {fotos.length > 1 && (
        <div className="flex items-center justify-center gap-1.5 py-3">
          {fotos.map((foto, i) => (
            <button
              key={foto}
              type="button"
              onClick={() => irA(i)}
              aria-label={`Ver la foto ${i + 1}`}
              aria-current={i === visible}
              /* El punto se dibuja pequeño, pero el boton mide 32 px: un punto
                 de 6 px es imposible de acertar con el pulgar. */
              className="flex h-8 w-8 items-center justify-center"
            >
              <span
                className={`block h-1.5 rounded-full transition-all ${
                  i === visible ? 'w-5 bg-oro-400' : 'w-1.5 bg-crema-100/30'
                }`}
              />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

/** Las fotos, la descripcion y lo que haya que advertir del plato. */
function Presentacion({ item }: { item: ItemCarta }) {
  const fotos = fotosDePlato(item)

  return (
    <div className="space-y-3">
      {fotos.length > 0 && <Fotos item={item} fotos={fotos} />}

      {item.descripcion && (
        <p className="text-[0.95rem] leading-relaxed text-crema-100/70">{item.descripcion}</p>
      )}

      {enPromocion(item) && (
        <p className="text-sm text-noche-400">
          Antes <span className="line-through">{formatoCOP(item.precio)}</span>
          {' · '}
          <span className="text-oro-300">hoy {formatoCOP(precioVigente(item))}</span>
        </p>
      )}
    </div>
  )
}

interface Props {
  item: ItemCarta | null
  /** Si la cocina esta recibiendo pedidos ahora mismo. */
  canalAbierto: boolean
  onCerrar: () => void
  onConfirmar: (seleccion: SeleccionProducto) => void
}

/**
 * La ficha que se abre al tocar un plato en la carta: las fotos, lo que lleva
 * y —si se puede— el pedido, en la misma hoja.
 *
 * Que el pedido este AQUI y no en una hoja aparte es la decision de fondo. El
 * cliente esta decidiendo mirando la foto; obligarlo a cerrar la ficha para
 * abrir otra hoja le quita de delante justo aquello con lo que estaba
 * decidiendo, y lo deja eligiendo el termino de una carne que ya no ve.
 *
 * Por eso no hay dos componentes: la hoja de pedido recibe la presentacion
 * como encabezado y sigue siendo la unica que sabe de modificadores, cantidad
 * y notas. La que usa el mesero es la misma, sin encabezado.
 */
export function FichaDePlato({ item, canalAbierto, onCerrar, onConfirmar }: Props) {
  if (!item) return null

  /*
   * Un plato agotado se mira pero no se pide, y esa cuenta se hace aqui a
   * proposito: si la dejara en manos de quien abre la ficha, bastaria con que
   * una pantalla nueva se olvidara de mirar `disponible` para que un cliente
   * pidiera algo que no hay en la nevera.
   */
  const sePuedePedir = canalAbierto && item.disponible

  if (!sePuedePedir) {
    return (
      <HojaInferior
        abierta
        titulo={item.nombre}
        descripcion={formatoCOP(precioVigente(item))}
        onCerrar={onCerrar}
      >
        <Presentacion item={item} />
        {!item.disponible && (
          <p className="mt-4 rounded-xl border border-noche-700 bg-noche-850 px-3.5 py-3 text-sm text-noche-300">
            Hoy está agotado. Mañana vuelve a la carta.
          </p>
        )}
      </HojaInferior>
    )
  }

  return (
    <HojaModificadores
      item={item}
      encabezado={<Presentacion item={item} />}
      onCerrar={onCerrar}
      onConfirmar={onConfirmar}
    />
  )
}
