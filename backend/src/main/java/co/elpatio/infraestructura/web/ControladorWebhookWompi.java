package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioAnticipos;
import co.elpatio.infraestructura.pagos.ValidadorFirmaWompi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Donde Wompi avisa que un anticipo se aprobo, se rechazo o se anulo.
 *
 * Nada de lo que diga el navegador del cliente al volver del checkout cuenta:
 * solo este webhook, con firma valida, mueve un pago de `pendiente`. Se
 * responde 200 en cuanto el evento se entiende, sin importar si el pago
 * resulto aprobado o no, porque un 4xx o 5xx aqui solo logra que Wompi
 * reintente el mismo aviso sin que cambie nada.
 */
@RestController
@RequestMapping("/webhooks/wompi")
public class ControladorWebhookWompi {

  private static final Logger registro = LoggerFactory.getLogger(ControladorWebhookWompi.class);

  private final ServicioAnticipos servicio;
  private final String secretoEventos;
  private final ObjectMapper json = new ObjectMapper();

  public ControladorWebhookWompi(
      ServicioAnticipos servicio, @Value("${elpatio.wompi.secreto-eventos}") String secretoEventos) {
    this.servicio = servicio;
    this.secretoEventos = secretoEventos;
  }

  @PostMapping
  public ResponseEntity<Void> recibir(@RequestBody String cuerpo) {
    JsonNode evento;
    try {
      evento = json.readTree(cuerpo);
    } catch (Exception e) {
      registro.warn("Webhook de Wompi con cuerpo ilegible");
      return ResponseEntity.badRequest().build();
    }

    if (!ValidadorFirmaWompi.esValida(evento, secretoEventos)) {
      registro.warn("Webhook de Wompi con firma invalida, se descarta");
      return ResponseEntity.status(401).build();
    }

    if (!"transaction.updated".equals(evento.path("event").asText())) {
      // Otros eventos (si algun dia Wompi agrega mas) no nos interesan todavia.
      return ResponseEntity.ok().build();
    }

    JsonNode transaccion = evento.path("data").path("transaction");
    String referencia = transaccion.path("reference").asText(null);
    String estado = transaccion.path("status").asText(null);
    String transactionId = transaccion.path("id").asText(null);

    if (referencia == null || estado == null) {
      registro.warn("Webhook de Wompi sin referencia o estado, se descarta");
      return ResponseEntity.ok().build();
    }

    try {
      // APPROVED es el unico estado que libera el pedido; DECLINED, VOIDED y
      // ERROR se tratan igual del lado de aca: el anticipo no se cobro.
      servicio.procesarEvento(referencia, "APPROVED".equals(estado), transactionId);
    } catch (RuntimeException e) {
      // Se registra pero se responde 200 igual: un evento que ya no encuentra
      // el pago (por ejemplo, uno viejo tras limpiar datos de prueba) no es
      // algo que un reintento de Wompi vaya a resolver.
      registro.error("No se pudo procesar el webhook de Wompi para {}", referencia, e);
    }
    return ResponseEntity.ok().build();
  }
}
