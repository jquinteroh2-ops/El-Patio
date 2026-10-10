package co.elpatio.agente;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Deja cada venta como un archivo en una carpeta del computador de Globalsoft.
 *
 * Es el primer paso y no el ultimo. Mientras no se sepa que formato importa
 * Globalsoft, el archivo es el JSON de la venta tal cual lo manda El Patio, y
 * sirve para ver el agente funcionando y para mostrarle a quien conozca
 * Globalsoft que datos llegan. Cuando se conozca su formato, se cambia el
 * contenido del archivo aqui —o se escribe otro {@link Entregador}— y nada mas.
 *
 * <p>Contesta {@code en_espera}: el archivo quedo, pero el documento lo emite
 * Globalsoft cuando alguien lo importe, y El Patio no puede decir que la venta
 * esta facturada sin el numero.
 */
public class EntregaPorCarpeta implements Entregador {

  private final Path carpeta;
  private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  public EntregaPorCarpeta(Path carpeta) {
    this.carpeta = carpeta;
  }

  @Override
  public Resultado entregar(JsonNode venta) {
    String nombre =
        "venta-" + venta.path("numeroComanda").asInt() + "-" + venta.path("idempotencyKey").asText() + ".json";
    try {
      Files.createDirectories(carpeta);
      // Se escribe con otro nombre y se renombra al final. Si Globalsoft vigila
      // la carpeta, nunca ve un archivo a medio escribir.
      Path temporal = carpeta.resolve(nombre + ".tmp");
      Files.writeString(temporal, json.writeValueAsString(venta), StandardCharsets.UTF_8);
      Files.move(temporal, carpeta.resolve(nombre), StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
      return Resultado.enEspera("Archivo " + nombre + " dejado para Globalsoft");
    } catch (IOException e) {
      return Resultado.rechazado("No se pudo escribir en " + carpeta + ": " + e.getMessage());
    }
  }

  @Override
  public String nombre() {
    return "carpeta";
  }
}
