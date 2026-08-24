package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioIntegracionErp;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.erp.EnvioErp;
import co.elpatio.dominio.erp.EstadoEnvioErp;
import co.elpatio.dominio.puertos.FacturacionExterna;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La conciliacion entre lo que El Patio vendio y lo que el ERP facturo.
 *
 * Es la pantalla del contador. Responde una sola pregunta y tiene que
 * responderla sin ambiguedad: de las ventas de este periodo, cuales tienen
 * documento y cuales no.
 */
@RestController
@RequestMapping("/api/erp")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ControladorErp {

  private static final ZoneId ZONA = ZoneId.of("America/Bogota");

  private final ServicioIntegracionErp servicio;
  private final FacturacionExterna adaptadorActivo;

  public ControladorErp(ServicioIntegracionErp servicio, FacturacionExterna adaptadorActivo) {
    this.servicio = servicio;
    this.adaptadorActivo = adaptadorActivo;
  }

  /**
   * Las ventas del periodo y en que va cada una.
   *
   * El rango se recibe en fechas locales y no en instantes: quien pregunta por
   * «el 23 de agosto» quiere el dia del restaurante, no una ventana UTC que le
   * parta la noche del viernes en dos.
   */
  @GetMapping("/conciliacion")
  public Dtos.ResumenConciliacion conciliacion(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

    List<EnvioErp> envios =
        servicio.conciliacion(
            desde.atStartOfDay(ZONA).toInstant(),
            // Exclusivo por arriba: el dia `hasta` entra completo.
            hasta.plusDays(1).atStartOfDay(ZONA).toInstant());

    List<Dtos.FilaConciliacion> filas = new ArrayList<>();
    long montoTotal = 0;
    long montoSinConciliar = 0;
    int facturadas = 0;
    int sinConciliar = 0;
    int conError = 0;

    for (EnvioErp envio : envios) {
      Pago pago = servicio.pagoDe(envio);
      Orden orden = servicio.ordenDe(pago);
      long total = pago == null ? 0 : pago.getTotal();
      montoTotal += total;

      switch (envio.getEstado()) {
        case FACTURADA_ERP -> facturadas++;
        case ERROR_ERP -> {
          conError++;
          montoSinConciliar += total;
        }
        // Pendiente y enviada-sin-confirmar son distintas por dentro y lo mismo
        // para quien cierra el mes: ventas sin documento.
        default -> {
          sinConciliar++;
          montoSinConciliar += total;
        }
      }

      filas.add(
          new Dtos.FilaConciliacion(
              envio.getId(),
              envio.getPagoId(),
              orden == null ? 0 : orden.getNumero(),
              pago == null ? envio.getCreadoEn() : pago.getFechaHora(),
              total,
              envio.getEstado(),
              envio.getDocumentoExterno(),
              envio.getIntentos(),
              envio.getProximoIntento(),
              envio.getError(),
              envio.getAdaptador()));
    }

    return new Dtos.ResumenConciliacion(
        envios.size(),
        montoTotal,
        facturadas,
        sinConciliar,
        conError,
        montoSinConciliar,
        adaptadorActivo.nombre(),
        filas);
  }

  /**
   * Devuelve un envio a la cola.
   *
   * Se usa despues de arreglar la causa: se levanto el servidor, se corrigio el
   * codigo del producto. Sobre una venta ya facturada el servicio se niega:
   * reintentarla emitiria un segundo documento por la misma comida.
   */
  @PostMapping("/envios/{envioId}/reintentar")
  public void reintentar(@PathVariable String envioId) {
    servicio.reintentar(envioId);
  }

  /** Cuantas ventas estan sin documento ahora mismo. Para el aviso del panel. */
  @GetMapping("/pendientes")
  public long pendientes() {
    LocalDate hoy = LocalDate.now(ZONA);
    return servicio
        .conciliacion(
            hoy.minusDays(30).atStartOfDay(ZONA).toInstant(),
            hoy.plusDays(1).atStartOfDay(ZONA).toInstant())
        .stream()
        .filter(e -> e.getEstado() != EstadoEnvioErp.FACTURADA_ERP)
        .count();
  }
}
