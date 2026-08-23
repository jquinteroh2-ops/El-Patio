package co.elpatio.dominio.canal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Por donde entro un pedido o una reserva, cuando el que atendio no fue una
 * persona parada en el mostrador.
 *
 * Es distinto del `TipoPedido` de una orden (mesa/domicilio/llevar, que dice
 * como se entrega): este dice quien atendio al cliente del otro lado. Hoy solo
 * WHATSAPP tiene un adaptador real. TELEFONO existe desde ya, vacio, para que
 * el futuro agente de voz entre por el mismo hueco sin que ningun caso de uso
 * tenga que enterarse de que existe un canal nuevo.
 */
public enum Canal {
  WHATSAPP,
  TELEFONO,
  WEB,
  PRESENCIAL;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static Canal de(String valor) { return valueOf(valor.toUpperCase()); }

  /** Si el canal es un bot o un agente el que conversa, no una persona del restaurante. */
  public boolean esAutomatizado() {
    return this == WHATSAPP || this == TELEFONO;
  }
}
