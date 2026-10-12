package co.elpatio.dominio.comanda;

import static org.assertj.core.api.Assertions.assertThat;

import co.elpatio.dominio.carta.Destino;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El mesero o la caja entregan un plato suelto sin esperar a cocina.
 *
 * El cocinero tiene las manos ocupadas y muchas veces no alcanza a marcar el
 * plato. Lo que se entrega así no puede volver a la pantalla de cocina cuando
 * el cocinero marca el resto del turno: sería invitar a prepararlo dos veces.
 */
class OrdenEntregaPorPlatoTest {

  private static ItemOrden item(String id, EstadoItem estado) {
    ItemOrden item = new ItemOrden();
    item.setId(id);
    item.setItemCartaId("p-" + id);
    item.setNombre("Plato " + id);
    item.setPrecioUnitario(30000);
    item.setCantidad(1);
    item.setDestino(Destino.COCINA);
    item.setEstado(estado);
    item.setTurnoEnvio(1);
    item.setModificadoresSeleccionados(List.of());
    return item;
  }

  private static Orden orden(ItemOrden... items) {
    Orden orden = new Orden();
    orden.setId("ord1");
    orden.setMesaId("m1");
    orden.setAbiertaEn(Instant.now());
    orden.setEstado(EstadoOrden.EN_PREPARACION);
    orden.setItems(new ArrayList<>(List.of(items)));
    return orden;
  }

  @Test
  @DisplayName("Se entrega un plato que cocina todavía no marcó listo")
  void entregaSinEsperarACocina() {
    Orden orden = orden(item("a", EstadoItem.EN_PREPARACION), item("b", EstadoItem.EN_PREPARACION));

    orden.marcarEntregados("a", Instant.now());

    assertThat(orden.getItems()).extracting(ItemOrden::getEstado)
        .containsExactly(EstadoItem.SERVIDO, EstadoItem.EN_PREPARACION);
  }

  @Test
  @DisplayName("Marcar listo el turno no devuelve a cocina lo ya entregado")
  void turnoNoRevive() {
    Orden orden = orden(item("a", EstadoItem.EN_PREPARACION), item("b", EstadoItem.EN_PREPARACION));
    orden.marcarEntregados("a", Instant.now());

    orden.cambiarEstadoTurno(1, Destino.COCINA, EstadoItem.LISTO, Instant.now());

    assertThat(orden.getItems()).extracting(ItemOrden::getEstado)
        .containsExactly(EstadoItem.SERVIDO, EstadoItem.LISTO);
  }
}
