package co.elpatio.dominio.reclutamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostulacionTest {

  private static final Instant AHORA = Instant.parse("2026-08-24T15:00:00Z");

  private static Postulacion recibir(boolean autoriza) {
    return Postulacion.recibir(
        "post_1",
        "Ana Pérez",
        TipoDocumento.CC,
        "1050123456",
        "Ana.Perez@Correo.com",
        "3001234567",
        CargoDeInteres.MESERO,
        "Tengo dos años de experiencia en salón.",
        "uuid.pdf",
        "hoja de vida.pdf",
        autoriza,
        "190.0.0.1",
        AHORA);
  }

  @Test
  void unaPostulacionNaceRecibidaYConSuEvidenciaDeAutorizacion() {
    Postulacion postulacion = recibir(true);

    assertThat(postulacion.getEstado()).isEqualTo(EstadoPostulacion.RECIBIDA);
    assertThat(postulacion.getFechaPostulacion()).isEqualTo(AHORA);
    assertThat(postulacion.isAutorizacionDatos()).isTrue();
    assertThat(postulacion.getAutorizacionFecha()).isEqualTo(AHORA);
    assertThat(postulacion.getAutorizacionIp()).isEqualTo("190.0.0.1");
  }

  /**
   * Sin autorizacion no hay postulacion.
   *
   * La regla vive en el agregado y no en el controlador a proposito: es una
   * obligacion de ley —Ley 1581 de 2012—, no una validacion de formulario.
   * Ponerla en el borde dejaria la puerta abierta a que otro camino de entrada
   * la salte sin que nadie lo note.
   */
  @Test
  void sinAutorizacionDeDatosNoSeRecibe() {
    assertThatThrownBy(() -> recibir(false))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("autorización");
  }

  /** El correo se normaliza: es el canal de respuesta y se busca por él. */
  @Test
  void elCorreoSeGuardaEnMinusculas() {
    assertThat(recibir(true).getEmail()).isEqualTo("ana.perez@correo.com");
  }

  @Test
  void rechazaLosCamposObligatoriosVacios() {
    assertThatThrownBy(
            () ->
                Postulacion.recibir(
                    "p", "  ", TipoDocumento.CC, "1", "a@b.co", "300", CargoDeInteres.CAJA,
                    null, "r.pdf", "n.pdf", true, "ip", AHORA))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("nombre");

    assertThatThrownBy(
            () ->
                Postulacion.recibir(
                    "p", "Ana", TipoDocumento.CC, "", "a@b.co", "300", CargoDeInteres.CAJA,
                    null, "r.pdf", "n.pdf", true, "ip", AHORA))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("identificación");
  }

  @Test
  void rechazaUnCorreoQueNoLoParece() {
    assertThatThrownBy(
            () ->
                Postulacion.recibir(
                    "p", "Ana", TipoDocumento.CC, "1", "sin-arroba", "300",
                    CargoDeInteres.CAJA, null, "r.pdf", "n.pdf", true, "ip", AHORA))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("correo");
  }

  /**
   * El mensaje se corta en vez de rechazarse.
   *
   * Que el servidor rechace un texto de 501 caracteres significa que alguien
   * pierde lo que escribio y tiene que volver a empezar. Cortar es mas amable y
   * no pierde nada que importe.
   */
  @Test
  void unMensajeMuyLargoSeCortaYNoSeRechaza() {
    String largo = "a".repeat(700);

    Postulacion postulacion =
        Postulacion.recibir(
            "p", "Ana", TipoDocumento.CC, "1", "a@b.co", "300", CargoDeInteres.CAJA,
            largo, "r.pdf", "n.pdf", true, "ip", AHORA);

    assertThat(postulacion.getMensaje()).hasSize(500);
  }

  @Test
  void unMensajeVacioQuedaEnNuloYNoEnCadenaVacia() {
    Postulacion postulacion =
        Postulacion.recibir(
            "p", "Ana", TipoDocumento.CC, "1", "a@b.co", "300", CargoDeInteres.CAJA,
            "   ", "r.pdf", "n.pdf", true, "ip", AHORA);

    assertThat(postulacion.getMensaje()).isNull();
  }

  @Test
  void cambiarDeEstadoActualizaLaMarcaDeTiempo() {
    Postulacion postulacion = recibir(true);
    Instant despues = AHORA.plusSeconds(3600);

    postulacion.cambiarEstado(EstadoPostulacion.CONTACTADO, despues);

    assertThat(postulacion.getEstado()).isEqualTo(EstadoPostulacion.CONTACTADO);
    assertThat(postulacion.getActualizadoEn()).isEqualTo(despues);
  }

  @Test
  void seleccionadoYDescartadoCierranLaPostulacion() {
    assertThat(EstadoPostulacion.RECIBIDA.estaAbierta()).isTrue();
    assertThat(EstadoPostulacion.EN_REVISION.estaAbierta()).isTrue();
    assertThat(EstadoPostulacion.CONTACTADO.estaAbierta()).isTrue();
    assertThat(EstadoPostulacion.SELECCIONADO.estaAbierta()).isFalse();
    assertThat(EstadoPostulacion.DESCARTADO.estaAbierta()).isFalse();
  }

  /** PEP y PPT existen porque en la Costa hay quien se identifica asi. */
  @Test
  void seAceptanLosDocumentosDePoblacionMigrante() {
    assertThat(TipoDocumento.de("pep")).isEqualTo(TipoDocumento.PEP);
    assertThat(TipoDocumento.de("PPT")).isEqualTo(TipoDocumento.PPT);
  }

  /** El filtro recorta el tamaño de pagina: pedir diez mil filas no ayuda. */
  @Test
  void elFiltroPoneUnTopeAlTamanoDePagina() {
    FiltroPostulaciones filtro =
        new FiltroPostulaciones(null, null, null, null, "  ", -5, 5000);

    assertThat(filtro.tamano()).isEqualTo(100);
    assertThat(filtro.pagina()).isZero();
    // Una busqueda en blanco no filtra: filtrar por cadena vacia no devolveria
    // nada y el usuario no entenderia por que.
    assertThat(filtro.busqueda()).isNull();
  }
}
