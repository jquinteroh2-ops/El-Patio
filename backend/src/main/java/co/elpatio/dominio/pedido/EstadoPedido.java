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
  NUEVO,
  ACEPTADO,
  EN_PREPARACION,
  LISTO,
  DESPACHADO,
  ENTREGADO,
  RECHAZADO,
  CANCELADO;

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static EstadoPedido de(String valor) { return valueOf(valor.toUpperCase()); }

  /** Secuencia normal. Los dos finales de excepcion quedan fuera a proposito. */
  private static final List<EstadoPedido> RECORRIDO =
      List.of(NUEVO, ACEPTADO, EN_PREPARACION, LISTO, DESPACHADO, ENTREGADO);

  public boolean esFinal() {
    return this == ENTREGADO || this == RECHAZADO || this == CANCELADO;
  }

  /**
   * Si se puede pasar de este estado al siguiente.
   *
   * El recorrido no se puede saltar ni devolver: un pedido no puede quedar
   * `entregado` sin haber pasado por `despachado`, porque entonces nadie sabria
   * quien lo llevo. Rechazar y cancelar si son posibles desde cualquier punto
   * que no sea final, porque la realidad del salon las impone.
   */
  public boolean puedePasarA(EstadoPedido siguiente) {
    if (esFinal()) return false;
    if (siguiente == RECHAZADO || siguiente == CANCELADO) return true;
    int actual = RECORRIDO.indexOf(this);
    int destino = RECORRIDO.indexOf(siguiente);
    return actual >= 0 && destino == actual + 1;
  }
}
