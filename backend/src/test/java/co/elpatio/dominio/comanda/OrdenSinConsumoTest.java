package co.elpatio.dominio.comanda;

import static org.assertj.core.api.Assertions.assertThat;

import co.elpatio.dominio.carta.Destino;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cuando el mesero puede desocupar una mesa sin cobrar.
 *
 * Es la frontera entre "se fueron sin consumir" y "hay una venta": si se corre
 * hacia el lado equivocado, una cuenta se puede borrar desde la tablet.
 */
class OrdenSinConsumoTest {

  private static ItemOrden item(String id, EstadoItem estado, int turno) {
    ItemOrden item = new ItemOrden();
    item.setId(id);
    item.setItemCartaId("p1");
    item.setNombre("Plato");
    item.setPrecioUnitario(30000);
    item.setCantidad(1);
    item.setDestino(Destino.COCINA);
    item.setEstado(estado);
    item.setTurnoEnvio(turno);
    item.setModificadoresSeleccionados(List.of());
    return item;
  }

  private static Orden orden(ItemOrden... items) {
    Orden orden = new Orden();
    orden.setId("ord1");
    orden.setMesaId("m1");
    orden.setAbiertaEn(Instant.now());
    orden.setItems(new ArrayList<>(List.of(items)));
    return orden;
  }

  @Test
  @DisplayName("Sin productos se puede desocupar")
  void vacia() {
    assertThat(orden().sinConsumo()).isTrue();
  }

  @Test
  @DisplayName("Lo que nunca se envio no cuenta como consumo")
  void soloSinEnviar() {
    assertThat(orden(item("a", EstadoItem.PENDIENTE, 0)).sinConsumo()).isTrue();
  }

  @Test
  @DisplayName("Lo enviado y anulado tampoco cuenta")
  void enviadoYAnulado() {
    assertThat(orden(item("a", EstadoItem.ANULADO, 1)).sinConsumo()).isTrue();
  }

  @Test
  @DisplayName("Un producto enviado y vigente obliga a cobrar o anular")
  void enviadoVigente() {
    Orden orden = orden(item("a", EstadoItem.ANULADO, 1), item("b", EstadoItem.SERVIDO, 1));
    assertThat(orden.sinConsumo()).isFalse();
  }

  @Test
  @DisplayName("Un cargo adicional tambien es algo por cobrar")
  void conCargo() {
    Orden orden = orden();
    orden.setCargosAdicionales(
        new ArrayList<>(List.of(new CargoAdicional("cg1", "Descorche", 20000, "Ana", Instant.now()))));
    assertThat(orden.sinConsumo()).isFalse();
  }

  @Test
  @DisplayName("Entregar todo saca de cocina lo enviado y deja lo que no se envio")
  void entregarTodo() {
    Orden orden =
        orden(
            item("a", EstadoItem.LISTO, 1),
            item("b", EstadoItem.EN_PREPARACION, 1),
            item("c", EstadoItem.ANULADO, 1),
            item("d", EstadoItem.PENDIENTE, 0));
    assertThat(orden.marcarEntregados(null, Instant.now())).isEqualTo(2);
    assertThat(orden.getItems()).extracting(ItemOrden::getEstado)
        .containsExactly(EstadoItem.SERVIDO, EstadoItem.SERVIDO, EstadoItem.ANULADO, EstadoItem.PENDIENTE);
  }

  @Test
  @DisplayName("Un plato sin enviar no se puede marcar entregado")
  void noEnviado() {
    Orden orden = orden(item("d", EstadoItem.PENDIENTE, 0));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> orden.marcarEntregados("d", Instant.now()))
        .hasMessageContaining("no se ha enviado");
  }
}
