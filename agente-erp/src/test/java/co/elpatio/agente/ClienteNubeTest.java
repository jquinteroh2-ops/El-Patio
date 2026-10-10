package co.elpatio.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El cliente contra un servidor local que contesta lo mismo que
 * ControladorAgenteErp del backend. Si alguien cambia los nombres de un lado y
 * no del otro, esto se rompe aqui y no en el restaurante.
 */
class ClienteNubeTest {

  private HttpServer servidor;
  private String url;
  private final List<String> recibidos = new ArrayList<>();

  @BeforeEach
  void arrancar() throws Exception {
    servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    servidor.createContext("/api/agente-erp", intercambio -> {
      String llave = intercambio.getRequestHeaders().getFirst("X-Agente-Token");
      String ruta = intercambio.getRequestURI().toString();
      String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      recibidos.add(intercambio.getRequestMethod() + " " + ruta + " " + cuerpo);

      int codigo;
      String respuesta = "";
      if (!"llave-buena".equals(llave)) {
        codigo = 401;
      } else if (ruta.equals("/api/agente-erp/estado")) {
        codigo = 200;
        respuesta = "{\"adaptador\":\"agente\",\"listo\":true}";
      } else if (ruta.startsWith("/api/agente-erp/reclamar")) {
        codigo = 200;
        respuesta = """
            [{"envioId":"erp_1","intento":1,"venta":{"idempotencyKey":"llave-1","pagoId":"pg_1",
              "numeroComanda":41,"fechaHora":"2026-10-11T01:00:00Z","total":82080,
              "lineas":[{"productoId":"plato_1","nombre":"Posta","cantidad":2,
                         "precioUnitario":38000,"totalLinea":76000}]}}]""";
      } else {
        codigo = 204;
      }
      byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
      intercambio.sendResponseHeaders(codigo, codigo == 204 ? -1 : bytes.length);
      if (codigo != 204) intercambio.getResponseBody().write(bytes);
      intercambio.close();
    });
    servidor.start();
    url = "http://127.0.0.1:" + servidor.getAddress().getPort();
  }

  @AfterEach
  void parar() {
    servidor.stop(0);
  }

  @Test
  void leeLaTandaComoLaMandaElBackend() throws Exception {
    ClienteNube cliente = new ClienteNube(url, "llave-buena");

    assertThat(cliente.comprobar()).isEqualTo("agente");
    List<Nube.EnvioReclamado> tanda = cliente.reclamar(20);

    assertThat(tanda).hasSize(1);
    assertThat(tanda.get(0).envioId()).isEqualTo("erp_1");
    assertThat(tanda.get(0).llave()).isEqualTo("llave-1");
    assertThat(tanda.get(0).numeroComanda()).isEqualTo(41);
    assertThat(recibidos).anyMatch(r -> r.startsWith("POST /api/agente-erp/reclamar?limite=20"));
  }

  @Test
  void reportaConLosNombresQueEsperaElBackend() throws Exception {
    new ClienteNube(url, "llave-buena").reportar("erp_1", Resultado.confirmado("FV-41", null));

    assertThat(recibidos.get(0))
        .startsWith("POST /api/agente-erp/envios/erp_1/resultado")
        .contains("\"desenlace\":\"confirmado\"")
        .contains("\"numeroDocumento\":\"FV-41\"");
  }

  @Test
  void unaLlaveMalaSeExplicaEnPalabras() {
    assertThatThrownBy(() -> new ClienteNube(url, "otra").comprobar())
        .hasMessageContaining("llave del agente no coincide");
  }
}
