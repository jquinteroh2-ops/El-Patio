package co.elpatio.infraestructura.imagenes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ReductorDeImagenesTest {

  /** Lo que Cloudinary acepta por imagen en el plan gratuito. */
  private static final long TOPE_DE_CLOUDINARY = 10L * 1024 * 1024;

  /**
   * Una foto con ruido, no un rectangulo de color.
   *
   * Un color plano se comprime a nada y haria pasar la prueba del peso sin
   * probar nada: una foto de verdad tiene detalle en cada pixel, y es ese
   * detalle el que hace grande al archivo.
   */
  private static byte[] fotoDe(int ancho, int alto) throws Exception {
    BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
    Random azar = new Random(7);
    Graphics2D g = imagen.createGraphics();
    for (int i = 0; i < (ancho * alto) / 40; i++) {
      g.setColor(new Color(azar.nextInt(0xFFFFFF)));
      g.fillRect(azar.nextInt(ancho), azar.nextInt(alto), 6, 6);
    }
    g.dispose();

    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    ImageIO.write(imagen, "jpg", salida);
    return salida.toByteArray();
  }

  private static BufferedImage abrir(byte[] contenido) throws Exception {
    return ImageIO.read(new ByteArrayInputStream(contenido));
  }

  // -------------------------------------------------------------------------

  @Test
  void encoge_el_lado_mayor_al_maximo_pedido() throws Exception {
    // Vertical 9:16, que es como vienen las fotos de la sesion del restaurante.
    byte[] reducida = ReductorDeImagenes.aJpeg(fotoDe(1200, 2133), 800, 0.86f);

    BufferedImage resultado = abrir(reducida);
    assertThat(resultado.getHeight()).isEqualTo(800);
    // La proporcion se conserva: 800 * 1200 / 2133 = 450.
    assertThat(resultado.getWidth()).isEqualTo(450);
  }

  @Test
  void una_foto_de_camara_termina_cabiendo_en_cloudinary() throws Exception {
    // El caso que motivo todo esto: Cloudinary corta en 10 MB y la camara del
    // restaurante entrega archivos de 12 a 18.
    byte[] reducida = ReductorDeImagenes.aJpeg(fotoDe(3376, 6000), 2600, 0.86f);

    assertThat(reducida.length).isLessThan((int) TOPE_DE_CLOUDINARY);
    assertThat(abrir(reducida).getHeight()).isEqualTo(2600);
  }

  @Test
  void una_foto_pequena_no_se_agranda() throws Exception {
    // Sin el tope en 1.0 del factor, una foto de 400 px saldria estirada a 2600
    // y ademas pesando mas que la original.
    byte[] reducida = ReductorDeImagenes.aJpeg(fotoDe(400, 300), 2600, 0.86f);

    BufferedImage resultado = abrir(reducida);
    assertThat(resultado.getWidth()).isEqualTo(400);
    assertThat(resultado.getHeight()).isEqualTo(300);
  }

  @Test
  void lo_que_no_es_una_imagen_se_rechaza_como_regla_de_negocio() {
    // Y no como un fallo del servidor: el que subio el archivo puede
    // arreglarlo, asi que tiene que enterarse.
    byte[] basura = "esto no es una foto".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> ReductorDeImagenes.aJpeg(basura, 1600, 0.82f))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("no es una imagen");
  }
}
