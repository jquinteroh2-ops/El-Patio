package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioPostulaciones;
import co.elpatio.dominio.reclutamiento.CargoDeInteres;
import co.elpatio.dominio.reclutamiento.EstadoPostulacion;
import co.elpatio.dominio.reclutamiento.FiltroPostulaciones;
import co.elpatio.dominio.reclutamiento.Pagina;
import co.elpatio.dominio.reclutamiento.Postulacion;
import java.time.LocalDate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La bandeja de hojas de vida.
 *
 * Todo aqui exige ADMINISTRADOR, incluida la descarga del PDF. Una hoja de vida
 * lleva cedula, telefono y direccion de una persona que confio en el
 * restaurante: no puede quedar detras de una URL que se adivine, ni al alcance
 * de un cajero.
 */
@RestController
@RequestMapping("/api/admin/postulaciones")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ControladorPostulacionesAdmin {

  private final ServicioPostulaciones servicio;

  public ControladorPostulacionesAdmin(ServicioPostulaciones servicio) {
    this.servicio = servicio;
  }

  @GetMapping
  public Pagina<Postulacion> listar(
      @RequestParam(required = false) EstadoPostulacion estado,
      @RequestParam(required = false) CargoDeInteres cargo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) String busqueda,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamano) {

    return servicio.buscar(
        new FiltroPostulaciones(estado, cargo, desde, hasta, busqueda, pagina, tamano));
  }

  /** El contador de nuevas, para la insignia del menu. */
  @GetMapping("/sin-revisar")
  public long sinRevisar() {
    return servicio.sinRevisar();
  }

  @GetMapping("/{id}")
  public Postulacion detalle(@PathVariable String id) {
    return servicio.porId(id);
  }

  /**
   * Descarga la hoja de vida.
   *
   * Va por este endpoint autenticado y no por una URL publica del volumen: el
   * archivo tiene un nombre de UUID, pero «dificil de adivinar» no es lo mismo
   * que «protegido», y basta que alguien comparta un enlace para que deje de
   * estarlo.
   *
   * Sale como `attachment` a proposito. Servido en linea, el navegador abriria
   * el PDF dentro del sitio, y un PDF puede llevar contenido activo: que se
   * descargue y se abra fuera es una molestia pequeña a cambio de cerrar eso.
   */
  @GetMapping("/{id}/hoja-de-vida")
  public ResponseEntity<ByteArrayResource> hojaDeVida(@PathVariable String id) {
    Postulacion postulacion = servicio.porId(id);
    byte[] contenido = servicio.hojaDeVida(id);

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + nombreDescarga(postulacion) + "\"")
        // Una hoja de vida no se guarda en la caché de un proxy compartido.
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(new ByteArrayResource(contenido));
  }

  public record CambioEstado(EstadoPostulacion estado) {}

  @PatchMapping("/{id}/estado")
  public Postulacion cambiarEstado(@PathVariable String id, @RequestBody CambioEstado cambio) {
    return servicio.cambiarEstado(id, cambio.estado());
  }

  public record CambioNotas(String notas) {}

  @PatchMapping("/{id}/notas")
  public Postulacion anotar(@PathVariable String id, @RequestBody CambioNotas cambio) {
    return servicio.anotar(id, cambio.notas());
  }

  /**
   * Borra la postulacion y su archivo.
   *
   * Es el derecho de supresion de la Ley 1581, no una limpieza de bandeja: el
   * PDF desaparece del disco. No hay papelera y no debe haberla, porque una
   * papelera es precisamente lo que hace que un dato «borrado» siga existiendo.
   */
  @DeleteMapping("/{id}")
  public void eliminar(@PathVariable String id) {
    servicio.eliminar(id);
  }

  /**
   * Un nombre de archivo con el que se pueda trabajar.
   *
   * Se arma con el nombre de la persona y no con el original que subio: los
   * nombres que trae la gente van desde «CV FINAL final(2).pdf» hasta cadenas
   * con caracteres que rompen la descarga en Windows. Se limpia todo lo que no
   * sea letra, numero, espacio o guion.
   */
  private String nombreDescarga(Postulacion postulacion) {
    String limpio =
        postulacion.getNombreCompleto().replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
    if (limpio.isEmpty()) limpio = "hoja-de-vida";
    return "HV " + limpio + ".pdf";
  }
}
