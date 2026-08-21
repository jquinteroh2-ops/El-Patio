import { PROVEEDOR_TECNOLOGICO, RESTAURANTE } from '@/compartido/config'
import { formatoCOP, formatoTelefono } from '@/compartido/formato'
import {
  DENOMINACION,
  ETIQUETA_IDENTIFICACION,
  NOMBRE_CODIGO_UNICO,
  NOMBRE_TRIBUTO,
  RESPONSABILIDAD_FISCAL,
} from '@/facturacion/catalogos'
import { fechaDian, horaDian } from '@/facturacion/cufe'
import type { DocumentoElectronico } from '@/facturacion/tipos'
import { CodigoQr } from './CodigoQr'
import './termica.css'

/**
 * La representación gráfica del documento electrónico, en 80 mm.
 *
 * Esta es la diferencia con `ComprobanteTermico`, y no es de diseño: aquel es
 * un comprobante interno que el restaurante imprime para su propio orden; este
 * es el retrato de un documento que existe ante la DIAN. Por eso lleva
 * denominación, numeración autorizada, código único y QR, y por eso el papel
 * ya no ES el documento: el documento es el XML que se transmitió, y esto es
 * la manera de que el cliente lo pueda ver y verificar.
 *
 * EL ORDEN DE LOS BLOQUES NO ES LIBRE. La norma enumera lo que tiene que
 * aparecer y un documento al que le falte un elemento es un documento mal
 * expedido, aunque el XML esté perfecto. Antes de mover algo de aquí, revise
 * el Anexo Técnico.
 *
 * Mientras `documento.esPrueba` sea verdadero, arriba y abajo sale una banda
 * que dice que no tiene valor fiscal. Esa banda es lo único que separa una
 * prueba de una factura, así que no se quita «para ver cómo queda».
 */

interface Props {
  documento: DocumentoElectronico
  /** «Mesa 7», «Domicilio #18». No lo exige la DIAN; lo necesita la caja. */
  etiqueta?: string
  atendidoPor?: string
  /** Cómo se pagó, en palabras. El código va en el XML, no en el papel. */
  medioPagoLegible?: string
}

/** El código único es una tira de 96 caracteres: partido se puede transcribir. */
function partirCodigo(codigo: string, cada = 32): string[] {
  const partes: string[] = []
  for (let i = 0; i < codigo.length; i += cada) partes.push(codigo.slice(i, i + cada))
  return partes
}

