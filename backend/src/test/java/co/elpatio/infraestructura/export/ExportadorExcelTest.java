package co.elpatio.infraestructura.export;

import static org.assertj.core.api.Assertions.assertThat;

import co.elpatio.dominio.reporte.ColumnaReporte;
import co.elpatio.dominio.reporte.DefinicionReporte;
import co.elpatio.dominio.reporte.FilaReporte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Lo que de verdad importa de este exportador.
 *
 * No es que el archivo se genere: es que sea una HOJA DE DATOS. El contador la
 * va a filtrar, ordenar y sumar, y nada de eso funciona si los numeros llegan
 * como texto. Por eso estas pruebas abren el archivo generado y comprueban el
 * tipo de cada celda, no su apariencia.
 */
class ExportadorExcelTest {

  private static final Instant CUANDO = Instant.parse("2026-08-24T20:15:00Z");

  private final ExportadorExcel exportador = new ExportadorExcel();

  private static DefinicionReporte definicion() {
    return new DefinicionReporte(
        "Ventas por periodo",
        "ventas",
        List.of(
            ColumnaReporte.fechaHora("Fecha"),
            ColumnaReporte.texto("Mesa", 14),
            ColumnaReporte.entero("Comensales"),
            ColumnaReporte.dinero("Total"),
            ColumnaReporte.fecha("Día")),
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 24),
        List.of("Medio de pago: Efectivo"),
        "Ana");
  }

  private static Stream<FilaReporte> filas() {
    return Stream.of(
        FilaReporte.de(CUANDO, "Mesa 4", 4L, 174640L, LocalDate.of(2026, 8, 24)),
        FilaReporte.de(CUANDO, "Mesa 7", 2L, 96000L, LocalDate.of(2026, 8, 24)));
  }

  private Workbook generarYAbrir() throws IOException {
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    exportador.exportar(definicion(), filas(), salida);
    // Abrirlo con XSSFWorkbook es la prueba de que es un .xlsx valido: si el
    // archivo estuviera corrupto, esta linea revienta.
    return new XSSFWorkbook(new ByteArrayInputStream(salida.toByteArray()));
  }

  @Test
  void elArchivoAbreYTieneLosDatos() throws IOException {
    try (Workbook libro = generarYAbrir()) {
      Sheet hoja = libro.getSheetAt(0);

      assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fecha");
      assertThat(hoja.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Mesa 4");
      // Dos filas de datos: la 0 es cabecera y la 3 es la de totales.
      assertThat(hoja.getLastRowNum()).isEqualTo(3);
    }
  }

  /**
   * El error mas comun al generar hojas de calculo, y el que las inutiliza.
   *
   * Una columna de plata que llega como texto no se puede sumar, ni filtrar por
   * «mayor que», ni meter en una formula. El archivo se ve bien y no sirve.
   */
  @Test
  void losMontosLleganComoNumeroYNoComoTexto() throws IOException {
    try (Workbook libro = generarYAbrir()) {
      Cell total = libro.getSheetAt(0).getRow(1).getCell(3);

      assertThat(total.getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(total.getNumericCellValue()).isEqualTo(174640d);
    }
  }

  @Test
  void losEnterosTambienLleganComoNumero() throws IOException {
    try (Workbook libro = generarYAbrir()) {
      Cell comensales = libro.getSheetAt(0).getRow(1).getCell(2);

      assertThat(comensales.getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(comensales.getNumericCellValue()).isEqualTo(4d);
    }
  }

  /**
   * Una fecha como texto no se ordena cronologicamente: «10/01» queda antes de
   * «9/01» porque se compara letra por letra.
   */
  @Test
  void lasFechasLleganComoFechaYNoComoTexto() throws IOException {
    try (Workbook libro = generarYAbrir()) {
      Cell fechaHora = libro.getSheetAt(0).getRow(1).getCell(0);
      Cell dia = libro.getSheetAt(0).getRow(1).getCell(4);

      assertThat(fechaHora.getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(DateUtil.isCellDateFormatted(fechaHora)).isTrue();

      assertThat(dia.getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(DateUtil.isCellDateFormatted(dia)).isTrue();
      assertThat(dia.getLocalDateTimeCellValue().toLocalDate())
          .isEqualTo(LocalDate.of(2026, 8, 24));
    }
  }

  @Test
  void elTextoSigueSiendoTexto() throws IOException {
    try (Workbook libro = generarYAbrir()) {
      assertThat(libro.getSheetAt(0).getRow(1).getCell(1).getCellType())
          .isEqualTo(CellType.STRING);
    }
  }

  /** La fila de totales suma solo lo sumable, y suma bien. */
  @Test
  void alPieVanLosTotalesDeLasColumnasSumables() throws IOException {
    try (Workbook libro = generarYAbrir()) {
      Row totales = libro.getSheetAt(0).getRow(3);

      assertThat(totales.getCell(0).getStringCellValue()).isEqualTo("TOTAL");
      assertThat(totales.getCell(2).getNumericCellValue()).isEqualTo(6d);
      assertThat(totales.getCell(3).getNumericCellValue()).isEqualTo(270640d);
    }
  }

  /** Cabecera congelada y autofiltro: lo primero que haria a mano quien la abre. */
  @Test
  void laCabeceraQuedaFijaYConAutofiltro() throws IOException {
    try (Workbook libro = generarYAbrir()) {
      Sheet hoja = libro.getSheetAt(0);

      assertThat(hoja.getPaneInformation()).isNotNull();
      assertThat(hoja.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
      assertThat(((org.apache.poi.xssf.usermodel.XSSFSheet) hoja).getCTWorksheet().isSetAutoFilter())
          .isTrue();
    }
  }

  /**
   * Excel prohibe siete caracteres en el nombre de hoja y corta en 31. Un
   * titulo con barras genera un archivo que no abre, y el fallo no aparece al
   * generarlo sino al abrirlo, cuando ya se lo mandaron al contador.
   */
  @Test
  void unTituloConCaracteresProhibidosNoRompeElArchivo() throws IOException {
    DefinicionReporte conBarras =
        new DefinicionReporte(
            "Ventas 01/08 a 24/08 [detalle]: caja",
            "ventas",
            List.of(ColumnaReporte.texto("Producto", 20)),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 24),
            List.of(),
            "Ana");

    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    exportador.exportar(conBarras, Stream.of(FilaReporte.de("Ceviche")), salida);

    try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(salida.toByteArray()))) {
      assertThat(libro.getSheetName(0)).hasSizeLessThanOrEqualTo(31);
      assertThat(libro.getSheetName(0)).doesNotContain("/", "[", "]", ":");
    }
  }

  /** Un reporte sin datos tiene que abrir igual, con su cabecera y su total. */
  @Test
  void unReporteVacioSigueSiendoUnArchivoValido() throws IOException {
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    exportador.exportar(definicion(), Stream.empty(), salida);

    try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(salida.toByteArray()))) {
      Sheet hoja = libro.getSheetAt(0);
      assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fecha");
      assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).isEqualTo("TOTAL");
      assertThat(hoja.getRow(1).getCell(3).getNumericCellValue()).isZero();
    }
  }
}
