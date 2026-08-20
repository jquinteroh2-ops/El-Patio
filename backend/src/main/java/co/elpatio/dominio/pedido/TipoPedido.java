package co.elpatio.dominio.pedido;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Por donde entro la venta.
 *
 * Un domicilio no es una entidad distinta de una comanda: es una comanda con
 * otro canal. Asi cocina lo ve igual que el de una mesa y la caja lo suma sin
 * logica aparte.
 */
public enum TipoPedido {
  MESA,
  DOMICILIO,
  LLEVAR;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static TipoPedido de(String valor) { return valueOf(valor.toUpperCase()); }

  /** Los que entran desde fuera del salon y pasan por recepcion. */
  public boolean esExterno() {
    return this != MESA;
  }
}
