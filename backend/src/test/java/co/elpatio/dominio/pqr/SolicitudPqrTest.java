package co.elpatio.dominio.pqr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SolicitudPqrTest {

  /** Lunes 24 de agosto de 2026, 10 de la mañana en Colombia. */
  private static final Instant AHORA = Instant.parse("2026-08-24T15:00:00Z");
  private static final LocalDate DIA = LocalDate.of(2026, 8, 24);

  private static SolicitudPqr radicar(TipoSolicitud tipo, int plazo, boolean autoriza) {
    return SolicitudPqr.radicar(
        "pqr_1",
        new Radicado(2026, 47),
        tipo,
        "Ana Pérez",
        "Ana@Correo.com",
        "3001234567",
        LocalDate.of(2026, 8, 22),
        "El pedido llegó frío",
        "Pedí un róbalo al bijao y llegó frío después de una hora.",
        null,
        null,
        autoriza,
        "190.0.0.1",
        plazo,
        AHORA);
  }

  private static SolicitudPqr reclamo() {
    return radicar(TipoSolicitud.RECLAMO, 15, true);
  }

  // -------------------------------------------------------------------------
  // El radicado
  // -------------------------------------------------------------------------

  @Test
  void elRadicadoSeEscribeConElFormatoAcordado() {
    assertThat(new Radicado(2026, 47).toString()).isEqualTo("PQR-2026-00047");
    assertThat(new Radicado(2026, 1).toString()).isEqualTo("PQR-2026-00001");
    assertThat(new Radicado(2026, 99999).toString()).isEqualTo("PQR-2026-99999");
  }

  /** El cliente lo copia de un correo o lo escribe de memoria. */
  @Test
  void elRadicadoSeLeeAunqueVengaEnMinusculasOConEspacios() {
    assertThat(Radicado.de("  pqr-2026-00047 ")).isEqualTo(new Radicado(2026, 47));
    assertThat(Radicado.de("PQR-2026-00047").toString()).isEqualTo("PQR-2026-00047");
  }

  @Test
  void unRadicadoConOtroFormatoSeRechaza() {
    assertThatThrownBy(() -> Radicado.de("2026-47")).isInstanceOf(ReglaDeNegocioError.class);
    assertThatThrownBy(() -> Radicado.de("ABC-2026-00047")).isInstanceOf(ReglaDeNegocioError.class);
    assertThatThrownBy(() -> Radicado.de("PQR-XXXX-00047")).isInstanceOf(ReglaDeNegocioError.class);
    assertThatThrownBy(() -> Radicado.de(null)).isInstanceOf(ReglaDeNegocioError.class);
  }

  @Test
  void elConsecutivoEmpiezaEnUno() {
    assertThatThrownBy(() -> new Radicado(2026, 0)).isInstanceOf(ReglaDeNegocioError.class);
  }

  // -------------------------------------------------------------------------
  // El término
  // -------------------------------------------------------------------------

  /**
   * Quince dias habiles desde el lunes 24 de agosto de 2026.
   *
   * No son quince dias corridos: por el camino hay tres fines de semana. Se
   * comprueba contra el calendario, no contra el propio calculo.
   */
  @Test
  void laFechaLimiteSeCuentaEnDiasHabiles() {
    SolicitudPqr reclamo = reclamo();

    LocalDate limite = reclamo.getFechaLimiteRespuesta();
    assertThat(limite).isNotNull();
    assertThat(limite).isAfter(DIA.plusDays(15));
    assertThat(co.elpatio.dominio.calendario.FestivosColombia.diasHabilesEntre(DIA, limite))
        .isEqualTo(15);
  }

  /**
   * Una felicitacion no tiene termino.
   *
   * Agradecerla es cortesia, no obligacion. Contarle un vencimiento la pondria
   * en rojo en el panel compitiendo por atencion con un reclamo que si vence.
   */
  @Test
  void unaFelicitacionNoTieneFechaLimite() {
    SolicitudPqr felicitacion = radicar(TipoSolicitud.FELICITACION, 15, true);

    assertThat(felicitacion.getFechaLimiteRespuesta()).isNull();
    assertThat(felicitacion.estaVencida(DIA.plusYears(1))).isFalse();
    assertThat(felicitacion.estaPorVencer(DIA, 3)).isFalse();
  }

  @Test
  void lasDemasSiTienenTermino() {
    for (TipoSolicitud tipo :
        new TipoSolicitud[] {
          TipoSolicitud.PETICION, TipoSolicitud.QUEJA,
          TipoSolicitud.RECLAMO, TipoSolicitud.SUGERENCIA
        }) {
      assertThat(radicar(tipo, 15, true).getFechaLimiteRespuesta())
          .as("%s debe tener término", tipo)
          .isNotNull();
    }
  }

  @Test
  void pasadaLaFechaLimiteQuedaVencida() {
    SolicitudPqr reclamo = reclamo();
    LocalDate limite = reclamo.getFechaLimiteRespuesta();

    assertThat(reclamo.estaVencida(limite)).isFalse();
    assertThat(reclamo.estaVencida(limite.plusDays(1))).isTrue();
  }

  /**
   * Una vez resuelta, deja de vencer.
   *
   * Seguir contandole el plazo la dejaria en rojo para siempre y esconderia las
   * que de verdad estan por vencer, que es justo lo que la pantalla tiene que
   * dejar ver.
   */
  @Test
  void unaSolicitudResueltaYaNoVence() {
    SolicitudPqr reclamo = reclamo();
    LocalDate muyDespues = reclamo.getFechaLimiteRespuesta().plusDays(30);

    reclamo.responder("Le devolvemos el valor del plato.", "Ana", AHORA);

    assertThat(reclamo.estaVencida(muyDespues)).isFalse();
    assertThat(reclamo.estaPorVencer(muyDespues, 3)).isFalse();
  }

  @Test
  void avisaCuandoSeAcercaElVencimiento() {
    SolicitudPqr reclamo = reclamo();
    LocalDate limite = reclamo.getFechaLimiteRespuesta();

    assertThat(reclamo.estaPorVencer(DIA, 3)).isFalse();
    // Tres días hábiles antes del límite ya avisa.
    assertThat(reclamo.estaPorVencer(limite.minusDays(1), 3)).isTrue();
  }

  @Test
  void losDiasRestantesSonNegativosCuandoYaSePaso() {
    SolicitudPqr reclamo = reclamo();
    LocalDate limite = reclamo.getFechaLimiteRespuesta();

    assertThat(reclamo.diasHabilesRestantes(DIA)).isEqualTo(15);
    assertThat(reclamo.diasHabilesRestantes(limite)).isZero();
    assertThat(reclamo.diasHabilesRestantes(limite.plusDays(7))).isNegative();
  }

  /** El plazo viene de la configuracion, no de una constante. */
  @Test
  void elPlazoLoDecideLaConfiguracion() {
    SolicitudPqr corta = radicar(TipoSolicitud.QUEJA, 5, true);
    SolicitudPqr larga = radicar(TipoSolicitud.QUEJA, 30, true);

    assertThat(corta.getFechaLimiteRespuesta()).isBefore(larga.getFechaLimiteRespuesta());
  }

  // -------------------------------------------------------------------------
  // El cumplimiento
  // -------------------------------------------------------------------------

  @Test
  void responderDentroDelPlazoCuentaComoCumplido() {
    SolicitudPqr reclamo = reclamo();
    reclamo.responder("Resuelto.", "Ana", AHORA.plusSeconds(86400));

    assertThat(reclamo.cumplioElPlazo(DIA.plusDays(1))).isTrue();
  }

  /**
   * Sin respuesta y con plazo por delante, el cumplimiento es «todavia no se
   * sabe» y no «incumplio».
   *
   * Escribir «No» ahi seria acusar al restaurante de un incumplimiento que no
   * ha ocurrido, y el reporte se usa justo para lo contrario.
   */
  @Test
  void sinResponderYConPlazoPorDelanteNoSeAfirmaNada() {
    assertThat(reclamo().cumplioElPlazo(DIA)).isNull();
  }

  @Test
  void sinResponderYConElPlazoVencidoSiEsIncumplimiento() {
    SolicitudPqr reclamo = reclamo();
    LocalDate despues = reclamo.getFechaLimiteRespuesta().plusDays(1);

    assertThat(reclamo.cumplioElPlazo(despues)).isFalse();
  }

  /** Una felicitacion no tiene termino, asi que no se le mide cumplimiento. */
  @Test
  void unaFelicitacionNoTieneCumplimientoQueMedir() {
    assertThat(radicar(TipoSolicitud.FELICITACION, 15, true).cumplioElPlazo(DIA.plusYears(1)))
        .isNull();
  }

  /**
   * Responder por segunda vez no pisa la fecha de la primera.
   *
   * Esa fecha es la que demuestra que se contesto dentro del plazo; si una
   * correccion posterior la moviera, una solicitud atendida a tiempo pasaria a
   * figurar como incumplida.
   */
  @Test
  void ampliarLaRespuestaNoMueveLaFechaDeLaPrimera() {
    SolicitudPqr reclamo = reclamo();
    Instant primera = AHORA.plusSeconds(86400);
    reclamo.responder("Resuelto.", "Ana", primera);

    reclamo.responder("Ampliamos: le devolvemos el valor.", "Ana", primera.plusSeconds(86400 * 30L));

    assertThat(reclamo.getFechaRespuesta()).isEqualTo(primera);
    assertThat(reclamo.getRespuesta()).contains("Ampliamos");
  }

  // -------------------------------------------------------------------------
  // Reglas de radicación
  // -------------------------------------------------------------------------

  @Test
  void sinAutorizacionDeDatosNoSeRadica() {
    assertThatThrownBy(() -> radicar(TipoSolicitud.QUEJA, 15, false))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("autorización");
  }

  @Test
  void unaSolicitudNaceRadicadaYConSuEvidencia() {
    SolicitudPqr reclamo = reclamo();

    assertThat(reclamo.getEstado()).isEqualTo(EstadoPqr.RADICADA);
    assertThat(reclamo.getRadicado()).isEqualTo("PQR-2026-00047");
    assertThat(reclamo.isAutorizacionDatos()).isTrue();
    assertThat(reclamo.getAutorizacionFecha()).isEqualTo(AHORA);
    assertThat(reclamo.getAutorizacionIp()).isEqualTo("190.0.0.1");
    assertThat(reclamo.getEmail()).isEqualTo("ana@correo.com");
  }

  /**
   * No se puede dar por resuelta sin haber respondido.
   *
   * Es como se pierde el rastro de una queja: el tablero queda en verde y el
   * cliente sin contestacion.
   */
  @Test
  void noSePuedeMarcarResueltaSinRespuesta() {
    SolicitudPqr reclamo = reclamo();

    assertThatThrownBy(() -> reclamo.cambiarEstado(EstadoPqr.RESUELTA, AHORA))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("respuesta");

    // En trámite sí, que es lo que se marca mientras se averigua.
    reclamo.cambiarEstado(EstadoPqr.EN_TRAMITE, AHORA);
    assertThat(reclamo.getEstado()).isEqualTo(EstadoPqr.EN_TRAMITE);
  }

  @Test
  void responderDejaLaSolicitudResuelta() {
    SolicitudPqr reclamo = reclamo();

    reclamo.responder("Le devolvemos el valor del plato.", "Ana", AHORA);

    assertThat(reclamo.getEstado()).isEqualTo(EstadoPqr.RESUELTA);
    assertThat(reclamo.getRespondidoPor()).isEqualTo("Ana");
    assertThat(reclamo.getEstado().sigueCorriendo()).isFalse();
  }

  @Test
  void elAsuntoYLaDescripcionSeCortanEnVezDeRechazarse() {
    SolicitudPqr solicitud =
        SolicitudPqr.radicar(
            "p", new Radicado(2026, 1), TipoSolicitud.QUEJA, "Ana", "a@b.co", null, null,
            "a".repeat(300), "b".repeat(3000), null, null, true, "ip", 15, AHORA);

    assertThat(solicitud.getAsunto()).hasSize(120);
    assertThat(solicitud.getDescripcion()).hasSize(2000);
  }

  @Test
  void faltanCamposObligatorios() {
    assertThatThrownBy(
            () ->
                SolicitudPqr.radicar(
                    "p", new Radicado(2026, 1), TipoSolicitud.QUEJA, "Ana", "a@b.co", null, null,
                    "  ", "algo", null, null, true, "ip", 15, AHORA))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("asunto");

    assertThatThrownBy(
            () ->
                SolicitudPqr.radicar(
                    "p", new Radicado(2026, 1), TipoSolicitud.QUEJA, "Ana", "no-es-correo", null,
                    null, "Asunto", "algo", null, null, true, "ip", 15, AHORA))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("correo");
  }
}
