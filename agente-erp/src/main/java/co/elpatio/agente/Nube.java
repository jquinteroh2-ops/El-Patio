package co.elpatio.agente;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;

/** Lo que el agente le pregunta y le contesta a El Patio en la nube. */
public interface Nube {

  /** Una venta que la nube le entrega al agente. La venta va tal cual, sin interpretar. */
  record EnvioReclamado(String envioId, int intento, JsonNode venta) {

    /** La llave que impide facturar dos veces la misma comida. */
    public String llave() {
      return venta.path("idempotencyKey").asText();
    }

    public int numeroComanda() {
      return venta.path("numeroComanda").asInt();
    }
  }

  /** Revisa direccion y llave. Lanza con un mensaje que se entienda si algo esta mal. */
  String comprobar() throws IOException, InterruptedException;

  List<EnvioReclamado> reclamar(int tanda) throws IOException, InterruptedException;

  void reportar(String envioId, Resultado resultado) throws IOException, InterruptedException;
}
