package co.elpatio.infraestructura.web;

import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.institucional.ContenidoInstitucional;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El texto institucional: quienes somos, mision, vision, valores.
 *
 * La lectura de lo visible es publica —es contenido de la pagina de inicio— y
 * la edicion exige administrador. Las dos cosas viven en el mismo controlador
 * porque son cuatro metodos y separarlas en dos archivos escondería que la
 * diferencia entre ellas es exactamente una anotacion.
 */
@RestController
@RequestMapping("/api")
public class ControladorInstitucional {

  private final Repositorios.DeContenidoInstitucional contenidos;
  private final Reloj reloj;
  private final PublicadorEventos eventos;

  public ControladorInstitucional(
      Repositorios.DeContenidoInstitucional contenidos, Reloj reloj, PublicadorEventos eventos) {
    this.contenidos = contenidos;
    this.reloj = reloj;
    this.eventos = eventos;
  }

  /** Lo que se pinta en el sitio. Publico y solo lo visible. */
  @GetMapping("/institucional")
  @Transactional(readOnly = true)
  public List<ContenidoInstitucional> visibles() {
    return contenidos.visibles();
  }

  /** Todos, incluidos los ocultos. Es lo que edita el dueño. */
  @GetMapping("/admin/institucional")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Transactional(readOnly = true)
  public List<ContenidoInstitucional> todos() {
    return contenidos.listar();
  }

  public record Edicion(String titulo, String cuerpo, boolean visible) {}

  /**
   * Cambia el texto de una seccion.
   *
   * Solo puede editar claves que ya existen: las secciones las define una
   * migracion, no el formulario. Aceptar claves nuevas dejaria que alguien
   * creara bloques que el sitio no sabe donde pintar.
   */
  @PutMapping("/admin/institucional/{clave}")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Transactional
  public ContenidoInstitucional editar(
      @PathVariable String clave, @RequestBody Edicion edicion) {

    ContenidoInstitucional contenido =
        contenidos
            .porClave(clave)
            .orElseThrow(() -> new NoEncontradoError("Esa sección no existe"));

    contenido.editar(edicion.titulo(), edicion.cuerpo(), edicion.visible(), reloj.ahora());
    ContenidoInstitucional guardado = contenidos.guardar(contenido);

    // El sitio público lo lee sin recargar: sin este aviso, el dueño corrige su
    // misión y no la ve cambiada hasta refrescar, y cree que no se guardó.
    eventos.publicar(List.of("institucional"));
    return guardado;
  }
}
