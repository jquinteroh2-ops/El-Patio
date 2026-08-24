package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioReportesExportables;
import co.elpatio.dominio.cobro.MetodoPago;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.Exportador;
import co.elpatio.infraestructura.export.ExportadorExcel;
import co.elpatio.infraestructura.export.ExportadorPdf;
import co.elpatio.infraestructura.seguridad.ServicioTokens;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La descarga de cualquier reporte, en cualquiera de los dos formatos.
 *
 * Un solo endpoint para todos. Agregar un reporte no exige un controlador
 * nuevo, ni un metodo nuevo aqui: se declara en
 * {@link ServicioReportesExportables} y ya se puede descargar en los dos
 * formatos.
 *
 * <p><b>El permiso se comprueba aqui, en el servidor.</b> Esconder el boton en
 * la pantalla no es control de acceso: quien sepa la URL la escribe igual. Toda
 * esta ruta exige ADMINISTRADOR, y ademas /api/reportes ya lo exige en la
 * configuracion de seguridad.
 */
@RestController
@RequestMapping("/api/reportes/exportar")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ControladorExportacion {

  private static final DateTimeFormatter ARCHIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final ServicioReportesExportables servicio;
  private final ExportadorExcel excel;
  private final ExportadorPdf pdf;

  public ControladorExportacion(
      ServicioReportesExportables servicio, ExportadorExcel excel, ExportadorPdf pdf) {
    this.servicio = servicio;
    this.excel = excel;
    this.pdf = pdf;
  }

  /**
   * Descarga un reporte.
   *
   * Escribe directo al flujo de la respuesta y no arma el archivo en memoria:
   * un mes de ventas son decenas de miles de filas, y juntarlas todas antes de
   * empezar a escribir es como se queda sin memoria un contenedor pequeño.
   */
  @GetMapping("/{tipo}")
  public void exportar(
      @PathVariable String tipo,
      @RequestParam(defaultValue = "xlsx") String formato,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) MetodoPago metodo,
      @AuthenticationPrincipal ServicioTokens.Credencial credencial,
      HttpServletResponse respuesta)
      throws IOException {

    if (desde.isAfter(hasta)) {
      throw new ReglaDeNegocioError("La fecha inicial no puede ser posterior a la final");
    }

    Exportador exportador = exportadorDe(formato);
    var reporte = servicio.armar(tipo, desde, hasta, credencial.nombre(), metodo);

    String nombre =
        String.format(
            "%s_%s_a_%s.%s",
            reporte.definicion().nombreCorto(),
            ARCHIVO.format(desde),
            ARCHIVO.format(hasta),
            exportador.extension());

    respuesta.setContentType(exportador.tipoDeContenido());
    respuesta.setHeader("Content-Disposition", "attachment; filename=\"" + nombre + "\"");
    // Un reporte se genera contra los datos del momento: servir una copia vieja
    // de caché haria que dos descargas del mismo rango dieran cifras distintas.
    respuesta.setHeader("Cache-Control", "no-store");

    try (OutputStream salida = respuesta.getOutputStream()) {
      exportador.exportar(reporte.definicion(), reporte.filas().stream(), salida);
    }
  }

  private Exportador exportadorDe(String formato) {
    return switch (formato.toLowerCase()) {
      case "xlsx" -> excel;
      case "pdf" -> pdf;
      default -> throw new ReglaDeNegocioError("Formato no soportado: " + formato);
    };
  }
}
