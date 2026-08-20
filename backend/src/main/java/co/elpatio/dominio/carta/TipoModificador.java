package co.elpatio.dominio.carta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoModificador {
  SELECCION_UNICA,
  SELECCION_MULTIPLE,
  TEXTO_LIBRE;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static TipoModificador de(String valor) { return valueOf(valor.toUpperCase()); }
}
