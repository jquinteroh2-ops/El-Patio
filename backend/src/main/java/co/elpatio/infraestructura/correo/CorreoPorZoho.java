package co.elpatio.infraestructura.correo;

import co.elpatio.dominio.puertos.NotificadorPorCorreo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * El correo que sale por la API de Zoho Mail, desde un buzón del restaurante.
 *
 * <p><b>Por qué no SMTP:</b> Railway bloquea los puertos de SMTP (25, 465, 587)
 * en el plan Hobby, que es el que tiene el restaurante. La API de Zoho va por
 * HTTPS, que no se bloquea, y el correo sale del buzón de verdad: queda en sus
 * enviados y llega firmado por el dominio, sin pasar por un tercero.
 *
 * <p>Se activa solo si hay un token de renovación
 * ({@code ELPATIO_ZOHO_REFRESH_TOKEN}). El token se saca una vez en
 * api-console.zoho.com («Self Client») con la sesión del buzón que va a enviar,
 * con los permisos {@code ZohoMail.messages.CREATE} y
 * {@code ZohoMail.accounts.READ}. Con él, esta clase pide cada hora el token de
 * acceso que exige la API.
 */
@Component
@Primary
// Expresion y no @ConditionalOnProperty: la variable llega como texto vacio
// cuando no esta puesta, y para @ConditionalOnProperty un vacio cuenta como
// presente.
@ConditionalOnExpression("!'${elpatio.zoho.refresh-token:}'.isBlank()")
public class CorreoPorZoho implements NotificadorPorCorreo {

  private static final Logger registro = LoggerFactory.getLogger(CorreoPorZoho.class);
  private static final Duration ESPERA = Duration.ofSeconds(30);

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(ESPERA).build();
  private final ObjectMapper json = new ObjectMapper();

  private final String clienteId;
  private final String clienteSecreto;
  private final String tokenRenovacion;
  /** https://accounts.zoho.com, o el del centro de datos de la cuenta. */
  private final String urlCuentas;
  /** https://mail.zoho.com/api, idem. */
  private final String urlApi;
  private String cuenta;
  private String remitente;

  private String tokenAcceso;
  private Instant venceEn = Instant.EPOCH;

  // Dos constructores: sin @Autowired Spring no sabe cuál usar y busca uno
  // vacío. El otro es el de las pruebas, con las URL de Zoho a mano.
  @Autowired
  public CorreoPorZoho(
      @Value("${elpatio.zoho.client-id}") String clienteId,
      @Value("${elpatio.zoho.client-secret}") String clienteSecreto,
      @Value("${elpatio.zoho.refresh-token}") String tokenRenovacion,
      @Value("${elpatio.zoho.dominio:zoho.com}") String dominio,
      @Value("${elpatio.zoho.cuenta:}") String cuenta,
      @Value("${elpatio.zoho.remitente:}") String remitente) {
    this(
        clienteId,
        clienteSecreto,
        tokenRenovacion,
        "https://accounts." + dominio,
        "https://mail." + dominio + "/api",
        cuenta,
        remitente);
  }

  /** Con las direcciones a mano: lo usan las pruebas contra un Zoho simulado. */
  CorreoPorZoho(
      String clienteId,
      String clienteSecreto,
      String tokenRenovacion,
      String urlCuentas,
      String urlApi,
      String cuenta,
      String remitente) {
    this.clienteId = clienteId;
    this.clienteSecreto = clienteSecreto;
    this.tokenRenovacion = tokenRenovacion;
    this.urlCuentas = urlCuentas;
    this.urlApi = urlApi;
    this.cuenta = cuenta == null ? "" : cuenta;
    this.remitente = remitente == null ? "" : remitente;
  }

  @Override
  public void enviar(String destinatario, String asunto, String cuerpo) {
    enviarConAdjunto(destinatario, asunto, cuerpo, null, null);
  }

  @Override
  public void enviarConAdjunto(
      String destinatario, String asunto, String cuerpo, String nombreArchivo, byte[] contenido) {
    enviarConAdjunto(destinatario, asunto, cuerpo, null, nombreArchivo, contenido);
  }

