package co.elpatio.infraestructura.export;

import co.elpatio.dominio.puertos.Exportador;
import co.elpatio.dominio.reporte.ColumnaReporte;
import co.elpatio.dominio.reporte.DefinicionReporte;
import co.elpatio.dominio.reporte.FilaReporte;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * El reporte como hoja de calculo de verdad.
 *
 * La regla que manda sobre todo lo demas: <b>esto tiene que ser una hoja de
 * datos, no una tabla dibujada</b>. El contador la va a filtrar, ordenar y
 * sumar; si los numeros llegan como texto, nada de eso funciona y el archivo no
 * sirve para lo unico que se pidio. Por eso cada valor entra con su tipo nativo
 * y el formato se aplica con estilos, no convirtiendo a cadena.
 *
 * Usa {@link SXSSFWorkbook} y no XSSFWorkbook: el segundo arma el libro entero
 * en memoria, y un mes de ventas en un contenedor pequeño lo tumba.
 */
@Component
public class ExportadorExcel implements Exportador {

  private static final ZoneId ZONA = ZoneId.of("America/Bogota");

  /**
   * Cuantas filas quedan en memoria antes de volcarse al disco temporal.
   *
   * Con esto el consumo no crece con el tamaño del reporte. El precio es que
   * una fila ya volcada no se puede volver a tocar, lo cual obliga a calcular
   * los anchos por adelantado en vez de auto-dimensionar al final.
   */
  private static final int FILAS_EN_MEMORIA = 200;

  @Override
  public void exportar(
      DefinicionReporte definicion, Stream<FilaReporte> filas, OutputStream destino) {

    try (SXSSFWorkbook libro = new SXSSFWorkbook(FILAS_EN_MEMORIA)) {
      // Sin esto, los archivos temporales del streaming se quedan en el disco
      // del contenedor hasta reiniciarlo.
      libro.setCompressTempFiles(true);

      SXSSFSheet hoja = libro.createSheet(nombreDeHoja(definicion.titulo()));
      Estilos estilos = new Estilos(libro);
      int columnas = definicion.columnas().size();

      // ---- Encabezado ----
      Row cabecera = hoja.createRow(0);
      for (int i = 0; i < columnas; i++) {
        Cell celda = cabecera.createCell(i);
        celda.setCellValue(definicion.columnas().get(i).titulo());
        celda.setCellStyle(estilos.cabecera);
        // El ancho se fija ahora porque `autoSizeColumn` necesita las filas en
        // memoria, y en streaming ya no estan cuando se acaba de escribir.
        hoja.setColumnWidth(i, definicion.columnas().get(i).anchoCaracteres() * 256);
      }

      // La cabecera queda fija al desplazarse y con autofiltro puesto. Es lo
      // primero que hace a mano quien recibe una hoja de mil filas.
      hoja.createFreezePane(0, 1);
      hoja.setAutoFilter(new CellRangeAddress(0, 0, 0, Math.max(columnas - 1, 0)));

      // ---- Datos ----
      long[] sumas = new long[columnas];
      int[] numeroFila = {1};

      filas.forEach(
          fila -> {
            Row destinoFila = hoja.createRow(numeroFila[0]++);
            for (int i = 0; i < columnas; i++) {
              escribir(destinoFila.createCell(i), definicion.columnas().get(i), fila.valor(i), estilos, sumas, i);
            }
          });

      // ---- Totales ----
      escribirTotales(hoja, definicion, estilos, sumas, numeroFila[0]);

      libro.write(destino);
      // Los temporales del streaming los borra el `close()` del try-with-resources.

    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo generar el Excel", e);
    }
  }

  /**
   * Mete el valor con su tipo nativo.
   *
   * `setCellValue(long)` y no `setCellValue(String)`: es la diferencia entre
   * una columna que se puede sumar y una que no.
   */
  private void escribir(
      Cell celda,
      ColumnaReporte columna,
      Object valor,
      Estilos estilos,
      long[] sumas,
      int indice) {

    celda.setCellStyle(estilos.para(columna.tipo()));
    if (valor == null) return;

    switch (columna.tipo()) {
      case DINERO, ENTERO -> {
        long numero = ((Number) valor).longValue();
        celda.setCellValue(numero);
        sumas[indice] += numero;
      }
      case PORCENTAJE -> celda.setCellValue(((Number) valor).doubleValue());
      case FECHA -> celda.setCellValue((LocalDate) valor);
      case FECHA_HORA -> celda.setCellValue(LocalDateTime.ofInstant((Instant) valor, ZONA));
      case TEXTO -> celda.setCellValue(String.valueOf(valor));
    }
  }

