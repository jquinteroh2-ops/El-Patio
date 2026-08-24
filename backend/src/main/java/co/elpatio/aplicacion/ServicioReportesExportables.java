package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.caja.CierreCaja;
import co.elpatio.dominio.cobro.MetodoPago;
import co.elpatio.dominio.erp.EnvioErp;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.reporte.ColumnaReporte;
import co.elpatio.dominio.reporte.DefinicionReporte;
import co.elpatio.dominio.reporte.FilaReporte;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los reportes que se pueden descargar, cada uno declarado una sola vez.
 *
 * Un reporte aqui es dos cosas: una {@link DefinicionReporte} —que columnas
 * tiene y de que tipo es cada una— y un {@link Stream} de filas. Nada mas. No
 * sabe si va a salir en Excel o en PDF, y no escribe una linea de ninguno de
 * los dos formatos.
 *
 * Eso es lo que hace que agregar el reporte de PQR o el de postulaciones, mas
 * adelante, sea escribir un metodo de veinte lineas y no volver a pelear con
 * POI. Y es lo que garantiza que el septimo reporte no salga con las fechas
 * como texto porque alguien copio mal el sexto.
 */
@Service
public class ServicioReportesExportables {

  private static final ZoneId ZONA = ZoneId.of("America/Bogota");

  private final ServicioAdministracion administracion;
  private final ServicioIntegracionErp integracionErp;
  private final Repositorios.DeCierres cierres;
  private final Repositorios.DePagosOnline anticipos;
  private final Repositorios.DeOrdenes ordenes;
  private final Reloj reloj;

  public ServicioReportesExportables(
      ServicioAdministracion administracion,
      ServicioIntegracionErp integracionErp,
      Repositorios.DeCierres cierres,
      Repositorios.DePagosOnline anticipos,
      Repositorios.DeOrdenes ordenes,
      Reloj reloj) {
    this.administracion = administracion;
    this.integracionErp = integracionErp;
    this.cierres = cierres;
    this.anticipos = anticipos;
    this.ordenes = ordenes;
    this.reloj = reloj;
  }

  /** Un reporte listo para exportar: como se pinta y que filas lleva. */
  public record ReporteListo(DefinicionReporte definicion, List<FilaReporte> filas) {}

  /**
   * Arma el reporte que se pida.
   *
   * El `switch` sobre el tipo es a proposito y no un mapa de estrategias: son
   * pocos, se leen de corrido y el compilador avisa cuando falta uno. Un
   * registro de implementaciones seria mas ceremonioso y menos claro.
   */
  @Transactional(readOnly = true)
  public ReporteListo armar(
      String tipo, LocalDate desde, LocalDate hasta, String generadoPor, MetodoPago metodo) {

    return switch (tipo) {
      case "ventas" -> ventas(desde, hasta, generadoPor, metodo);
      case "productos" -> productos(desde, hasta, generadoPor);
      case "cierres" -> cierresDeCaja(desde, hasta, generadoPor);
      case "conciliacion" -> conciliacion(desde, hasta, generadoPor);
      case "anticipos" -> anticiposRecibidos(desde, hasta, generadoPor);
      default -> throw new NoEncontradoError("No existe el reporte «" + tipo + "»");
    };
  }

  // -------------------------------------------------------------------------

