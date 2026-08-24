package co.elpatio.infraestructura.erp;

import static org.assertj.core.api.Assertions.assertThat;

import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.erp.VentaParaErp;
import co.elpatio.dominio.puertos.FacturacionExterna;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FacturacionPorArchivoTest extends ContratoFacturacionExterna {

  @TempDir Path carpeta;

  @Override
  protected FacturacionExterna adaptador() {
    return new FacturacionPorArchivo(carpeta.toString());
  }

  @Test
  void dejaUnArchivoConLaVenta() throws IOException {
    ResultadoFacturacion resultado = adaptador().emitirDocumento(ventaDeEjemplo());

    assertThat(resultado.desenlace()).isEqualTo(ResultadoFacturacion.Desenlace.EN_ESPERA);
    try (var archivos = Files.list(carpeta)) {
      List<Path> generados = archivos.toList();
      assertThat(generados).hasSize(1);
      String contenido = Files.readString(generados.get(0));
      assertThat(contenido).startsWith(MapeadorGlobalsoft.ENCABEZADO);
      // Una fila por linea de consumo, mas el encabezado.
      assertThat(contenido.lines()).hasSize(3);
      assertThat(contenido).contains("Robalo al bijao").contains("Ceviche El Patio");
    }
  }

  /**
   * Reintentar no puede depositar la venta dos veces.
   *
   * Es la misma regla de idempotencia del contrato, vista desde el disco: dos
   * archivos con la misma venta son dos documentos cuando el importador los
   * procese.
   */
  @Test
  void reintentarNoDuplicaElArchivo() throws IOException {
    VentaParaErp venta = ventaConLlave("llave-repetida");

    adaptador().emitirDocumento(venta);
    ResultadoFacturacion segunda = adaptador().emitirDocumento(venta);

    try (var archivos = Files.list(carpeta)) {
      assertThat(archivos.toList()).hasSize(1);
    }
    assertThat(segunda.motivo()).contains("Ya estaba depositado");
  }

  /**
   * No puede quedar un archivo a medio escribir a la vista del importador.
   *
   * Se escribe con nombre temporal y se renombra, asi que al terminar no debe
   * sobrar ningun `.parcial`.
   */
  @Test
  void noDejaArchivosParciales() throws IOException {
    adaptador().emitirDocumento(ventaDeEjemplo());

    try (var archivos = Files.list(carpeta)) {
      assertThat(archivos.map(Path::toString)).noneMatch(n -> n.endsWith(".parcial"));
    }
  }

  /**
   * Un plato con punto y coma en el nombre partiria la fila en dos y correria
   * todas las columnas siguientes: el precio caeria en la casilla de la
   * cantidad. Se limpia antes de escribir.
   */
  @Test
  void unNombreConElSeparadorNoRompeLaFila() {
    VentaParaErp venta =
        new VentaParaErp(
            "llave-1", "pg_1", "or_1", 5, java.time.Instant.parse("2026-08-24T20:15:00Z"),
            "mesa", "presencial",
            List.of(new VentaParaErp.LineaVenta("p18", "Mar y tierra; del Patio", 1, 72000, 72000)),
            72000, 5760, 8, 0, 0, 0, 77760, "efectivo", List.of(), "Ana");

    String plano = MapeadorGlobalsoft.aPlano(venta);

    assertThat(plano.lines()).hasSize(1);
    assertThat(plano.split(";")).hasSize(20);
    assertThat(plano).contains("Mar y tierra  del Patio");
  }
}
