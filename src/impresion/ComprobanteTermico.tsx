import { DATOS_FISCALES, RESTAURANTE } from '@/compartido/config'
import { formatoCOP, formatoFechaHora } from '@/compartido/formato'
import { precioItem } from '@/compartido/calculos'
import type { Cuenta } from '@/compartido/calculos'
import type { MetodoPago, Orden } from '@/compartido/tipos'
import './termica.css'

/**
 * La cuenta del cliente en 80 mm.
 *
 * ADVERTENCIA LEGAL, y es la que manda sobre el diseño: este documento NO es
 * una factura electrónica de venta ante la DIAN. Es un comprobante interno.
 * Por eso no se titula «Factura», no lleva número de resolución inventado y el
 * pie lo dice textualmente. El espacio para la resolución y el prefijo está
 * previsto y se llena solo cuando el restaurante quede habilitado.
 *
 * Es una versión aparte de src/comandera/Comprobante.tsx y no una adaptación:
 * aquel es crema sobre oscuro y está hecho para mirarse en una tablet. Este es
 * plano, angosto y monocromo, porque una térmica quema papel y no imprime
 * fondos.
 */

interface Props {
  orden: Orden
  cuenta: Cuenta
  /** «Mesa 7», «Domicilio #18». */
  etiqueta: string
  atendidoPor: string
  metodo?: MetodoPago
  /** Precuenta: la que se lleva a la mesa antes de cobrar. */
  esPrecuenta?: boolean
  /** Una parte de una cuenta dividida. */
  parte?: { nombre: string; valor: number; metodo: MetodoPago }
}

const NOMBRE_METODO: Record<MetodoPago, string> = {
  efectivo: 'Efectivo',
  tarjeta: 'Tarjeta',
  transferencia: 'Transferencia',
  mixto: 'Pago mixto',
}

