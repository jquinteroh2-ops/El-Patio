package co.elpatio.dominio.comanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elpatio.dominio.carta.Destino;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Un plato pedido en la mesa puede irse para llevar.
 *
 * El cliente lo pide sentado, a veces cuando el plato ya está en la plancha.
 * Cocina tiene que saberlo antes de emplatar, y la mesera tiene que poder
 * marcar un plato suelto o la mesa entera.
 */
class OrdenParaLlevarTest {

  private static final LocalDate HOY = LocalDate.of(2026, 11, 5);

  private static ItemCarta carta(String id) {
    ItemCarta carta = new ItemCarta();
    carta.setId(id);
    carta.setNombre("Plato " + id);
    carta.setPrecio(30000);
    carta.setDisponible(true);
    carta.setDestino(Destino.COCINA);
    return carta;
  }

  private static Orden ordenAbierta() {
    Orden orden = new Orden();
    orden.setId("ord1");
    orden.setMesaId("m1");
    orden.setAbiertaEn(Instant.now());
    orden.setEstado(EstadoOrden.ABIERTA);
    orden.setItems(new ArrayList<>());
    return orden;
  }

  @Test
  @DisplayName("El mismo plato para comer aquí y para llevar queda en dos líneas")
  void noSeAcumulaConElDeLaMesa() {
    Orden orden = ordenAbierta();
    ItemCarta lomo = carta("lomo");

    orden.agregarItem(lomo, 1, List.of(), null, false, "a", HOY);
    orden.agregarItem(lomo, 1, List.of(), null, true, "b", HOY);
    orden.agregarItem(lomo, 2, List.of(), null, true, "c", HOY);

    assertThat(orden.getItems()).hasSize(2);
    assertThat(orden.getItems()).extracting(ItemOrden::isParaLlevar).containsExactly(false, true);
    assertThat(orden.getItems()).extracting(ItemOrden::getCantidad).containsExactly(1, 3);
  }

  @Test
  @DisplayName("Un plato ya enviado a cocina se puede marcar para llevar")
  void seMarcaDespuesDeEnviado() {
    Orden orden = ordenAbierta();
    ItemOrden item = orden.agregarItem(carta("lomo"), 1, List.of(), null, "a", HOY);
    item.setTurnoEnvio(1);
    item.setEstado(EstadoItem.EN_PREPARACION);

    orden.marcarParaLlevar("a", true);
    assertThat(item.isParaLlevar()).isTrue();

    orden.marcarParaLlevar("a", false);
    assertThat(item.isParaLlevar()).isFalse();
  }

  @Test
  @DisplayName("Toda la mesa para llevar deja fuera lo anulado")
  void todaLaMesa() {
    Orden orden = ordenAbierta();
    orden.agregarItem(carta("a"), 1, List.of(), null, "a", HOY);
    orden.agregarItem(carta("b"), 1, List.of(), null, "b", HOY);
    orden.getItems().get(1).setEstado(EstadoItem.ANULADO);

    orden.marcarTodoParaLlevar(true);

    assertThat(orden.getItems()).extracting(ItemOrden::isParaLlevar).containsExactly(true, false);
  }

  @Test
  @DisplayName("Un plato anulado no se puede marcar para llevar")
  void anuladoNoCambia() {
    Orden orden = ordenAbierta();
    orden.agregarItem(carta("a"), 1, List.of(), null, "a", HOY);
    orden.getItems().get(0).setEstado(EstadoItem.ANULADO);

    assertThatThrownBy(() -> orden.marcarParaLlevar("a", true))
        .isInstanceOf(ReglaDeNegocioError.class);
  }
}
