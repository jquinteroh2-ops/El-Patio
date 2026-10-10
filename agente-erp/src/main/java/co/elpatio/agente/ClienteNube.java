package co.elpatio.agente;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Habla con {@code /api/agente-erp} de El Patio por HTTPS.
 *
 * Todas las conexiones salen del restaurante hacia la nube; ninguna entra. Por
 * eso no hay que abrir puertos en el router ni pedirle nada al proveedor de
 * internet.
 */
public class ClienteNube implements Nube {

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
  private final ObjectMapper json =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final String url;
  private final String llave;

  public ClienteNube(String url, String llave) {
    this.url = url;
    this.llave = llave;
  }

  @Override
  public String comprobar() throws IOException, InterruptedException {
    HttpResponse<String> r = enviar(peticion("/estado").GET().build());
    return json.readTree(r.body()).path("adaptador").asText();
  }

  @Override
  public List<EnvioReclamado> reclamar(int tanda) throws IOException, InterruptedException {
    HttpResponse<String> r =
        enviar(
            peticion("/reclamar?limite=" + tanda)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    List<JsonNode> filas = json.readValue(r.body(), new TypeReference<List<JsonNode>>() {});
    return filas.stream()
        .map(f -> new EnvioReclamado(f.path("envioId").asText(), f.path("intento").asInt(), f.path("venta")))
        .toList();
  }

  @Override
  public void reportar(String envioId, Resultado resultado)
      throws IOException, InterruptedException {
    enviar(
        peticion("/envios/" + envioId + "/resultado")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(resultado)))
            .build());
  }

  private HttpRequest.Builder peticion(String ruta) {
    return HttpRequest.newBuilder(URI.create(url + "/api/agente-erp" + ruta))
        // Railway gratis o dormido puede tardar en contestar la primera vez.
        .timeout(Duration.ofSeconds(60))
        .header("X-Agente-Token", llave)
        .header("Accept", "application/json");
  }

  /** Manda y traduce los codigos que significan algo a un mensaje que se entienda. */
  private HttpResponse<String> enviar(HttpRequest peticion)
      throws IOException, InterruptedException {
    HttpResponse<String> r = http.send(peticion, HttpResponse.BodyHandlers.ofString());
    int codigo = r.statusCode();
    if (codigo >= 200 && codigo < 300) return r;
    String motivo =
        switch (codigo) {
          case 401 -> "La llave del agente no coincide con la de El Patio (nube.llave)";
          case 404 -> "El Patio no tiene la ventanilla del agente activa (falta ELPATIO_ERP_AGENTE_TOKEN)";
          case 409 -> "El Patio no esta configurado para el agente (ELPATIO_ERP_ADAPTADOR debe ser agente)";
          default -> "El Patio respondio " + codigo + ": " + recortar(r.body());
        };
    throw new RespuestaDeLaNube(codigo, motivo);
  }

  private static String recortar(String texto) {
    if (texto == null) return "";
    return texto.length() > 300 ? texto.substring(0, 300) + "..." : texto;
  }

  /** La nube contesto, pero no con lo esperado. */
  public static class RespuestaDeLaNube extends IOException {
    private final int codigo;

    public RespuestaDeLaNube(int codigo, String mensaje) {
      super(mensaje);
      this.codigo = codigo;
    }

    public int codigo() {
      return codigo;
    }
  }
}
