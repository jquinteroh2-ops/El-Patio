package co.elpatio.dominio.reserva;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EstadoReserva {
  SOLICITADA,
  CONFIRMADA,
  CANCELADA,
  CUMPLIDA,
  NO_ASISTIO;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoReserva de(String valor) { return valueOf(valor.toUpperCase()); }

  /** Estados en los que la mesa separada vuelve a quedar disponible. */
  public boolean liberaMesa() {
    return this == CANCELADA || this == CUMPLIDA || this == NO_ASISTIO;
  }
}
