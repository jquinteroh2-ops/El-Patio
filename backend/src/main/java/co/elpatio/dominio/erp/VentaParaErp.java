package co.elpatio.dominio.erp;

import java.time.Instant;
import java.util.List;

/**
 * Una venta cerrada, en el vocabulario minimo que un ERP necesita.
 *
 * Es la frontera del dominio hacia afuera. A la izquierda queda el lenguaje del
 * restaurante —comandas, modificadores, turnos de envio, mesas—; a la derecha,
 * el de la contabilidad —lineas, bases, impuesto—. La traduccion ocurre en un
 * solo sitio y es lo unico que hay que revisar si el ERP cambia.
 *
 * Este objeto NO conoce a Globalsoft. No lleva codigos de Globalsoft, ni sus
 * nombres de campo, ni su formato de fecha. Traducir esto a lo que Globalsoft
 * entienda es trabajo del mapeador de cada adaptador, y por eso cambiar de ERP
 * no toca el dominio.
 */
public record VentaParaErp(
    /**
     * La llave que hace el envio idempotente.
     *
     * Nace con la venta y no cambia entre reintentos. Es lo unico que impide
     * que un timeout —el ERP recibio, proceso y la respuesta se perdio en el
     * camino— termine en dos documentos fiscales para una sola comida.
     */
    String idempotencyKey,
    String pagoId,
    String ordenId,
    /** El consecutivo diario de la comanda, que es como el restaurante la llama. */
    int numeroComanda,
    Instant fechaHora,
    /** salon, domicilio o llevar. */
    String tipoPedido,
    /** El canal por donde entro: mostrador, whatsapp, web. */
    String canal,
    List<LineaVenta> lineas,
    /** Alimentos y bebidas antes de impuesto y de propina. Base del INC. */
    long subtotal,
    long inc,
    int porcentajeInc,
    /** Cargos adicionales y domicilio: no causan INC. */
    long cargosAdicionales,
    long costoEnvio,
    /**
     * La propina va aparte y nunca dentro del total gravable.
     *
     * Es voluntaria, es del personal y no es ingreso del restaurante. Meterla
     * en la base del impuesto seria cobrarle al cliente un impuesto sobre algo
     * que el restaurante no se queda. Se manda porque el ERP la necesita para
     * cuadrar el dinero que entro, no para declararla como venta.
     */
    long propina,
    long total,
    String metodoPago,
    List<ParteDelPago> divisiones,
    String recibidoPor) {

  /** Una linea de la venta, ya sin modificadores ni estados intermedios. */
  public record LineaVenta(
      /**
       * El identificador del producto EN EL PATIO.
       *
       * No es el codigo de Globalsoft. La equivalencia entre los dos catalogos
       * la resuelve el mapeador del adaptador, con una tabla que el restaurante
       * tiene que entregar. Sin esa tabla no hay integracion real posible, y es
       * la razon por la que el adaptador manual existe.
       */
      String productoId,
      String nombre,
      int cantidad,
      long precioUnitario,
      long totalLinea) {}

  /** Un pedazo de un pago mixto. */
  public record ParteDelPago(String metodo, long valor) {}
}
