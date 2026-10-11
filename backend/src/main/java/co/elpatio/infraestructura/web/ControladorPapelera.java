package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioPapelera;
import co.elpatio.dominio.papelera.RegistroEliminado;
import co.elpatio.infraestructura.seguridad.ServicioTokens;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Eliminar ventas erradas y la papelera donde quedan, con su auditoria.
 *
 * Solo el administrador. Quien elimina o restaura sale del token, no del
 * cuerpo: es el dato con que se le pregunta a alguien despues por que lo hizo.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ControladorPapelera {

  private final ServicioPapelera servicio;

  public ControladorPapelera(ServicioPapelera servicio) {
    this.servicio = servicio;
  }

  public record DatosEliminacion(String motivo) {}

  /** Lo que se muestra de un registro. La copia de los datos no sale por la API. */
  public record FilaPapelera(
      String id,
      String tipo,
      String referencia,
      String resumen,
      String motivo,
      String eliminadoPor,
      Instant eliminadoEn,
      String restauradoPor,
      Instant restauradoEn) {

    static FilaPapelera de(RegistroEliminado r) {
      return new FilaPapelera(
          r.getId(), r.getTipo(), r.getReferencia(), r.getResumen(), r.getMotivo(),
          r.getEliminadoPor(), r.getEliminadoEn(), r.getRestauradoPor(), r.getRestauradoEn());
    }
  }

  @PostMapping("/ventas/{pagoId}/eliminar")
  public FilaPapelera eliminarVenta(
      @PathVariable String pagoId,
      @RequestBody DatosEliminacion datos,
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    return FilaPapelera.de(
        servicio.eliminarVenta(pagoId, datos == null ? null : datos.motivo(), credencial.nombre()));
  }

  @GetMapping("/papelera")
  public List<FilaPapelera> papelera() {
    return servicio.listar().stream().map(FilaPapelera::de).toList();
  }

  @PostMapping("/papelera/{id}/restaurar")
  public FilaPapelera restaurar(
      @PathVariable String id, @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    return FilaPapelera.de(servicio.restaurar(id, credencial.nombre()));
  }
}
