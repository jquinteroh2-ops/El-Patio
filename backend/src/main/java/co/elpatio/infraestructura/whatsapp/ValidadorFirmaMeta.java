package co.elpatio.infraestructura.whatsapp;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifica el encabezado `X-Hub-Signature-256` que Meta pone en cada webhook:
 * HMAC-SHA256 del cuerpo crudo con el app secret de la App de Meta.
 *
 * El cuerpo tiene que ser el texto exacto que llego, antes de que nadie lo
 * parseara: si Jackson lo reordena o cambia un espacio, la firma calculada ya
 * no coincide aunque el contenido sea "el mismo" en JSON.
 */
public final class ValidadorFirmaMeta {

  private ValidadorFirmaMeta() {}

  public static boolean esValida(String cuerpoCrudo, String encabezadoFirma, String appSecret) {
    if (encabezadoFirma == null || !encabezadoFirma.startsWith("sha256=")) return false;
    String firmaRecibida = encabezadoFirma.substring("sha256=".length());

    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] resumen = mac.doFinal(cuerpoCrudo.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : resumen) hex.append("%02x".formatted(b));
      return hex.toString().equalsIgnoreCase(firmaRecibida);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo calcular HMAC-SHA256", e);
    }
  }
}
