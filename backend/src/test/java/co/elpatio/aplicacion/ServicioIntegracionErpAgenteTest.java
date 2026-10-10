package co.elpatio.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elpatio.dominio.erp.EnvioErp;
import co.elpatio.dominio.erp.EstadoEnvioErp;
import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.erp.VentaParaErp;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.infraestructura.erp.FacturacionPorAgente;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Lo que la nube hace cuando las ventas las viene a buscar el agente del restaurante. */
class ServicioIntegracionErpAgenteTest {

  private static final Instant AHORA = Instant.parse("2026-10-11T01:00:00Z");

  private Repositorios.DeEnviosErp envios;
  private ServicioIntegracionErp servicio;
  private final ObjectMapper json =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @BeforeEach
  void preparar() {
    envios = mock(Repositorios.DeEnviosErp.class);
    Reloj reloj = mock(Reloj.class);
    when(reloj.ahora()).thenReturn(AHORA);
    when(envios.guardar(any())).thenAnswer(i -> i.getArgument(0));
    servicio =
        new ServicioIntegracionErp(
            envios,
            mock(Repositorios.DePagos.class),
            mock(Repositorios.DeOrdenes.class),
            new FacturacionPorAgente(),
            mock(GeneradorIds.class),
            reloj,
            json);
  }

  private EnvioErp encolado(String id) throws Exception {
    VentaParaErp venta =
        new VentaParaErp(
            "llave-" + id, "pg_" + id, "ord_" + id, 41, AHORA, "mesa", "salon",
            List.of(new VentaParaErp.LineaVenta("plato_1", "Posta cartagenera", 2, 38000, 76000)),
            76000, 6080, 8, 0, 0, 0, 82080, "efectivo", List.of(), "caja");
    return EnvioErp.encolar(id, "pg_" + id, "llave-" + id, json.writeValueAsString(venta), AHORA);
  }

  @Test
  void conElAgenteLaTareaDeCadaMinutoNoMandaNada() {
    assertThat(servicio.laNubeEntrega()).isFalse();
    assertThat(servicio.nombreAdaptador()).isEqualTo("agente");
  }

  @Test
  void elAgenteRecibeLaVentaYQuedaReservadaParaEl() throws Exception {
    EnvioErp envio = encolado("erp_1");
    when(envios.pendientesListos(any(), anyInt())).thenReturn(List.of(envio));

    List<ServicioIntegracionErp.EnvioReclamado> tanda = servicio.reclamarParaAgente(20);

    assertThat(tanda).hasSize(1);
    assertThat(tanda.get(0).envioId()).isEqualTo("erp_1");
    assertThat(tanda.get(0).venta().idempotencyKey()).isEqualTo("llave-erp_1");
    assertThat(tanda.get(0).venta().total()).isEqualTo(82080);
    assertThat(envio.getAdaptador()).isEqualTo("agente");
    assertThat(envio.debeIntentarse(AHORA)).isFalse();
  }

  @Test
  void unaVentaIlegibleNoSeLeMandaAlAgenteYQuedaParaRevision() {
    EnvioErp roto = EnvioErp.encolar("erp_2", "pg_2", "llave-2", "{esto no es json", AHORA);
    when(envios.pendientesListos(any(), anyInt())).thenReturn(List.of(roto));

    assertThat(servicio.reclamarParaAgente(20)).isEmpty();
    assertThat(roto.getEstado()).isEqualTo(EstadoEnvioErp.ERROR_ERP);
  }

  @Test
  void laConfirmacionDelAgenteDejaLaVentaFacturada() throws Exception {
    EnvioErp envio = encolado("erp_3");
    envio.reservarParaAgente(AHORA, java.time.Duration.ofMinutes(10));
    when(envios.porId("erp_3")).thenReturn(Optional.of(envio));

    servicio.registrarResultadoDelAgente(
        "erp_3", ResultadoFacturacion.confirmado("FV-1001", "{\"ok\":true}"));

    assertThat(envio.getEstado()).isEqualTo(EstadoEnvioErp.FACTURADA_ERP);
    assertThat(envio.getDocumentoExterno()).isEqualTo("FV-1001");
  }

  /** El agente reintenta cuando no le llega la respuesta: repetir lo mismo no puede fallar. */
  @Test
  void repetirLaMismaConfirmacionNoHaceNada() throws Exception {
    EnvioErp envio = encolado("erp_4");
    envio.confirmar(ResultadoFacturacion.confirmado("FV-1002", null), AHORA);
    when(envios.porId("erp_4")).thenReturn(Optional.of(envio));

    servicio.registrarResultadoDelAgente("erp_4", ResultadoFacturacion.confirmado("FV-1002", null));

    verify(envios, never()).guardar(any());
  }

  /** Otro numero sobre una venta ya facturada serian dos documentos por una comida. */
  @Test
  void otroNumeroSobreUnaVentaFacturadaSeRechaza() throws Exception {
    EnvioErp envio = encolado("erp_5");
    envio.confirmar(ResultadoFacturacion.confirmado("FV-1003", null), AHORA);
    when(envios.porId("erp_5")).thenReturn(Optional.of(envio));

    assertThatThrownBy(
            () ->
                servicio.registrarResultadoDelAgente(
                    "erp_5", ResultadoFacturacion.confirmado("FV-9999", null)))
        .isInstanceOf(ReglaDeNegocioError.class);
    assertThat(envio.getDocumentoExterno()).isEqualTo("FV-1003");
  }

  @Test
  void unRechazoDelAgenteDevuelveLaVentaALaColaConSuMotivo() throws Exception {
    EnvioErp envio = encolado("erp_6");
    envio.reservarParaAgente(AHORA, java.time.Duration.ofMinutes(10));
    when(envios.porId("erp_6")).thenReturn(Optional.of(envio));

    servicio.registrarResultadoDelAgente(
        "erp_6", ResultadoFacturacion.rechazado("Globalsoft no responde", null));

    assertThat(envio.getEstado()).isEqualTo(EstadoEnvioErp.PENDIENTE_ENVIO_ERP);
    assertThat(envio.getError()).isEqualTo("Globalsoft no responde");
  }
}