  /**
   * La API de Zoho manda un solo formato por correo. Con HTML va el HTML; si no
   * hay, el texto plano de siempre.
   */
  @Override
  public void enviarConAdjunto(
      String destinatario,
      String asunto,
      String cuerpo,
      String cuerpoHtml,
      String nombreArchivo,
      byte[] contenido) {
    boolean conHtml = cuerpoHtml != null && !cuerpoHtml.isBlank();
    try {
      String token = tokenDeAcceso();
      conocerCuenta(token);

      ObjectNode mensaje = json.createObjectNode();
      mensaje.put("fromAddress", remitente);
      mensaje.put("toAddress", destinatario);
      mensaje.put("subject", asunto);
      mensaje.put("content", conHtml ? cuerpoHtml : cuerpo);
      mensaje.put("mailFormat", conHtml ? "html" : "plaintext");
      if (contenido != null && contenido.length > 0) {
        ArrayNode adjuntos = mensaje.putArray("attachments");
        adjuntos.add(subirAdjunto(token, nombreArchivo, contenido));
      }

      HttpResponse<String> respuesta =
          http.send(
              HttpRequest.newBuilder(URI.create(api("/accounts/" + cuenta + "/messages")))
                  .timeout(ESPERA)
                  .header("Authorization", "Zoho-oauthtoken " + token)
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(mensaje)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      exigirExito(respuesta, "enviar el correo");
      registro.info("Correo enviado a {} por Zoho", destinatario);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo hablar con Zoho Mail", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Se interrumpió el envío por Zoho Mail", e);
    }
  }

  @Override
  public boolean estaActivo() {
    return true;
  }

  /** El archivo se sube primero; el correo lo referencia con lo que devuelve Zoho. */
  private JsonNode subirAdjunto(String token, String nombreArchivo, byte[] contenido)
      throws IOException, InterruptedException {
    String nombre = nombreArchivo == null || nombreArchivo.isBlank() ? "adjunto.pdf" : nombreArchivo;
    HttpResponse<String> respuesta =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(
                        api(
                            "/accounts/"
                                + cuenta
                                + "/messages/attachments?fileName="
                                + URLEncoder.encode(nombre, StandardCharsets.UTF_8))))
                .timeout(ESPERA)
                .header("Authorization", "Zoho-oauthtoken " + token)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(contenido))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    exigirExito(respuesta, "subir el adjunto");
    JsonNode datos = json.readTree(respuesta.body()).path("data");
    JsonNode adjunto = datos.isArray() ? datos.path(0) : datos;
    ObjectNode referencia = json.createObjectNode();
    referencia.put("storeName", adjunto.path("storeName").asText());
    referencia.put("attachmentPath", adjunto.path("attachmentPath").asText());
    referencia.put("attachmentName", adjunto.path("attachmentName").asText(nombre));
    return referencia;
  }

  /**
   * El id de la cuenta y la dirección que envía, si no vinieron configurados.
   *
   * Se piden una sola vez a la API: son los del buzón dueño del token, que es
   * justo el que tiene que enviar.
   */
  private synchronized void conocerCuenta(String token) throws IOException, InterruptedException {
    if (!cuenta.isBlank() && !remitente.isBlank()) return;
    HttpResponse<String> respuesta =
        http.send(
            HttpRequest.newBuilder(URI.create(api("/accounts")))
                .timeout(ESPERA)
                .header("Authorization", "Zoho-oauthtoken " + token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    exigirExito(respuesta, "leer la cuenta");
    JsonNode primera = json.readTree(respuesta.body()).path("data").path(0);
    if (cuenta.isBlank()) cuenta = primera.path("accountId").asText();
    if (remitente.isBlank()) remitente = primera.path("primaryEmailAddress").asText();
    if (cuenta.isBlank() || remitente.isBlank()) {
      throw new IllegalStateException("Zoho no devolvió la cuenta del buzón");
    }
  }

  /** El token de acceso dura una hora; se renueva con un minuto de margen. */
  private synchronized String tokenDeAcceso() throws IOException, InterruptedException {
    if (tokenAcceso != null && Instant.now().isBefore(venceEn)) return tokenAcceso;
    String formulario =
        "refresh_token="
            + URLEncoder.encode(tokenRenovacion, StandardCharsets.UTF_8)
            + "&client_id="
            + URLEncoder.encode(clienteId, StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(clienteSecreto, StandardCharsets.UTF_8)
            + "&grant_type=refresh_token";
    HttpResponse<String> respuesta =
        http.send(
            HttpRequest.newBuilder(URI.create(urlCuentas + "/oauth/v2/token"))
                .timeout(ESPERA)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formulario))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    exigirExito(respuesta, "renovar el token");
    JsonNode cuerpo = json.readTree(respuesta.body());
    String nuevo = cuerpo.path("access_token").asText("");
    if (nuevo.isBlank()) {
      // Zoho responde 200 con {"error": "..."} cuando el token no sirve.
      throw new IllegalStateException(
          "Zoho no entregó token de acceso: " + cuerpo.path("error").asText("sin detalle"));
    }
    tokenAcceso = nuevo;
    venceEn = Instant.now().plusSeconds(cuerpo.path("expires_in").asLong(3600) - 60);
    return tokenAcceso;
  }

  private String api(String ruta) {
    return urlApi + ruta;
  }

  private static void exigirExito(HttpResponse<String> respuesta, String que) {
    if (respuesta.statusCode() / 100 != 2) {
      throw new IllegalStateException(
          "Zoho rechazó " + que + " (HTTP " + respuesta.statusCode() + "): " + respuesta.body());
    }
  }
}
