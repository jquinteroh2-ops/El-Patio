package co.elpatio.infraestructura.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentosEnDiscoTest {

  @TempDir Path carpeta;

  private static final byte[] PDF = "%PDF-1.7\nhoja de vida".getBytes(StandardCharsets.US_ASCII);

  private DocumentosEnDisco almacen() {
    return new DocumentosEnDisco(carpeta.toString());
  }

  @Test
  void guardaYRecuperaElDocumentoIntacto() {
    DocumentosEnDisco almacen = almacen();

    String referencia = almacen.guardar("Hoja de vida Ana.pdf", PDF);

    assertThat(almacen.existe(referencia)).isTrue();
    // Byte por byte: un documento es evidencia y no se recomprime como una foto.
    assertThat(almacen.leer(referencia)).isEqualTo(PDF);
    assertThat(almacen.tipoDeContenido(referencia)).isEqualTo("application/pdf");
  }

  /**
   * El nombre de guardado lo pone el almacen, nunca quien sube.
   *
   * Un nombre que viene de afuera puede traer rutas adentro y escribir donde no
   * debe, y ademas puede identificar a la persona en un listado de la carpeta.
   */
  @Test
  void elNombreDeGuardadoEsUnUuidYNoElOriginal() {
    String referencia = almacen().guardar("Ana Pérez - hoja de vida.pdf", PDF);

    assertThat(referencia).doesNotContain("Ana").doesNotContain("Pérez").endsWith(".pdf");
    // 36 del UUID más «.pdf».
    assertThat(referencia).hasSize(40);
  }

  /** Un nombre con rutas dentro no puede sacar el archivo de la carpeta. */
  @Test
  void unNombreConRutasNoEscapaDeLaCarpeta() throws IOException {
    almacen().guardar("../../../fuera.pdf", PDF);

    try (var archivos = Files.list(carpeta)) {
      assertThat(archivos.toList()).hasSize(1);
    }
    assertThat(Files.exists(carpeta.getParent().resolve("fuera.pdf"))).isFalse();
  }

  /** Y una referencia con rutas tampoco puede leer fuera de la carpeta. */
  @Test
  void unaReferenciaConRutasNoLeeFueraDeLaCarpeta() throws IOException {
    Path secreto = carpeta.getParent().resolve("secreto.txt");
    Files.writeString(secreto, "no se debe poder leer");

    assertThat(almacen().leer("../secreto.txt")).isEmpty();
    assertThat(almacen().existe("../secreto.txt")).isFalse();

    // Y borrar tampoco lo toca.
    almacen().borrar("../secreto.txt");
    assertThat(Files.exists(secreto)).isTrue();
  }

  @Test
  void rechazaLoQueNoSeaPdfNiImagen() {
    byte[] ejecutable = "MZ...".getBytes(StandardCharsets.ISO_8859_1);

    assertThatThrownBy(() -> almacen().guardar("hoja-de-vida.pdf", ejecutable))
        .isInstanceOf(ReglaDeNegocioError.class);
  }

  @Test
  void rechazaUnArchivoVacio() {
    assertThatThrownBy(() -> almacen().guardar("vacio.pdf", new byte[0]))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("vacío");
  }

  @Test
  void rechazaLoQuePeseMasDeCincoMegas() {
    byte[] enorme = new byte[6 * 1024 * 1024];
    System.arraycopy(PDF, 0, enorme, 0, PDF.length);

    assertThatThrownBy(() -> almacen().guardar("gordo.pdf", enorme))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("5 MB");
  }

  /**
   * El borrado es de verdad: es el derecho de supresion de la Ley 1581.
   *
   * Si el archivo quedara en el disco despues de eliminar la fila, se le habria
   * dicho al titular que sus datos se borraron sin que fuera cierto.
   */
  @Test
  void borrarEliminaElArchivoDelDisco() throws IOException {
    DocumentosEnDisco almacen = almacen();
    String referencia = almacen.guardar("hoja.pdf", PDF);

    almacen.borrar(referencia);

    assertThat(almacen.existe(referencia)).isFalse();
    try (var archivos = Files.list(carpeta)) {
      assertThat(archivos.toList()).isEmpty();
    }
  }

  /** Borrar lo que ya no está no es un error: el objetivo es que no quede. */
  @Test
  void borrarDosVecesNoFalla() {
    DocumentosEnDisco almacen = almacen();
    String referencia = almacen.guardar("hoja.pdf", PDF);
    almacen.borrar(referencia);

    assertThatCode(() -> almacen.borrar(referencia)).doesNotThrowAnyException();
  }

  /** No puede quedar un `.parcial` a la vista de nadie. */
  @Test
  void noDejaArchivosParciales() throws IOException {
    almacen().guardar("hoja.pdf", PDF);

    try (var archivos = Files.list(carpeta)) {
      assertThat(archivos.map(Path::toString)).noneMatch(n -> n.endsWith(".parcial"));
    }
  }

  @Test
  void leerAlgoQueNoExisteDevuelveVacioEnVezDeReventar() {
    assertThat(almacen().leer("no-existe.pdf")).isEmpty();
    assertThat(almacen().existe("no-existe.pdf")).isFalse();
  }
}
