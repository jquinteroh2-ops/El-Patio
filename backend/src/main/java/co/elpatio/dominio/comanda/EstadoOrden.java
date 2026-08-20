package co.elpatio.dominio.comanda;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EstadoOrden {
  ABIERTA,
  ENVIADA,
  EN_PREPARACION,
  SERVIDA,
  CUENTA_PEDIDA,
  PAGADA,
  ANULADA;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoOrden de(String valor) { return valueOf(valor.toUpperCase()); }

  /** Una comanda cerrada ya no admite cambios de ningun tipo. */
  public boolean esCerrada() {
    return this == PAGADA || this == ANULADA;
  }
}
