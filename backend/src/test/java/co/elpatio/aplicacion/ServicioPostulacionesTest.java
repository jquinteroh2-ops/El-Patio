package co.elpatio.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.AlmacenDeDocumentos;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.NotificadorPorCorreo;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.reclutamiento.CargoDeInteres;
import co.elpatio.dominio.reclutamiento.EstadoPostulacion;
import co.elpatio.dominio.reclutamiento.FiltroPostulaciones;
import co.elpatio.dominio.reclutamiento.Pagina;
import co.elpatio.dominio.reclutamiento.Postulacion;
import co.elpatio.dominio.reclutamiento.TipoDocumento;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El flujo completo de una postulacion, de punta a punta.
 *
 * Va contra dobles en memoria y no contra Postgres a proposito: lo que se
 * quiere comprobar aqui es el ORDEN de las cosas —cuando se escribe el archivo,
 * cuando se borra, que pasa si algo falla en medio—, y eso no depende del motor
 * de base de datos. Los dobles hacen visible lo que una base real escondería.
 */
class ServicioPostulacionesTest {

  private static final Instant AHORA = Instant.parse("2026-08-24T15:00:00Z");
  private static final byte[] PDF = "%PDF-1.7\nhoja de vida".getBytes(StandardCharsets.US_ASCII);

  private AlmacenFalso almacen;
  private RepositorioFalso repositorio;
  private CorreoFalso correo;
  private ServicioPostulaciones servicio;

  @BeforeEach
  void preparar() {
    almacen = new AlmacenFalso();
    repositorio = new RepositorioFalso();
    correo = new CorreoFalso();
    servicio =
        new ServicioPostulaciones(
            repositorio, almacen, correo, new IdsFalsos(), new RelojFijo(), "jefe@elpatio.co", 30);
  }

  private ServicioPostulaciones.DatosPostulacion datos(byte[] archivo, String documento) {
    return new ServicioPostulaciones.DatosPostulacion(
        "Ana Pérez",
        TipoDocumento.CC,
        documento,
        "ana@correo.com",
        "3001234567",
        CargoDeInteres.MESERO,
        "Dos años en salón.",
        true,
        "190.0.0.1",
        "hoja de vida.pdf",
        archivo);
  }

  // -------------------------------------------------------------------------

  @Test
  void elFlujoCompletoGuardaLaPostulacionYSuArchivo() {
    Postulacion recibida = servicio.recibir(datos(PDF, "1050123456"));

    assertThat(recibida.getEstado()).isEqualTo(EstadoPostulacion.RECIBIDA);
    assertThat(recibida.getNombreCompleto()).isEqualTo("Ana Pérez");
    // El archivo quedó guardado y la fila apunta a él.
    assertThat(almacen.guardados).hasSize(1);
    assertThat(almacen.existe(recibida.getHojaDeVidaRef())).isTrue();
    assertThat(repositorio.porId(recibida.getId())).isPresent();
    // Y se avisó al administrador.
    assertThat(correo.enviados).hasSize(1);
    assertThat(correo.enviados.get(0)).contains("jefe@elpatio.co");
  }

  /**
   * El caso que el encargo pide expresamente: un archivo que no es PDF.
   *
   * Lo importante no es solo que se rechace, sino que NO quede nada escrito: ni
   * fila ni archivo. Un rechazo que deja basura en el volumen convierte cada
   * intento fallido en un archivo huerfano que nadie va a limpiar.
   */
  @Test
  void unArchivoQueNoEsPdfSeRechazaSinDejarRastro() {
    byte[] ejecutable = "MZ este no es un pdf".getBytes(StandardCharsets.ISO_8859_1);

    assertThatThrownBy(() -> servicio.recibir(datos(ejecutable, "1050123456")))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("PDF");

    assertThat(almacen.guardados).isEmpty();
    assertThat(repositorio.todas()).isEmpty();
  }

  /** Un JPEG renombrado a .pdf tampoco pasa: se mira el contenido, no el nombre. */
  @Test
  void unaImagenRenombradaAPdfTampocoPasa() {
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};

    assertThatThrownBy(() -> servicio.recibir(datos(jpeg, "1050123456")))
        .isInstanceOf(ReglaDeNegocioError.class);

