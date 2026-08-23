package co.elpatio.infraestructura.web;

import co.elpatio.infraestructura.whatsapp.DaoMensajesProcesados;
import co.elpatio.infraestructura.whatsapp.FilaMensajeProcesado;
import co.elpatio.infraestructura.whatsapp.OrquestadorWhatsApp;
import co.elpatio.infraestructura.whatsapp.ValidadorFirmaMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La puerta de entrada de WhatsApp: la verificacion que Meta exige al
 * suscribir el webhook, y los mensajes de los clientes.
 *
 * Se responde 200 en cuanto el mensaje se identifica y se marca procesado; lo
 * que hace el bot con el (`OrquestadorWhatsApp.procesarMensaje`, `@Async`)
 * corre despues, para no arriesgarse a que Meta reintente un mensaje que en
 * realidad si llego pero se demoro en contestar.
 */
@RestController
@RequestMapping("/webhooks/whatsapp")
public class ControladorWebhookWhatsApp {

  private static final Logger registro = LoggerFactory.getLogger(ControladorWebhookWhatsApp.class);

  private final OrquestadorWhatsApp orquestador;
  private final DaoMensajesProcesados mensajesProcesados;
  private final String verifyToken;
  private final String appSecret;
  private final ObjectMapper json = new ObjectMapper();

  public ControladorWebhookWhatsApp(
      OrquestadorWhatsApp orquestador,
      DaoMensajesProcesados mensajesProcesados,
      @Value("${elpatio.whatsapp.verify-token}") String verifyToken,
      @Value("${elpatio.whatsapp.app-secret}") String appSecret) {
    this.orquestador = orquestador;
    this.mensajesProcesados = mensajesProcesados;
    this.verifyToken = verifyToken;
    this.appSecret = appSecret;
  }

  /** La verificacion que Meta hace una sola vez, al configurar la URL del webhook. */
  @GetMapping
  public ResponseEntity<String> verificar(
      @RequestParam("hub.mode") String modo,
      @RequestParam("hub.verify_token") String tokenRecibido,
      @RequestParam("hub.challenge") String challenge) {
    if ("subscribe".equals(modo) && verifyToken.equals(tokenRecibido) && !verifyToken.isBlank()) {
      return ResponseEntity.ok(challenge);
    }
    return ResponseEntity.status(403).build();
  }

  @PostMapping
  public ResponseEntity<Void> recibir(
      @RequestBody String cuerpo,
      @RequestHeader(value = "X-Hub-Signature-256", required = false) String firma) {
    if (!ValidadorFirmaMeta.esValida(cuerpo, firma, appSecret)) {
      registro.warn("Webhook de WhatsApp con firma invalida, se descarta");
      return ResponseEntity.status(401).build();
    }

    JsonNode evento;
    try {
      evento = json.readTree(cuerpo);
    } catch (Exception e) {
      registro.warn("Webhook de WhatsApp con cuerpo ilegible");
      return ResponseEntity.badRequest().build();
    }

    for (JsonNode entrada : evento.path("entry")) {
      for (JsonNode cambio : entrada.path("changes")) {
        for (JsonNode mensaje : cambio.path("value").path("messages")) {
          procesarSiEsNuevo(mensaje);
        }
      }
    }
    return ResponseEntity.ok().build();
  }

  private void procesarSiEsNuevo(JsonNode mensaje) {
    String messageId = mensaje.path("id").asText(null);
    String telefono = mensaje.path("from").asText(null);
    if (messageId == null || telefono == null) return;

    try {
      mensajesProcesados.save(new FilaMensajeProcesado(messageId, Instant.now()));
    } catch (DataIntegrityViolationException e) {
      // Ya se proceso este mismo message.id: Meta reenvio el evento.
      return;
    }

    orquestador.procesarMensaje(new OrquestadorWhatsApp.MensajeEntrante(telefono, idInteractivo(mensaje), textoDe(mensaje)));
  }

  private String idInteractivo(JsonNode mensaje) {
    JsonNode interactivo = mensaje.path("interactive");
    if (interactivo.has("button_reply")) return interactivo.path("button_reply").path("id").asText(null);
    if (interactivo.has("list_reply")) return interactivo.path("list_reply").path("id").asText(null);
    return null;
  }

  private String textoDe(JsonNode mensaje) {
    if (mensaje.has("text")) return mensaje.path("text").path("body").asText(null);
    return null;
  }
}
