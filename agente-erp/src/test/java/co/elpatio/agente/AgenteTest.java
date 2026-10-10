package co.elpatio.agente;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgenteTest {

  @TempDir Path carpeta;

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Una nube de mentiras: ofrece lo que se le ponga y anota lo que le contestan. */
  static class NubeDePrueba implements Nube {
    final List<EnvioReclamado> porOfrecer = new ArrayList<>();
    final Map<String, Resultado> reportados = new LinkedHashMap<>();
    boolean fallarAlReportar;

    @Override
    public String comprobar() {
      return "agente";
    }

    @Override
    public List<EnvioReclamado> reclamar(int tanda) {
      List<EnvioReclamado> tandaActual = List.copyOf(porOfrecer);
      porOfrecer.clear();
      return tandaActual;
    }

    @Override
    public void reportar(String envioId, Resultado resultado) throws IOException {
      if (fallarAlReportar) throw new IOException("sin internet");
      reportados.put(envioId, resultado);
    }
  }

  /** Un Globalsoft de mentiras que cuenta cuantas veces le entregan cada venta. */
  static class GlobalsoftDePrueba implements Entregador {
    final Map<String, Integer> entregas = new LinkedHashMap<>();
    boolean cerrado;

    @Override
    public Resultado entregar(JsonNode venta) {
      if (cerrado) return Resultado.rechazado("Globalsoft cerrado");
      String llave = venta.path("idempotencyKey").asText();
      entregas.merge(llave, 1, Integer::sum);
      return Resultado.confirmado("FV-" + venta.path("numeroComanda").asInt(), null);
    }

    @Override
    public String nombre() {
      return "prueba";
    }
  }

  private static Nube.EnvioReclamado envio(String id, String llave, int comanda) throws IOException {
    JsonNode venta =
        JSON.readTree(
            "{\"idempotencyKey\":\"" + llave + "\",\"numeroComanda\":" + comanda + ",\"total\":82080}");
    return new Nube.EnvioReclamado(id, 1, venta);
  }

  private Agente agente(NubeDePrueba nube, Entregador globalsoft) throws IOException {
    return new Agente(nube, globalsoft, new RegistroDeEntregas(carpeta.resolve("entregadas.jsonl")), 20);
  }

  @Test
  void entregaLaVentaYLeCuentaALaNubeComoLeFue() throws Exception {
    NubeDePrueba nube = new NubeDePrueba();
    GlobalsoftDePrueba globalsoft = new GlobalsoftDePrueba();
    nube.porOfrecer.add(envio("erp_1", "llave-1", 41));

    assertThat(agente(nube, globalsoft).pasada()).isEqualTo(1);

    assertThat(globalsoft.entregas).containsEntry("llave-1", 1);
    assertThat(nube.reportados.get("erp_1").desenlace()).isEqualTo("confirmado");
    assertThat(nube.reportados.get("erp_1").numeroDocumento()).isEqualTo("FV-41");
  }

  /**
   * El caso que justifica todo el registro: la entrega salio, el aviso no, y la
   * nube vuelve a ofrecer la venta. Globalsoft no puede recibirla dos veces.
   */
  @Test
  void siLaNubeVuelveAOfrecerUnaVentaYaEntregadaNoSeEntregaDosVeces() throws Exception {
    NubeDePrueba nube = new NubeDePrueba();
    GlobalsoftDePrueba globalsoft = new GlobalsoftDePrueba();

    nube.fallarAlReportar = true;
    nube.porOfrecer.add(envio("erp_1", "llave-1", 41));
    agente(nube, globalsoft).pasada();

    // Otro arranque del agente, como despues de un apagon: lee su registro del disco.
    nube.fallarAlReportar = false;
    nube.porOfrecer.add(envio("erp_1", "llave-1", 41));
    agente(nube, globalsoft).pasada();

    assertThat(globalsoft.entregas).containsEntry("llave-1", 1);
    assertThat(nube.reportados.get("erp_1").numeroDocumento()).isEqualTo("FV-41");
  }

  @Test
  void unRechazoNoSeAnotaYLaProximaVezSeIntentaDeVerdad() throws Exception {
    NubeDePrueba nube = new NubeDePrueba();
    GlobalsoftDePrueba globalsoft = new GlobalsoftDePrueba();

    globalsoft.cerrado = true;
    nube.porOfrecer.add(envio("erp_1", "llave-1", 41));
    agente(nube, globalsoft).pasada();
    assertThat(nube.reportados.get("erp_1").desenlace()).isEqualTo("rechazado");

    globalsoft.cerrado = false;
    nube.porOfrecer.add(envio("erp_1", "llave-1", 41));
    agente(nube, globalsoft).pasada();

    assertThat(globalsoft.entregas).containsEntry("llave-1", 1);
    assertThat(nube.reportados.get("erp_1").desenlace()).isEqualTo("confirmado");
  }

  @Test
  void enModoCarpetaDejaUnArchivoPorVentaYQuedaEnEspera() throws Exception {
    NubeDePrueba nube = new NubeDePrueba();
    Path destino = carpeta.resolve("para-globalsoft");
    nube.porOfrecer.add(envio("erp_1", "llave-1", 41));

    agente(nube, new EntregaPorCarpeta(destino)).pasada();

    Path archivo = destino.resolve("venta-41-llave-1.json");
    assertThat(archivo).exists();
    assertThat(JSON.readTree(Files.readString(archivo)).path("total").asLong()).isEqualTo(82080);
    assertThat(nube.reportados.get("erp_1").desenlace()).isEqualTo("en_espera");
    try (var archivos = Files.list(destino)) {
      assertThat(archivos.filter(p -> p.toString().endsWith(".tmp"))).isEmpty();
    }
  }
}
