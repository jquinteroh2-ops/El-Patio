package co.elpatio.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LimitadorDePeticionesTest {

  @Test
  void dejaPasarHastaElMaximoYNoMas() {
    LimitadorDePeticiones limitador = new LimitadorDePeticiones();

    assertThat(limitador.permite("pqr:1.2.3.4", 3)).isTrue();
    assertThat(limitador.permite("pqr:1.2.3.4", 3)).isTrue();
    assertThat(limitador.permite("pqr:1.2.3.4", 3)).isTrue();
    assertThat(limitador.permite("pqr:1.2.3.4", 3)).isFalse();
    assertThat(limitador.permite("pqr:1.2.3.4", 3)).isFalse();
  }

  /** Dos IP distintas no se estorban. */
  @Test
  void cadaIpLlevaSuPropioContador() {
    LimitadorDePeticiones limitador = new LimitadorDePeticiones();

    assertThat(limitador.permite("pqr:1.1.1.1", 1)).isTrue();
    assertThat(limitador.permite("pqr:1.1.1.1", 1)).isFalse();

    assertThat(limitador.permite("pqr:2.2.2.2", 1)).isTrue();
  }

  /**
   * Y dos formularios distintos tampoco.
   *
   * Agotar el cupo de postulaciones no puede cerrar el de PQR: son cosas
   * distintas, hechas por gente distinta, y compartir contador significaria que
   * un robot llenando un formulario deja sin canal de quejas al restaurante.
   */
  @Test
  void cadaFormularioLlevaSuPropioContador() {
    LimitadorDePeticiones limitador = new LimitadorDePeticiones();

    assertThat(limitador.permite("postulaciones:1.1.1.1", 1)).isTrue();
    assertThat(limitador.permite("postulaciones:1.1.1.1", 1)).isFalse();

    assertThat(limitador.permite("pqr:1.1.1.1", 1)).isTrue();
  }

  /**
   * Bajo concurrencia no se regala cupo de mas.
   *
   * Es el caso que un contador sin cuidado falla: veinte hilos a la vez leen el
   * mismo valor, todos deciden que caben, y pasan veinte en vez de cinco.
   */
  @Test
  void bajoConcurrenciaNoSeCuelaNadieDeMas() throws InterruptedException {
    LimitadorDePeticiones limitador = new LimitadorDePeticiones();
    int hilos = 40;
    int maximo = 5;

    AtomicInteger permitidas = new AtomicInteger();
    CountDownLatch salida = new CountDownLatch(1);
    CountDownLatch terminaron = new CountDownLatch(hilos);
    ExecutorService pool = Executors.newFixedThreadPool(hilos);

    for (int i = 0; i < hilos; i++) {
      pool.submit(
          () -> {
            try {
              salida.await();
              if (limitador.permite("pqr:9.9.9.9", maximo)) permitidas.incrementAndGet();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              terminaron.countDown();
            }
          });
    }

    salida.countDown();
    assertThat(terminaron.await(10, TimeUnit.SECONDS)).isTrue();
    pool.shutdown();

    assertThat(permitidas.get()).isEqualTo(maximo);
  }

  /** Quien agota el cupo puede saber cuanto falta para reintentar. */
  @Test
  void diceCuantoFaltaParaQueSeLibereElCupo() {
    LimitadorDePeticiones limitador = new LimitadorDePeticiones();
    limitador.permite("pqr:3.3.3.3", 1);

    assertThat(limitador.minutosParaReintentar("pqr:3.3.3.3")).isBetween(0L, 10L);
    // Una clave que nunca se usó no tiene espera.
    assertThat(limitador.minutosParaReintentar("pqr:nadie")).isZero();
  }
}
