package co.elpatio.dominio.reporte;

import java.time.LocalDate;
import java.util.List;

/**
 * Que es un reporte, dicho una sola vez.
 *
 * Un reporte declara esto y ya puede salir en Excel y en PDF. No escribe una
 * linea de POI ni de PDF: esa es toda la razon de que exista esta clase. Sin
 * ella, cada reporte nuevo repetiria la logica de exportacion, y el septimo
 * saldria con las fechas como texto porque alguien copio mal el sexto.
 */
public record DefinicionReporte(
    /** El titulo que va en el encabezado y en el nombre del archivo. */
    String titulo,
    /** Un nombre corto y estable para el archivo: `ventas`, `postulaciones`. */
    String nombreCorto,
    List<ColumnaReporte> columnas,
    /** Inicio del periodo. Va en el encabezado y en el nombre del archivo. */
    LocalDate desde,
    LocalDate hasta,
    /**
     * Los filtros que el usuario tenia puestos, en texto legible.
     *
     * No es adorno. Un PDF que dice «Ventas» sin decir que solo trae domicilios
     * pagados en efectivo no sirve como respaldo de nada: dentro de tres meses
     * nadie va a poder afirmar que cifra representa. Va impreso bajo el titulo.
     */
    List<String> filtrosAplicados,
    /** Quien lo genero. Va en el pie del PDF. */
    String generadoPor) {

  /**
   * Si conviene apaisar el PDF.
   *
   * Mas de seis columnas en vertical salen apretadas hasta volverse ilegibles.
   * El umbral es un juicio y no una constante universal, pero es mejor que
   * dejar que cada reporte lo decida por su cuenta y que no coincidan.
   */
  public boolean prefiereApaisado() {
    return columnas.size() > 6;
  }
}
