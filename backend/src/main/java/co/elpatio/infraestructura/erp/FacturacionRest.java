package co.elpatio.infraestructura.erp;

import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.erp.VentaParaErp;
import co.elpatio.dominio.puertos.FacturacionExterna;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Manda la venta por HTTP al ERP.
 *
 * <p><b>Este adaptador esta incompleto a proposito y no debe activarse todavia.</b>
 * Nadie ha visto la documentacion de Globalsoft: no se sabe la ruta, ni como
 * autentica, ni que forma tiene el cuerpo, ni que devuelve al confirmar. Lo que
 * hay aqui es el esqueleto —tiempos limite, llave de idempotencia en cabecera,
 * traduccion de la respuesta a {@link ResultadoFacturacion}— para que el dia
 * que llegue esa documentacion el trabajo sea rellenar {@code cuerpoDe} y
 * {@code leerRespuesta}, y no discutir de nuevo la arquitectura.
 *
 * <p><b>Ademas hay una duda de fondo sin resolver.</b> El restaurante dice que
 * Globalsoft esta instalado localmente. Si es asi, El Patio —que corre en la
 * nube— no puede alcanzarlo: no hay IP publica a la que llamar, y abrir el
 * router del local para que la haya es una decision de seguridad que no toma el
 * desarrollo. Mientras eso no se aclare, el camino realista es
 * {@link FacturacionPorArchivo} o invertir la direccion de la conexion, con
 * algo dentro del local que venga a buscar las ventas.
 */
@Component
@ConditionalOnProperty(name = "elpatio.erp.adaptador", havingValue = "rest")
public class FacturacionRest implements FacturacionExterna {

  private static final Logger registro = LoggerFactory.getLogger(FacturacionRest.class);

  /**
   * Un limite de espera corto y no negociable.
   *
   * El worker que llama a esto tiene un pool de hilos. Un ERP que acepta la
   * conexion y nunca contesta —el fallo mas comun de un servidor saturado— se
   * come un hilo por venta hasta que no queda ninguno, y entonces deja de
   * funcionar tambien lo que si estaba sano.
   */
  private static final Duration ESPERA = Duration.ofSeconds(15);

  private final HttpClient cliente;
  private final String urlBase;
  private final String token;

  public FacturacionRest(
      @Value("${elpatio.erp.rest.url:}") String urlBase,
      @Value("${elpatio.erp.rest.token:}") String token) {
    this.urlBase = urlBase;
    this.token = token;
    this.cliente = HttpClient.newBuilder().connectTimeout(ESPERA).build();
  }

  @Override
  public ResultadoFacturacion emitirDocumento(VentaParaErp venta) {
    if (urlBase.isBlank()) {
      // Falla en claro en vez de intentar contra una URL vacia. El mensaje va a
      // la pantalla de conciliacion, que es donde alguien lo va a leer.
      return ResultadoFacturacion.rechazado(
          "El adaptador REST esta activo pero sin configurar: falta elpatio.erp.rest.url", null);
    }

    try {
      HttpRequest peticion =
          HttpRequest.newBuilder(URI.create(urlBase))
              .timeout(ESPERA)
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + token)
              // La llave viaja en cabecera para que el ERP pueda descartar el
              // duplicado por su cuenta. Aunque no la respete, el outbox no
              // reintenta una venta ya confirmada; esto es el segundo cerrojo.
              .header("Idempotency-Key", venta.idempotencyKey())
              .POST(HttpRequest.BodyPublishers.ofString(cuerpoDe(venta)))
              .build();

      HttpResponse<String> respuesta =
          cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
      return leerRespuesta(respuesta);

    } catch (InterruptedException e) {
      // Restaurar la marca antes de salir: tragarsela deja un hilo que ya no
      // responde a la parada del contexto y bloquea el apagado.
      Thread.currentThread().interrupt();
      return ResultadoFacturacion.rechazado("Envio interrumpido", null);
    } catch (Exception e) {
      registro.warn("Fallo el envio de la venta {} al ERP", venta.pagoId(), e);
      return ResultadoFacturacion.rechazado("No se pudo contactar al ERP: " + e.getMessage(), null);
    }
  }

  /**
   * El cuerpo que espera Globalsoft.
   *
   * PENDIENTE: sin documentacion, esto manda el plano que ya sabe armar el
   * mapeador. Es casi seguro que no sea lo que Globalsoft quiere.
   */
  private String cuerpoDe(VentaParaErp venta) {
    return MapeadorGlobalsoft.aPlano(venta);
  }

  /**
   * Traduce lo que contesto el ERP.
   *
   * PENDIENTE: de donde sale el numero del documento depende del contrato real.
   * Hoy solo se distingue por el codigo HTTP, que es lo unico que se puede
   * afirmar sin conocerlo.
   */
  private ResultadoFacturacion leerRespuesta(HttpResponse<String> respuesta) {
    String cuerpo = respuesta.body();
    if (respuesta.statusCode() >= 200 && respuesta.statusCode() < 300) {
      // Sin saber donde viene el numero, no se puede confirmar: confirmar sin
      // numero dejaria la venta en verde y sin nada con que conciliarla.
      return ResultadoFacturacion.enEspera(
          "El ERP acepto, falta leer el numero de documento de su respuesta");
    }
    return ResultadoFacturacion.rechazado(
        "El ERP respondio " + respuesta.statusCode(), cuerpo);
  }

  @Override
  public String nombre() {
    return "rest";
  }
}
