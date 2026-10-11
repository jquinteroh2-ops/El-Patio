package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioReservas;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.reserva.Reserva;
import co.elpatio.infraestructura.seguridad.LimitadorDePeticiones;
import jakarta.servlet.http.HttpServletRequest;
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
 *
 * Quien responde es recepcion, que es el mostrador que ya atiende el telefono y
 * los domicilios; caja y administracion entran tambien porque una solicitud sin
 * responder no puede quedarse esperando a que llegue el turno de recepcion.
 */
@RestController
@RequestMapping("/api/reservas")
public class ControladorReservas {

  /**
   * Cuantas reservas admite una IP desde el sitio publico por ventana.
   *
   * Cinco cubre a quien reserva para dos fechas o se equivoca y la repite, y
   * corta al robot que llenaria la pantalla de recepcion de reservas falsas.
   */
  private static final int MAXIMO_RESERVAS_WEB = 5;

  private final ServicioReservas servicio;
  private final LimitadorDePeticiones limitador;

  public ControladorReservas(ServicioReservas servicio, LimitadorDePeticiones limitador) {
    this.servicio = servicio;
    this.limitador = limitador;
  }

  /** Equivale a `listarReservas()`. */
  @GetMapping
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public List<Reserva> listarReservas() {
    return servicio.listarReservas();
  }

  /** Equivale a `crearReserva(datos)`. */
  @PostMapping
  public Reserva crearReserva(
      @RequestBody Dtos.NuevaReserva datos, HttpServletRequest peticion) {
    String clave = "reservas:" + ControladorPostulacionesPublico.ipDe(peticion);
    if (!limitador.permite(clave, MAXIMO_RESERVAS_WEB)) {
      long minutos = limitador.minutosParaReintentar(clave);
      throw new ReglaDeNegocioError(
          "Ya recibimos varias reservas desde este dispositivo. Intente de nuevo en "
              + Math.max(1, minutos) + " minutos o escríbanos por WhatsApp.");
    }
    return servicio.crearReserva(datos);
  }

  /**
   * La reserva que anota el personal: la que pidieron por WhatsApp, por
   * telefono o de pie en la puerta.
   *
   * Va por su propia ruta y no por la de arriba porque puede nacer confirmada y
   * con mesa separada, y eso no se le puede dejar decidir a quien manda el
   * formulario del sitio publico, que entra sin sesion.
   */
  @PostMapping("/mostrador")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Reserva crearDesdeMostrador(@RequestBody Dtos.NuevaReservaMostrador datos) {
    return servicio.crearReservaDeMostrador(datos);
  }

  /** Equivale a `cambiarEstadoReserva(reservaId, estado, mesaAsignadaId)`. */
  @PatchMapping("/{reservaId}/estado")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Reserva cambiarEstado(
      @PathVariable String reservaId, @RequestBody Dtos.CambioEstadoReserva cambio) {
    return servicio.cambiarEstadoReserva(reservaId, cambio.estado(), cambio.mesaAsignadaId());
  }

  /** Equivale a `reprogramarReserva(reservaId, fechaHora)`. */
  @PatchMapping("/{reservaId}/fecha")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public ResponseEntity<Void> reprogramar(
      @PathVariable String reservaId, @RequestBody Dtos.PeticionReprogramar peticion) {
    servicio.reprogramarReserva(reservaId, peticion.fechaHora());
    return ResponseEntity.noContent().build();
  }
}
