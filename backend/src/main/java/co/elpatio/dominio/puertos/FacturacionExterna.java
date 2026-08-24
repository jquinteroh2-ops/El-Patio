package co.elpatio.dominio.puertos;

import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.erp.VentaParaErp;

/**
 * Quien emite el documento fiscal de una venta.
 *
 * El Patio no factura. Este puerto es la puerta por donde la venta sale hacia
 * el sistema que si lo hace —hoy Globalsoft— y el dominio no sabe nada mas: no
 * sabe si detras hay una API, una carpeta con archivos planos o una persona
 * digitando. Esa ignorancia es el punto. Cuando se averigue que expone de
 * verdad Globalsoft, se escribe un adaptador y no se toca ni una linea de aqui
 * hacia adentro.
 *
 * Es el mismo trato que ya tienen PasarelaDePagos y AlmacenDeImagenes.
 *
 * <p><b>Contrato que toda implementacion debe cumplir:</b>
 *
 * <ol>
 *   <li><b>Idempotencia.</b> Dos llamadas con la misma llave de idempotencia no
 *       pueden producir dos documentos. La segunda devuelve el de la primera.
 *       Sin esto, un tiempo de espera agotado le cobra dos veces al mismo
 *       cliente ante la DIAN.
 *   <li><b>No lanzar por fallos esperables.</b> Que el ERP no responda, rechace
 *       o este apagado son desenlaces, no excepciones: se devuelven como
 *       {@link ResultadoFacturacion}. Una excepcion aqui significa que el
 *       adaptador esta roto, no que el ERP dijo que no.
 *   <li><b>No bloquear indefinidamente.</b> Quien llame a esto lo hace desde un
 *       hilo de fondo, pero un adaptador sin tiempo limite termina agotando el
 *       pool igual.
 * </ol>
 */
public interface FacturacionExterna {

  /** Manda la venta y dice como quedo. Nunca lanza por un fallo del ERP. */
  ResultadoFacturacion emitirDocumento(VentaParaErp venta);

  /** Como se llama este adaptador en la bitacora: rest, archivo o manual. */
  String nombre();
}
