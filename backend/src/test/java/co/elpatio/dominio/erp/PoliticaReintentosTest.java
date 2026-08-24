package co.elpatio.dominio.erp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PoliticaReintentosTest {

  private static final Instant AHORA = Instant.parse("2026-08-24T20:00:00Z");

  /**
   * El primer fallo espera lo configurado, no el doble.
   *
   * El parametro son fallos acumulados: con uno encima, la espera es la
   * inicial. Contarlo desde cero adelantaria un escalon toda la escalera.
   */
  @Test
  void laEsperaSeDuplicaConCadaFallo() {
    PoliticaReintentos politica =
        new PoliticaReintentos(Duration.ofMinutes(1), 8, Duration.ofHours(1));

    assertThat(politica.proximoIntento(AHORA, 1)).isEqualTo(AHORA.plusSeconds(60));
    assertThat(politica.proximoIntento(AHORA, 2)).isEqualTo(AHORA.plusSeconds(120));
    assertThat(politica.proximoIntento(AHORA, 3)).isEqualTo(AHORA.plusSeconds(240));
    assertThat(politica.proximoIntento(AHORA, 4)).isEqualTo(AHORA.plusSeconds(480));
  }

  /**
   * Sin techo, duplicar lleva a esperas de dias: al intento numero veinte, un
   * minuto inicial se convierte en mas de un año. El ERP se levanta mucho antes
   * y la venta seguiria esperando.
   */
  @Test
  void laEsperaNoPasaDelTecho() {
    PoliticaReintentos politica =
        new PoliticaReintentos(Duration.ofMinutes(1), 50, Duration.ofHours(1));

    assertThat(politica.proximoIntento(AHORA, 30)).isEqualTo(AHORA.plusSeconds(3600));
  }

  @Test
  void agotadoElTopeYaNoQuedanIntentos() {
    PoliticaReintentos politica =
        new PoliticaReintentos(Duration.ofMinutes(1), 3, Duration.ofHours(1));

    assertThat(politica.quedanIntentos(2)).isTrue();
    assertThat(politica.quedanIntentos(3)).isFalse();
    assertThat(politica.quedanIntentos(4)).isFalse();
  }

  /** Con cero de margen, el primer fallo ya es definitivo. */
  @Test
  void unaPoliticaSinReintentosNoDaNiUno() {
    PoliticaReintentos politica =
        new PoliticaReintentos(Duration.ofMinutes(1), 0, Duration.ofHours(1));

    assertThat(politica.quedanIntentos(0)).isFalse();
  }
}
