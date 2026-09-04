package co.elpatio.infraestructura.imagenes;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

/**
 * Leer una imagen y reducirla, que es lo unico que los dos almacenes hacen
 * igual.
 *
 * Vive aparte porque estaba escrito dos veces y no lo estaba: el almacen en
 * disco reducia y el de Cloudinary no, y cuando hizo falta que Cloudinary
 * tambien redujera —para las fotos de camara, que no le caben— habia que
 * copiar cuarenta lineas de Java2D de un archivo al otro. Copiarlas es como
 * empiezan las dos versiones que se van separando.
 */
final class ReductorDeImagenes {

  private ReductorDeImagenes() {}

  static BufferedImage leer(byte[] contenido) {
    try {
      BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(contenido));
      if (imagen == null) {
        throw new ReglaDeNegocioError("El archivo no es una imagen que se pueda leer");
      }
      return imagen;
    } catch (IOException e) {
      throw new ReglaDeNegocioError("El archivo no es una imagen que se pueda leer");
    }
  }

  /** La imagen como JPEG, con el lado mayor recortado a {@code ladoMaximo}. */
  static byte[] aJpeg(byte[] contenido, int ladoMaximo, float calidad) {
    BufferedImage original = leer(contenido);

    int ancho = original.getWidth();
    int alto = original.getHeight();
    double factor = Math.min(1.0, (double) ladoMaximo / Math.max(ancho, alto));
    int nuevoAncho = Math.max(1, (int) Math.round(ancho * factor));
    int nuevoAlto = Math.max(1, (int) Math.round(alto * factor));

    // TYPE_INT_RGB y no ARGB: el JPEG no tiene transparencia, y un PNG con
    // fondo transparente guardado como JPEG sin este paso sale con el fondo
    // negro. Se pinta blanco debajo, que es lo que espera cualquiera.
    BufferedImage destino = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = destino.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, nuevoAncho, nuevoAlto);
    g.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
    g.dispose();

    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    try {
      var escritores = ImageIO.getImageWritersByFormatName("jpg");
      var escritor = escritores.next();
      var parametros = escritor.getDefaultWriteParam();
      parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      parametros.setCompressionQuality(calidad);
      try (var flujo = ImageIO.createImageOutputStream(salida)) {
        escritor.setOutput(flujo);
        escritor.write(null, new IIOImage(destino, null, null), parametros);
      } finally {
        escritor.dispose();
      }
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo comprimir la imagen", e);
    }
    return salida.toByteArray();
  }
}
