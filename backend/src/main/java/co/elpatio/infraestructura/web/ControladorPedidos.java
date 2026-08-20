package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioPedidos;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.pedido.ZonaDomicilio;
import co.elpatio.infraestructura.seguridad.ServicioTokens;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Domicilios y para llevar.
 *
 * Dos rutas quedan abiertas porque las usa el cliente desde la calle, sin
 * sesion: consultar el estado del canal y crear el pedido. Todo lo demas exige
 * personal, porque son datos de contacto de clientes y decisiones de operacion.
 */
@RestController
@RequestMapping("/api/pedidos")
public class ControladorPedidos {

  private final ServicioPedidos servicio;

  public ControladorPedidos(ServicioPedidos servicio) {
    this.servicio = servicio;
  }

  // ---------------------------------------------------------------------------
  // Sitio publico
  // ---------------------------------------------------------------------------

  /** Si el canal esta abierto, a que zonas se lleva y cuanto cuesta cada una. */
  @GetMapping("/canal")
  public Dtos.EstadoCanal estadoCanal() {
    return servicio.estadoCanal();
  }

  /**
   * Crea el pedido hecho desde el sitio publico.
   *
   * Es el punto exacto donde el canal deja de ser una pantalla y se vuelve
   * operacion: de aqui sale el evento que enciende la pantalla de recepcion.
   */
  @PostMapping
  public Dtos.PedidoCreado crearPedidoExterno(@RequestBody Dtos.NuevoPedidoExterno datos) {
    return servicio.crearPedidoExterno(datos);
  }

  // ---------------------------------------------------------------------------
  // Recepcion
  // ---------------------------------------------------------------------------

  @GetMapping
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public List<Dtos.PedidoEnRecepcion> listarPedidos(
      @RequestParam(defaultValue = "false") boolean incluirCerrados) {
    return servicio.listarPedidos(incluirCerrados);
  }

  @PostMapping("/{ordenId}/aceptar")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Orden aceptar(
      @PathVariable String ordenId,
      @RequestBody Dtos.PeticionAceptar peticion,
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    // Quien acepta queda como responsable del pedido, igual que el mesero de
    // una mesa: si el cliente reclama, hay a quien preguntarle.
    return servicio.aceptar(ordenId, peticion.minutosEstimados(), credencial.usuarioId());
  }

  @PostMapping("/{ordenId}/rechazar")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Orden rechazar(@PathVariable String ordenId, @RequestBody Dtos.PeticionMotivo peticion) {
    return servicio.rechazar(ordenId, peticion.motivo());
  }

  @PostMapping("/{ordenId}/cancelar")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Orden cancelar(@PathVariable String ordenId, @RequestBody Dtos.PeticionMotivo peticion) {
    return servicio.cancelar(ordenId, peticion.motivo());
  }

  @PatchMapping("/{ordenId}/estado")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Orden cambiarEstado(
      @PathVariable String ordenId, @RequestBody Dtos.PeticionEstadoPedido peticion) {
    return servicio.cambiarEstado(ordenId, peticion.estado());
  }

  @PostMapping("/{ordenId}/despachar")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Orden despachar(@PathVariable String ordenId, @RequestBody Dtos.PeticionDespacho peticion) {
    return servicio.despachar(ordenId, peticion.repartidor());
  }

  @PostMapping("/{ordenId}/entregar")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Orden entregar(@PathVariable String ordenId) {
    return servicio.entregar(ordenId);
  }

  // ---------------------------------------------------------------------------
  // Zonas
  // ---------------------------------------------------------------------------

  @GetMapping("/zonas")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public List<ZonaDomicilio> listarZonas() {
    return servicio.listarZonas();
  }

  @PutMapping("/zonas")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public ZonaDomicilio guardarZona(@RequestBody ZonaDomicilio zona) {
    return servicio.guardarZona(zona);
  }

  @DeleteMapping("/zonas/{zonaId}")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public ResponseEntity<Void> eliminarZona(@PathVariable String zonaId) {
    servicio.eliminarZona(zonaId);
    return ResponseEntity.noContent().build();
  }
}
