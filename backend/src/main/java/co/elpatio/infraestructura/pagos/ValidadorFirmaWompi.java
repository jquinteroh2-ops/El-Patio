package co.elpatio.infraestructura.pagos;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Verifica que un evento de webhook lo mando Wompi y no un tercero que
 * adivino la URL.
 *
 * Wompi firma cada evento con el SHA-256 de: los valores de las propiedades
 * que declara en `signature.properties` (en ese orden, leidas del `data` del
 * evento), seguidos del `timestamp` y del secreto de eventos del comercio. Ese
 * secreto es distinto del que se usa para el widget de pago y solo vive en
 * variables de entorno, nunca en el repositorio.
 */
public final class ValidadorFirmaWompi {

  private ValidadorFirmaWompi() {}

  public static boolean esValida(JsonNode evento, String secretoEventos) {
    JsonNode firma = evento.path("signature");
    String checksumRecibido = firma.path("checksum").asText(null);
    if (checksumRecibido == null || checksumRecibido.isBlank()) return false;

    StringBuilder cadena = new StringBuilder();
    for (JsonNode propiedad : firma.path("properties")) {
      cadena.append(valorDe(evento, propiedad.asText()));
    }
    cadena.append(evento.path("timestamp").asText(""));
    cadena.append(secretoEventos);

    String calculado = sha256Hex(cadena.toString());
    return calculado.equalsIgnoreCase(checksumRecibido);
  }

  /** Recorre un path tipo "transaction.id" dentro de `data`. */
  private static String valorDe(JsonNode evento, String path) {
    JsonNode actual = evento.path("data");
    for (String parte : path.split("\\.")) {
      actual = actual.path(parte);
    }
    return actual.isMissingNode() || actual.isNull() ? "" : actual.asText();
  }

  private static String sha256Hex(String texto) {
    try {
      byte[] resumen = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : resumen) hex.append("%02x".formatted(b));
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No hay SHA-256 disponible", e);
    }
  }
}
