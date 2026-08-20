package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioCobro;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.infraestructura.seguridad.ServicioTokens;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El cobro de las cuentas. */
@RestController
@RequestMapping("/api/cobro")
@PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR')")
public class ControladorCobro {

  private final ServicioCobro servicio;

  public ControladorCobro(ServicioCobro servicio) {
    this.servicio = servicio;
  }

  /** Equivale a `registrarPago(datos)`. */
  @PostMapping("/pagos")
  public Pago registrarPago(
      @RequestBody Dtos.DatosPago datos,
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    // Quien recibio el dinero sale del token: es el dato con el que despues se
    // le reclama a alguien un faltante de caja, y no puede venir del cliente.
    Dtos.DatosPago conResponsable =
        new Dtos.DatosPago(
            datos.ordenId(),
            datos.porcentajePropina(),
            datos.propina(),
            datos.metodo(),
            datos.divisiones(),
            credencial.nombre());
    return servicio.registrarPago(conResponsable);
  }

  /** Equivale a `obtenerComprobante(pagoId)`. Devuelve 204 si el pago no existe. */
  @GetMapping("/comprobantes/{pagoId}")
  public ResponseEntity<Dtos.ComprobanteDetallado> obtenerComprobante(@PathVariable String pagoId) {
    Dtos.ComprobanteDetallado comprobante = servicio.obtenerComprobante(pagoId);
    return comprobante == null
        ? ResponseEntity.noContent().build()
        : ResponseEntity.ok(comprobante);
  }
}
