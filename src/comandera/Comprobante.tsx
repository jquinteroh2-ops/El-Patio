import { precioItem } from '@/compartido/calculos'
import { DATOS_FISCALES, RESTAURANTE } from '@/compartido/config'
import { formatoCOP, formatoFechaHora } from '@/compartido/formato'
import type { Orden, Pago } from '@/compartido/tipos'
import { DENOMINACION } from '@/facturacion/catalogos'
import type { DocumentoElectronico } from '@/facturacion/tipos'

const NOMBRE_METODO: Record<Pago['metodo'], string> = {
  efectivo: 'Efectivo',
  tarjeta: 'Tarjeta',
  transferencia: 'Transferencia',
  mixto: 'Pago mixto',
}

interface Props {
  pago: Pago
  orden: Orden
  mesaEtiqueta: string
  meseroNombre: string
  /**
   * El documento ante la DIAN, si salió.
   *
   * En nulo significa que el cobro quedó registrado y el documento no: eso el
   * cajero tiene que verlo en la pantalla, no solo en un aviso que se va solo a
   * los pocos segundos, porque es él quien lo tiene que resolver después.
   */
  documento?: DocumentoElectronico | null
}

/**
 * Comprobante para el cliente.
 *
 * Va en claro sobre oscuro a proposito: es lo unico que ve el comensal, no una
 * herramienta de trabajo. La propina figura como linea aparte y marcada como
 * voluntaria, y cada cargo adicional aparece con su nombre.
 */
export function Comprobante({ pago, orden, mesaEtiqueta, meseroNombre, documento }: Props) {
  const vigentes = orden.items.filter((i) => i.estado !== 'anulado')

  return (
    <article className="mx-auto w-full max-w-md rounded-2xl bg-crema-50 p-5 text-bosque-950 shadow-xl shadow-black/40">
      <header className="border-b border-bosque-950/15 pb-3 text-center">
        <p className="font-marca text-lg tracking-[0.28em]">EL PATIO</p>
        <p className="mt-1 text-xs text-bosque-950/70">
          {RESTAURANTE.direccion} · {RESTAURANTE.ciudad}
        </p>
        <p className="text-xs text-bosque-950/70">{RESTAURANTE.telefono}</p>
      </header>

      <div className="grid grid-cols-2 gap-y-1 border-b border-bosque-950/15 py-3 text-xs text-bosque-950/80">
        <span>Comanda</span>
        <span className="text-right font-medium">#{orden.numero}</span>
        <span>Mesa</span>
        <span className="text-right font-medium">{mesaEtiqueta}</span>
        <span>Comensales</span>
        <span className="text-right font-medium">{orden.comensales}</span>
        <span>Atendió</span>
        <span className="text-right font-medium">{meseroNombre}</span>
        <span>Fecha</span>
        <span className="text-right font-medium">{formatoFechaHora(pago.fechaHora)}</span>
      </div>

      <ul className="space-y-1.5 border-b border-bosque-950/15 py-3">
        {vigentes.map((item) => (
          <li key={item.id} className="flex items-start justify-between gap-3 text-sm">
            <span className="min-w-0">
              <span className="font-medium">
                {item.cantidad} × {item.nombre}
              </span>
              {item.modificadoresSeleccionados.length > 0 && (
                <span className="block text-xs text-bosque-950/60">
                  {item.modificadoresSeleccionados.map((m) => m.valor).join(' · ')}
                </span>
              )}
            </span>
            <span className="shrink-0 tabular-nums">{formatoCOP(precioItem(item))}</span>
          </li>
        ))}
      </ul>

      <dl className="space-y-1.5 py-3 text-sm">
        <div className="flex justify-between">
          <dt>Subtotal</dt>
          <dd className="tabular-nums">{formatoCOP(pago.subtotal)}</dd>
        </div>
        <div className="flex justify-between">
          <dt>Impuesto al consumo (INC)</dt>
          <dd className="tabular-nums">{formatoCOP(pago.inc)}</dd>
        </div>

        {orden.cargosAdicionales.map((cargo) => (
          <div key={cargo.id} className="flex justify-between">
            <dt>{cargo.nombre}</dt>
            <dd className="tabular-nums">{formatoCOP(cargo.valor)}</dd>
          </div>
        ))}

        <div className="flex justify-between border-t border-dashed border-bosque-950/25 pt-1.5">
          <dt>
            Propina
            <span className="ml-1.5 rounded border border-bosque-950/25 px-1 text-[0.65rem] uppercase tracking-wide">
              Voluntaria
            </span>
          </dt>
          <dd className="tabular-nums">{formatoCOP(pago.propina)}</dd>
        </div>

        <div className="flex justify-between border-t border-bosque-950/25 pt-2 text-base font-bold">
          <dt>Total</dt>
          <dd className="tabular-nums">{formatoCOP(pago.total)}</dd>
        </div>
      </dl>

      <div className="border-t border-bosque-950/15 pt-3 text-sm">
        <div className="flex justify-between font-medium">
          <span>{NOMBRE_METODO[pago.metodo]}</span>
          <span className="tabular-nums">{formatoCOP(pago.total)}</span>
        </div>
        {pago.divisiones && pago.divisiones.length > 1 && (
          <ul className="mt-1.5 space-y-1 text-xs text-bosque-950/70">
            {pago.divisiones.map((parte, i) => (
              <li key={i} className="flex justify-between">
                <span>
                  {parte.nombre} · {NOMBRE_METODO[parte.metodo]}
                </span>
                <span className="tabular-nums">{formatoCOP(parte.valor)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <footer className="mt-4 border-t border-bosque-950/15 pt-3 text-center">
        <p className="text-xs text-bosque-950/70">
          La propina es voluntaria. Si no está de acuerdo con ella, puede solicitar su retiro.
        </p>

        {/* Qué es este papel, dicho sin rodeos y según lo que realmente pasó.
            Son tres situaciones distintas y ninguna se puede describir con el
            texto de otra: hay documento fiscal, hay documento de prueba, o no
            hay documento. */}
        {documento && !documento.esPrueba && (
          <p className="mt-2 text-[0.65rem] leading-relaxed text-bosque-950/70">
            {DENOMINACION[documento.tipo]} N.º {documento.numeroCompleto}
            <br />
            Consúltela en el portal de la DIAN con el código impreso.
          </p>
        )}

        {documento?.esPrueba && (
          <p className="mt-2 text-[0.65rem] leading-relaxed text-bosque-950/50">
            Documento de prueba N.º {documento.numeroCompleto} · sin valor fiscal.
            <br />
            {DATOS_FISCALES.leyenda}
          </p>
        )}

        {!documento && (
          <p className="mt-2 text-[0.65rem] leading-relaxed text-bosque-950/50">
            {DATOS_FISCALES.leyenda}
          </p>
        )}
      </footer>
    </article>
  )
}
