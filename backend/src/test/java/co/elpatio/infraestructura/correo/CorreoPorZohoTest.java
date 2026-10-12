package co.elpatio.infraestructura.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El envío por la API de Zoho, contra un Zoho simulado con el servidor HTTP
 * del JDK: se comprueba el orden de las llamadas y lo que lleva cada una.
 */
class CorreoPorZohoTest {

  private static final byte[] PDF = "%PDF-1.7 hoja".getBytes(StandardCharsets.US_ASCII);

  private HttpServer servidor;
  private String base;
  private final List<String> llamadas = new CopyOnWriteArrayList<>();
  private volatile String cuerpoDelCorreo;
  private volatile byte[] adjuntoRecibido;
  private volatile String tokenRespuesta = "{\"access_token\":\"acceso-1\",\"expires_in\":3600}";

  @BeforeEach
  void arrancar() throws IOException {
    servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    servidor.createContext(
        "/oauth/v2/token",
        ex -> {
          llamadas.add("token " + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          responder(ex, 200, tokenRespuesta);
        });
    servidor.createContext(
        "/api/accounts",
        ex -> {
          String ruta = ex.getRequestURI().getPath();
          String autorizacion = ex.getRequestHeaders().getFirst("Authorization");
          llamadas.add(ex.getRequestMethod() + " " + ruta + " " + autorizacion);
          if (ruta.equals("/api/accounts")) {
            responder(
                ex,
                200,
                "{\"data\":[{\"accountId\":\"777\",\"primaryEmailAddress\":\"rrhh@restauranteelpatio.com\"}]}");
          } else if (ruta.endsWith("/messages/attachments")) {
            adjuntoRecibido = ex.getRequestBody().readAllBytes();
            llamadas.add("nombre " + ex.getRequestURI().getQuery());
            responder(
                ex,
                200,
                "{\"data\":{\"storeName\":\"st1\",\"attachmentPath\":\"/ruta/1\",\"attachmentName\":\"hv.pdf\"}}");
          } else if (ruta.endsWith("/messages")) {
            cuerpoDelCorreo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            responder(ex, 200, "{\"status\":{\"code\":200}}");
          } else {
            responder(ex, 404, "{}");
          }
        });
    servidor.start();
    base = "http://127.0.0.1:" + servidor.getAddress().getPort();
  }

  @AfterEach
  void parar() {
    servidor.stop(0);
  }

  private CorreoPorZoho correo() {
    return new CorreoPorZoho("cliente", "secreto", "renovar", base, base + "/api", "", "");
  }

  @Test
  void enviaConAdjuntoDesdeElBuzonDelToken() throws IOException {
    correo().enviarConAdjunto("rrhh@restauranteelpatio.com", "Nueva hoja", "Hola", "hv.pdf", PDF);

    assertThat(llamadas.get(0)).startsWith("token ").contains("grant_type=refresh_token");
    assertThat(llamadas.get(1)).isEqualTo("GET /api/accounts Zoho-oauthtoken acceso-1");
    assertThat(llamadas.get(2)).startsWith("POST /api/accounts/777/messages/attachments");
    assertThat(llamadas.get(3)).isEqualTo("nombre fileName=hv.pdf");
    assertThat(adjuntoRecibido).isEqualTo(PDF);

    JsonNode correo = new ObjectMapper().readTree(cuerpoDelCorreo);
    assertThat(correo.path("fromAddress").asText()).isEqualTo("rrhh@restauranteelpatio.com");
    assertThat(correo.path("toAddress").asText()).isEqualTo("rrhh@restauranteelpatio.com");
    assertThat(correo.path("subject").asText()).isEqualTo("Nueva hoja");
    assertThat(correo.path("attachments").path(0).path("storeName").asText()).isEqualTo("st1");
    assertThat(correo.path("attachments").path(0).path("attachmentPath").asText()).isEqualTo("/ruta/1");
  }

  @Test
  void reusaElTokenYLaCuentaEntreEnvios() {
    CorreoPorZoho correo = correo();
    correo.enviar("a@b.co", "uno", "x");
    correo.enviar("a@b.co", "dos", "x");

    assertThat(llamadas.stream().filter(l -> l.startsWith("token")).count()).isEqualTo(1);
    assertThat(llamadas.stream().filter(l -> l.startsWith("GET /api/accounts")).count()).isEqualTo(1);
  }

  @Test
  void sinAdjuntoNoSubeArchivo() {
    correo().enviar("a@b.co", "uno", "x");
    assertThat(llamadas).noneMatch(l -> l.contains("attachments"));
  }

  @Test
  void unTokenRechazadoSeDiceClaro() {
    tokenRespuesta = "{\"error\":\"invalid_code\"}";
    assertThatThrownBy(() -> correo().enviar("a@b.co", "uno", "x"))
        .hasMessageContaining("invalid_code");
  }

  private static void responder(HttpExchange ex, int codigo, String cuerpo) throws IOException {
    byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(codigo, bytes.length);
    ex.getResponseBody().write(bytes);
    ex.close();
  }
}
