package co.elpatio.dominio.carta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** A donde se imprime la comanda: cocina o bar. */
public enum Destino {
  COCINA,
  BAR;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static Destino de(String valor) { return valueOf(valor.toUpperCase()); }
}
