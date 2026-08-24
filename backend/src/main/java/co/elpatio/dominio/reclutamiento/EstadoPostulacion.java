package co.elpatio.dominio.reclutamiento;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Por donde va una hoja de vida desde que llega. */
public enum EstadoPostulacion {
  RECIBIDA,
  EN_REVISION,
  CONTACTADO,
  SELECCIONADO,
  DESCARTADO;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoPostulacion de(String valor) { return valueOf(valor.toUpperCase()); }

  /** Si todavia esta en juego. Es el filtro por defecto de la bandeja. */
  public boolean estaAbierta() {
    return this != SELECCIONADO && this != DESCARTADO;
  }
}