  /** Cada venta cobrada del periodo, con su desglose. */
  private ReporteListo ventas(
      LocalDate desde, LocalDate hasta, String generadoPor, MetodoPago metodo) {

    List<Dtos.VentaHistorica> historico =
        administracion.historicoVentas(desde, hasta, null, metodo);

    List<FilaReporte> filas = new ArrayList<>();
    for (Dtos.VentaHistorica venta : historico) {
      filas.add(
          FilaReporte.de(
              venta.pago().getFechaHora(),
              venta.orden().getNumero(),
              venta.mesaEtiqueta(),
              venta.meseroNombre(),
              venta.pago().getSubtotal(),
              venta.pago().getInc(),
              venta.pago().getCargosAdicionales(),
              venta.pago().getCostoEnvio(),
              venta.pago().getPropina(),
              venta.pago().getTotal(),
              etiqueta(venta.pago().getMetodo())));
    }

    List<String> filtros = new ArrayList<>();
    if (metodo != null) filtros.add("Medio de pago: " + etiqueta(metodo));

    return new ReporteListo(
        new DefinicionReporte(
            "Ventas por periodo",
            "ventas",
            List.of(
                ColumnaReporte.fechaHora("Fecha"),
                ColumnaReporte.entero("Comanda"),
                ColumnaReporte.texto("Mesa / canal", 18),
                ColumnaReporte.texto("Atendió", 16),
                ColumnaReporte.dinero("Subtotal"),
                ColumnaReporte.dinero("INC"),
                ColumnaReporte.dinero("Cargos"),
                ColumnaReporte.dinero("Domicilio"),
                ColumnaReporte.dinero("Propina"),
                ColumnaReporte.dinero("Total"),
                ColumnaReporte.texto("Medio de pago", 14)),
            desde,
            hasta,
            filtros,
            generadoPor),
        filas);
  }

  /** El ranking de productos: que se vende y cuanto deja. */
  private ReporteListo productos(LocalDate desde, LocalDate hasta, String generadoPor) {
    int dias = (int) Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1);
    Dtos.Reportes datos = administracion.reportes(dias);

    List<FilaReporte> filas =
        datos.masVendidos().stream()
            .map(p -> FilaReporte.de(p.nombre(), (long) p.unidades(), p.ingreso()))
            .toList();

