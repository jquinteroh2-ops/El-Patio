package co.elpatio.dominio.conversacion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * En que paso de la charla esta el cliente.
 *
 * Vive aparte del estado del pedido a proposito: un pedido puede quedar
 * `esperando_anticipo` mientras la conversacion ya paso a `finalizada`, porque
 * el bot ya mando el link de pago y no tiene mas que decir hasta que llegue el
 * webhook. Son dos relojes distintos que miden cosas distintas.
 */
public enum EstadoConversacion {
  INICIADA,
  EN_MENU_PRINCIPAL,
  ARMANDO_PEDIDO,
  ARMANDO_RESERVA,
  DERIVADA_A_HUMANO,
  FINALIZADA,
  EXPIRADA;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoConversacion de(String valor) { return valueOf(valor.toUpperCase()); }

  public boolean esFinal() {
    return this == FINALIZADA || this == EXPIRADA;
  }
}
