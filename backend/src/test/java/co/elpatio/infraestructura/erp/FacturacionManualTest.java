package co.elpatio.infraestructura.erp;

import static org.assertj.core.api.Assertions.assertThat;

import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.puertos.FacturacionExterna;
import org.junit.jupiter.api.Test;

class FacturacionManualTest extends ContratoFacturacionExterna {

  private final FacturacionManual adaptador = new FacturacionManual();

  @Override
  protected FacturacionExterna adaptador() {
    return adaptador;
  }

  /**
   * Lo que de verdad importa de este adaptador: que NO finja exito.
   *
   * Un NoOp que devolviera CONFIRMADO dejaria todas las ventas en verde con un
   * documento inexistente, y el contador cerraria el mes creyendo que esta al
   * dia. EN_ESPERA es la verdad: la venta esta bien y falta digitarla.
   */
  @Test
  void nuncaConfirmaPorqueNoTransmiteNada() {
    ResultadoFacturacion resultado = adaptador.emitirDocumento(ventaDeEjemplo());

    assertThat(resultado.desenlace()).isEqualTo(ResultadoFacturacion.Desenlace.EN_ESPERA);
    assertThat(resultado.confirmo()).isFalse();
    assertThat(resultado.numeroDocumento()).isNull();
  }

  /** El motivo lo lee un administrador en la pantalla; que diga que hacer. */
  @Test
  void elMotivoDiceQueFaltaDigitarlaYEnQueComanda() {
    ResultadoFacturacion resultado = adaptador.emitirDocumento(ventaDeEjemplo());

    assertThat(resultado.motivo()).contains("digitacion").contains("47");
  }
}
