package co.elpatio.dominio.canal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Por donde entro un pedido o una reserva, cuando el que atendio no fue una
 * persona parada en el mostrador.
 *
 * Es distinto del `TipoPedido` de una orden (mesa/domicilio/llevar, que dice
 * como se entrega): este dice quien atendio al cliente del otro lado.
 *
 * Hoy ninguno de los dos canales automatizados tiene adaptador. WHATSAPP lo
 * tuvo -un bot que tomaba el pedido- y se retiro cuando el cliente descarto la
 * automatizacion; los dos valores siguen aqui porque hay pedidos guardados que
 * los llevan escritos, y porque son el hueco por donde entraria el agente de
 * voz sin que ningun caso de uso tenga que enterarse.
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
