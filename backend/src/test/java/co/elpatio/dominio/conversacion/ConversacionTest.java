package co.elpatio.dominio.conversacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConversacionTest {

  @Test
  void empiezaIniciadaYAvanzaDePaso() {
    Conversacion conversacion = new Conversacion();
    conversacion.setCanal(Canal.WHATSAPP);
    conversacion.setIdentificadorExterno("573001234567");

    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.INICIADA);

    Instant ahora = Instant.now();
    conversacion.cambiarEstado(EstadoConversacion.ARMANDO_PEDIDO, ahora);

    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.ARMANDO_PEDIDO);
    assertThat(conversacion.getActualizadaEn()).isEqualTo(ahora);
  }

  @Test
  void unaConversacionFinalizadaNoAvanzaMas() {
    Conversacion conversacion = new Conversacion();
    conversacion.cambiarEstado(EstadoConversacion.FINALIZADA, Instant.now());

    assertThatThrownBy(() -> conversacion.cambiarEstado(EstadoConversacion.ARMANDO_PEDIDO, Instant.now()))
        .isInstanceOf(ReglaDeNegocioError.class);
  }

  @Test
  void asociarPedidoNoCambiaElEstado() {
    Conversacion conversacion = new Conversacion();
    conversacion.cambiarEstado(EstadoConversacion.ARMANDO_PEDIDO, Instant.now());

    conversacion.asociarPedido("ord1", Instant.now());

    assertThat(conversacion.getPedidoId()).isEqualTo("ord1");
    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.ARMANDO_PEDIDO);
  }
}
