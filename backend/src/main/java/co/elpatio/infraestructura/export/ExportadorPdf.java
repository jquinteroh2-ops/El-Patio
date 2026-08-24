package co.elpatio.infraestructura.export;

import co.elpatio.dominio.puertos.Exportador;
import co.elpatio.dominio.reporte.ColumnaReporte;
import co.elpatio.dominio.reporte.DefinicionReporte;
import co.elpatio.dominio.reporte.FilaReporte;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * El reporte como documento de respaldo.
 *
 * A diferencia del Excel, este no se manipula: se archiva, se imprime y se
 * entrega. Por eso lo que importa aqui no es que los numeros sean sumables sino
 * que el papel se explique solo dentro de tres meses: que reporte es, de que
 * periodo, con que filtros y quien lo saco. Un PDF sin esos cuatro datos no
 * sirve como respaldo de nada.
 */
@Component
public class ExportadorPdf implements Exportador {

  private static final ZoneId ZONA = ZoneId.of("America/Bogota");
  private static final Locale CO = Locale.forLanguageTag("es-CO");
  private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy", CO);
  private static final DateTimeFormatter DIA_HORA =
      DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", CO);

  private static final Color TINTA = new Color(0x1A, 0x18, 0x16);
  private static final Color GRIS = new Color(0x6B, 0x66, 0x60);
  private static final Color FONDO_CABECERA = new Color(0xEC, 0xE8, 0xE0);

  private final String razonSocial;
  private final String nit;

  public ExportadorPdf(
      @Value("${elpatio.reportes.razon-social:Restaurante El Patio}") String razonSocial,
      @Value("${elpatio.reportes.nit:}") String nit) {
    this.razonSocial = razonSocial;
    this.nit = nit;
  }

  @Override
  public void exportar(
      DefinicionReporte definicion, Stream<FilaReporte> filas, OutputStream destino) {

    // Apaisado cuando hay muchas columnas: en vertical se aprietan hasta que
    // los encabezados se parten en tres lineas y la tabla deja de leerse.
    Rectangle tamano = definicion.prefiereApaisado() ? PageSize.LETTER.rotate() : PageSize.LETTER;
    Document documento = new Document(tamano, 36, 36, 42, 46);

    try {
      PdfWriter escritor = PdfWriter.getInstance(documento, destino);
      // El pie con «Pagina X de Y» y quien lo genero. Va por evento porque el
      // total de paginas no se sabe hasta cerrar el documento.
      escritor.setPageEvent(new PieDePagina(definicion.generadoPor()));
      documento.open();

      escribirEncabezado(documento, definicion);
      escribirTabla(documento, definicion, filas);

      documento.close();
    } catch (DocumentException e) {
      throw new IllegalStateException("No se pudo generar el PDF", e);
    }
  }

