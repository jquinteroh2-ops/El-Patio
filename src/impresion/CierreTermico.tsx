import { DATOS_FISCALES, RESTAURANTE } from '@/compartido/config'
import { formatoCOP, formatoFecha, formatoFechaHora } from '@/compartido/formato'
import type { ResumenTurno } from '@/compartido/mockApi'
import './termica.css'

/**
 * El corte del turno.
 *
 * Es el papel con el que se entrega la caja, así que lleva las dos lecturas que
 * hacen falta para cuadrarla: por medio de pago, que es contra lo que se cuenta
 * el efectivo del cajón, y por canal, que es lo que responde de dónde vino la
 * venta. Sin la segunda, el domicilio queda mezclado con el salón y nadie sabe
 * cuánto pesa.
 */

interface Props {
  resumen: ResumenTurno
  cerradoPor: string
}

export function CierreTermico({ resumen, cerradoPor }: Props) {
  // Lo cobrado por envíos no es venta de cocina: es un costo que se le traslada
  // al cliente. Se muestra aparte para que no infle el rendimiento del canal.
  const ventaDomicilioSinEnvios = resumen.totalDomicilio - resumen.totalEnvios

  return (
    <div className="ticket">
      <div className="ticket-centro">
        <p className="ticket-grande">{RESTAURANTE.nombre}</p>
        <p>NIT {DATOS_FISCALES.nitCompleto}</p>
      </div>

      <hr className="ticket-separador" />

      <div className="ticket-centro ticket-negrita">CIERRE DE CAJA</div>
      <div className="ticket-centro">
        {resumen.turno === 'almuerzo' ? 'Turno de almuerzo' : 'Turno de cena'}
      </div>
      <div className="ticket-centro">{formatoFecha(resumen.fecha)}</div>

      <hr className="ticket-separador" />

      <div className="ticket-linea">
        <span>Cerró</span>
        <span>{cerradoPor}</span>
      </div>
      <div className="ticket-linea">
        <span>Impreso</span>
        <span>{formatoFechaHora(new Date())}</span>
      </div>

      <hr className="ticket-separador" />

      {/* ---------- Por medio de pago ---------- */}
      <div className="ticket-negrita">POR MEDIO DE PAGO</div>
      <div className="ticket-linea">
        <span>Efectivo</span>
        <span>{formatoCOP(resumen.totalEfectivo)}</span>
      </div>
      <div className="ticket-linea">
        <span>Tarjeta</span>
        <span>{formatoCOP(resumen.totalTarjeta)}</span>
      </div>
      <div className="ticket-linea">
        <span>Transferencia</span>
        <span>{formatoCOP(resumen.totalTransferencia)}</span>
      </div>

      <hr className="ticket-separador" />

      {/* ---------- Por canal ---------- */}
      <div className="ticket-negrita">POR CANAL</div>
      <div className="ticket-linea">
        <span>Salón</span>
        <span>{formatoCOP(resumen.totalSalon)}</span>
      </div>
      <div className="ticket-linea">
        <span>Domicilio</span>
        <span>{formatoCOP(resumen.totalDomicilio)}</span>
      </div>
      <div className="ticket-detalle">
        <div className="ticket-linea">
          <span>de eso, comida</span>
          <span>{formatoCOP(ventaDomicilioSinEnvios)}</span>
        </div>
        <div className="ticket-linea">
          <span>de eso, envíos</span>
          <span>{formatoCOP(resumen.totalEnvios)}</span>
        </div>
      </div>
      <div className="ticket-linea">
        <span>Para llevar</span>
        <span>{formatoCOP(resumen.totalLlevar)}</span>
      </div>

      <hr className="ticket-separador" />

      {/* ---------- Totales ---------- */}
      <div className="ticket-linea">
        <span>Cuentas cobradas</span>
        <span>{resumen.ordenesAtendidas}</span>
      </div>
      <div className="ticket-linea">
        <span>Ticket promedio</span>
        <span>{formatoCOP(resumen.ticketPromedio)}</span>
      </div>
      <div className="ticket-linea">
        <span>INC recaudado</span>
        <span>{formatoCOP(resumen.incTotal)}</span>
      </div>
      <div className="ticket-linea">
        <span>Propinas</span>
        <span>{formatoCOP(resumen.propinasTotales)}</span>
      </div>

      <hr className="ticket-separador" />

      <div className="ticket-linea ticket-grande">
        <span>VENTA TOTAL</span>
        <span>{formatoCOP(resumen.ventaTotal)}</span>
      </div>

      <hr className="ticket-separador" />

      {/* ---------- Cuadre a mano ---------- */}
      <div className="ticket-negrita">CONTEO DEL CAJÓN</div>
      <p>Efectivo contado: _______________</p>
      <p>Diferencia: _______________</p>
      <div style={{ height: '8mm' }} />
      <p className="ticket-centro">___________________________</p>
      <p className="ticket-centro">Firma de quien entrega</p>

      <div className="ticket-pie">
        <p className="ticket-negrita">{DATOS_FISCALES.leyenda}</p>
        <p>Documento interno de control. No es un soporte tributario.</p>
      </div>

      <div className="ticket-avance" />
    </div>
  )
}
