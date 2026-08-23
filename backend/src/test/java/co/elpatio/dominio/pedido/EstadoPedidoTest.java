package co.elpatio.dominio.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * El recorrido del pedido tiene que seguir soportando el flujo de siempre
 * (mostrador y sitio publico, que entran directo en `nuevo`) al mismo tiempo
 * que el nuevo tramo de anticipo, incluido el salto opcional de
 * `pendiente_verificacion`.
 */
class EstadoPedidoTest {

  @Nested
  @DisplayName("recorrido de siempre (sin anticipo)")
  class RecorridoDeSiempre {
    @Test
    void avanzaUnPasoALaVez() {
      assertThat(EstadoPedido.NUEVO.puedePasarA(EstadoPedido.ACEPTADO)).isTrue();
      assertThat(EstadoPedido.ACEPTADO.puedePasarA(EstadoPedido.EN_PREPARACION)).isTrue();
      assertThat(EstadoPedido.EN_PREPARACION.puedePasarA(EstadoPedido.LISTO)).isTrue();
      assertThat(EstadoPedido.LISTO.puedePasarA(EstadoPedido.DESPACHADO)).isTrue();
      assertThat(EstadoPedido.DESPACHADO.puedePasarA(EstadoPedido.ENTREGADO)).isTrue();
    }

    @Test
    void noPermiteSaltarsePasos() {
      assertThat(EstadoPedido.NUEVO.puedePasarA(EstadoPedido.EN_PREPARACION)).isFalse();
      assertThat(EstadoPedido.ACEPTADO.puedePasarA(EstadoPedido.DESPACHADO)).isFalse();
    }

    @Test
    void noPermiteDevolverse() {
      assertThat(EstadoPedido.LISTO.puedePasarA(EstadoPedido.ACEPTADO)).isFalse();
    }

    @Test
    void unEstadoFinalNoAvanzaMas() {
      assertThat(EstadoPedido.ENTREGADO.puedePasarA(EstadoPedido.NUEVO)).isFalse();
      assertThat(EstadoPedido.RECHAZADO.puedePasarA(EstadoPedido.ACEPTADO)).isFalse();
    }
  }

  @Nested
  @DisplayName("tramo de anticipo (canales automatizados)")
  class TramoDeAnticipo {
    @Test
    void whatsappSaltaPendienteVerificacion() {
      assertThat(EstadoPedido.ESPERANDO_ANTICIPO.puedePasarA(EstadoPedido.ANTICIPO_PAGADO)).isTrue();
    }

    @Test
    void telefonoPodraPasarPorPendienteVerificacion() {
      assertThat(EstadoPedido.BORRADOR.puedePasarA(EstadoPedido.PENDIENTE_VERIFICACION)).isTrue();
      assertThat(EstadoPedido.PENDIENTE_VERIFICACION.puedePasarA(EstadoPedido.ESPERANDO_ANTICIPO))
          .isTrue();
    }

    @Test
    void elAnticipoPagadoEntraComoUnPedidoNuevoNormal() {
      assertThat(EstadoPedido.ANTICIPO_PAGADO.puedePasarA(EstadoPedido.NUEVO)).isTrue();
    }

    @Test
    void noPermiteSaltarUnPasoObligatorio() {
      // De borrador a anticipo_pagado se salta esperando_anticipo, que no es opcional.
      assertThat(EstadoPedido.BORRADOR.puedePasarA(EstadoPedido.ANTICIPO_PAGADO)).isFalse();
    }
  }

  @Nested
  @DisplayName("finales de excepcion")
  class FinalesDeExcepcion {
    @Test
    void seLlegaDesdeCualquierPuntoNoFinal() {
      assertThat(EstadoPedido.ESPERANDO_ANTICIPO.puedePasarA(EstadoPedido.EXPIRADO)).isTrue();
      assertThat(EstadoPedido.NUEVO.puedePasarA(EstadoPedido.CANCELADO)).isTrue();
      assertThat(EstadoPedido.ACEPTADO.puedePasarA(EstadoPedido.RECHAZADO)).isTrue();
    }

    @Test
    void expiradoEsFinal() {
      assertThat(EstadoPedido.EXPIRADO.esFinal()).isTrue();
      assertThat(EstadoPedido.EXPIRADO.puedePasarA(EstadoPedido.NUEVO)).isFalse();
    }
  }
}
