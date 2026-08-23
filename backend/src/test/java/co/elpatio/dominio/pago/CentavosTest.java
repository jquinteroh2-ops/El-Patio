package co.elpatio.dominio.pago;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * La unica funcion que convierte entre pesos y centavos. Si esto se rompe,
 * cada anticipo cobra cien veces mas o cien veces menos.
 */
class CentavosTest {

  @Test
  void convierteDePesosACentavos() {
    assertThat(Centavos.deCOP(15_000)).isEqualTo(1_500_000);
    assertThat(Centavos.deCOP(0)).isEqualTo(0);
  }

  @Test
  void convierteDeCentavosAPesos() {
    assertThat(Centavos.aCOP(1_500_000)).isEqualTo(15_000);
  }

  @Test
  void esInversaExacta() {
    long pesos = 37_450;
    assertThat(Centavos.aCOP(Centavos.deCOP(pesos))).isEqualTo(pesos);
  }
}
