package co.elpatio.dominio.caja;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.LocalTime;

public enum Turno {
  ALMUERZO,
  CENA;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static Turno de(String valor) { return valueOf(valor.toUpperCase()); }

  /** Turno operativo segun la hora: antes de las 5 p. m. es almuerzo. */
  public static Turno enHora(LocalTime hora) {
    return hora.getHour() < 17 ? ALMUERZO : CENA;
  }
}
