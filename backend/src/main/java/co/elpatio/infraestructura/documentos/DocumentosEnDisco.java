package co.elpatio.infraestructura.documentos;

import co.elpatio.dominio.archivo.TipoDeArchivo;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.AlmacenDeDocumentos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Las hojas de vida y los adjuntos de PQR, en un disco.
 *
 * En Railway ese disco tiene que ser un volumen montado, igual que el de las
 * fotos. Sin volumen, las hojas de vida se pierden en el siguiente despliegue
 * y el aspirante que se postulo ayer desaparece sin que nadie se entere.
 *
 * <p><b>Para el dia del respaldo:</b> `respaldar.sh` vuelca la base, y estos
 * archivos no estan en la base. Un respaldo completo tiene que llevarse tambien
 * esta carpeta.
 */
@Component
public class DocumentosEnDisco implements AlmacenDeDocumentos {

  /**
   * El tope de peso.
   *
   * Cinco megas es de sobra para una hoja de vida y para la foto de un recibo.
   * Por encima de eso casi siempre es un escaneo sin comprimir, y aceptarlo
   * llena el volumen con archivos que nadie va a abrir dos veces.
   */
  private static final long PESO_MAXIMO_BYTES = 5L * 1024 * 1024;

  private final Path carpeta;

  public DocumentosEnDisco(@Value("${elpatio.documentos.ruta}") String ruta) {
    this.carpeta = Path.of(ruta).toAbsolutePath().normalize();
    try {
      Files.createDirectories(carpeta);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo preparar la carpeta de documentos: " + carpeta, e);
    }
  }

  @Override
  public String guardar(String nombreOriginal, byte[] contenido) {
    if (contenido == null || contenido.length == 0) {
      throw new ReglaDeNegocioError("El archivo llegó vacío");
    }
    if (contenido.length > PESO_MAXIMO_BYTES) {
      throw new ReglaDeNegocioError("El archivo pesa más de 5 MB. Redúzcalo antes de subirlo");
    }

    // El tipo sale del contenido, no del nombre. `nombreOriginal` solo se usa
    // como dato para mostrarlo después, nunca para construir la ruta: un nombre
    // que viene de afuera puede traer «../» adentro y escribir donde no debe.
    TipoDeArchivo tipo = TipoDeArchivo.exigirPdfOImagen(contenido);

    String referencia = UUID.randomUUID() + "." + tipo.extension();
    Path destino = carpeta.resolve(referencia);

    try {
      // Se escribe con nombre temporal y se renombra. Sin esto, un proceso que
      // lea la carpeta puede toparse con un archivo a medio escribir; el
      // renombrado dentro del mismo sistema de archivos es atómico.
      Path temporal = carpeta.resolve(referencia + ".parcial");
      Files.write(temporal, contenido);
      Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
      return referencia;
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo guardar el documento", e);
    }
  }

  @Override
  public byte[] leer(String referencia) {
    Path archivo = rutaSegura(referencia);
    if (archivo == null || !Files.exists(archivo)) return new byte[0];
    try {
      return Files.readAllBytes(archivo);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer el documento", e);
    }
  }

  @Override
  public boolean existe(String referencia) {
    Path archivo = rutaSegura(referencia);
    return archivo != null && Files.exists(archivo);
  }

  @Override
  public void borrar(String referencia) {
    Path archivo = rutaSegura(referencia);
    if (archivo == null) return;
    try {
      Files.deleteIfExists(archivo);
    } catch (IOException e) {
      // Es el derecho de supresión: si el archivo no se pudo borrar, hay que
      // enterarse. Callarlo dejaría datos personales en el disco después de
      // haberle dicho al titular que se eliminaron.
      throw new IllegalStateException("No se pudo borrar el documento " + referencia, e);
    }
  }

  @Override
  public String tipoDeContenido(String referencia) {
    if (referencia == null) return "application/octet-stream";
    if (referencia.endsWith(".pdf")) return TipoDeArchivo.PDF.tipoDeContenido();
    if (referencia.endsWith(".jpg")) return TipoDeArchivo.JPEG.tipoDeContenido();
    if (referencia.endsWith(".png")) return TipoDeArchivo.PNG.tipoDeContenido();
    return "application/octet-stream";
  }

  /**
   * La ruta del archivo, o null si la referencia intenta salirse de la carpeta.
   *
   * Las referencias las genera este almacén y son UUID, así que en teoría nunca
   * traen rutas. En la práctica una referencia llega desde la base de datos, y
   * la base puede haber sido tocada por una migración, un respaldo restaurado o
   * un script. Comprobar que el resultado sigue estando dentro de la carpeta
   * cuesta una línea y cierra el camino entero.
   */
  private Path rutaSegura(String referencia) {
    if (referencia == null || referencia.isBlank()) return null;
    Path candidato = carpeta.resolve(referencia).normalize();
    return candidato.startsWith(carpeta) ? candidato : null;
  }
}
