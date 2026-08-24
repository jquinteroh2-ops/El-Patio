package co.elpatio.dominio.pqr;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Por donde va una solicitud desde que se radica. */
public enum EstadoPqr {
  RADICADA,
  EN_TRAMITE,
  RESUELTA,
  CERRADA;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoPqr de(String valor) { return valueOf(valor.toUpperCase()); }

  /**
   * Si el reloj del termino sigue corriendo.
   *
   * Una vez resuelta, la fecha limite deja de importar: el restaurante ya
   * respondio. Seguir contandole el vencimiento la dejaria en rojo para
   * siempre y esconderia las que de verdad estan por vencer.
   */
  public boolean sigueCorriendo() {
    return this == RADICADA || this == EN_TRAMITE;
  }
}
