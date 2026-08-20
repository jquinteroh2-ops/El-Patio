package co.elpatio.dominio.cobro;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MetodoPago {
  EFECTIVO,
  TARJETA,
  TRANSFERENCIA,
  MIXTO;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static MetodoPago de(String valor) { return valueOf(valor.toUpperCase()); }
}
