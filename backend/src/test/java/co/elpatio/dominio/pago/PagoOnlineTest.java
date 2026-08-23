package co.elpatio.dominio.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PagoOnlineTest {

  @Test
  void aprobarCambiaElEstadoYGuardaElTransactionId() {
    PagoOnline pago = new PagoOnline();
    Instant ahora = Instant.now();

    boolean cambio = pago.aprobar("txn_1", ahora);

    assertThat(cambio).isTrue();
    assertThat(pago.getEstado()).isEqualTo(EstadoPagoOnline.APROBADO);
    assertThat(pago.getTransactionId()).isEqualTo("txn_1");
    assertThat(pago.getActualizadaEn()).isEqualTo(ahora);
  }

  @Test
  void aprobarDosVecesEsIdempotente() {
    PagoOnline pago = new PagoOnline();
    pago.aprobar("txn_1", Instant.now());

    boolean segundaVez = pago.aprobar("txn_1", Instant.now());

    assertThat(segundaVez).isFalse();
    assertThat(pago.getEstado()).isEqualTo(EstadoPagoOnline.APROBADO);
  }

  @Test
  void noSePuedeAprobarUnPagoYaRechazado() {
    PagoOnline pago = new PagoOnline();
    pago.rechazar("txn_1", Instant.now());

    assertThatThrownBy(() -> pago.aprobar("txn_2", Instant.now()))
        .isInstanceOf(ReglaDeNegocioError.class);
  }

  @Test
  void expirarSoloAplicaAUnPagoPendiente() {
    PagoOnline pago = new PagoOnline();
    pago.aprobar("txn_1", Instant.now());

    assertThat(pago.expirar(Instant.now())).isFalse();
    assertThat(pago.getEstado()).isEqualTo(EstadoPagoOnline.APROBADO);
  }

  @Test
  void estaVencidoSoloCuandoSiguePendienteYPasoLaFecha() {
    PagoOnline pago = new PagoOnline();
    Instant expira = Instant.now();
    pago.setExpiraEn(expira);

    assertThat(pago.estaVencido(expira.plusSeconds(1))).isTrue();
    assertThat(pago.estaVencido(expira.minusSeconds(1))).isFalse();

    pago.aprobar("txn_1", Instant.now());
    assertThat(pago.estaVencido(expira.plusSeconds(60))).isFalse();
  }
}
