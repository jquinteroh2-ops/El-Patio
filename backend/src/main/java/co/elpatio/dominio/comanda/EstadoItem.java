package co.elpatio.dominio.comanda;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EstadoItem {
  PENDIENTE,
  EN_PREPARACION,
  LISTO,
  SERVIDO,
  ANULADO;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoItem de(String valor) { return valueOf(valor.toUpperCase()); }
}
