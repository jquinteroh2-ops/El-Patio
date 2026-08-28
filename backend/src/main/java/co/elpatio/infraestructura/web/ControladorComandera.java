package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioComandas;
import co.elpatio.aplicacion.ServicioSalon;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.comanda.CargoAdicional;
import co.elpatio.dominio.comanda.Orden;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * La comandera: el mapa de mesas y la comanda de cada una.
 *
 * Hay una ruta por cada funcion de la seccion "Comandas" de mockApi.ts, con la
 * misma firma y el mismo tipo de retorno.
 */
@RestController
@RequestMapping("/api/comandera")
@PreAuthorize("hasAnyRole('MESERO', 'ADMINISTRADOR')")
public class ControladorComandera {

  private final ServicioComandas comandas;
  private final ServicioSalon salon;

  public ControladorComandera(ServicioComandas comandas, ServicioSalon salon) {
    this.comandas = comandas;
    this.salon = salon;
  }

  /**
   * Equivale a `listarMesas()`.
   *
   * Recepcion entra aqui, y solo aqui dentro de la comandera, porque al
   * confirmar una reserva asigna mesa y necesita saber cuales estan libres.
   */
  @GetMapping("/mesas")
  @PreAuthorize("hasAnyRole('MESERO', 'RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public List<Dtos.MesaEnMapa> listarMesas() {
    return salon.listarMesas();
  }

  /** Equivale a `obtenerOrdenDeMesa(mesaId)`. Devuelve 204 cuando la mesa esta libre. */
  @GetMapping("/mesas/{mesaId}/orden")
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR')")
  public ResponseEntity<Dtos.OrdenDetallada> obtenerOrdenDeMesa(@PathVariable String mesaId) {
    Dtos.OrdenDetallada detalle = comandas.obtenerOrdenDeMesa(mesaId);
    return detalle == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(detalle);
  }

  /** Equivale a `abrirMesa(mesaId, meseroId, comensales)`. */
  @PostMapping("/mesas/{mesaId}/abrir")
  public Orden abrirMesa(@PathVariable String mesaId, @RequestBody Dtos.PeticionAbrirMesa peticion) {
    return comandas.abrirMesa(mesaId, peticion.meseroId(), peticion.comensales());
  }

