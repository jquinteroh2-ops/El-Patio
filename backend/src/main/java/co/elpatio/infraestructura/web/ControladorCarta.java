package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioCarta;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.carta.CategoriaCarta;
import co.elpatio.dominio.carta.ItemCarta;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La carta.
 *
 * Las lecturas quedan abiertas porque el sitio publico muestra el menu a quien
 * pase por la calle. Todo lo que escribe exige rol de administrador.
 */
@RestController
@RequestMapping("/api/carta")
public class ControladorCarta {

  private final ServicioCarta servicio;

  public ControladorCarta(ServicioCarta servicio) {
    this.servicio = servicio;
  }

  /** Equivale a `listarCategorias()`. */
  @GetMapping("/categorias")
  public List<CategoriaCarta> listarCategorias() {
    return servicio.listarCategorias();
  }

  /** Equivale a `listarCarta()`. */
  @GetMapping
  public List<ItemCarta> listarCarta() {
    return servicio.listarCarta();
  }

  /** Equivale a `cartaAgrupada(soloDisponibles)`. */
  @GetMapping("/agrupada")
  public List<Dtos.CategoriaConItems> cartaAgrupada(
      @RequestParam(defaultValue = "false") boolean soloDisponibles) {
    return servicio.cartaAgrupada(soloDisponibles);
  }

  /** Equivale a `cambiarDisponibilidad(itemId, disponible)`. */
  @PatchMapping("/{itemId}/disponibilidad")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COCINA')")
  public ResponseEntity<Void> cambiarDisponibilidad(
      @PathVariable String itemId, @RequestBody Dtos.CambioDisponibilidad cambio) {
    servicio.cambiarDisponibilidad(itemId, cambio.disponible());
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `guardarItemCarta(item)`. */
  @PutMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public ItemCarta guardarItemCarta(@RequestBody ItemCarta item) {
    return servicio.guardarItemCarta(item);
  }

  /** Equivale a `eliminarItemCarta(itemId)`. */
  @DeleteMapping("/{itemId}")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public ResponseEntity<Void> eliminarItemCarta(@PathVariable String itemId) {
    servicio.eliminarItemCarta(itemId);
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `guardarCategoria(categoria)`. */
  @PutMapping("/categorias")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public CategoriaCarta guardarCategoria(@RequestBody CategoriaCarta categoria) {
    return servicio.guardarCategoria(categoria);
  }
}