  private void escribirEncabezado(Document documento, DefinicionReporte definicion)
      throws DocumentException {

    Font fuenteMarca = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, TINTA);
    Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TINTA);
    Font fuenteMenuda = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, GRIS);

    Paragraph marca = new Paragraph(razonSocial, fuenteMarca);
    documento.add(marca);

    if (!nit.isBlank()) {
      documento.add(new Paragraph("NIT " + nit, fuenteMenuda));
    }

    Paragraph titulo = new Paragraph(definicion.titulo(), fuenteTitulo);
    titulo.setSpacingBefore(10);
    titulo.setSpacingAfter(2);
    documento.add(titulo);

    if (definicion.desde() != null && definicion.hasta() != null) {
      documento.add(
          new Paragraph(
              "Del " + DIA.format(definicion.desde()) + " al " + DIA.format(definicion.hasta()),
              fuenteMenuda));
    }

    // Los filtros, en claro. Es lo que convierte una cifra suelta en una cifra
    // que alguien puede volver a justificar dentro de tres meses.
    if (definicion.filtrosAplicados() != null && !definicion.filtrosAplicados().isEmpty()) {
      Paragraph filtros =
          new Paragraph("Filtros: " + String.join(" · ", definicion.filtrosAplicados()), fuenteMenuda);
      filtros.setSpacingBefore(3);
      documento.add(filtros);
    }
  }

  private void escribirTabla(
      Document documento, DefinicionReporte definicion, Stream<FilaReporte> filas)
      throws DocumentException {

    int columnas = definicion.columnas().size();
    PdfPTable tabla = new PdfPTable(columnas);
    tabla.setWidthPercentage(100);
    tabla.setSpacingBefore(14);
    tabla.setWidths(anchos(definicion));
    // Los encabezados se repiten en cada pagina: sin esto, la segunda hoja de
    // una tabla larga es una reja de numeros sin nombre.
    tabla.setHeaderRows(1);

    Font fuenteCabecera = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, TINTA);
    Font fuenteCelda = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, TINTA);
    Font fuenteTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TINTA);

    for (ColumnaReporte columna : definicion.columnas()) {
      PdfPCell celda = new PdfPCell(new Phrase(columna.titulo(), fuenteCabecera));
      celda.setBackgroundColor(FONDO_CABECERA);
      celda.setPadding(5);
      celda.setBorderColor(GRIS);
      celda.setHorizontalAlignment(
          columna.alineaDerecha() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
      tabla.addCell(celda);
    }

    long[] sumas = new long[columnas];

    filas.forEach(
        fila -> {
          for (int i = 0; i < columnas; i++) {
            ColumnaReporte columna = definicion.columnas().get(i);
            Object valor = fila.valor(i);
            if (columna.sumable() && valor instanceof Number numero) {
              sumas[i] += numero.longValue();
            }
            PdfPCell celda = new PdfPCell(new Phrase(comoTexto(columna, valor), fuenteCelda));
            celda.setPadding(4);
            celda.setBorderColor(new Color(0xDD, 0xD8, 0xD0));
            celda.setHorizontalAlignment(
                columna.alineaDerecha() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            tabla.addCell(celda);
          }
        });

    // Fila de totales, separada del cuerpo para que no se lea como un dato mas.
    for (int i = 0; i < columnas; i++) {
      ColumnaReporte columna = definicion.columnas().get(i);
      String texto = i == 0 ? "TOTAL" : columna.sumable() ? comoTexto(columna, sumas[i]) : "";
      PdfPCell celda = new PdfPCell(new Phrase(texto, fuenteTotal));
      celda.setPadding(5);
      celda.setBackgroundColor(FONDO_CABECERA);
      celda.setBorderColor(GRIS);
      celda.setBorderWidthTop(1.2f);
      celda.setHorizontalAlignment(
          columna.alineaDerecha() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
      tabla.addCell(celda);
    }

    documento.add(tabla);
  }

  /** Reparte el ancho segun lo declarado por cada columna. */
  private float[] anchos(DefinicionReporte definicion) {
    float[] anchos = new float[definicion.columnas().size()];
    for (int i = 0; i < anchos.length; i++) {
      anchos[i] = definicion.columnas().get(i).anchoCaracteres();
    }
    return anchos;
  }

  /**
   * El valor como se imprime.
   *
   * Aqui SI se convierte a texto, al reves que en el Excel: un PDF no se suma
   * ni se filtra, se lee. Y se lee en formato colombiano —punto de miles, fecha
   * dd/MM/yyyy—, que es lo que espera quien lo recibe.
   */
  private String comoTexto(ColumnaReporte columna, Object valor) {
    if (valor == null) return "";
    return switch (columna.tipo()) {
      case DINERO -> String.format(CO, "$ %,d", ((Number) valor).longValue());
      case ENTERO -> String.format(CO, "%,d", ((Number) valor).longValue());
      case PORCENTAJE -> String.format(CO, "%.1f %%", ((Number) valor).doubleValue());
      case FECHA -> DIA.format((LocalDate) valor);
      case FECHA_HORA -> DIA_HORA.format(((Instant) valor).atZone(ZONA));
      case TEXTO -> String.valueOf(valor);
    };
  }

  @Override
  public String extension() {
    return "pdf";
  }

  @Override
  public String tipoDeContenido() {
    return "application/pdf";
  }

  // -------------------------------------------------------------------------

  /**
   * El pie de cada pagina.
   *
   * Se escribe sobre una plantilla reservada porque el total de paginas no se
   * conoce hasta cerrar el documento: se deja el hueco en cada pagina y se
   * rellena al final con el numero verdadero.
   */
  private static final class PieDePagina extends com.lowagie.text.pdf.PdfPageEventHelper {

    private final String generadoPor;
    private com.lowagie.text.pdf.PdfTemplate totalPaginas;
    private final Font fuente = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, GRIS);

    PieDePagina(String generadoPor) {
      this.generadoPor = generadoPor == null ? "" : generadoPor;
    }

    @Override
    public void onOpenDocument(PdfWriter escritor, Document documento) {
      totalPaginas = escritor.getDirectContent().createTemplate(30, 12);
    }

    @Override
    public void onEndPage(PdfWriter escritor, Document documento) {
      Rectangle pagina = documento.getPageSize();

      String sello =
          "Generado el "
              + DIA_HORA.format(Instant.now().atZone(ZONA))
              + (generadoPor.isBlank() ? "" : " por " + generadoPor);

      com.lowagie.text.pdf.ColumnText.showTextAligned(
          escritor.getDirectContent(),
          Element.ALIGN_LEFT,
          new Phrase(sello, fuente),
          documento.leftMargin(),
          pagina.getBottom(28),
          0);

      String numero = "Página " + escritor.getPageNumber() + " de ";
      float anchoNumero =
          fuente.getCalculatedBaseFont(true).getWidthPoint(numero, fuente.getCalculatedSize());
      float derecha = pagina.getWidth() - documento.rightMargin();

      com.lowagie.text.pdf.ColumnText.showTextAligned(
          escritor.getDirectContent(),
          Element.ALIGN_LEFT,
          new Phrase(numero, fuente),
          derecha - anchoNumero - 22,
          pagina.getBottom(28),
          0);

      escritor.getDirectContent().addTemplate(totalPaginas, derecha - 22, pagina.getBottom(28));
    }

    @Override
    public void onCloseDocument(PdfWriter escritor, Document documento) {
      totalPaginas.beginText();
      totalPaginas.setFontAndSize(fuente.getCalculatedBaseFont(true), fuente.getCalculatedSize());
      totalPaginas.setColorFill(GRIS);
      // getPageNumber() en el cierre ya devuelve una pagina de mas.
      totalPaginas.showText(String.valueOf(escritor.getPageNumber() - 1));
      totalPaginas.endText();
    }
  }
}
