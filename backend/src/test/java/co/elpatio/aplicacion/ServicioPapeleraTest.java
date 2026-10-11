package co.elpatio.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elpatio.dominio.caja.CierreCaja;
import co.elpatio.dominio.caja.Turno;
import co.elpatio.dominio.cobro.MetodoPago;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.erp.EnvioErp;
import co.elpatio.dominio.erp.EstadoEnvioErp;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.papelera.RegistroEliminado;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Las reglas de la papelera: motivo, turno cerrado, ERP facturado y restaurar una sola vez. */
class ServicioPapeleraTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  /** 8:30 p. m. del 10 de octubre en Bogota: turno de cena. */
  private static final Instant COBRO = Instant.parse("2026-10-11T01:30:00Z");

  private Repositorios.DePapelera papelera;
  private Repositorios.DePagos pagos;
  private Repositorios.DeCierres cierres;
  private Repositorios.DeEnviosErp envios;
  private ServicioPapelera servicio;

  @BeforeEach
  void preparar() {
    papelera = mock(Repositorios.DePapelera.class);
    pagos = mock(Repositorios.DePagos.class);
    Repositorios.DeOrdenes ordenes = mock(Repositorios.DeOrdenes.class);
    Repositorios.DeMesas mesas = mock(Repositorios.DeMesas.class);
    cierres = mock(Repositorios.DeCierres.class);
    envios = mock(Repositorios.DeEnviosErp.class);
    Reloj reloj = mock(Reloj.class);
    when(reloj.ahora()).thenReturn(COBRO.plusSeconds(600));
    when(reloj.diaDe(any())).thenAnswer(i -> i.<Instant>getArgument(0).atZone(BOGOTA).toLocalDate());
    when(reloj.horaDe(any())).thenAnswer(i -> i.<Instant>getArgument(0).atZone(BOGOTA).toLocalTime());

    Pago pago = new Pago();
    pago.setId("pg1");
    pago.setOrdenId("ord1");
    pago.setTotal(66_960);
    pago.setMetodo(MetodoPago.EFECTIVO);
    pago.setFechaHora(COBRO);
    when(pagos.porId("pg1")).thenReturn(Optional.of(pago));

    Orden orden = new Orden();
    orden.setId("ord1");
    orden.setNumero(1);
    when(ordenes.porId("ord1")).thenReturn(Optional.of(orden));

    when(cierres.listar()).thenReturn(List.of());
    when(envios.porPago(anyString())).thenReturn(Optional.empty());
    when(papelera.copiaDeVenta("pg1", "ord1")).thenReturn("{\"pago\":{}}");

    servicio =
        new ServicioPapelera(
            papelera, pagos, ordenes, mesas, cierres, envios, p -> p + "-1", reloj,
            mock(PublicadorEventos.class));
  }

  @Test
  void eliminaGuardandoLaCopiaYQuienLoHizo() {
    RegistroEliminado r = servicio.eliminarVenta("pg1", "Se cobró a la mesa equivocada", "Álvaro");

    verify(papelera).borrarVenta("pg1", "ord1");
    ArgumentCaptor<RegistroEliminado> guardado = ArgumentCaptor.forClass(RegistroEliminado.class);
    verify(papelera).guardar(guardado.capture());
    assertThat(guardado.getValue().getDatos()).isEqualTo("{\"pago\":{}}");
    assertThat(r.getEliminadoPor()).isEqualTo("Álvaro");
    assertThat(r.getResumen()).contains("Comanda #1").contains("66.960").contains("10/10/2026");
  }

  @Test
  void sinMotivoNoSeBorraNada() {
    assertThatThrownBy(() -> servicio.eliminarVenta("pg1", "  ", "Álvaro"))
        .isInstanceOf(ReglaDeNegocioError.class);
    verify(papelera, never()).borrarVenta(anyString(), anyString());
  }

  @Test
  void unaVentaDeUnTurnoCerradoNoSeElimina() {
    CierreCaja cierre = new CierreCaja();
    cierre.setFecha(LocalDate.of(2026, 10, 10));
    cierre.setTurno(Turno.CENA);
    when(cierres.listar()).thenReturn(List.of(cierre));

    assertThatThrownBy(() -> servicio.eliminarVenta("pg1", "error", "Álvaro"))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("cierre de caja");
    verify(papelera, never()).borrarVenta(anyString(), anyString());
  }

  @Test
  void elCierreDeOtroTurnoNoEstorba() {
    CierreCaja almuerzo = new CierreCaja();
    almuerzo.setFecha(LocalDate.of(2026, 10, 10));
    almuerzo.setTurno(Turno.ALMUERZO);
    when(cierres.listar()).thenReturn(List.of(almuerzo));

    servicio.eliminarVenta("pg1", "error", "Álvaro");

    verify(papelera).borrarVenta("pg1", "ord1");
  }

  @Test
  void unaVentaYaFacturadaEnElErpNoSeElimina() {
    EnvioErp envio = new EnvioErp();
    envio.setEstado(EstadoEnvioErp.FACTURADA_ERP);
    envio.setDocumentoExterno("FV-123");
    when(envios.porPago("pg1")).thenReturn(Optional.of(envio));

    assertThatThrownBy(() -> servicio.eliminarVenta("pg1", "error", "Álvaro"))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("FV-123");
  }

  @Test
  void restaurarDosVecesNoSePuede() {
    RegistroEliminado r =
        RegistroEliminado.deVenta("pap-1", "pg9", "Comanda #9", "error", "{}", "Álvaro", COBRO);
    r.marcarRestaurado("Álvaro", COBRO);
    when(papelera.porId("pap-1")).thenReturn(Optional.of(r));

    assertThatThrownBy(() -> servicio.restaurar("pap-1", "Álvaro"))
        .isInstanceOf(ReglaDeNegocioError.class);
    verify(papelera, never()).restaurarVenta(anyString());
  }

  @Test
  void restaurarDevuelveLaVentaYLoAnota() {
    RegistroEliminado r =
        RegistroEliminado.deVenta("pap-1", "pg1", "Comanda #1", "error", "{}", "Álvaro", COBRO);
    when(papelera.porId("pap-1")).thenReturn(Optional.of(r));
    Pago pago = pagos.porId("pg1").orElseThrow();
    // Antes de restaurar no existe; despues, si.
    when(pagos.porId("pg1")).thenReturn(Optional.empty(), Optional.of(pago));

    RegistroEliminado restaurado = servicio.restaurar("pap-1", "Katherine");

    verify(papelera).restaurarVenta("{}");
    assertThat(restaurado.getRestauradoPor()).isEqualTo("Katherine");
    assertThat(restaurado.estaRestaurado()).isTrue();
  }
}
