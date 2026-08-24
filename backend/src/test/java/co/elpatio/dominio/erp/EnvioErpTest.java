package co.elpatio.dominio.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EnvioErpTest {

  private static final Instant AHORA = Instant.parse("2026-08-24T20:00:00Z");

  private static EnvioErp nuevo() {
    return EnvioErp.encolar("erp_1", "pg_1", "llave-1", "{}", AHORA);
  }

  private static PoliticaReintentos politicaDe(int maximo) {
    return new PoliticaReintentos(Duration.ofMinutes(1), maximo, Duration.ofHours(1));
  }

  @Test
  void unEnvioReciennacidoEstaPendienteYListoParaSalir() {
    EnvioErp envio = nuevo();

    assertThat(envio.getEstado()).isEqualTo(EstadoEnvioErp.PENDIENTE_ENVIO_ERP);
    assertThat(envio.getIntentos()).isZero();
    assertThat(envio.debeIntentarse(AHORA)).isTrue();
  }

  @Test
  void confirmarGuardaElNumeroDelDocumentoYCierraElCamino() {
    EnvioErp envio = nuevo();
    envio.marcarEnviado("manual", AHORA);

    envio.confirmar(ResultadoFacturacion.confirmado("FV-8891", "{\"ok\":true}"), AHORA);

    assertThat(envio.getEstado()).isEqualTo(EstadoEnvioErp.FACTURADA_ERP);
    assertThat(envio.getDocumentoExterno()).isEqualTo("FV-8891");
    assertThat(envio.getProximoIntento()).isNull();
    assertThat(envio.debeIntentarse(AHORA.plusSeconds(99999))).isFalse();
  }

  @Test
  void mientrasQuedeMargenElFalloDevuelveElEnvioALaCola() {
    EnvioErp envio = nuevo();
    envio.marcarEnviado("rest", AHORA);

    envio.fallar("El ERP no responde", null, politicaDe(3), AHORA);

    assertThat(envio.getEstado()).isEqualTo(EstadoEnvioErp.PENDIENTE_ENVIO_ERP);
    assertThat(envio.getIntentos()).isEqualTo(1);
    assertThat(envio.getProximoIntento()).isEqualTo(AHORA.plusSeconds(60));
    // El motivo se guarda aunque vaya a reintentar: cuando falle el ultimo,
    // saber que los anteriores fallaron por lo mismo es lo que lo resuelve.
    assertThat(envio.getError()).isEqualTo("El ERP no responde");
  }

  @Test
  void agotadosLosIntentosQuedaParaRevisionHumana() {
    EnvioErp envio = nuevo();
    PoliticaReintentos politica = politicaDe(2);

    envio.marcarEnviado("rest", AHORA);
    envio.fallar("timeout", null, politica, AHORA);
    envio.marcarEnviado("rest", AHORA);
    envio.fallar("timeout", null, politica, AHORA);

    assertThat(envio.getIntentos()).isEqualTo(2);
    assertThat(envio.getEstado()).isEqualTo(EstadoEnvioErp.ERROR_ERP);
    assertThat(envio.getProximoIntento()).isNull();
    assertThat(envio.getEstado().esFinal()).isTrue();
  }

  /**
   * Quien pulsa reintentar acaba de arreglar la causa y merece la tanda
   * completa, no el unico intento que le quedaba a la anterior.
   */
  @Test
  void reencolarDevuelveElMargenCompleto() {
    EnvioErp envio = nuevo();
    PoliticaReintentos politica = politicaDe(1);
    envio.marcarEnviado("rest", AHORA);
    envio.fallar("timeout", null, politica, AHORA);
    assertThat(envio.getEstado()).isEqualTo(EstadoEnvioErp.ERROR_ERP);

    Instant despues = AHORA.plusSeconds(3600);
    envio.reencolar(despues);

    assertThat(envio.getEstado()).isEqualTo(EstadoEnvioErp.PENDIENTE_ENVIO_ERP);
    assertThat(envio.getIntentos()).isZero();
    assertThat(envio.getError()).isNull();
    assertThat(envio.debeIntentarse(despues)).isTrue();
  }

  @Test
  void unEnvioPendienteNoSaleAntesDeSuHora() {
    EnvioErp envio = nuevo();
    envio.marcarEnviado("rest", AHORA);
    envio.fallar("timeout", null, politicaDe(5), AHORA);

    assertThat(envio.debeIntentarse(AHORA.plusSeconds(30))).isFalse();
    assertThat(envio.debeIntentarse(AHORA.plusSeconds(60))).isTrue();
  }

  /**
   * Un ERP que dice «listo» sin decir con que numero no confirmo nada: sin ese
   * dato la conciliacion no tiene contra que cruzar la venta.
   */
  @Test
  void noSePuedeConfirmarSinNumeroDeDocumento() {
    assertThatThrownBy(() -> ResultadoFacturacion.confirmado("", "{}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ResultadoFacturacion.confirmado(null, "{}"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
