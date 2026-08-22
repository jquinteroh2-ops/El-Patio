package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioPublicaciones;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.publicacion.Publicacion;
import co.elpatio.dominio.publicacion.TipoPublicacion;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Promociones, eventos y fotos del local.
 *
 * Las lecturas de lo publicado quedan abiertas, igual que la carta: es lo que
 * el sitio le muestra a quien pase por la calle. Todo lo que escribe, y la
 * lista completa con los borradores, exige rol de administrador.
 */
@RestController
@RequestMapping("/api/publicaciones")
public class ControladorPublicaciones {

  private final ServicioPublicaciones servicio;

  public ControladorPublicaciones(ServicioPublicaciones servicio) {
    this.servicio = servicio;
  }

  /**
   * Lo que esta publicado y vigente hoy. Sin sesion.
   *
   * `tipo` es opcional: el sitio pide promociones para la portada y galeria
   * para la pagina del local, pero el que quiera todo lo tiene en una llamada.
   */
  @GetMapping("/visibles")
  public List<Publicacion> visibles(@RequestParam(required = false) TipoPublicacion tipo) {
    return servicio.visibles(tipo);
  }

  /** Todas, incluidos los borradores y lo vencido. Es la pantalla del dueno. */
  @GetMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public List<Publicacion> listar() {
    return servicio.listarTodas();
  }

  @PutMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Publicacion guardar(@RequestBody Publicacion publicacion) {
    return servicio.guardar(publicacion);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public ResponseEntity<Void> eliminar(@PathVariable String id) {
    servicio.eliminar(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Sube una foto y devuelve el nombre con que quedo.
   *
   * Se sube aparte de la publicacion a proposito: asi el dueno ve la foto antes
   * de guardar, y una publicacion a medio escribir no obliga a volver a subir
   * los cuatro megas desde el celular.
   */
  @PostMapping("/imagenes")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Map<String, String> subirImagen(@RequestParam("archivo") MultipartFile archivo)
      throws IOException {
    String nombre = servicio.guardarImagen(archivo.getOriginalFilename(), archivo.getBytes());
    return Map.of("imagen", nombre);
  }

  /**
   * Entrega la foto.
   *
   * Va con cache larga porque el nombre del archivo es unico e irrepetible: si
   * la foto cambia, cambia el nombre, asi que ninguna version vieja se queda
   * pegada en el navegador de nadie.
   */
  @GetMapping("/imagenes/{nombre}")
  public ResponseEntity<byte[]> verImagen(@PathVariable String nombre) {
    byte[] contenido = servicio.leerImagen(nombre);
    if (contenido.length == 0) {
      throw new NoEncontradoError("Esa imagen no existe");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(servicio.tipoDeContenido(nombre)))
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic())
        .body(contenido);
  }
}
