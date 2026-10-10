package co.elpatio.agente;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * La memoria del agente: que ventas ya le entrego a Globalsoft y como le fue.
 *
 * Es lo que impide facturar dos veces la misma comida. La nube puede ofrecer
 * una venta dos veces —el agente se apago antes de contestar, se cayo el
 * internet justo al reportar—, y cuando pasa, el agente la busca aqui por su
 * llave, ve que ya la entrego y repite la respuesta en vez de entregarla otra
 * vez.
 *
 * <p>Un archivo de texto con una linea por entrega. Se escribe y se fuerza al
 * disco ANTES de avisarle a la nube: si se va la luz entre las dos cosas, al
 * volver el agente sabe que ya entrego y solo le falta avisar.
 */
public class RegistroDeEntregas {

  private final Path archivo;
  private final ObjectMapper json = new ObjectMapper();
  private final Map<String, Resultado> porLlave = new HashMap<>();

  public RegistroDeEntregas(Path archivo) throws IOException {
    this.archivo = archivo;
    Files.createDirectories(archivo.toAbsolutePath().getParent());
    if (Files.exists(archivo)) {
      for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
        if (linea.isBlank()) continue;
        try {
          JsonNode fila = json.readTree(linea);
          porLlave.put(
              fila.path("llave").asText(), json.treeToValue(fila.path("resultado"), Resultado.class));
        } catch (IOException e) {
          // Una linea cortada por un apagon a mitad de escritura. Las demas
          // siguen valiendo; esa venta, si no alcanzo a quedar, se entrega de
          // nuevo y la idempotencia del lado de Globalsoft tiene que cubrirla.
        }
      }
    }
  }

  public Optional<Resultado> buscar(String llave) {
    return Optional.ofNullable(porLlave.get(llave));
  }

  public synchronized void anotar(String llave, String envioId, Resultado resultado)
      throws IOException {
    var fila = json.createObjectNode();
    fila.put("llave", llave);
    fila.put("envioId", envioId);
    fila.put("cuando", java.time.Instant.now().toString());
    fila.set("resultado", json.valueToTree(resultado));
    byte[] linea = (json.writeValueAsString(fila) + "\n").getBytes(StandardCharsets.UTF_8);
    try (FileChannel canal =
        FileChannel.open(archivo, StandardOpenOption.CREATE, StandardOpenOption.APPEND,
            StandardOpenOption.WRITE)) {
      canal.write(ByteBuffer.wrap(linea));
      canal.force(true);
    }
    porLlave.put(llave, resultado);
  }

  public int cuantas() {
    return porLlave.size();
  }
}
