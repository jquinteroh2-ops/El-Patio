package co.elpatio.agente;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/**
 * Lo que el agente lee de {@code agente.properties} al arrancar.
 *
 * Un archivo de texto y no variables de entorno: quien lo instala en el
 * restaurante tiene que poder abrirlo con el Bloc de notas, cambiar la llave y
 * reiniciar, sin saber que es una variable de entorno.
 */
public record Configuracion(
    /** La API de El Patio, sin barra final. Ej.: https://backend-production-3cd7.up.railway.app */
    String urlNube,
    /** La misma llave que ELPATIO_ERP_AGENTE_TOKEN en Railway. */
    String llave,
    /** Como se le entrega la venta a Globalsoft. Hoy solo "carpeta". */
    String modoEntrega,
    /** Donde se dejan las ventas en modo carpeta. */
    Path carpetaEntrega,
    /** Donde el agente guarda su registro de entregas y su bitacora. */
    Path carpetaDatos,
    /** Cada cuanto pregunta si hay ventas nuevas. */
    Duration intervalo,
    /** Cuantas ventas pide por vez. */
    int tanda) {

  public static Configuracion leer(Path archivo) throws IOException {
    Properties p = new Properties();
    try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
      p.load(lector);
    }
    Path base = archivo.toAbsolutePath().getParent();

    String url = obligatoria(p, "nube.url");
    while (url.endsWith("/")) url = url.substring(0, url.length() - 1);

    return new Configuracion(
        url,
        obligatoria(p, "nube.llave"),
        p.getProperty("entrega.modo", "carpeta").trim().toLowerCase(),
        base.resolve(p.getProperty("entrega.carpeta", "ventas-para-globalsoft").trim()),
        base.resolve(p.getProperty("datos.carpeta", "datos").trim()),
        Duration.ofSeconds(Long.parseLong(p.getProperty("intervalo.segundos", "30").trim())),
        Integer.parseInt(p.getProperty("tanda", "20").trim()));
  }

  private static String obligatoria(Properties p, String clave) {
    String valor = p.getProperty(clave, "").trim();
    if (valor.isEmpty()) {
      throw new IllegalArgumentException("Falta " + clave + " en agente.properties");
    }
    return valor;
  }
}
