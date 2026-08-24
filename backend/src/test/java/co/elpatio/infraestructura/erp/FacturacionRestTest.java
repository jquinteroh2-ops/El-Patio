package co.elpatio.infraestructura.erp;

import static org.assertj.core.api.Assertions.assertThat;

import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.puertos.FacturacionExterna;
import org.junit.jupiter.api.Test;

/**
 * El adaptador REST todavia no tiene contrato real con Globalsoft.
 *
 * Lo que se puede probar hoy es su comportamiento defensivo: que sin
 * configuracion falle en claro y que un ERP inalcanzable no lo haga estallar.
 * Cuando llegue la documentacion, aqui es donde entran las pruebas del formato
 * del cuerpo y de la lectura del numero de documento.
 */
class FacturacionRestTest extends ContratoFacturacionExterna {

  @Override
  protected FacturacionExterna adaptador() {
    // Sin URL: el caso que hoy se puede ejercitar sin depender de una red.
    return new FacturacionRest("", "");
  }

  /**
   * Activado sin configurar, tiene que decirlo con todas las letras.
   *
   * El mensaje termina en la pantalla de conciliacion, y ahi lo lee alguien que
   * no va a mirar el `application.yml` por su cuenta si el error dice
   * «no se pudo contactar al ERP».
   */
  @Test
  void sinUrlFallaDiciendoQueFaltaConfigurarlo() {
    ResultadoFacturacion resultado = adaptador().emitirDocumento(ventaDeEjemplo());

    assertThat(resultado.desenlace()).isEqualTo(ResultadoFacturacion.Desenlace.RECHAZADO);
    assertThat(resultado.motivo()).contains("elpatio.erp.rest.url");
  }

  /** Una direccion que no responde es un desenlace, no una excepcion. */
  @Test
  void unErpInalcanzableSeDevuelveComoRechazoYNoComoExcepcion() {
    // Puerto reservado por IANA para «sin servicio»: no hay nada escuchando.
    FacturacionExterna sinRuta = new FacturacionRest("http://127.0.0.1:9/facturas", "x");

    ResultadoFacturacion resultado = sinRuta.emitirDocumento(ventaDeEjemplo());

    assertThat(resultado.desenlace()).isEqualTo(ResultadoFacturacion.Desenlace.RECHAZADO);
    assertThat(resultado.motivo()).isNotBlank();
  }
}
