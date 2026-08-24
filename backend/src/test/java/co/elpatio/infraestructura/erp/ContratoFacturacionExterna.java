package co.elpatio.infraestructura.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.erp.VentaParaErp;
import co.elpatio.dominio.puertos.FacturacionExterna;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El contrato que TODO adaptador del ERP tiene que cumplir.
 *
 * Cada adaptador extiende esta clase y hereda las pruebas. No es ceremonia: el
 * dia que llegue el adaptador real de Globalsoft, lo primero que hay que saber
 * de el es si respeta estas tres reglas, y aqui ya estan escritas. Un adaptador
 * nuevo que las pase se puede enchufar sin releer el resto del sistema.
 *
 * Lo que NO se prueba aqui es si el ERP acepta el documento. Eso depende de un
 * sistema que no controlamos y no cabe en una prueba automatica; lo que si cabe
 * es que el adaptador se comporte bien cuando el ERP no colabora.
 */
abstract class ContratoFacturacionExterna {

  /** El adaptador bajo prueba, ya configurado. */
  protected abstract FacturacionExterna adaptador();

  protected static VentaParaErp ventaDeEjemplo() {
    return ventaConLlave(UUID.randomUUID().toString());
  }

  protected static VentaParaErp ventaConLlave(String llave) {
    return new VentaParaErp(
        llave,
        "pg_1",
        "or_1",
        47,
        Instant.parse("2026-08-24T20:15:00Z"),
        "mesa",
        "presencial",
        List.of(
            new VentaParaErp.LineaVenta("p09", "Robalo al bijao", 2, 58000, 116000),
            new VentaParaErp.LineaVenta("p02", "Ceviche El Patio", 1, 32000, 32000)),
        148000,
        11840,
        8,
        0,
        0,
        14800,
        174640,
        "tarjeta",
        List.of(),
        "Ana");
  }

  // -------------------------------------------------------------------------

  /**
   * Regla 1: los fallos del ERP se devuelven, no se lanzan.
   *
   * Una excepcion que sube desde aqui llega al worker, que la registra como
   * fallo del adaptador. Es una distincion util: significa «el adaptador esta
   * roto», no «el ERP dijo que no».
   */
  @Test
  void nuncaLanzaPorUnFalloDelErp() {
    assertThatCode(() -> adaptador().emitirDocumento(ventaDeEjemplo())).doesNotThrowAnyException();
  }

  /** Regla 2: siempre hay un desenlace, nunca un nulo. */
  @Test
  void siempreDevuelveUnResultado() {
    ResultadoFacturacion resultado = adaptador().emitirDocumento(ventaDeEjemplo());

    assertThat(resultado).isNotNull();
    assertThat(resultado.desenlace()).isNotNull();
  }

  /**
   * Regla 3: idempotencia.
   *
   * Dos llamadas con la misma llave no pueden producir dos documentos. Es la
   * regla que impide que un tiempo de espera agotado —el ERP recibio, emitio y
   * la respuesta se perdio— le cobre dos veces al mismo cliente ante la DIAN.
   */
  @Test
  void dosLlamadasConLaMismaLlaveNoProducenDosDocumentos() {
    VentaParaErp venta = ventaConLlave("llave-fija-para-la-prueba");

    ResultadoFacturacion primera = adaptador().emitirDocumento(venta);
    ResultadoFacturacion segunda = adaptador().emitirDocumento(venta);

    // O ninguna confirmo, o las dos apuntan al mismo documento. Lo que no puede
    // pasar es que la segunda emita uno nuevo.
    if (primera.confirmo() || segunda.confirmo()) {
      assertThat(segunda.numeroDocumento()).isEqualTo(primera.numeroDocumento());
    }
  }

  /** El nombre va a la bitacora y a la pantalla de conciliacion. */
  @Test
  void tieneNombre() {
    assertThat(adaptador().nombre()).isNotBlank();
  }
}