export function ComprobanteTermico({
  orden,
  cuenta,
  etiqueta,
  atendidoPor,
  metodo,
  esPrecuenta = false,
  parte,
}: Props) {
  const items = orden.items.filter((i) => i.estado !== 'anulado')

  return (
    <div className="ticket">
      {/* ---------- Establecimiento ---------- */}
      <div className="ticket-centro">
        <p className="ticket-grande">{RESTAURANTE.nombre}</p>
        <p>{DATOS_FISCALES.razonSocial}</p>
        <p>NIT {DATOS_FISCALES.nit}</p>
        <p>
          {RESTAURANTE.direccion}
          <br />
          {RESTAURANTE.ciudad}
        </p>
        <p>{RESTAURANTE.telefono}</p>
      </div>

      <hr className="ticket-separador" />

      {/* ---------- Documento ---------- */}
      <div className="ticket-centro ticket-negrita">
        {esPrecuenta ? 'PRECUENTA — NO ES COBRO' : 'COMPROBANTE INTERNO DE VENTA'}
      </div>

      <hr className="ticket-separador" />

      <div className="ticket-linea">
        <span>Consecutivo</span>
        <span className="ticket-negrita">N.º {orden.numero}</span>
      </div>
      <div className="ticket-linea">
        <span>Fecha</span>
        <span>{formatoFechaHora(orden.cerradaEn ?? new Date())}</span>
      </div>
      <div className="ticket-linea">
        <span>{orden.tipo === 'mesa' ? 'Mesa' : 'Pedido'}</span>
        <span>{etiqueta}</span>
      </div>
      <div className="ticket-linea">
        <span>Atendió</span>
        <span>{atendidoPor}</span>
      </div>
      {orden.cliente && (
        <>
          <div className="ticket-linea">
            <span>Cliente</span>
            <span>{orden.cliente.nombre}</span>
          </div>
          {orden.cliente.direccion && (
            <div className="ticket-detalle">{orden.cliente.direccion}</div>
          )}
        </>
      )}

      <hr className="ticket-separador" />

      {/* ---------- Productos ---------- */}
      {items.map((item) => (
        <div key={item.id} className="ticket-item">
          <div className="ticket-linea">
            <span>
              {item.cantidad} × {item.nombre}
            </span>
            <span>{formatoCOP(precioItem(item))}</span>
          </div>
          {item.modificadoresSeleccionados.map((m, i) => (
            <div key={`${item.id}-${i}`} className="ticket-detalle">
              + {m.valor}
              {m.precioAdicional > 0 && ` (${formatoCOP(m.precioAdicional)} c/u)`}
            </div>
          ))}
          {item.notaCocina && <div className="ticket-detalle">* {item.notaCocina}</div>}
        </div>
      ))}

      <hr className="ticket-separador" />

      {/* ---------- Totales ---------- */}
      <div className="ticket-linea">
        <span>Subtotal</span>
        <span>{formatoCOP(cuenta.subtotal)}</span>
      </div>

      {/* El INC va discriminado con su porcentaje: es lo que el cliente tiene
          derecho a ver desglosado, y lo que distingue un consumo gravado. */}
      <div className="ticket-linea">
        <span>INC {cuenta.porcentajeInc}%</span>
        <span>{formatoCOP(cuenta.inc)}</span>
      </div>

      {cuenta.cargosAdicionales > 0 && (
        <div className="ticket-linea">
          <span>Otros cargos</span>
          <span>{formatoCOP(cuenta.cargosAdicionales)}</span>
        </div>
      )}

      {/* El envío va después del impuesto porque no causa INC. */}
      {cuenta.costoEnvio > 0 && (
        <div className="ticket-linea">
          <span>Domicilio</span>
          <span>{formatoCOP(cuenta.costoEnvio)}</span>
        </div>
      )}

      {cuenta.propina > 0 && (
        <div className="ticket-linea">
          <span>Propina voluntaria</span>
          <span>{formatoCOP(cuenta.propina)}</span>
        </div>
      )}

      <hr className="ticket-separador" />

      <div className="ticket-linea ticket-grande">
        <span>TOTAL</span>
        <span>{formatoCOP(cuenta.total)}</span>
      </div>

      {/* ---------- Parte de una cuenta dividida ---------- */}
      {parte && (
        <>
          <hr className="ticket-separador" />
          <div className="ticket-centro ticket-negrita">PARTE: {parte.nombre.toUpperCase()}</div>
          <div className="ticket-linea ticket-grande">
            <span>A PAGAR</span>
            <span>{formatoCOP(parte.valor)}</span>
          </div>
          <div className="ticket-linea">
            <span>Forma de pago</span>
            <span>{NOMBRE_METODO[parte.metodo]}</span>
          </div>
        </>
      )}

      {!parte && metodo && (
        <div className="ticket-linea">
          <span>Forma de pago</span>
          <span>{NOMBRE_METODO[metodo]}</span>
        </div>
      )}

      {/* ---------- Pie legal ---------- */}
      <div className="ticket-pie">
        {cuenta.propina > 0 && (
          <p>La propina es voluntaria y fue autorizada por el cliente.</p>
        )}
        {cuenta.propina === 0 && <p>La propina es voluntaria y no está incluida.</p>}

        <p>{DATOS_FISCALES.regimen}</p>
        <p>{DATOS_FISCALES.responsabilidad}</p>

        {/* Espacio previsto para cuando el restaurante quede habilitado ante la
            DIAN. Mientras `resolucion` esté vacío no se imprime nada: un número
            inventado en un documento fiscal es una falsedad. */}
        {DATOS_FISCALES.resolucion && (
          <p className="ticket-resolucion">
            {DATOS_FISCALES.resolucion}
            {DATOS_FISCALES.prefijo && ` · Prefijo ${DATOS_FISCALES.prefijo}`}
          </p>
        )}

        <p className="ticket-negrita">{DATOS_FISCALES.leyenda}</p>

        {esPrecuenta && <p className="ticket-negrita">Este documento no constituye pago.</p>}

        <p>¡Gracias por visitarnos!</p>
      </div>

      <div className="ticket-avance" />
    </div>
  )
}
