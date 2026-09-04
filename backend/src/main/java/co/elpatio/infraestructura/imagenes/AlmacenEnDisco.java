package co.elpatio.infraestructura.imagenes;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.AlmacenDeImagenes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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

  /**
   * Lo que se acepta que entre.
   *
   * 25 MB da para una foto de camara sin recortar, que es justo lo que sube el
   * restaurante: la sesion del fotografo son archivos de 12 a 18 MB y el
   * encuadre y el fondo son parte de la foto, asi que no se tocan antes de
   * subirlas. Lo que se GUARDA no depende de esto: pase lo que pase, abajo se
   * reduce a {@code LADO_MAXIMO} y queda en el orden de los 200 KB.
   */
  private static final long PESO_MAXIMO_BYTES = 25L * 1024 * 1024;

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
      throw new ReglaDeNegocioError("La imagen pesa mas de 25 MB. Reduzcala antes de subirla");
    }

    // Se guarda SIEMPRE como JPEG reducido, sin importar que llego. Aceptar el
    // archivo tal cual seria dejar entrar un PNG de 8 MB, o peor, cualquier cosa
    // con extension de imagen que el navegador termine interpretando de otro
    // modo. Volver a codificarla garantiza que lo guardado es una imagen.
    byte[] reducida = ReductorDeImagenes.aJpeg(contenido, LADO_MAXIMO, CALIDAD);

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

}
