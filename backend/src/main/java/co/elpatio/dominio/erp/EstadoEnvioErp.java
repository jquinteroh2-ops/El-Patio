package co.elpatio.dominio.erp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * En que va el viaje de una venta hacia el ERP.
 *
 * Es el estado del ENVIO, no el de la venta. La venta ya ocurrio y ya se cobro
 * el momento en que este registro nace: nada de lo que pase aqui la deshace ni
 * la pone en duda. Esa separacion es deliberada y es lo que permite que el
 * restaurante siga cobrando con Globalsoft caido.
 */
public enum EstadoEnvioErp {
  /** Escrita en la cola, todavia no ha salido. */
  PENDIENTE_ENVIO_ERP,
  /** Entregada al adaptador, esperando que el ERP confirme. */
  ENVIADA_ERP,
  /** El ERP la acepto y devolvio el numero de su documento. Fin del camino. */
  FACTURADA_ERP,
  /**
   * Se agotaron los reintentos.
   *
   * No significa que la venta este mal: significa que hay que mirarla. La
   * pantalla de conciliacion existe para eso, y desde ahi se reintenta.
   */
  ERROR_ERP;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoEnvioErp de(String valor) { return valueOf(valor.toUpperCase()); }

  /** Si ya no hay nada automatico que hacer con este envio. */
  public boolean esFinal() {
    return this == FACTURADA_ERP || this == ERROR_ERP;
  }
}
