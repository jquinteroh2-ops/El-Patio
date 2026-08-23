package co.elpatio.infraestructura.ia;

import co.elpatio.dominio.interprete.ItemDelMenu;
import co.elpatio.dominio.interprete.ItemReconocido;
import co.elpatio.dominio.interprete.ResultadoInterpretacion;
import co.elpatio.dominio.puertos.InterpretePedidoTexto;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traduce un pedido escrito en texto libre a ids reales del menu, con Claude.
 *
 * Reglas que no son negociables y que por eso se validan aqui, no se confian
 * al modelo: (1) el modelo nunca ve precios ni nombres fuera del `nombre` que
 * ya es publico en el menu, (2) todo id que la respuesta traiga y que no este
 * en el menu que se le paso se descarta y su texto pasa a "no reconocido", (3)
 * si la respuesta no es JSON valido, TODO el texto se trata como no
 * reconocido -- nunca se le adivina un producto al cliente.
 */
public class InterpreteClaude implements InterpretePedidoTexto {

  private static final Logger registro = LoggerFactory.getLogger(InterpreteClaude.class);

  private static final String INSTRUCCIONES =
      """
      Traduces pedidos de comida escritos en lenguaje natural a productos de un menu.

      Reglas estrictas:
      - Responde EXCLUSIVAMENTE con un JSON valido, sin texto antes ni despues, con esta forma exacta:
        {"items": [{"id": "<id del menu>", "cantidad": <entero mayor que 0>}], "no_reconocidos": ["<fragmento del texto del cliente>"]}
      - Solo puedes usar ids que aparezcan literalmente en el menu que se te da. Nunca inventes un id.
      - Si el cliente menciona algo que no esta en el menu, que es ambiguo, o que no puedes emparejar con certeza,
        ponlo en "no_reconocidos" con el texto tal cual lo escribio el cliente. No elijas el producto que mas se parece.
      - Si no se menciona una cantidad para un producto, usa 1.
      - No agregues ningun campo mas al JSON, ni comentarios, ni texto explicativo.
      """;

  private final AnthropicClient client;
  private final String modelo;
  private final ObjectMapper json = new ObjectMapper();

  public InterpreteClaude(AnthropicClient client, String modelo) {
    this.client = client;
    this.modelo = modelo;
  }

  @Override
  public ResultadoInterpretacion interpretar(String textoLibre, List<ItemDelMenu> menu) {
    Set<String> idsValidos = menu.stream().map(ItemDelMenu::id).collect(Collectors.toSet());
    if (idsValidos.isEmpty()) {
      return new ResultadoInterpretacion(List.of(), List.of(textoLibre));
    }

    String menuJson;
    try {
      menuJson = json.writeValueAsString(menu);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo serializar el menu para el interprete", e);
    }

    String textoRespuesta;
    try {
      MessageCreateParams params =
          MessageCreateParams.builder()
              .model(modelo)
              .maxTokens(1024L)
              .system(INSTRUCCIONES)
              .addUserMessage("Menu disponible (JSON): " + menuJson + "\n\nPedido del cliente: " + textoLibre)
              .build();
      Message respuesta = client.messages().create(params);
      textoRespuesta =
          respuesta.content().stream()
              .flatMap(bloque -> bloque.text().stream())
              .map(bloque -> bloque.text())
              .collect(Collectors.joining());
    } catch (RuntimeException e) {
      registro.error("Fallo llamando al interprete de pedidos", e);
      return new ResultadoInterpretacion(List.of(), List.of(textoLibre));
    }

    return parsear(textoRespuesta, idsValidos, textoLibre);
  }

  /** Aislado para poder probarlo con respuestas ya escritas, sin llamar a la API de verdad. */
  static ResultadoInterpretacion parsear(String textoRespuesta, Set<String> idsValidos, String textoOriginal) {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode raiz;
    try {
      raiz = mapper.readTree(extraerJson(textoRespuesta));
    } catch (Exception e) {
      // Si el modelo no devolvio JSON valido, nada de lo que dijo es confiable:
      // se trata todo el pedido como no reconocido y el bot pregunta.
      return new ResultadoInterpretacion(List.of(), List.of(textoOriginal));
    }

    List<ItemReconocido> reconocidos = new ArrayList<>();
    List<String> noReconocidos = new ArrayList<>();

    for (JsonNode item : raiz.path("items")) {
      String id = item.path("id").asText(null);
      int cantidad = item.path("cantidad").asInt(1);
      if (id == null || !idsValidos.contains(id) || cantidad <= 0) {
        // El modelo trajo un id que no esta en el menu real: se descarta su
        // aporte por completo antes de que llegue a tocar un pedido de verdad.
        continue;
      }
      reconocidos.add(new ItemReconocido(id, cantidad));
    }

    for (JsonNode texto : raiz.path("no_reconocidos")) {
      if (texto.isTextual() && !texto.asText().isBlank()) {
        noReconocidos.add(texto.asText());
      }
    }

    return new ResultadoInterpretacion(reconocidos, noReconocidos);
  }

  /** Por si el modelo envuelve el JSON en una cerca de codigo pese a la instruccion de no hacerlo. */
  private static String extraerJson(String texto) {
    String limpio = texto.trim();
    int inicio = limpio.indexOf('{');
    int fin = limpio.lastIndexOf('}');
    if (inicio < 0 || fin < inicio) return limpio;
    return limpio.substring(inicio, fin + 1);
  }
}
