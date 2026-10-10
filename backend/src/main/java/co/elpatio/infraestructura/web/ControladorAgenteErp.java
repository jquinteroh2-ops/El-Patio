package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioIntegracionErp;
import co.elpatio.dominio.erp.ResultadoFacturacion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La ventanilla del agente que vive en el computador de Globalsoft.
 *
 * El agente no tiene sesion de nadie: no es una persona, es un programa que
 * corre de dia y de noche. Se identifica con una llave propia en la cabecera
 * {@code X-Agente-Token}, que se configura en {@code ELPATIO_ERP_AGENTE_TOKEN} y
 * en el archivo del agente, y que no sirve para nada mas que esto. Por eso la
 * ruta esta abierta en la configuracion de seguridad y la llave se revisa aqui
 * dentro, como la firma de los webhooks.
 *
 * <p>Con la llave vacia la ventanilla no existe (404). Con el sistema
 * configurado para otro adaptador responde 409: si la nube y el agente
 * entregaran a la vez, una venta saldria por dos lados.
 */
@RestController
@RequestMapping("/api/agente-erp")
public class ControladorAgenteErp {

  private static final Logger registro = LoggerFactory.getLogger(ControladorAgenteErp.class);

  private final ServicioIntegracionErp servicio;
  private final byte[] huellaLlave;

  public ControladorAgenteErp(
      ServicioIntegracionErp servicio, @Value("${elpatio.erp.agente.token:}") String llave) {
    this.servicio = servicio;
    this.huellaLlave = llave == null || llave.isBlank() ? null : huella(llave);
  }

  /** Lo que el agente pregunta al arrancar para saber si la llave y la direccion estan bien. */
  public record EstadoAgente(String adaptador, boolean listo) {}

  /** Como le fue al agente con una venta. */
  public record ResultadoDelAgente(
      String desenlace, String numeroDocumento, String motivo, String respuestaCruda) {}

  @GetMapping("/estado")
  public ResponseEntity<EstadoAgente> estado(
      @RequestHeader(value = "X-Agente-Token", required = false) String llave) {
    ResponseEntity<EstadoAgente> rechazo = revisar(llave);
    if (rechazo != null) return rechazo;
    return ResponseEntity.ok(new EstadoAgente(servicio.nombreAdaptador(), true));
  }

  @PostMapping("/reclamar")
  public ResponseEntity<List<ServicioIntegracionErp.EnvioReclamado>> reclamar(
      @RequestHeader(value = "X-Agente-Token", required = false) String llave,
      @RequestParam(defaultValue = "20") int limite) {
    ResponseEntity<List<ServicioIntegracionErp.EnvioReclamado>> rechazo = revisar(llave);
    if (rechazo != null) return rechazo;
    return ResponseEntity.ok(servicio.reclamarParaAgente(limite));
  }

  @PostMapping("/envios/{envioId}/resultado")
  public ResponseEntity<Void> resultado(
      @RequestHeader(value = "X-Agente-Token", required = false) String llave,
      @PathVariable String envioId,
      @RequestBody ResultadoDelAgente cuerpo) {
    ResponseEntity<Void> rechazo = revisar(llave);
    if (rechazo != null) return rechazo;
    servicio.registrarResultadoDelAgente(envioId, traducir(cuerpo));
    return ResponseEntity.noContent().build();
  }

  /**
   * De lo que manda el agente a un resultado del dominio. Un desenlace que no
   * se reconoce es un error del agente y se contesta 400 (IllegalArgument),
   * en vez de adivinar.
   */
  private static ResultadoFacturacion traducir(ResultadoDelAgente cuerpo) {
    String desenlace = cuerpo.desenlace() == null ? "" : cuerpo.desenlace().toLowerCase();
    return switch (desenlace) {
      case "confirmado" ->
          ResultadoFacturacion.confirmado(cuerpo.numeroDocumento(), cuerpo.respuestaCruda());
      case "rechazado" ->
          ResultadoFacturacion.rechazado(
              cuerpo.motivo() == null ? "El agente no dijo por que" : cuerpo.motivo(),
              cuerpo.respuestaCruda());
      case "en_espera" ->
          ResultadoFacturacion.enEspera(
              cuerpo.motivo() == null ? "Entregada a Globalsoft, sin numero todavia" : cuerpo.motivo());
      default -> throw new IllegalArgumentException("Desenlace desconocido: " + cuerpo.desenlace());
    };
  }

  /** Null si la peticion puede seguir; si no, la respuesta con que se corta. */
  private <T> ResponseEntity<T> revisar(String llave) {
    if (huellaLlave == null) return ResponseEntity.notFound().build();
    // Se comparan huellas de largo fijo y en tiempo constante: comparar el
    // texto directo deja adivinar la llave letra por letra midiendo cuanto
    // tarda la respuesta.
    if (llave == null || !MessageDigest.isEqual(huellaLlave, huella(llave))) {
      registro.warn("Peticion al agente del ERP con llave invalida");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (servicio.laNubeEntrega()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
    return null;
  }

  private static byte[] huella(String texto) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("La JVM no trae SHA-256", e);
    }
  }
}
