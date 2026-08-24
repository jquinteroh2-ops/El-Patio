package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioPqr;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.pqr.SolicitudPqr;
import co.elpatio.dominio.pqr.TipoSolicitud;
import co.elpatio.infraestructura.seguridad.LimitadorDePeticiones;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * El canal de PQR, abierto a cualquier cliente.
 *
 * Publico porque tiene que serlo: un canal de quejas que exige crear una cuenta
 * no es un canal de quejas. Lo defienden el limite por IP y el señuelo, igual
 * que el formulario de postulaciones, y lo defiende de forma distinta la
 * consulta: exigir radicado Y correo es lo que impide recorrer los numeros en
 * orden y leer las quejas de todo el mundo.
 */
@RestController
@RequestMapping("/api/public/pqr")
public class ControladorPqrPublico {

  private static final Logger registro = LoggerFactory.getLogger(ControladorPqrPublico.class);

  /** Radicar cuesta trabajo: tres por ventana es holgado para una persona. */
  private static final int MAXIMO_RADICACIONES = 3;

  /**
   * Consultar es barato, y ahi esta el riesgo.
   *
   * Sin limite, alguien podria probar radicados y correos en serie hasta dar
   * con una combinacion valida. Diez por ventana deja consultar con calma —y
   * equivocarse escribiendo— y cierra la puerta a recorrerlos en masa.
   */
  private static final int MAXIMO_CONSULTAS = 10;

  private final ServicioPqr servicio;
  private final LimitadorDePeticiones limitador;

  public ControladorPqrPublico(ServicioPqr servicio, LimitadorDePeticiones limitador) {
    this.servicio = servicio;
    this.limitador = limitador;
  }

  public record TipoDto(String id, String etiqueta) {}

  /** Los tipos y el plazo, para que el formulario no los tenga escritos aparte. */
  @GetMapping("/tipos")
  public Configuracion tipos() {
    return new Configuracion(
        Arrays.stream(TipoSolicitud.values())
            .map(t -> new TipoDto(t.codigo(), t.etiqueta()))
            .toList(),
        servicio.plazoEnDiasHabiles());
  }

  public record Configuracion(List<TipoDto> tipos, int diasHabilesDeRespuesta) {}

  /** Lo que se le responde a quien acaba de radicar: su numero. */
  public record Radicada(String radicado, LocalDate fechaLimiteRespuesta, String mensaje) {}

  @PostMapping(consumes = "multipart/form-data")
  public Radicada radicar(
      @RequestParam String tipo,
      @RequestParam String nombreCompleto,
      @RequestParam String email,
      @RequestParam(required = false) String telefono,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fechaVisita,
      @RequestParam String asunto,
      @RequestParam String descripcion,
      @RequestParam(defaultValue = "false") boolean autorizacionDatos,
      /** El señuelo. Ver `ControladorPostulacionesPublico`. */
      @RequestParam(required = false) String sitioWeb,
      @RequestParam(required = false) MultipartFile adjunto,
      HttpServletRequest peticion)
      throws IOException {

    String ip = ControladorPostulacionesPublico.ipDe(peticion);

    // Al robot se le responde algo que parece bien y no se guarda nada. Lleva un
    // radicado falso a propósito: uno vacío delataría la trampa igual de rápido.
    if (sitioWeb != null && !sitioWeb.isBlank()) {
      registro.info("PQR descartada por el señuelo desde {}", ip);
      return new Radicada("PQR-0000-00000", null, "Recibimos su solicitud.");
    }

    String clave = "pqr:" + ip;
    if (!limitador.permite(clave, MAXIMO_RADICACIONES)) {
      long minutos = limitador.minutosParaReintentar(clave);
      throw new ReglaDeNegocioError(
          "Ya recibimos varias solicitudes desde este dispositivo. "
              + "Intente de nuevo en " + Math.max(1, minutos) + " minutos.");
    }

    SolicitudPqr solicitud =
        servicio.radicar(
            new ServicioPqr.DatosPqr(
                ServicioPqr.tipoDe(tipo),
                nombreCompleto,
                email,
                telefono,
                fechaVisita,
                asunto,
                descripcion,
                autorizacionDatos,
                ip,
                adjunto == null || adjunto.isEmpty() ? null : adjunto.getOriginalFilename(),
                adjunto == null || adjunto.isEmpty() ? null : adjunto.getBytes()));

    return new Radicada(
        solicitud.getRadicado(),
        solicitud.getFechaLimiteRespuesta(),
        "Guarde este número: con él y con su correo puede consultar el estado de su solicitud.");
  }

  /**
   * Consulta el estado con radicado y correo.
   *
   * Las dos cosas, siempre. Y con limite propio: consultar es barato y sin
   * freno alguien podria recorrer los radicados en serie hasta dar con uno.
   */
  @GetMapping("/consulta")
  public ServicioPqr.ConsultaPublica consultar(
      @RequestParam String radicado, @RequestParam String email, HttpServletRequest peticion) {

    String clave = "pqr-consulta:" + ControladorPostulacionesPublico.ipDe(peticion);
    if (!limitador.permite(clave, MAXIMO_CONSULTAS)) {
      long minutos = limitador.minutosParaReintentar(clave);
      throw new ReglaDeNegocioError(
          "Demasiadas consultas seguidas. Intente de nuevo en "
              + Math.max(1, minutos) + " minutos.");
    }

    return servicio.consultar(radicado, email);
  }
}
