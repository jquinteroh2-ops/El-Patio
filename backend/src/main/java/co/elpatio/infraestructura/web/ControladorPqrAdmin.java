package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioPqr;
import co.elpatio.dominio.pqr.EstadoPqr;
import co.elpatio.dominio.pqr.FiltroPqr;
import co.elpatio.dominio.pqr.SolicitudPqr;
import co.elpatio.dominio.pqr.TipoSolicitud;
import co.elpatio.dominio.reclutamiento.Pagina;
import co.elpatio.infraestructura.seguridad.ServicioTokens;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La bandeja de PQR.
 *
 * Exige ADMINISTRADOR entera. Una queja lleva el nombre, el correo y a veces el
 * telefono de un cliente, ademas de lo que ese cliente opina del servicio: no
 * es informacion que deba ver cualquiera con acceso al panel.
 */
@RestController
@RequestMapping("/api/admin/pqr")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ControladorPqrAdmin {

  private static final ZoneId ZONA = ZoneId.of("America/Bogota");

  private final ServicioPqr servicio;

  public ControladorPqrAdmin(ServicioPqr servicio) {
    this.servicio = servicio;
  }

  /**
   * La solicitud con lo que hace falta para priorizarla.
   *
   * El vencimiento se calcula al leer y no se guarda: depende de que dia es
   * hoy, y guardarlo obligaria a un job que recorriera la tabla cada
   * medianoche para mantenerlo al dia.
   */
  public record FilaPqr(
      String id,
      String radicado,
      TipoSolicitud tipo,
      String nombreCompleto,
      String email,
      String telefono,
      LocalDate fechaVisita,
      String asunto,
      String descripcion,
      boolean tieneAdjunto,
      EstadoPqr estado,
      java.time.Instant fechaRadicacion,
      LocalDate fechaLimiteRespuesta,
      java.time.Instant fechaRespuesta,
      String respuesta,
      String respondidoPor,
      String notasInternas,
      /** Negativo si ya se paso del plazo. */
      int diasHabilesRestantes,
      boolean vencida,
      boolean porVencer) {

    static FilaPqr de(SolicitudPqr s, LocalDate hoy) {
      return new FilaPqr(
          s.getId(),
          s.getRadicado(),
          s.getTipo(),
          s.getNombreCompleto(),
          s.getEmail(),
          s.getTelefono(),
          s.getFechaVisita(),
          s.getAsunto(),
          s.getDescripcion(),
          s.getAdjuntoRef() != null,
          s.getEstado(),
          s.getFechaRadicacion(),
          s.getFechaLimiteRespuesta(),
          s.getFechaRespuesta(),
          s.getRespuesta(),
          s.getRespondidoPor(),
          s.getNotasInternas(),
          s.diasHabilesRestantes(hoy),
          s.estaVencida(hoy),
          s.estaPorVencer(hoy, 3));
    }
  }

  @GetMapping
  public Pagina<FilaPqr> listar(
      @RequestParam(required = false) TipoSolicitud tipo,
      @RequestParam(required = false) EstadoPqr estado,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) String busqueda,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamano) {

    LocalDate hoy = LocalDate.now(ZONA);
    Pagina<SolicitudPqr> encontradas =
        servicio.buscar(new FiltroPqr(tipo, estado, desde, hasta, busqueda, pagina, tamano));

    return new Pagina<>(
        encontradas.contenido().stream().map(s -> FilaPqr.de(s, hoy)).toList(),
        encontradas.pagina(),
        encontradas.tamano(),
        encontradas.total());
  }

  /** Cuantas siguen abiertas. Es el contador del menu. */
  @GetMapping("/abiertas")
  public long abiertas() {
    return servicio.abiertas();
  }

  /** Las que vencen pronto o ya vencieron. Para el aviso del panel. */
  @GetMapping("/por-vencer")
  public List<FilaPqr> porVencer() {
    LocalDate hoy = LocalDate.now(ZONA);
    return servicio.porVencer().stream().map(s -> FilaPqr.de(s, hoy)).toList();
  }

  @GetMapping("/{id}")
  public FilaPqr detalle(@PathVariable String id) {
    return FilaPqr.de(servicio.porId(id), LocalDate.now(ZONA));
  }

  /** El adjunto que mandó el cliente. Como `attachment`, nunca en línea. */
  @GetMapping("/{id}/adjunto")
  public ResponseEntity<ByteArrayResource> adjunto(@PathVariable String id) {
    SolicitudPqr solicitud = servicio.porId(id);
    byte[] contenido = servicio.adjunto(id);
    String nombre = solicitud.getRadicado() + extensionDe(solicitud.getAdjuntoRef());

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(new ByteArrayResource(contenido));
  }

  public record CambioEstado(EstadoPqr estado) {}

  @PatchMapping("/{id}/estado")
  public FilaPqr cambiarEstado(@PathVariable String id, @RequestBody CambioEstado cambio) {
    return FilaPqr.de(servicio.cambiarEstado(id, cambio.estado()), LocalDate.now(ZONA));
  }

  public record Respuesta(String texto) {}

  /**
   * Registra la respuesta y se la manda al cliente.
   *
   * Quien responde sale del token y no del cuerpo: es el dato con el que
   * despues se sabe quien atendio una queja, y no puede venir del navegador.
   */
  @PostMapping("/{id}/respuesta")
  public FilaPqr responder(
      @PathVariable String id,
      @RequestBody Respuesta respuesta,
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    return FilaPqr.de(
        servicio.responder(id, respuesta.texto(), credencial.nombre()), LocalDate.now(ZONA));
  }

  public record CambioNotas(String notas) {}

  @PatchMapping("/{id}/notas")
  public FilaPqr anotar(@PathVariable String id, @RequestBody CambioNotas cambio) {
    return FilaPqr.de(servicio.anotar(id, cambio.notas()), LocalDate.now(ZONA));
  }

  /** La extensión con que se guardó, para que el archivo abra al descargarlo. */
  private String extensionDe(String referencia) {
    if (referencia == null) return "";
    int punto = referencia.lastIndexOf('.');
    return punto < 0 ? "" : referencia.substring(punto);
  }
}
