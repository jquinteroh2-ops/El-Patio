package co.elpatio.infraestructura.documentos;

import co.elpatio.dominio.archivo.TipoDeArchivo;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.AlmacenDeDocumentos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger registro = LoggerFactory.getLogger(DocumentosEnDisco.class);

  private final Path carpeta;

  /**
   * Prepara la carpeta, y si no puede NO tumba el arranque.
   *
   * Esta clase se construye siempre, así que una excepción aquí deja al
   * restaurante entero sin sistema —sin comandas, sin cocina, sin caja— porque
   * no se pudo crear un directorio donde guardar hojas de vida. Esa desproporción
   * es el fallo, no el directorio.
   *
   * Ocurrió de verdad en el primer despliegue: el contenedor corre como un
   * usuario sin privilegios y la carpeta de trabajo era de root, así que
   * `createDirectories` fallaba y Spring no levantaba el contexto. El error que
   * salía no mencionaba los permisos por ningún lado.
   *
   * Así que se avisa fuerte en la bitácora y se sigue. Guardar un documento sí
   * falla, con un mensaje que dice qué pasa, y lo demás funciona.
   */
  public DocumentosEnDisco(@Value("${elpatio.documentos.ruta}") String ruta) {
    this.carpeta = Path.of(ruta).toAbsolutePath().normalize();
    try {
      Files.createDirectories(carpeta);
    } catch (IOException e) {
      registro.error(
          "NO SE PUDO PREPARAR LA CARPETA DE DOCUMENTOS ({}). Las hojas de vida y los adjuntos"
              + " de PQR no se van a poder guardar. Revise los permisos o el volumen montado."
              + " El resto del sistema sigue funcionando.",
          carpeta,
          e);
    }
  }

  /** Si la carpeta está utilizable. Lo pregunta `guardar` antes de escribir. */
  private boolean carpetaLista() {
    if (Files.isDirectory(carpeta) && Files.isWritable(carpeta)) return true;
    try {
      // Se reintenta: puede que el volumen se montara después del arranque.
      Files.createDirectories(carpeta);
      return Files.isWritable(carpeta);
    } catch (IOException e) {
      return false;
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

    // Aquí sí se falla, y con un mensaje que diga qué pasa: quien llenó el
    // formulario tiene que enterarse de que su archivo no quedó guardado, en vez
    // de recibir un «gracias» sobre un archivo que no existe.
    if (!carpetaLista()) {
      throw new IllegalStateException(
          "El almacenamiento de documentos no está disponible: no se puede escribir en "
              + carpeta
              + ". Revise los permisos o el volumen montado.");
    }

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