  /** La fila de totales, marcada para que se distinga de un dato mas. */
  private void escribirTotales(
      SXSSFSheet hoja,
      DefinicionReporte definicion,
      Estilos estilos,
      long[] sumas,
      int fila) {

    Row totales = hoja.createRow(fila);
    for (int i = 0; i < definicion.columnas().size(); i++) {
      ColumnaReporte columna = definicion.columnas().get(i);
      Cell celda = totales.createCell(i);
      if (i == 0) {
        celda.setCellValue("TOTAL");
        celda.setCellStyle(estilos.totalTexto);
      } else if (columna.sumable()) {
        celda.setCellValue(sumas[i]);
        celda.setCellStyle(
            columna.tipo() == ColumnaReporte.Tipo.DINERO ? estilos.totalDinero : estilos.totalEntero);
      } else {
        celda.setCellStyle(estilos.totalTexto);
      }
    }
  }

  /**
   * Excel no acepta cualquier nombre de hoja: prohibe siete caracteres y corta
   * en 31. Un titulo con barras —«Ventas 01/08 a 23/08»— genera un archivo que
   * no abre, y el fallo aparece al abrirlo, no al generarlo.
   */
  private String nombreDeHoja(String titulo) {
    String limpio = titulo.replaceAll("[\\\\/*?\\[\\]:]", " ").trim();
    if (limpio.isEmpty()) limpio = "Reporte";
    return limpio.length() > 31 ? limpio.substring(0, 31) : limpio;
  }

  @Override
  public String extension() {
    return "xlsx";
  }

  @Override
  public String tipoDeContenido() {
    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  }

  // -------------------------------------------------------------------------

  /**
   * Los estilos del libro, creados una sola vez.
   *
   * POI tiene un limite duro de unos 64.000 estilos por libro. Crear uno por
   * celda revienta ese limite en un reporte grande, y el error que sale no
   * menciona los estilos por ningun lado.
   */
  private static final class Estilos {
    private final Map<ColumnaReporte.Tipo, CellStyle> porTipo = new EnumMap<>(ColumnaReporte.Tipo.class);
    final CellStyle cabecera;
    final CellStyle totalTexto;
    final CellStyle totalDinero;
    final CellStyle totalEntero;

    Estilos(SXSSFWorkbook libro) {
      CreationHelper formatos = libro.getCreationHelper();

      cabecera = libro.createCellStyle();
      Font negrita = libro.createFont();
      negrita.setBold(true);
      cabecera.setFont(negrita);
      cabecera.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      cabecera.setBorderBottom(BorderStyle.THIN);

      porTipo.put(ColumnaReporte.Tipo.TEXTO, libro.createCellStyle());

      CellStyle entero = libro.createCellStyle();
      entero.setDataFormat(formatos.createDataFormat().getFormat("#,##0"));
      entero.setAlignment(HorizontalAlignment.RIGHT);
      porTipo.put(ColumnaReporte.Tipo.ENTERO, entero);

      // Pesos colombianos: separador de miles y sin decimales. El peso no tiene
      // centavos en la practica, y dos ceros por linea solo estorban la lectura.
      CellStyle dinero = libro.createCellStyle();
      dinero.setDataFormat(formatos.createDataFormat().getFormat("\"$\" #,##0"));
      dinero.setAlignment(HorizontalAlignment.RIGHT);
      porTipo.put(ColumnaReporte.Tipo.DINERO, dinero);

      CellStyle fecha = libro.createCellStyle();
      fecha.setDataFormat(formatos.createDataFormat().getFormat("dd/mm/yyyy"));
      porTipo.put(ColumnaReporte.Tipo.FECHA, fecha);

      CellStyle fechaHora = libro.createCellStyle();
      fechaHora.setDataFormat(formatos.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
      porTipo.put(ColumnaReporte.Tipo.FECHA_HORA, fechaHora);

      CellStyle porcentaje = libro.createCellStyle();
      porcentaje.setDataFormat(formatos.createDataFormat().getFormat("0.0\"%\""));
      porcentaje.setAlignment(HorizontalAlignment.RIGHT);
      porTipo.put(ColumnaReporte.Tipo.PORCENTAJE, porcentaje);

      Font negritaTotal = libro.createFont();
      negritaTotal.setBold(true);

      totalTexto = libro.createCellStyle();
      totalTexto.setFont(negritaTotal);
      totalTexto.setBorderTop(BorderStyle.DOUBLE);

      totalDinero = libro.createCellStyle();
      totalDinero.cloneStyleFrom(dinero);
      totalDinero.setFont(negritaTotal);
      totalDinero.setBorderTop(BorderStyle.DOUBLE);

      totalEntero = libro.createCellStyle();
      totalEntero.cloneStyleFrom(entero);
      totalEntero.setFont(negritaTotal);
      totalEntero.setBorderTop(BorderStyle.DOUBLE);
    }

    CellStyle para(ColumnaReporte.Tipo tipo) {
      return porTipo.get(tipo);
    }
  }
}
