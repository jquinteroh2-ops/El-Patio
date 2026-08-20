import { formatoHora } from '@/compartido/formato'
import type { Destino, ItemOrden } from '@/compartido/tipos'
import './termica.css'

/**
 * La comanda de cocina o de barra.
 *
 * No lleva precios. A quien está en la plancha no le sirve saber cuánto cuesta
 * el plato: le sirve saber qué es, cuántos son y qué le cambiaron. Por eso los
 * modificadores y las notas van en cuerpo grande, que es lo que se lee de reojo
 * con las manos ocupadas y a un metro del papel.
 */

interface Props {
  /** «Mesa 7», «Domicilio #18». */
  etiqueta: string
  numero: number
  turno: number
  destino: Destino
  atendidoPor: string
  items: ItemOrden[]
  notas?: string
  /** Para que cocina sepa que sale del local y hay que empacarlo. */
  paraLlevar?: boolean
}

export function ComandaTermica({
  etiqueta,
  numero,
  turno,
  destino,
  atendidoPor,
  items,
  notas,
  paraLlevar = false,
}: Props) {
  const vigentes = items.filter((i) => i.estado !== 'anulado')

  return (
    <div className="ticket">
      <div className="ticket-centro ticket-enorme">
        {destino === 'cocina' ? 'COCINA' : 'BARRA'}
      </div>

      <hr className="ticket-separador" />

      <div className="ticket-centro ticket-enorme">{etiqueta}</div>

      {paraLlevar && (
        <div className="ticket-centro ticket-negrita">*** EMPACAR PARA LLEVAR ***</div>
      )}

      <hr className="ticket-separador" />

      <div className="ticket-linea">
        <span>Comanda</span>
        <span className="ticket-negrita">N.º {numero}</span>
      </div>
      <div className="ticket-linea">
        <span>Turno</span>
        <span>{turno}</span>
      </div>
      <div className="ticket-linea">
        <span>Hora</span>
        <span>{formatoHora(new Date())}</span>
      </div>
      <div className="ticket-linea">
        <span>Tomó</span>
        <span>{atendidoPor}</span>
      </div>

      <hr className="ticket-separador" />

      {vigentes.map((item) => (
        <div key={item.id} className="ticket-item">
          <div className="ticket-enorme">
            {item.cantidad} × {item.nombre}
          </div>
          {item.modificadoresSeleccionados.map((m, i) => (
            <div key={`${item.id}-${i}`} className="ticket-detalle ticket-negrita">
              &gt; {m.nombre}: {m.valor}
            </div>
          ))}
          {item.notaCocina && (
            <div className="ticket-detalle ticket-negrita">** {item.notaCocina} **</div>
          )}
        </div>
      ))}

      {notas && (
        <>
          <hr className="ticket-separador" />
          <div className="ticket-negrita">NOTA DE LA MESA:</div>
          <div>{notas}</div>
        </>
      )}

      <div className="ticket-avance" />
    </div>
  )
}
