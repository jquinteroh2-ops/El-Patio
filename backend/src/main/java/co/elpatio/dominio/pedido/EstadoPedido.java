package co.elpatio.dominio.pedido;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/**
 * El recorrido de un pedido externo, en el orden en que ocurre.
 *
 * Es distinto del estado de la comanda: aquel sigue la cocina, este sigue al
 * cliente. Un pedido puede estar `despachado` mientras su comanda ya esta
 * `servida`, porque son dos relojes que miden cosas distintas.
 */
public enum EstadoPedido {
  BORRADOR,
  PENDIENTE_VERIFICACION,
  ESPERANDO_ANTICIPO,
  ANTICIPO_PAGADO,
  NUEVO,
  ACEPTADO,
  EN_PREPARACION,
  LISTO,
  DESPACHADO,
  ENTREGADO,
  RECHAZADO,
  CANCELADO,
  EXPIRADO;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoPedido de(String valor) { return valueOf(valor.toUpperCase()); }

  /**
   * Secuencia normal. Los finales de excepcion quedan fuera a proposito.
   *
   * Los primeros cuatro pasos (`borrador` a `anticipo_pagado`) solo los
   * recorren los canales automatizados que cobran anticipo; un pedido tomado
   * en el mostrador o por el sitio publico entra directo en `nuevo`, que es
   * donde siempre empezo este recorrido.
   */
  private static final List<EstadoPedido> RECORRIDO =
      List.of(
          BORRADOR,
          PENDIENTE_VERIFICACION,
          ESPERANDO_ANTICIPO,
          ANTICIPO_PAGADO,
          NUEVO,
          ACEPTADO,
          EN_PREPARACION,
          LISTO,
          DESPACHADO,
          ENTREGADO);

  /** Los pasos del recorrido que un canal puede saltarse sin que sea un salto ilegal. */
  public boolean esOpcional() {
    return this == PENDIENTE_VERIFICACION;
  }

  public boolean esFinal() {
    return this == ENTREGADO || this == RECHAZADO || this == CANCELADO || this == EXPIRADO;
  }

  /**
   * Si se puede pasar de este estado al siguiente.
   *
   * El recorrido no se puede saltar ni devolver, salvo por los pasos marcados
   * como opcionales: un pedido no puede quedar `entregado` sin haber pasado
   * por `despachado`, porque entonces nadie sabria quien lo llevo, pero si
   * puede ir de `esperando_anticipo` a `anticipo_pagado` sin pasar por
   * `pendiente_verificacion` porque WhatsApp nunca lo usa. Rechazar, cancelar
   * y expirar son posibles desde cualquier punto que no sea final, porque la
   * realidad del salon y del pago las impone.
   */
  public boolean puedePasarA(EstadoPedido siguiente) {
    if (esFinal()) return false;
    if (siguiente == RECHAZADO || siguiente == CANCELADO || siguiente == EXPIRADO) return true;
    int actual = RECORRIDO.indexOf(this);
    int destino = RECORRIDO.indexOf(siguiente);
    if (actual < 0 || destino <= actual) return false;
    for (int i = actual + 1; i < destino; i++) {
      if (!RECORRIDO.get(i).esOpcional()) return false;
    }
    return true;
  }
}
