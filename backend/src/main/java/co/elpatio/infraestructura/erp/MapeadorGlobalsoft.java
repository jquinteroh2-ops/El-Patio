package co.elpatio.infraestructura.erp;

import co.elpatio.dominio.erp.VentaParaErp;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Traduce una venta de El Patio al vocabulario de Globalsoft.
 *
 * Es la capa anticorrupcion, y esta sola en su propia clase por una razon: es
 * lo unico que hay que reescribir cuando se sepa de verdad que espera
 * Globalsoft. Ni el dominio ni el worker ni la pantalla de conciliacion se
 * enteran de que cambio.
 *
 * <p><b>Lo que aqui todavia es una suposicion.</b> Nadie ha visto la
 * documentacion de integracion de Globalsoft. El formato que sale de aqui es un
 * plano de importacion convencional —una fila por linea de venta, con la
 * cabecera repetida— porque es lo que casi cualquier ERP colombiano acepta
 * importar. Puede que Globalsoft quiera otra cosa. Cuando se sepa, se cambia
 * este archivo y nada mas.
 *
 * <p><b>La equivalencia de productos no esta resuelta.</b> Se escribe el
 * identificador de El Patio en la columna del codigo. Globalsoft tiene su
 * propio catalogo con sus propios codigos, y hasta que el restaurante entregue
 * la tabla de equivalencias, ningun archivo generado aqui va a importar sin
 * intervencion. Esta escrito asi a proposito, en vez de inventar un codigo que
 * parezca valido: un archivo que falla al importar se arregla, uno que importa
 * contra el producto equivocado se descubre en el inventario de fin de mes.
 */
public final class MapeadorGlobalsoft {

  private MapeadorGlobalsoft() {}

  private static final ZoneId ZONA = ZoneId.of("America/Bogota");
  private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

  /** El encabezado del plano. Va una sola vez por archivo. */
  public static final String ENCABEZADO =
      String.join(
          ";",
          "llave_idempotencia",
          "comanda",
          "fecha",
          "hora",
          "tipo_pedido",
          "canal",
          "codigo_producto",
          "descripcion",
          "cantidad",
          "valor_unitario",
          "valor_linea",
          "subtotal_venta",
          "inc_venta",
          "porcentaje_inc",
          "cargos_venta",
          "envio_venta",
          "propina_venta",
          "total_venta",
          "medio_pago",
          "cajero");

  /**
   * Las filas de una venta: una por linea de consumo.
   *
   * Los totales de la venta se repiten en cada fila. Es redundante y es a
   * proposito: un importador que procese fila por fila necesita el total en la
   * fila, y quien abra el archivo en Excel para revisarlo tambien.
   */
  public static String aPlano(VentaParaErp venta) {
    var fechaHora = venta.fechaHora().atZone(ZONA);
    String fecha = FECHA.format(fechaHora);
    String hora = HORA.format(fechaHora);

    StringBuilder salida = new StringBuilder();
    for (VentaParaErp.LineaVenta linea : venta.lineas()) {
      salida
          .append(campo(venta.idempotencyKey())).append(';')
          .append(venta.numeroComanda()).append(';')
          .append(fecha).append(';')
          .append(hora).append(';')
          .append(campo(venta.tipoPedido())).append(';')
          .append(campo(venta.canal())).append(';')
          .append(campo(linea.productoId())).append(';')
          .append(campo(linea.nombre())).append(';')
          .append(linea.cantidad()).append(';')
          .append(linea.precioUnitario()).append(';')
          .append(linea.totalLinea()).append(';')
          .append(venta.subtotal()).append(';')
          .append(venta.inc()).append(';')
          .append(venta.porcentajeInc()).append(';')
          .append(venta.cargosAdicionales()).append(';')
          .append(venta.costoEnvio()).append(';')
          .append(venta.propina()).append(';')
          .append(venta.total()).append(';')
          .append(campo(venta.metodoPago())).append(';')
          .append(campo(venta.recibidoPor()))
          .append('\n');
    }
    return salida.toString();
  }

  /**
   * Limpia un valor de texto para que no rompa el plano.
   *
   * El separador y los saltos de linea se sustituyen en vez de escaparse con
   * comillas. Es menos elegante y es lo correcto aqui: los importadores de ERP
   * tradicionales suelen no entender comillas de escape, y un plato que se
   * llame «Mar y tierra; del Patio» partiria la fila en dos y correria todas
   * las columnas siguientes. Un espacio en el nombre no le hace dano a nadie.
   */
  private static String campo(String valor) {
    if (valor == null) return "";
    return valor.replace(';', ' ').replace('\n', ' ').replace('\r', ' ').trim();
  }

  /** El nombre del archivo de una venta. Lleva la llave para no pisarse nunca. */
  public static String nombreArchivo(VentaParaErp venta) {
    var fechaHora = venta.fechaHora().atZone(ZONA);
    return String.format(
        Locale.ROOT,
        "venta_%s_comanda-%d_%s.csv",
        FECHA.format(fechaHora),
        venta.numeroComanda(),
        venta.idempotencyKey());
  }
}
