package co.elpatio.infraestructura.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

/**
 * Reconstruye a mano el checksum que arma Wompi, para probar que el validador
 * calcula exactamente lo mismo. Si el secreto no coincide o el evento fue
 * alterado en el camino, la firma tiene que quedar invalida.
 */
class ValidadorFirmaWompiTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SECRETO = "secreto-de-prueba";

  private static String checksumEsperado(String id, String status, String reference, String timestamp) {
    try {
      String cadena = id + status + reference + timestamp + SECRETO;
      byte[] resumen = MessageDigest.getInstance("SHA-256").digest(cadena.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : resumen) hex.append("%02x".formatted(b));
      return hex.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static com.fasterxml.jackson.databind.JsonNode evento(String checksum, String timestamp)
      throws Exception {
    String cuerpo =
        """
        {
          "event": "transaction.updated",
          "data": {
            "transaction": {
              "id": "txn_1",
              "status": "APPROVED",
              "reference": "anticipo_abc"
            }
          },
          "signature": {
            "checksum": "%s",
            "properties": ["transaction.id", "transaction.status", "transaction.reference"]
          },
          "timestamp": "%s"
        }
        """
            .formatted(checksum, timestamp);
    return JSON.readTree(cuerpo);
  }

  @Test
  void aceptaUnaFirmaCalculadaCorrectamente() throws Exception {
    String timestamp = "1700000000";
    String checksum = checksumEsperado("txn_1", "APPROVED", "anticipo_abc", timestamp);

    assertThat(ValidadorFirmaWompi.esValida(evento(checksum, timestamp), SECRETO)).isTrue();
  }

  @Test
  void rechazaUnaFirmaConSecretoDistinto() throws Exception {
    String timestamp = "1700000000";
    String checksum = checksumEsperado("txn_1", "APPROVED", "anticipo_abc", timestamp);

    assertThat(ValidadorFirmaWompi.esValida(evento(checksum, timestamp), "otro-secreto")).isFalse();
  }

  @Test
  void rechazaUnEventoAlteradoDespuesDeFirmado() throws Exception {
    String timestamp = "1700000000";
    // El checksum se calculo para "anticipo_abc", pero el evento que llega dice otra referencia.
    String checksum = checksumEsperado("txn_1", "APPROVED", "anticipo_abc", timestamp);
    var alterado =
        JSON.readTree(
            evento(checksum, timestamp)
                .toString()
                .replace("anticipo_abc", "anticipo_otro"));

    assertThat(ValidadorFirmaWompi.esValida(alterado, SECRETO)).isFalse();
  }
}