export function FacturaTermica({
  documento: doc,
  etiqueta,
  atendidoPor,
  medioPagoLegible,
}: Props) {
  const identificacion = ETIQUETA_IDENTIFICACION[doc.adquiriente.tipoIdentificacion]
  const nitEmisor = `${doc.emisor.nit}-${doc.emisor.digitoVerificacion}`
  const emitidoEn = new Date(doc.emitidoEn)

  return (
    <div className="ticket">
      {doc.esPrueba && <BandaDePrueba />}

      {/* ---------- 1. Quién vende ---------- */}
      <div className="ticket-centro">
        <p className="ticket-grande">{RESTAURANTE.nombre}</p>
        <p className="ticket-negrita">{doc.emisor.razonSocial}</p>
        <p>NIT {nitEmisor}</p>
        <p>
          {doc.emisor.direccion}
          <br />
          {doc.emisor.municipio}, {doc.emisor.departamento}
        </p>
        <p>{formatoTelefono(RESTAURANTE.telefono)}</p>
        <p className="ticket-fiscal">{doc.emisor.regimen}</p>
        {doc.emisor.responsabilidades.length > 0 && (
          <p className="ticket-fiscal">
            {doc.emisor.responsabilidades
              .map((codigo) => `${codigo} ${RESPONSABILIDAD_FISCAL[codigo] ?? ''}`.trim())
              .join(' · ')}
          </p>
        )}
      </div>

      <hr className="ticket-separador" />

      {/* ---------- 2. Qué documento es ----------
          La denominación es obligatoria y tiene que ser la exacta: no vale
          «Factura» a secas ni un título comercial. */}
      <div className="ticket-centro">
        <p className="ticket-negrita">{DENOMINACION[doc.tipo].toUpperCase()}</p>
        <p className="ticket-grande">N.º {doc.numeroCompleto}</p>
      </div>

      <hr className="ticket-separador" />

      {/* La fecha y la hora salen del mismo cálculo que entró al código único,
          en hora de Colombia y no en la del equipo. Si el reloj de la caja está
          mal puesto, el papel tiene que seguir diciendo lo que se transmitió:
          una fecha impresa que no coincide con la del código único es un
          documento que no se puede conciliar. Van en dos renglones porque
          juntos no caben en 72 mm y partidos no se leen. */}
      <div className="ticket-linea">
        <span>Fecha de emisión</span>
        <span>{fechaDian(emitidoEn)}</span>
      </div>
      <div className="ticket-linea">
        <span>Hora</span>
        <span>{horaDian(emitidoEn)}</span>
      </div>
      <div className="ticket-linea">
        <span>Moneda</span>
        <span>{doc.moneda}</span>
      </div>
      <div className="ticket-linea">
        <span>Forma de pago</span>
        <span>{doc.formaPago === '1' ? 'Contado' : 'Crédito'}</span>
      </div>
      {medioPagoLegible && (
        <div className="ticket-linea">
          <span>Medio de pago</span>
          <span>{medioPagoLegible}</span>
        </div>
      )}
      {etiqueta && (
        <div className="ticket-linea">
          <span>Origen</span>
          <span>{etiqueta}</span>
        </div>
      )}
      {atendidoPor && (
        <div className="ticket-linea">
          <span>Atendió</span>
          <span>{atendidoPor}</span>
        </div>
      )}

      {/* Una nota crédito no se explica sola: tiene que decir a qué documento
          corrige, o no hay forma de saber qué se está anulando. */}
      {doc.documentoReferenciado && (
        <>
          <div className="ticket-linea">
            <span>Corrige a</span>
            <span>{doc.documentoReferenciado}</span>
          </div>
          {doc.motivo && <div className="ticket-detalle">Motivo: {doc.motivo}</div>}
        </>
      )}

      <hr className="ticket-separador" />

      {/* ---------- 3. Quién compra ---------- */}
      <div className="ticket-negrita">ADQUIRIENTE</div>
      <div className="ticket-linea">
        <span>{doc.adquiriente.nombre}</span>
      </div>
      <div className="ticket-linea">
        <span>{identificacion}</span>
        <span>
          {doc.adquiriente.numeroIdentificacion}
          {doc.adquiriente.digitoVerificacion && `-${doc.adquiriente.digitoVerificacion}`}
        </span>
      </div>
      {doc.adquiriente.direccion && (
        <div className="ticket-detalle">{doc.adquiriente.direccion}</div>
      )}
      {doc.adquiriente.correo && <div className="ticket-detalle">{doc.adquiriente.correo}</div>}

      <hr className="ticket-separador" />

      {/* ---------- 4. Qué se vendió ----------
          Cada línea lleva código, cantidad, unidad de medida, valor unitario y
          su impuesto. La DIAN exige «descripción específica»: no vale
          «consumo» ni «varios». */}
      {doc.lineas.map((linea) => (
        <div key={linea.numero} className="ticket-item">
          <div className="ticket-linea">
            <span className="ticket-negrita">{linea.descripcion}</span>
          </div>
          <div className="ticket-linea">
            <span>
              {linea.cantidad} × {formatoCOP(linea.valorUnitario)}
            </span>
            <span>{formatoCOP(linea.valorBruto)}</span>
          </div>
          {linea.detalles.map((detalle, i) => (
            <div key={`${linea.numero}-${i}`} className="ticket-detalle">
              + {detalle}
            </div>
          ))}
          <div className="ticket-detalle ticket-fiscal">
            Cód. {linea.codigo} · Un. {linea.unidadMedida}
          </div>
          {linea.tributos.map((tributo) => (
            <div key={`${linea.numero}-${tributo.codigo}`} className="ticket-linea ticket-detalle">
              <span>
                {NOMBRE_TRIBUTO[tributo.codigo] ?? tributo.codigo} {tributo.tarifa}%
              </span>
              <span>{formatoCOP(tributo.valor)}</span>
            </div>
          ))}
          {!linea.gravada && <div className="ticket-detalle ticket-fiscal">No gravado</div>}
        </div>
      ))}

      <hr className="ticket-separador" />

      {/* ---------- 5. Los totales ----------
          Base gravable e impuesto van separados y con su tarifa: es lo que
          permite que el documento se pueda auditar sin recalcularlo. */}
      <div className="ticket-linea">
        <span>Subtotal</span>
        <span>{formatoCOP(doc.subtotal)}</span>
      </div>
      <div className="ticket-linea">
        <span>Base gravable</span>
        <span>{formatoCOP(doc.baseGravable)}</span>
      </div>
      {doc.subtotal - doc.baseGravable > 0 && (
        <div className="ticket-linea">
          <span>Base no gravada</span>
          <span>{formatoCOP(doc.subtotal - doc.baseGravable)}</span>
        </div>
      )}
      {doc.tributos.map((tributo) => (
        <div key={`total-${tributo.codigo}-${tributo.tarifa}`} className="ticket-linea">
          <span>
            {NOMBRE_TRIBUTO[tributo.codigo] ?? tributo.codigo} {tributo.tarifa}%
          </span>
          <span>{formatoCOP(tributo.valor)}</span>
        </div>
      ))}

      {/* La propina va después del impuesto y con su nombre completo: no es
          venta del restaurante y no puede parecer un cargo obligatorio. */}
      {doc.propina > 0 && (
        <div className="ticket-linea">
          <span>Propina voluntaria</span>
          <span>{formatoCOP(doc.propina)}</span>
        </div>
      )}

      <hr className="ticket-separador" />

      <div className="ticket-linea ticket-grande">
        <span>TOTAL</span>
        <span>{formatoCOP(doc.total)}</span>
      </div>

      <hr className="ticket-separador" />

      {/* ---------- 6. Lo que hace verificable el documento ---------- */}
      <div className="ticket-centro ticket-fiscal">
        <p className="ticket-negrita">{NOMBRE_CODIGO_UNICO[doc.tipo]}</p>
        <div className="ticket-codigo">
          {partirCodigo(doc.codigoUnico).map((parte, i) => (
            <span key={i}>{parte}</span>
          ))}
        </div>
      </div>

      <div className="ticket-centro">
        <CodigoQr contenido={doc.contenidoQr} />
        <p className="ticket-fiscal">
          Escanee el código para consultar este documento
          <br />
          en el portal de la DIAN
        </p>
      </div>

      {/* ---------- 7. Con qué autorización se numeró ---------- */}
      <hr className="ticket-separador" />

      <div className="ticket-centro ticket-fiscal">
        {doc.numeracion.resolucion ? (
          <>
            <p>
              Resolución DIAN {doc.numeracion.resolucion} de {doc.numeracion.fechaResolucion}
            </p>
            <p>
              Autoriza {doc.numeracion.prefijo}
              {doc.numeracion.desde} al {doc.numeracion.prefijo}
              {doc.numeracion.hasta} · Vigente hasta {doc.numeracion.vigenteHasta}
            </p>
          </>
        ) : (
          /* Sin resolución no se imprime una inventada: se dice que no la hay.
             Es la diferencia entre un documento de prueba y uno falso. */
          <p className="ticket-negrita">SIN RESOLUCIÓN DE NUMERACIÓN DE LA DIAN</p>
        )}

        {PROVEEDOR_TECNOLOGICO.nombre && (
          <p>
            Proveedor tecnológico: {PROVEEDOR_TECNOLOGICO.nombre}
            {PROVEEDOR_TECNOLOGICO.nit && ` · NIT ${PROVEEDOR_TECNOLOGICO.nit}`}
          </p>
        )}
        {doc.ambiente === 'pruebas' && <p>Ambiente de habilitación</p>}
      </div>

      {/* ---------- 8. Pie ---------- */}
      <div className="ticket-pie">
        {doc.propina > 0 ? (
          <p>La propina es voluntaria y fue autorizada por el cliente.</p>
        ) : (
          <p>La propina es voluntaria y no está incluida.</p>
        )}
        <p>¡Gracias por visitarnos!</p>
      </div>

      {doc.esPrueba && <BandaDePrueba />}

      <div className="ticket-avance" />
    </div>
  )
}

/**
 * La banda que declara que el papel no vale.
 *
 * Va arriba y abajo porque un tiquete se lee por cualquiera de los dos
 * extremos, y porque alguien puede cortar el rollo por la mitad. Es un recuadro
 * de borde grueso y no un fondo negro: una térmica quema el papel, y un bloque
 * relleno saldría como un borrón que tapa justamente lo que hay que leer.
 */
function BandaDePrueba() {
  return (
    <div className="ticket-banda">
      <p className="ticket-negrita">DOCUMENTO DE PRUEBA</p>
      <p>SIN VALOR FISCAL · NO ES UNA FACTURA</p>
      <p>No transmitido a la DIAN</p>
    </div>
  )
}
