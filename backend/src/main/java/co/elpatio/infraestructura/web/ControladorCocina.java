package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioCocina;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.carta.Destino;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** La pantalla de cocina y la de barra. */
@RestController
@RequestMapping("/api/cocina")
@PreAuthorize("hasAnyRole('COCINA', 'ADMINISTRADOR')")
public class ControladorCocina {

  private final ServicioCocina servicio;

  public ControladorCocina(ServicioCocina servicio) {
    this.servicio = servicio;
  }

  /** Equivale a `comandasActivas(destino)`. */
  @GetMapping("/comandas")
  public List<Dtos.TurnoEnCocina> comandasActivas(@RequestParam Destino destino) {
    return servicio.comandasActivas(destino);
  }

  /** Equivale a `cambiarEstadoItem(ordenId, itemId, estado)`. */
  @PatchMapping("/ordenes/{ordenId}/items/{itemId}/estado")
  public ResponseEntity<Void> cambiarEstadoItem(
      @PathVariable String ordenId,
      @PathVariable String itemId,
      @RequestBody Dtos.PeticionEstadoItem peticion) {
    servicio.cambiarEstadoItem(ordenId, itemId, peticion.estado());
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `cambiarEstadoTurno(ordenId, turno, destino, estado)`. */
  @PatchMapping("/ordenes/{ordenId}/turnos/{turno}/estado")
  public ResponseEntity<Void> cambiarEstadoTurno(
      @PathVariable String ordenId,
      @PathVariable int turno,
      @RequestParam Destino destino,
      @RequestBody Dtos.PeticionEstadoTurno peticion) {
    servicio.cambiarEstadoTurno(ordenId, turno, destino, peticion.estado());
    return ResponseEntity.noContent().build();
  }
}
