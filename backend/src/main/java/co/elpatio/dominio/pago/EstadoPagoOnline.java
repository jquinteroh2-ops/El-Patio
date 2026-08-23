package co.elpatio.dominio.pago;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** En que va el cobro del anticipo con la pasarela externa. */
public enum EstadoPagoOnline {
  PENDIENTE,
  APROBADO,
  RECHAZADO,
  EXPIRADO;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoPagoOnline de(String valor) { return valueOf(valor.toUpperCase()); }

  public boolean esFinal() {
    return this != PENDIENTE;
  }
}