    assertThat(almacen.guardados).isEmpty();
  }

  @Test
  void sinArchivoNoHayPostulacion() {
    assertThatThrownBy(() -> servicio.recibir(datos(new byte[0], "1050123456")))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("hoja de vida");
  }

  @Test
  void sinAutorizacionDeDatosNoSeRecibeYNoQuedaElArchivo() {
    var sinAutorizar =
        new ServicioPostulaciones.DatosPostulacion(
            "Ana", TipoDocumento.CC, "1", "a@b.co", "300", CargoDeInteres.CAJA, null,
            false, "ip", "hv.pdf", PDF);

    assertThatThrownBy(() -> servicio.recibir(sinAutorizar))
        .isInstanceOf(ReglaDeNegocioError.class);

    // El archivo alcanzó a escribirse y el servicio lo deshizo: si no, quedaría
    // un PDF con datos de alguien que nunca llegó a registrarse.
    assertThat(almacen.borrados).hasSize(1);
    assertThat(almacen.existe(almacen.borrados.get(0))).isFalse();
    assertThat(repositorio.todas()).isEmpty();
  }

  /**
   * El mismo documento dos veces seguidas no entra dos veces.
   *
   * No es sospecha de fraude: la gente vuelve a pulsar el boton cuando la
   * pagina tarda, y la bandeja termina con la misma hoja de vida cinco veces.
   */
  @Test
  void elMismoDocumentoNoSePuedeRepetirDentroDeLaVentana() {
    servicio.recibir(datos(PDF, "1050123456"));

    assertThatThrownBy(() -> servicio.recibir(datos(PDF, "1050123456")))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("Ya recibimos");

    assertThat(repositorio.todas()).hasSize(1);
    // Y el segundo intento no dejó un archivo suelto.
    assertThat(almacen.guardados).hasSize(1);
  }

  @Test
  void otroDocumentoSiEntra() {
    servicio.recibir(datos(PDF, "1050123456"));
    servicio.recibir(datos(PDF, "1050999999"));

    assertThat(repositorio.todas()).hasSize(2);
  }

  /**
   * Que el correo falle no puede perder la postulacion.
   *
   * Es la regla del puerto: un aviso que no sale es un problema; que por eso se
   * pierda la hoja de vida de quien lleno el formulario es otro mucho peor.
   */
  @Test
  void siElCorreoFallaLaPostulacionSeGuardaIgual() {
    correo.reventar = true;

    Postulacion recibida = servicio.recibir(datos(PDF, "1050123456"));

    assertThat(repositorio.porId(recibida.getId())).isPresent();
  }

  // -------------------------------------------------------------------------

  @Test
  void cambiarEstadoYAnotarQuedanGuardados() {
    Postulacion recibida = servicio.recibir(datos(PDF, "1050123456"));

    servicio.cambiarEstado(recibida.getId(), EstadoPostulacion.CONTACTADO);
    servicio.anotar(recibida.getId(), "Se le llamó el martes.");

    Postulacion despues = servicio.porId(recibida.getId());
    assertThat(despues.getEstado()).isEqualTo(EstadoPostulacion.CONTACTADO);
    assertThat(despues.getNotasInternas()).isEqualTo("Se le llamó el martes.");
  }

  /**
   * Eliminar borra la fila Y el archivo.
   *
   * Es el derecho de supresion de la Ley 1581: si el PDF sobreviviera al
   * borrado de la fila, se le habria dicho al titular que sus datos se
   * eliminaron sin que fuera cierto, y ademas quedaria imposible de encontrar.
   */
  @Test
  void eliminarBorraTambienElArchivoDelDisco() {
    Postulacion recibida = servicio.recibir(datos(PDF, "1050123456"));
    String referencia = recibida.getHojaDeVidaRef();

    servicio.eliminar(recibida.getId());

    assertThat(repositorio.todas()).isEmpty();
    assertThat(almacen.existe(referencia)).isFalse();
  }

  @Test
  void pedirAlgoQueNoExisteDaNoEncontrado() {
    assertThatThrownBy(() -> servicio.porId("no-existe"))
        .isInstanceOf(NoEncontradoError.class);
  }

  /**
   * Si el volumen se perdio, se dice tal cual.
   *
   * Devolver un PDF vacio dejaria al administrador pensando que el archivo esta
   * corrupto en vez de que ya no esta.
   */
  @Test
  void siElArchivoDesaparecioDelDiscoSeDiceQueNoEstaDisponible() {
    Postulacion recibida = servicio.recibir(datos(PDF, "1050123456"));
    almacen.borrar(recibida.getHojaDeVidaRef());

    assertThatThrownBy(() -> servicio.hojaDeVida(recibida.getId()))
        .isInstanceOf(NoEncontradoError.class)
        .hasMessageContaining("disponible");
  }

  @Test
  void contarLasSinRevisar() {
    servicio.recibir(datos(PDF, "1"));
    Postulacion segunda = servicio.recibir(datos(PDF, "2"));
    servicio.cambiarEstado(segunda.getId(), EstadoPostulacion.DESCARTADO);

    assertThat(servicio.sinRevisar()).isEqualTo(1);
  }

  // -------------------------------------------------------------------------
  // Dobles
  // -------------------------------------------------------------------------

  /** Un almacen que anota lo que se le pide, para poder comprobar el orden. */
  private static final class AlmacenFalso implements AlmacenDeDocumentos {
    final Map<String, byte[]> archivos = new HashMap<>();
    final List<String> guardados = new ArrayList<>();
    final List<String> borrados = new ArrayList<>();

    @Override
    public String guardar(String nombreOriginal, byte[] contenido) {
      String referencia = UUID.randomUUID() + ".pdf";
      archivos.put(referencia, contenido);
      guardados.add(referencia);
      return referencia;
    }

    @Override
    public byte[] leer(String referencia) {
      return archivos.getOrDefault(referencia, new byte[0]);
    }

    @Override
    public boolean existe(String referencia) {
      return archivos.containsKey(referencia);
    }

    @Override
    public void borrar(String referencia) {
      archivos.remove(referencia);
      borrados.add(referencia);
    }

    @Override
    public String tipoDeContenido(String referencia) {
      return "application/pdf";
    }
  }

  private static final class RepositorioFalso implements Repositorios.DePostulaciones {
    private final Map<String, Postulacion> filas = new HashMap<>();

    List<Postulacion> todas() {
      return new ArrayList<>(filas.values());
    }

    @Override
    public Optional<Postulacion> porId(String id) {
      return Optional.ofNullable(filas.get(id));
    }

    @Override
    public Pagina<Postulacion> buscar(FiltroPostulaciones filtro) {
      List<Postulacion> encontradas =
          filas.values().stream()
              .filter(p -> filtro.estado() == null || p.getEstado() == filtro.estado())
              .filter(p -> filtro.cargo() == null || p.getCargoInteres() == filtro.cargo())
              .sorted(Comparator.comparing(Postulacion::getFechaPostulacion).reversed())
              .toList();
      return new Pagina<>(encontradas, filtro.pagina(), filtro.tamano(), encontradas.size());
    }

    @Override
    public long sinRevisar() {
      return filas.values().stream().filter(p -> p.getEstado() == EstadoPostulacion.RECIBIDA).count();
    }

    @Override
    public List<Postulacion> delDocumentoDesde(String numeroDocumento, Instant desde) {
      return filas.values().stream()
          .filter(p -> p.getNumeroDocumento().equals(numeroDocumento))
          .filter(p -> p.getFechaPostulacion().isAfter(desde))
          .toList();
    }

    @Override
    public List<Postulacion> entre(Instant desde, Instant hasta) {
      return filas.values().stream()
          .filter(p -> !p.getFechaPostulacion().isBefore(desde))
          .filter(p -> p.getFechaPostulacion().isBefore(hasta))
          .toList();
    }

    @Override
    public Postulacion guardar(Postulacion postulacion) {
      filas.put(postulacion.getId(), postulacion);
      return postulacion;
    }

    @Override
    public void eliminar(String id) {
      filas.remove(id);
    }
  }

  private static final class CorreoFalso implements NotificadorPorCorreo {
    final List<String> enviados = new ArrayList<>();
    boolean reventar = false;

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
      if (reventar) throw new IllegalStateException("el proveedor no responde");
      enviados.add(destinatario + " | " + asunto);
    }

    @Override
    public boolean estaActivo() {
      return true;
    }
  }

  private static final class IdsFalsos implements GeneradorIds {
    private final AtomicInteger contador = new AtomicInteger();

    @Override
    public String nuevo(String prefijo) {
      return prefijo + "_" + contador.incrementAndGet();
    }
  }

  private static final class RelojFijo implements Reloj {
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @Override
    public Instant ahora() {
      return AHORA;
    }

    @Override
    public LocalDate hoy() {
      return AHORA.atZone(ZONA).toLocalDate();
    }

    @Override
    public LocalDate diaDe(Instant instante) {
      return instante.atZone(ZONA).toLocalDate();
    }

    @Override
    public LocalTime horaDe(Instant instante) {
      return instante.atZone(ZONA).toLocalTime();
    }

    @Override
    public Instant inicioDelDia(LocalDate dia) {
      return dia.atStartOfDay(ZONA).toInstant();
    }
  }
}
