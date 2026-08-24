package co.elpatio.infraestructura.export;

import static org.assertj.core.api.Assertions.assertThat;

import co.elpatio.dominio.reporte.ColumnaReporte;
import co.elpatio.dominio.reporte.DefinicionReporte;
import co.elpatio.dominio.reporte.FilaReporte;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ExportadorPdfTest {

  private final ExportadorPdf exportador = new ExportadorPdf("Restaurante El Patio S.A.S.", "901.234.567-8");

  private static DefinicionReporte definicion(List<String> filtros) {
    return new DefinicionReporte(
        "Ventas por periodo",
        "ventas",
        List.of(
            ColumnaReporte.fechaHora("Fecha"),
            ColumnaReporte.texto("Mesa", 14),
            ColumnaReporte.dinero("Total")),
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 24),
        filtros,
        "Ana");
  }

  private byte[] generar(DefinicionReporte definicion, Stream<FilaReporte> filas) {
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    exportador.exportar(definicion, filas, salida);
    return salida.toByteArray();
  }

  @Test
  void generaUnPdfValido() {
    byte[] pdf =
        generar(
            definicion(List.of()),
            Stream.of(FilaReporte.de(Instant.parse("2026-08-24T20:15:00Z"), "Mesa 4", 174640L)));

    assertThat(pdf).isNotEmpty();
    // Todo PDF empieza por esta firma. Es la comprobacion barata de que lo que
    // salio es un PDF y no un volcado de error.
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
  }

  /**
   * Un reporte largo tiene que paginar, y cada pagina repetir los encabezados.
   *
   * La segunda hoja de una tabla sin encabezados es una reja de numeros sin
   * nombre. Aqui se comprueba lo que se puede afirmar sin abrir el PDF: que
   * paginó de verdad.
   */
  @Test
  void unReporteLargoOcupaVariasPaginas() {
    Stream<FilaReporte> muchas =
        IntStream.range(0, 400)
            .mapToObj(
                i -> FilaReporte.de(Instant.parse("2026-08-24T20:15:00Z"), "Mesa " + i, 50000L + i));

    String contenido = new String(generar(definicion(List.of()), muchas));

    // Cada pagina del documento aparece como un objeto /Type /Page.
    int paginas = contenido.split("/Type\\s*/Page[^s]").length - 1;
    assertThat(paginas).isGreaterThan(1);
  }

  /** Un reporte sin filas tiene que salir igual, con su encabezado y su total. */
  @Test
  void unReporteVacioSigueGenerandoDocumento() {
    byte[] pdf = generar(definicion(List.of()), Stream.empty());

    assertThat(pdf).isNotEmpty();
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
  }

  /**
   * Con muchas columnas el PDF se apaisa solo.
   *
   * En vertical, once columnas salen tan apretadas que los encabezados se
   * parten en tres lineas y la tabla deja de leerse.
   */
  @Test
  void conMuchasColumnasSeApaisa() {
    List<ColumnaReporte> once =
        IntStream.range(0, 11).mapToObj(i -> ColumnaReporte.dinero("Col " + i)).toList();

    DefinicionReporte ancha =
        new DefinicionReporte(
            "Cierres", "cierres", once, LocalDate.now(), LocalDate.now(), List.of(), "Ana");

    assertThat(ancha.prefiereApaisado()).isTrue();
    assertThat(definicion(List.of()).prefiereApaisado()).isFalse();

    // Y que apaisado siga generando un documento valido.
    assertThat(new String(generar(ancha, Stream.empty()), 0, 5)).isEqualTo("%PDF-");
  }

  /**
   * Los filtros van impresos.
   *
   * Un PDF que dice «Ventas» sin decir que solo trae efectivo no sirve como
   * respaldo: dentro de tres meses nadie puede afirmar que representa la cifra.
   */
  @Test
  void losFiltrosAplicadosViajanEnLaDefinicion() {
    DefinicionReporte conFiltro = definicion(List.of("Medio de pago: Efectivo"));

    assertThat(conFiltro.filtrosAplicados()).containsExactly("Medio de pago: Efectivo");
    assertThat(new String(generar(conFiltro, Stream.empty()), 0, 5)).isEqualTo("%PDF-");
  }
}
