package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioPostulaciones;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.reclutamiento.CargoDeInteres;
import co.elpatio.dominio.reclutamiento.TipoDocumento;
import co.elpatio.infraestructura.seguridad.LimitadorDePeticiones;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * El formulario de «Trabaja con nosotros», abierto a cualquiera.
 *
 * Publico por diseño: quien busca empleo no tiene ni va a crear una cuenta en
 * el sistema del restaurante. Eso obliga a defenderlo, porque cualquier cosa
 * abierta en internet recibe antes o despues un robot que la llena mil veces.
 * Las tres defensas son el limite por IP, el señuelo y el control de envios
 * repetidos por documento, y ninguna sola alcanza.
 */
@RestController
@RequestMapping("/api/public/postulaciones")
public class ControladorPostulacionesPublico {

  private static final Logger registro =
      LoggerFactory.getLogger(ControladorPostulacionesPublico.class);

  /**
   * Cuantas postulaciones admite una IP por ventana.
   *
   * Tres es holgado para una persona —dos intentos fallidos y el bueno— y
   * cerrado para un robot. Una familia detras del mismo router puede tropezar
   * con esto; por eso el mensaje dice cuando volver y no «acceso denegado».
   */
  private static final int MAXIMO_POR_VENTANA = 3;

  private final ServicioPostulaciones servicio;
  private final LimitadorDePeticiones limitador;

  public ControladorPostulacionesPublico(
      ServicioPostulaciones servicio, LimitadorDePeticiones limitador) {
    this.servicio = servicio;
    this.limitador = limitador;
  }

  /** Los cargos, para que el formulario no los tenga escritos por su cuenta. */
  @GetMapping("/cargos")
  public List<CargoDto> cargos() {
    return Arrays.stream(CargoDeInteres.values())
        .map(c -> new CargoDto(c.codigo(), c.etiqueta()))
        .toList();
  }

  public record CargoDto(String id, String etiqueta) {}

  /** Lo que se le responde a quien acaba de enviar su hoja de vida. */
  public record Recibida(String id, String mensaje) {}

  @PostMapping(consumes = "multipart/form-data")
  public Recibida postularse(
      @RequestParam String nombreCompleto,
      @RequestParam TipoDocumento tipoDocumento,
      @RequestParam String numeroDocumento,
      @RequestParam String email,
      @RequestParam String telefono,
      @RequestParam CargoDeInteres cargoInteres,
      @RequestParam(required = false) String mensaje,
      @RequestParam(defaultValue = "false") boolean autorizacionDatos,
      /**
       * El señuelo.
       *
       * Es un campo escondido con CSS que una persona nunca ve y por tanto
       * nunca llena. Un robot que rellena todo lo que encuentra sí lo llena, y
       * ahí se delata. Se llama `sitioWeb` y no `honeypot` a propósito: el
       * nombre es parte de la trampa.
       */
      @RequestParam(required = false) String sitioWeb,
      @RequestParam MultipartFile hojaDeVida,
      HttpServletRequest peticion)
      throws IOException {

    // Al robot se le responde que todo salió bien y no se guarda nada. Decirle
    // «detectado» le enseña qué campo evitar en el siguiente intento.
    if (sitioWeb != null && !sitioWeb.isBlank()) {
      registro.info("Postulación descartada por el señuelo desde {}", ipDe(peticion));
      return new Recibida("", "Recibimos su hoja de vida. Gracias por su interés.");
    }

    String ip = ipDe(peticion);
    String clave = "postulaciones:" + ip;
    if (!limitador.permite(clave, MAXIMO_POR_VENTANA)) {
      long minutos = limitador.minutosParaReintentar(clave);
      throw new ReglaDeNegocioError(
          "Ya recibimos varias solicitudes desde este dispositivo. "
              + "Intente de nuevo en " + Math.max(1, minutos) + " minutos.");
    }

    var recibida =
        servicio.recibir(
            new ServicioPostulaciones.DatosPostulacion(
                nombreCompleto,
                tipoDocumento,
                numeroDocumento,
                email,
                telefono,
                cargoInteres,
                mensaje,
                autorizacionDatos,
                ip,
                hojaDeVida.getOriginalFilename(),
                hojaDeVida.getBytes()));

    return new Recibida(
        recibida.getId(),
        "Recibimos su hoja de vida. Si su perfil encaja con una vacante, nos comunicamos con usted.");
  }

  /**
   * La IP de quien envía, mirando primero la cabecera del proxy.
   *
   * Railway y cualquier proxy ponen la IP real en `X-Forwarded-For` y dejan en
   * `getRemoteAddr` la del propio proxy. Sin esto, todas las postulaciones
   * quedarían registradas con la misma IP —la del balanceador— y el límite se
   * aplicaría a todo el mundo a la vez.
   *
   * La cabecera la puede falsificar quien llame directamente al servidor, así
   * que esto vale para contar y para dejar evidencia, no como identificación
   * fiable de nadie.
   */
  static String ipDe(HttpServletRequest peticion) {
    String reenviada = peticion.getHeader("X-Forwarded-For");
    if (reenviada != null && !reenviada.isBlank()) {
      // Puede traer una cadena de proxies: la primera es la del cliente.
      int coma = reenviada.indexOf(',');
      String primera = coma > 0 ? reenviada.substring(0, coma) : reenviada;
      return primera.trim();
    }
    return peticion.getRemoteAddr();
  }
}
