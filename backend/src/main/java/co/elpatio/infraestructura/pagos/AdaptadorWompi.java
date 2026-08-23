package co.elpatio.infraestructura.pagos;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.PasarelaDePagos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Los anticipos, cobrados con un link de pago de Wompi.
 *
 * Un link de un solo uso (`single_use`) con vencimiento corto: si el cliente
 * no paga a tiempo, el link deja de servir por si solo y el job de expiracion
 * (`ServicioAnticipos.expirarVencidos`) cierra el pedido del lado de aca. La
 * misma URL base sirve para sandbox y produccion: lo que cambia de ambiente es
 * la llave privada, no el dominio.
 */
public class AdaptadorWompi implements PasarelaDePagos {

  private final String urlBase;
  private final String llavePrivada;
  private final RestClient http;
  private final ObjectMapper json = new ObjectMapper();

  public AdaptadorWompi(String urlBase, String llavePrivada) {
    this.urlBase = urlBase;
    this.llavePrivada = llavePrivada;
    this.http = RestClient.create();
  }

  @Override
  public String crearLinkDePago(String referencia, long montoCentavos, String descripcion, Instant expiraEn) {
    var cuerpo =
        new java.util.LinkedHashMap<String, Object>() {
          {
            put("name", "Anticipo El Patio");
            put("description", descripcion);
            put("single_use", true);
            put("collect_shipping", false);
            put("currency", "COP");
            put("amount_in_cents", montoCentavos);
            put("reference", referencia);
            put("expires_at", DateTimeFormatter.ISO_INSTANT.format(expiraEn));
          }
        };

    String respuesta;
    try {
      respuesta =
          http.post()
              .uri(urlBase + "/payment_links")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + llavePrivada)
              .contentType(MediaType.APPLICATION_JSON)
              .body(cuerpo)
              .retrieve()
              .body(String.class);
    } catch (HttpClientErrorException.Unauthorized e) {
      throw new IllegalStateException("Wompi rechazo la llave privada configurada", e);
    } catch (RestClientException e) {
      throw new ReglaDeNegocioError(
          "No se pudo generar el link de pago. Intente de nuevo en un momento");
    }

    JsonNode nodo;
    try {
      nodo = json.readTree(respuesta);
    } catch (Exception e) {
      throw new IllegalStateException("Wompi respondio algo que no se pudo leer", e);
    }
    JsonNode id = nodo.path("data").path("id");
    if (id.isMissingNode() || id.asText().isBlank()) {
      throw new IllegalStateException("Wompi no devolvio el id del link de pago");
    }
    return "https://checkout.wompi.co/l/" + id.asText();
  }
}