  /** Equivale a `cambiarComensales(ordenId, comensales)`. */
  @PatchMapping("/ordenes/{ordenId}/comensales")
  public ResponseEntity<Void> cambiarComensales(
      @PathVariable String ordenId, @RequestBody Dtos.PeticionComensales peticion) {
    comandas.cambiarComensales(ordenId, peticion.comensales());
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `agregarItems(ordenId, nuevos)`. */
  @PostMapping("/ordenes/{ordenId}/items")
  public Orden agregarItems(
      @PathVariable String ordenId, @RequestBody Dtos.PeticionAgregarItems peticion) {
    return comandas.agregarItems(ordenId, peticion.items());
  }

  /** Equivale a `cambiarCantidad(ordenId, itemId, cantidad)`. */
  @PatchMapping("/ordenes/{ordenId}/items/{itemId}/cantidad")
  public ResponseEntity<Void> cambiarCantidad(
      @PathVariable String ordenId,
      @PathVariable String itemId,
      @RequestBody Dtos.PeticionCantidad peticion) {
    comandas.cambiarCantidad(ordenId, itemId, peticion.cantidad());
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `quitarItem(ordenId, itemId)`, que en mockApi era cantidad 0. */
  @DeleteMapping("/ordenes/{ordenId}/items/{itemId}")
  public ResponseEntity<Void> quitarItem(@PathVariable String ordenId, @PathVariable String itemId) {
    comandas.cambiarCantidad(ordenId, itemId, 0);
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `anularItem(ordenId, itemId, motivo)`. */
  @PostMapping("/ordenes/{ordenId}/items/{itemId}/anular")
  public ResponseEntity<Void> anularItem(
      @PathVariable String ordenId,
      @PathVariable String itemId,
      @RequestBody Dtos.PeticionMotivo peticion) {
    comandas.anularItem(ordenId, itemId, peticion.motivo());
    return ResponseEntity.noContent().build();
  }

  /**
   * Equivale a `enviarACocina(ordenId)`.
   *
   * El cuerpo es opcional: sin el se manda todo lo pendiente. La comandera lo
   * usa al vaciar su cola para reponer un turno tal como se dicto.
   */
  @PostMapping("/ordenes/{ordenId}/enviar")
  public Dtos.ResultadoEnvio enviarACocina(
      @PathVariable String ordenId, @RequestBody(required = false) Dtos.PeticionEnvio peticion) {
    return comandas.enviarACocina(
        ordenId,
        peticion == null ? null : peticion.itemIds(),
        peticion == null ? null : peticion.turno());
  }

  /**
   * La comanda por su identificador.
   *
   * `obtenerOrdenDeMesa` parte de la mesa, pero el envio a cocina y el vaciado
   * de la cola solo conocen el identificador de la comanda: sin esta ruta la
   * comandera no podria saber que productos le faltan por mandar.
   */
  @GetMapping("/ordenes/{ordenId}")
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR')")
  public Orden obtenerOrden(@PathVariable String ordenId) {
    return comandas.obtenerOrden(ordenId);
  }

  /** Equivale a `agregarCargo(ordenId, nombre, valor, agregadoPor)`. */
  @PostMapping("/ordenes/{ordenId}/cargos")
  public CargoAdicional agregarCargo(
      @PathVariable String ordenId,
      @RequestBody Dtos.PeticionCargo peticion,
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    // El responsable del cargo se toma del token y no del cuerpo: si viniera de
    // la tablet, cualquiera podria firmar un descorche a nombre de otro.
    return comandas.agregarCargo(ordenId, peticion.nombre(), peticion.valor(), credencial.nombre());
  }

  /** Equivale a `quitarCargo(ordenId, cargoId)`. */
  @DeleteMapping("/ordenes/{ordenId}/cargos/{cargoId}")
  public ResponseEntity<Void> quitarCargo(@PathVariable String ordenId, @PathVariable String cargoId) {
    comandas.quitarCargo(ordenId, cargoId);
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `pedirCuenta(ordenId)`. */
  @PostMapping("/ordenes/{ordenId}/pedir-cuenta")
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR')")
  public ResponseEntity<Void> pedirCuenta(@PathVariable String ordenId) {
    comandas.pedirCuenta(ordenId);
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `trasladarMesa(ordenId, mesaDestinoId)`. */
  @PostMapping("/ordenes/{ordenId}/trasladar")
  public ResponseEntity<Void> trasladarMesa(
      @PathVariable String ordenId, @RequestBody Dtos.PeticionTraslado peticion) {
    comandas.trasladarMesa(ordenId, peticion.mesaDestinoId());
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `agregarNota(ordenId, notas)`. */
  @PutMapping("/ordenes/{ordenId}/nota")
  public ResponseEntity<Void> agregarNota(
      @PathVariable String ordenId, @RequestBody Dtos.PeticionNota peticion) {
    comandas.agregarNota(ordenId, peticion.notas());
    return ResponseEntity.noContent().build();
  }

  /**
   * Anula la comanda con motivo.
   *
   * No existia en mockApi.ts. Se agrego porque el plan exige que una comanda ya
   * cobrada no se pueda editar y que la correccion sea una anulacion con motivo
   * mas una comanda nueva: sin esta ruta la regla no tendria como ejecutarse.
   * Solo el administrador puede hacerlo, porque toca dinero ya cobrado.
   */
  @PostMapping("/ordenes/{ordenId}/anular")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Orden anularOrden(
      @PathVariable String ordenId,
      @RequestBody Dtos.PeticionMotivo peticion,
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    return comandas.anularOrden(ordenId, peticion.motivo(), credencial.usuarioId());
  }
}
