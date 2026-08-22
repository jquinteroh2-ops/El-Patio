package co.elpatio.infraestructura.imagenes;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.AlmacenDeImagenes;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Las fotos, en un disco.
 *
 * En Railway ese disco es un volumen montado en el servicio: sobrevive a los
 * despliegues, que es justo lo que el sistema de archivos normal del contenedor
 * NO hace. Si la ruta no apunta a un volumen, las fotos se pierden en el
 * siguiente despliegue y nadie se entera hasta que el sitio queda con los
 * marcos vacios.
 *
 * ADVERTENCIA para el dia del respaldo: `respaldar.sh` vuelca la base, y las
 * fotos no estan en la base. Un respaldo completo tiene que llevarse tambien
 * esta carpeta.
 */
public class AlmacenEnDisco implements AlmacenDeImagenes {

  /**
   * El lado mayor al que se reduce toda foto que entre.
   *
   * 1600 px cubre una pantalla grande sin quedar borroso y deja el archivo en
   * el orden de los 200 KB. La foto original de un celular anda por los 4 MB:
   * seis de esas en una pagina son 24 MB, y esa pagina no abre con datos
   * moviles en Turbaco. Reducir al subir es lo que hace que el sitio siga
   * cargando rapido despues de que el dueno suba veinte fotos.
   */
  private static final int LADO_MAXIMO = 1600;

  /** Calidad del JPEG resultante. 0.82 es donde el ojo deja de notar la perdida. */
  private static final float CALIDAD = 0.82f;

  /** Lo que se acepta que entre. Mas que esto es una foto sin recortar. */
  private static final long PESO_MAXIMO_BYTES = 12L * 1024 * 1024;

  private final Path carpeta;

  public AlmacenEnDisco(String ruta) {
    this.carpeta = Path.of(ruta).toAbsolutePath().normalize();
    try {
      Files.createDirectories(carpeta);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo preparar la carpeta de imagenes: " + carpeta, e);
    }
  }

  @Override
  public String guardar(String nombreOriginal, byte[] contenido) {
    if (contenido == null || contenido.length == 0) {
      throw new ReglaDeNegocioError("La imagen llego vacia");
    }
    if (contenido.length > PESO_MAXIMO_BYTES) {
      throw new ReglaDeNegocioError("La imagen pesa mas de 12 MB. Reduzcala antes de subirla");
    }

    BufferedImage original = leerImagen(contenido);
    // Se guarda SIEMPRE como JPEG reducido, sin importar que llego. Aceptar el
    // archivo tal cual seria dejar entrar un PNG de 8 MB, o peor, cualquier cosa
    // con extension de imagen que el navegador termine interpretando de otro
    // modo. Volver a codificarla garantiza que lo guardado es una imagen.
    byte[] reducida = aJpegReducido(original);

    String nombre = UUID.randomUUID().toString().replace("-", "") + ".jpg";
    try {
      Files.write(carpeta.resolve(nombre), reducida);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo guardar la imagen", e);
    }
    return nombre;
  }

  @Override
  public byte[] leer(String nombre) {
    Path archivo = resolverSeguro(nombre);
    try {
      return Files.exists(archivo) ? Files.readAllBytes(archivo) : new byte[0];
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer la imagen " + nombre, e);
    }
  }

  @Override
  public boolean existe(String nombre) {
    return Files.exists(resolverSeguro(nombre));
  }

  @Override
  public void borrar(String nombre) {
    try {
      Files.deleteIfExists(resolverSeguro(nombre));
    } catch (IOException e) {
      // Que no se pueda borrar un archivo no puede impedir que la publicacion
      // se elimine: lo que le importa al dueno es que deje de salir en el sitio.
      // El archivo huerfano no hace dano y se limpia despues.
    }
  }

  @Override
  public String tipoDeContenido(String nombre) {
    // Todo se guarda como JPEG, asi que no hay que adivinar por la extension.
    return "image/jpeg";
  }

  // ---------------------------------------------------------------------------

  /**
   * Convierte el nombre en una ruta dentro de la carpeta, y en ninguna otra.
   *
   * Los nombres los genera este mismo almacen, pero llegan de vuelta por la URL
   * que pide el navegador, y eso los vuelve entrada del exterior. Sin esta
   * comprobacion, un nombre como `../../application.yml` leeria un archivo que
   * no es una foto.
   */
  private Path resolverSeguro(String nombre) {
    Path destino = carpeta.resolve(nombre).normalize();
    if (!destino.startsWith(carpeta)) {
      throw new ReglaDeNegocioError("Nombre de imagen invalido");
    }
    return destino;
  }

  private BufferedImage leerImagen(byte[] contenido) {
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

  private byte[] aJpegReducido(BufferedImage original) {
    int ancho = original.getWidth();
    int alto = original.getHeight();
    double factor = Math.min(1.0, (double) LADO_MAXIMO / Math.max(ancho, alto));
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
    g.setColor(java.awt.Color.WHITE);
    g.fillRect(0, 0, nuevoAncho, nuevoAlto);
    g.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
    g.dispose();

    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    try {
      var escritores = ImageIO.getImageWritersByFormatName("jpg");
      var escritor = escritores.next();
      var parametros = escritor.getDefaultWriteParam();
      parametros.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
      parametros.setCompressionQuality(CALIDAD);
      try (var flujo = ImageIO.createImageOutputStream(salida)) {
        escritor.setOutput(flujo);
        escritor.write(null, new javax.imageio.IIOImage(destino, null, null), parametros);
      } finally {
        escritor.dispose();
      }
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo comprimir la imagen", e);
    }
    return salida.toByteArray();
  }
}