    return new ReporteListo(
        new DefinicionReporte(
            "Productos más vendidos",
            "productos",
            List.of(
                ColumnaReporte.texto("Producto", 34),
                ColumnaReporte.entero("Unidades"),
                ColumnaReporte.dinero("Ingreso")),
            desde,
            hasta,
            List.of("Ordenado por unidades vendidas"),
            generadoPor),
        filas);
  }

  /** Los cierres de turno, con las cifras que el contador necesita declarar. */
  private ReporteListo cierresDeCaja(LocalDate desde, LocalDate hasta, String generadoPor) {
    List<FilaReporte> filas =
        cierres.listar().stream()
            .filter(c -> !c.getFecha().isBefore(desde) && !c.getFecha().isAfter(hasta))
            .sorted(Comparator.comparing(CierreCaja::getFecha).reversed())
            .map(
                c ->
                    FilaReporte.de(
                        c.getFecha(),
                        c.getTurno().name().toLowerCase(),
                        c.getBaseGravable(),
                        c.getBaseNoGravada(),
                        c.getIncTotal(),
                        c.getPropinasTotales(),
                        c.getVentaTotal(),
                        c.getTotalEfectivo(),
                        c.getTotalTarjeta(),
                        c.getTotalTransferencia(),
                        (long) c.getOrdenesAtendidas(),
                        c.getCerradoPor()))
            .toList();

    return new ReporteListo(
        new DefinicionReporte(
            "Cierres de caja",
            "cierres",
            List.of(
                ColumnaReporte.fecha("Fecha"),
                ColumnaReporte.texto("Turno", 12),
                ColumnaReporte.dinero("Base gravable"),
                ColumnaReporte.dinero("No gravada"),
                ColumnaReporte.dinero("INC"),
                ColumnaReporte.dinero("Propinas"),
                ColumnaReporte.dinero("Venta total"),
                ColumnaReporte.dinero("Efectivo"),
                ColumnaReporte.dinero("Tarjeta"),
                ColumnaReporte.dinero("Transferencia"),
                ColumnaReporte.entero("Comandas"),
                ColumnaReporte.texto("Cerrado por", 18)),
            desde,
            hasta,
            List.of(),
            generadoPor),
        filas);
  }

  /**
   * El que mas va a usar el contador: ventas contra documentos del ERP.
   *
   * Es el respaldo de que el cierre del mes cuadra, o la lista de lo que falta
   * para que cuadre.
   */
  private ReporteListo conciliacion(LocalDate desde, LocalDate hasta, String generadoPor) {
    var ventas =
        integracionErp.conciliacionDetallada(
            desde.atStartOfDay(ZONA).toInstant(), hasta.plusDays(1).atStartOfDay(ZONA).toInstant());

    List<FilaReporte> filas = new ArrayList<>();
    for (var venta : ventas) {
      EnvioErp envio = venta.envio();
      filas.add(
          FilaReporte.de(
              venta.fechaVenta(),
              (long) venta.numeroComanda(),
              venta.total(),
              // La misma etiqueta que la pantalla: el estado de una venta no
              // puede leerse de una forma en pantalla y de otra en el archivo
              // que se le manda al contador.
              venta.estadoLegible(),
              envio.getDocumentoExterno() == null ? "" : envio.getDocumentoExterno(),
              (long) envio.getIntentos(),
              envio.getError() == null ? "" : envio.getError()));
    }

    return new ReporteListo(
        new DefinicionReporte(
            "Conciliación con el ERP",
            "conciliacion",
            List.of(
                ColumnaReporte.fechaHora("Fecha venta"),
                ColumnaReporte.entero("Comanda"),
                ColumnaReporte.dinero("Total"),
                ColumnaReporte.texto("Estado", 16),
                ColumnaReporte.texto("Documento ERP", 18),
                ColumnaReporte.entero("Intentos"),
                ColumnaReporte.texto("Detalle", 40)),
            desde,
            hasta,
            List.of("Todas las ventas del periodo, facturadas y pendientes"),
            generadoPor),
        filas);
  }

  /**
   * Los anticipos cobrados antes de mandar el pedido a cocina.
   *
   * NO son documentos fiscales y el reporte no los llama factura: son cobros
   * parciales del restaurante, y el documento de esa venta lo emite el ERP
   * cuando el pedido se cierra.
   */
  private ReporteListo anticiposRecibidos(LocalDate desde, LocalDate hasta, String generadoPor) {
    var limiteInferior = desde.atStartOfDay(ZONA).toInstant();
    var limiteSuperior = hasta.plusDays(1).atStartOfDay(ZONA).toInstant();

    List<FilaReporte> filas =
        anticipos.creadosEntre(limiteInferior, limiteSuperior).stream()
            .map(
                p ->
                    FilaReporte.de(
                        p.getCreadaEn(),
                        numeroDeComanda(p.getOrdenId()),
                        p.getReferencia(),
                        // Wompi trabaja en centavos y el resto del sistema en
                        // pesos. La division va aqui, en el borde, y no en el
                        // exportador: mezclar las dos unidades en una misma
                        // columna es como se reporta cien veces de mas.
                        p.getMontoCentavos() / 100,
                        p.getEstado().name().toLowerCase(),
                        p.getTransactionId() == null ? "" : p.getTransactionId()))
            .toList();

    return new ReporteListo(
        new DefinicionReporte(
            "Anticipos y pagos parciales",
            "anticipos",
            List.of(
                ColumnaReporte.fechaHora("Fecha"),
                ColumnaReporte.entero("Comanda"),
                ColumnaReporte.texto("Referencia", 26),
                ColumnaReporte.dinero("Monto"),
                ColumnaReporte.texto("Estado", 14),
                ColumnaReporte.texto("Transacción", 26)),
            desde,
            hasta,
            List.of("Cobros parciales del restaurante, no documentos fiscales"),
            generadoPor),
        filas);
  }

  // -------------------------------------------------------------------------

  private long numeroDeComanda(String ordenId) {
    return ordenes.porId(ordenId).map(o -> (long) o.getNumero()).orElse(0L);
  }

  private String etiqueta(MetodoPago metodo) {
    return switch (metodo) {
      case EFECTIVO -> "Efectivo";
      case TARJETA -> "Tarjeta";
      case TRANSFERENCIA -> "Transferencia";
      case MIXTO -> "Mixto";
    };
  }

  /** Hoy, en la zona del restaurante. Sirve de valor por defecto del rango. */
  public LocalDate hoy() {
    return reloj.hoy();
  }

  /** Las filas como flujo, que es lo que consume el exportador. */
  public static Stream<FilaReporte> comoFlujo(ReporteListo reporte) {
    return reporte.filas().stream();
  }
}
