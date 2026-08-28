package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioAjustes;
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
  private final ServicioAjustes ajustes;

  public ControladorPedidos(ServicioPedidos servicio, ServicioAjustes ajustes) {
    this.servicio = servicio;
    this.ajustes = ajustes;
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

  /**
   * El pedido que llega por WhatsApp o por telefono y lo anota una persona.
   *
   * Es la misma orden que crea el sitio publico; lo que cambia es que aqui hay
   * un usuario del restaurante detras, asi que el pedido entra al tablero sin
   * pasar por el horario del canal y `canal` guarda por donde escribio el
   * cliente. Sin esto, ese pedido solo existia en un papel del mostrador.
   */
  @PostMapping("/mostrador")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Dtos.PedidoCreado crearDesdeMostrador(@RequestBody Dtos.NuevoPedidoExterno datos) {
    return servicio.crearPedidoDeMostrador(datos);
  }

  /**
   * Abre, cierra o corre el horario del canal de pedidos.
   *
   * Va aqui y no en `PUT /api/ajustes` a proposito: ese endpoint tambien mueve
   * el impuesto al consumo y el plazo de las PQR, que no son del mostrador.
   * Recepcion es quien esta mirando la cocina cuando hay que pausar, asi que
   * recibe exactamente esos tres campos y ninguno mas.
   */
  @PutMapping("/canal")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Dtos.AjustesDto cambiarCanal(@RequestBody Dtos.CambiosCanal cambios) {
    return ajustes.actualizar(
        new Dtos.CambiosAjustes(
            null, cambios.pausados(), cambios.desde(), cambios.hasta(), null, null));
  }

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

  /**
   * Corrige el tiempo prometido cuando la cocina se atrasa.
   *
   * Va aparte de aceptar porque no es la misma decision: aceptar mete el pedido
   * a la plancha, esto solo cambia lo que se le dijo al cliente.
   */
  @PatchMapping("/{ordenId}/tiempo")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Orden cambiarTiempo(
      @PathVariable String ordenId, @RequestBody Dtos.PeticionTiempo peticion) {
    return servicio.cambiarTiempo(ordenId, peticion.minutosEstimados());
  }

  @PostMapping("/{ordenId}/despachar")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public Orden despachar(@PathVariable String ordenId, @RequestBody Dtos.PeticionDespacho peticion) {
    return servicio.despachar(ordenId, peticion.repartidor(), peticion.repartidorId());
  }

  /** Para llenar la lista de «¿quién lo lleva?» sin exponer el resto del personal. */
  @GetMapping("/repartidores")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public List<Dtos.RepartidorDisponible> repartidores() {
    return servicio.repartidores();
  }

  // ---------------------------------------------------------------------------
  // Reparto
  // ---------------------------------------------------------------------------

  /**
   * Lo que el repartidor lleva encima ahora mismo.
   *
   * Sale del token y no de un parametro: si el identificador viajara en la URL,
   * cualquiera podria pedir la lista de otro, y son direcciones y telefonos de
   * clientes.
   */
  @GetMapping("/mios")
  @PreAuthorize("hasAnyRole('REPARTIDOR', 'ADMINISTRADOR')")
  public List<Dtos.PedidoEnRecepcion> misEntregas(
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    return servicio.misEntregas(credencial.usuarioId());
  }

  /** El repartidor cierra la entrega en la puerta, y solo la suya. */
  @PostMapping("/mios/{ordenId}/entregar")
  @PreAuthorize("hasAnyRole('REPARTIDOR', 'ADMINISTRADOR')")
  public Orden entregarLoMio(
      @PathVariable String ordenId,
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    return servicio.entregarComoRepartidor(ordenId, credencial.usuarioId());
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
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public ZonaDomicilio guardarZona(@RequestBody ZonaDomicilio zona) {
    return servicio.guardarZona(zona);
  }

  @DeleteMapping("/zonas/{zonaId}")
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public ResponseEntity<Void> eliminarZona(@PathVariable String zonaId) {
    servicio.eliminarZona(zonaId);
    return ResponseEntity.noContent().build();
  }
}
