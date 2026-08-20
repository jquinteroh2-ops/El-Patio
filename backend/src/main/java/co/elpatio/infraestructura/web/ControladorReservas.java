package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioReservas;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.reserva.Reserva;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reservas.
 *
 * Crear una reserva queda abierto porque el formulario del sitio publico lo usa
 * sin sesion. Verlas y responderlas exige personal: son datos de contacto de
 * clientes.
 */
@RestController
@RequestMapping("/api/reservas")
public class ControladorReservas {

  private final ServicioReservas servicio;

  public ControladorReservas(ServicioReservas servicio) {
    this.servicio = servicio;
  }

  /** Equivale a `listarReservas()`. */
  @GetMapping
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR')")
  public List<Reserva> listarReservas() {
    return servicio.listarReservas();
  }

  /** Equivale a `crearReserva(datos)`. */
  @PostMapping
  public Reserva crearReserva(@RequestBody Dtos.NuevaReserva datos) {
    return servicio.crearReserva(datos);
  }

  /** Equivale a `cambiarEstadoReserva(reservaId, estado, mesaAsignadaId)`. */
  @PatchMapping("/{reservaId}/estado")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR')")
  public Reserva cambiarEstado(
      @PathVariable String reservaId, @RequestBody Dtos.CambioEstadoReserva cambio) {
    return servicio.cambiarEstadoReserva(reservaId, cambio.estado(), cambio.mesaAsignadaId());
  }

  /** Equivale a `reprogramarReserva(reservaId, fechaHora)`. */
  @PatchMapping("/{reservaId}/fecha")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR')")
  public ResponseEntity<Void> reprogramar(
      @PathVariable String reservaId, @RequestBody Dtos.PeticionReprogramar peticion) {
    servicio.reprogramarReserva(reservaId, peticion.fechaHora());
    return ResponseEntity.noContent().build();
  }
}
