package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioCarta;
import co.elpatio.aplicacion.ServicioCocina;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.carta.Destino;
import co.elpatio.infraestructura.seguridad.ServicioTokens;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@PreAuthorize("hasAnyRole('COCINA', 'BAR', 'ADMINISTRADOR')")
public class ControladorCocina {

  /**
   * La pantalla que le toca a cada rol: cocina solo platos, bar solo bebidas.
   * El administrador ve las dos; para el, null significa sin limite.
   */
  private static Destino destinoDe(ServicioTokens.Credencial credencial) {
    return switch (credencial.rol()) {
      case COCINA -> Destino.COCINA;
      case BAR -> Destino.BAR;
      default -> null;
    };
  }

  private static void exigirDestino(ServicioTokens.Credencial credencial, Destino destino) {
    Destino propio = destinoDe(credencial);
    if (propio != null && propio != destino) {
      throw new AccessDeniedException("Esta pantalla no le corresponde a su rol");
    }
  }

  private final ServicioCocina servicio;
  private final ServicioCarta carta;

  public ControladorCocina(ServicioCocina servicio, ServicioCarta carta) {
    this.servicio = servicio;
    this.carta = carta;
  }

  /**
   * Equivale a `cambiarTiempoPlato(itemCartaId, minutos)`.
   *
   * Va bajo /api/cocina y no bajo /api/carta porque la carta solo la escribe
   * el administrador, y este cambio lo hace el cocinero.
   */
  @PatchMapping("/platos/{itemCartaId}/tiempo")
  public ResponseEntity<Void> cambiarTiempoPlato(
      @PathVariable String itemCartaId, @RequestBody Dtos.PeticionTiempoPlato peticion) {
    carta.cambiarTiempoPreparacion(itemCartaId, peticion.minutos());
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `comandasActivas(destino)`. */
  @GetMapping("/comandas")
  public List<Dtos.TurnoEnCocina> comandasActivas(
      @RequestParam Destino destino, @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    exigirDestino(credencial, destino);
    return servicio.comandasActivas(destino);
  }

  /** Equivale a `cambiarEstadoItem(ordenId, itemId, estado)`. */
  @PatchMapping("/ordenes/{ordenId}/items/{itemId}/estado")
  public ResponseEntity<Void> cambiarEstadoItem(
      @PathVariable String ordenId,
      @PathVariable String itemId,
      @RequestBody Dtos.PeticionEstadoItem peticion,
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    servicio.cambiarEstadoItem(ordenId, itemId, peticion.estado(), destinoDe(credencial));
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `cambiarEstadoTurno(ordenId, turno, destino, estado)`. */
  @PatchMapping("/ordenes/{ordenId}/turnos/{turno}/estado")
  public ResponseEntity<Void> cambiarEstadoTurno(
      @PathVariable String ordenId,
      @PathVariable int turno,
      @RequestParam Destino destino,
      @RequestBody Dtos.PeticionEstadoTurno peticion,
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    exigirDestino(credencial, destino);
    servicio.cambiarEstadoTurno(ordenId, turno, destino, peticion.estado());
    return ResponseEntity.noContent().build();
  }
}
