package co.elpatio.infraestructura.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class ValidadorFirmaMetaTest {

  private static final String SECRETO = "app-secret-de-prueba";

  private static String firmarComoMeta(String cuerpo) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] resumen = mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte b : resumen) hex.append("%02x".formatted(b));
    return "sha256=" + hex;
  }

  @Test
  void aceptaUnaFirmaValida() throws Exception {
    String cuerpo = "{\"hola\":\"mundo\"}";
    assertThat(ValidadorFirmaMeta.esValida(cuerpo, firmarComoMeta(cuerpo), SECRETO)).isTrue();
  }

  @Test
  void rechazaSiElCuerpoCambioDespuesDeFirmado() throws Exception {
    String cuerpo = "{\"hola\":\"mundo\"}";
    String firma = firmarComoMeta(cuerpo);
    assertThat(ValidadorFirmaMeta.esValida("{\"hola\":\"otro\"}", firma, SECRETO)).isFalse();
  }

  @Test
  void rechazaSinEncabezado() {
    assertThat(ValidadorFirmaMeta.esValida("{}", null, SECRETO)).isFalse();
  }

  @Test
  void rechazaUnEncabezadoSinElPrefijoEsperado() {
    assertThat(ValidadorFirmaMeta.esValida("{}", "abc123", SECRETO)).isFalse();
  }

  @Test
  void rechazaConOtroSecreto() throws Exception {
    String cuerpo = "{\"hola\":\"mundo\"}";
    assertThat(ValidadorFirmaMeta.esValida(cuerpo, firmarComoMeta(cuerpo), "otro-secreto")).isFalse();
  }
}
