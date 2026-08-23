package co.elpatio.infraestructura.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Los mensajes salientes de WhatsApp, contra la Graph API de Meta directa (sin
 * BSP intermediario).
 *
 * Solo sabe mandar tres formas de mensaje: texto libre, botones interactivos y
 * listas interactivas. No arma frases de negocio ni decide que decir; eso es
 * del `OrquestadorWhatsApp`, que es quien conoce el guion de la conversacion.
 * Un fallo de red aqui se registra y no revienta la llamada: el mensaje que no
 * llego no puede tumbar el procesamiento del webhook que lo origino.
 */
public class ClienteGraphApi {

  private static final Logger registro = LoggerFactory.getLogger(ClienteGraphApi.class);

  /** Un boton de respuesta rapida. WhatsApp permite hasta tres por mensaje. */
  public record Boton(String id, String titulo) {}

  /** Una fila dentro de una seccion de la lista interactiva. */
  public record FilaLista(String id, String titulo, String descripcion) {}

  public record SeccionLista(String titulo, List<FilaLista> filas) {}

  private final String urlBase;
  private final String phoneNumberId;
  private final String tokenAcceso;
  private final RestClient http;
  private final ObjectMapper json = new ObjectMapper();

  public ClienteGraphApi(String urlBase, String phoneNumberId, String tokenAcceso) {
    this.urlBase = urlBase;
    this.phoneNumberId = phoneNumberId;
    this.tokenAcceso = tokenAcceso;
    this.http = RestClient.create();
  }

  public void enviarTexto(String telefono, String texto) {
    Map<String, Object> cuerpo = base(telefono, "text");
    cuerpo.put("text", Map.of("body", texto));
    enviar(cuerpo);
  }

  public void enviarBotones(String telefono, String texto, List<Boton> botones) {
    Map<String, Object> cuerpo = base(telefono, "interactive");
    List<Map<String, Object>> filas =
        botones.stream()
            .map(b -> Map.<String, Object>of("type", "reply", "reply", Map.of("id", b.id(), "title", b.titulo())))
            .toList();
    cuerpo.put(
        "interactive",
        Map.of(
            "type", "button",
            "body", Map.of("text", texto),
            "action", Map.of("buttons", filas)));
    enviar(cuerpo);
  }

  public void enviarLista(String telefono, String texto, String tituloBoton, List<SeccionLista> secciones) {
    Map<String, Object> cuerpo = base(telefono, "interactive");
    List<Map<String, Object>> seccionesJson =
        secciones.stream()
            .map(
                s ->
                    Map.<String, Object>of(
                        "title", s.titulo(),
                        "rows",
                            s.filas().stream()
                                .map(
                                    f ->
                                        Map.of(
                                            "id", f.id(),
                                            "title", f.titulo(),
                                            "description", f.descripcion() == null ? "" : f.descripcion()))
                                .toList()))
            .toList();
    cuerpo.put(
        "interactive",
        Map.of(
            "type", "list",
            "body", Map.of("text", texto),
            "action", Map.of("button", tituloBoton, "sections", seccionesJson)));
    enviar(cuerpo);
  }

  private Map<String, Object> base(String telefono, String tipo) {
    Map<String, Object> cuerpo = new LinkedHashMap<>();
    cuerpo.put("messaging_product", "whatsapp");
    cuerpo.put("to", telefono);
    cuerpo.put("type", tipo);
    return cuerpo;
  }

  private void enviar(Map<String, Object> cuerpo) {
    try {
      http.post()
          .uri(urlBase + "/" + phoneNumberId + "/messages")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAcceso)
          .contentType(MediaType.APPLICATION_JSON)
          .body(cuerpo)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      registro.error("No se pudo enviar el mensaje de WhatsApp: {}", resumenSeguro(cuerpo), e);
    }
  }

  /** Para el registro: sin el numero completo del cliente ni el contenido del mensaje. */
  private String resumenSeguro(Map<String, Object> cuerpo) {
    try {
      return json.writeValueAsString(Map.of("type", cuerpo.get("type")));
    } catch (Exception e) {
      return "?";
    }
  }
}
