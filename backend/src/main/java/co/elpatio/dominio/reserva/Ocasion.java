package co.elpatio.dominio.reserva;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Ocasion {
  CUMPLEANOS,
  ANIVERSARIO,
  NEGOCIOS,
  NINGUNA;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static Ocasion de(String valor) { return valueOf(valor.toUpperCase()); }
}
