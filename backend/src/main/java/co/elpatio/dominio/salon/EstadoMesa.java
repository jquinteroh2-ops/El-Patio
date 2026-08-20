package co.elpatio.dominio.salon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EstadoMesa {
  LIBRE,
  OCUPADA,
  CUENTA_PEDIDA,
  RESERVADA;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoMesa de(String valor) { return valueOf(valor.toUpperCase()); }
}
