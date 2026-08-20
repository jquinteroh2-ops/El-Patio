package co.elpatio.dominio.personal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Los cuatro roles de la casa. El codigo en minuscula es el mismo literal que
 * usa el frontend en tipos.ts: si aqui cambia, alla deja de compilar.
 */
public enum Rol {
  MESERO,
  COCINA,
  /** Recibe y gestiona lo que entra por domicilio y para llevar. */
  RECEPCION,
  CAJERO,
  ADMINISTRADOR;

  @JsonValue
  public String codigo() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static Rol de(String valor) {
    return valueOf(valor.toUpperCase());
  }

  /** Nombre de la autoridad tal como la nombra Spring Security. */
  public String autoridad() {
    return "ROLE_" + name();
  }
}
