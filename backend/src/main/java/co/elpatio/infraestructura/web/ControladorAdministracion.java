package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioAdministracion;
import co.elpatio.aplicacion.ServicioAjustes;
import co.elpatio.aplicacion.ServicioSalon;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.caja.CierreCaja;
import co.elpatio.dominio.cobro.MetodoPago;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** El panel administrativo: indicadores, caja, reportes, ajustes y salon. */
@RestController
@RequestMapping("/api")
public class ControladorAdministracion {

  private final ServicioAdministracion administracion;
  private final ServicioAjustes ajustes;
  private final ServicioSalon salon;

  public ControladorAdministracion(
      ServicioAdministracion administracion, ServicioAjustes ajustes, ServicioSalon salon) {
    this.administracion = administracion;
    this.ajustes = ajustes;
    this.salon = salon;
  }

  // ---------------------------------------------------------------------------
  // Ajustes
  // ---------------------------------------------------------------------------

  /** Equivale a `obtenerAjustes()`. */
  @GetMapping("/ajustes")
  public Dtos.AjustesDto obtenerAjustes() {
    return ajustes.obtener();
  }

  /** Equivale a `actualizarAjustes(cambios)`. Solo cambia lo que venga informado. */
  @PutMapping("/ajustes")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Dtos.AjustesDto actualizarAjustes(@RequestBody Dtos.CambiosAjustes cambios) {
    return ajustes.actualizar(cambios);
  }

  // ---------------------------------------------------------------------------
  // Salon
  // ---------------------------------------------------------------------------

  /** Equivale a `guardarMesa(mesa)`. */
  @PutMapping("/salon/mesas")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Dtos.MesaDto guardarMesa(@RequestBody Dtos.MesaDto mesa) {
    return salon.guardarMesa(mesa);
  }

  /** Equivale a `eliminarMesa(mesaId)`. */
  @DeleteMapping("/salon/mesas/{mesaId}")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public ResponseEntity<Void> eliminarMesa(@PathVariable String mesaId) {
    salon.eliminarMesa(mesaId);
    return ResponseEntity.noContent().build();
  }

  // ---------------------------------------------------------------------------
  // Indicadores y caja
  // ---------------------------------------------------------------------------

  /** Equivale a `indicadoresDia()`. */
  @GetMapping("/caja/indicadores")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR')")
  public Dtos.IndicadoresDia indicadoresDia() {
    return administracion.indicadoresDia();
  }

  /** Equivale a `historicoVentas(filtro)`. */
  @GetMapping("/caja/ventas")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR')")
  public List<Dtos.VentaHistorica> historicoVentas(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) String meseroId,
      @RequestParam(required = false) MetodoPago metodo) {
    return administracion.historicoVentas(desde, hasta, meseroId, metodo);
  }

  /** Equivale a `resumenTurnoActual()`. */
  @GetMapping("/caja/turno")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR')")
  public Dtos.ResumenTurno resumenTurnoActual() {
    return administracion.resumenTurnoActual();
  }

  /** Equivale a `listarCierres()`. */
  @GetMapping("/caja/cierres")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR')")
  public List<CierreCaja> listarCierres() {
    return administracion.listarCierres();
  }

  /** Equivale a `cerrarTurno(cerradoPor)`. */
  @PostMapping("/caja/cierres")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR')")
  public CierreCaja cerrarTurno(
      @AuthenticationPrincipal co.elpatio.infraestructura.seguridad.ServicioTokens.Credencial credencial) {
    // Quien cierra la caja es quien tiene la sesion abierta, no lo que diga el
    // cuerpo de la peticion: el cierre es el documento con el que se entrega el
    // dinero y tiene que llevar un nombre que nadie pudo escribir a mano.
    return administracion.cerrarTurno(credencial.nombre());
  }

  /** Equivale a `alertas(umbralMinutos)`. */
  @GetMapping("/caja/alertas")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR')")
  public List<Dtos.Alerta> alertas(@RequestParam(defaultValue = "20") int umbralMinutos) {
    return administracion.alertas(umbralMinutos);
  }

  // ---------------------------------------------------------------------------
  // Reportes
  // ---------------------------------------------------------------------------

  /** Equivale a `reportes(dias)`. */
  @GetMapping("/reportes")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Dtos.Reportes reportes(@RequestParam(defaultValue = "10") int dias) {
    return administracion.reportes(dias);
  }
}
