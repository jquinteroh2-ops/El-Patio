package co.elpatio.infraestructura.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mantiene igual la cuenta del dueño en los dos restaurantes.
 *
 * <p>El dueño tiene una cuenta de administrador en cada local, y hasta ahora
 * eran dos cuentas de verdad: cambiar la clave en La Carreta Gourmet no la cambiaba en La
 * Carreta, y había que acordarse de hacerlo dos veces y de cuál era cuál. Ahora
 * el cambio que hace en un panel viaja al otro.
 *
 * <p><b>Qué se copia y qué no.</b> Se copia lo que ES la persona: su nombre, su
 * correo, su clave y su nombre de usuario. NO se copian el rol ni si la cuenta
 * está activa, porque esos dos dicen qué puede hacer AQUÍ, y son una decisión
 * de cada restaurante: suspenderle el acceso a un local no tiene por qué
 * cerrarle el otro.
 *
 * <p><b>Viaja el hash, no la clave.</b> Los dos sistemas cifran igual, así que
 * el hash que se calculó en un lado sirve tal cual en el otro. La clave en
 * limpio no sale nunca del servidor donde se escribió.
 *
 * ── Por qué es síncrono y por qué avisa cuando falla ─────────────────────────
 * Ayer se descartó sincronizar el personal entero justamente por esto: dos
 * bases que se escriben la una a la otra se desfasan en silencio cuando una
 * llamada se pierde, y nadie se entera hasta que algo no cuadra.
 *
 * Aquí el riesgo se acota de tres maneras. Es UNA cuenta, no un directorio. El
 * cambio lo hace una persona que está mirando la pantalla, así que se le puede
 * decir la verdad —«se cambió aquí pero no allá»— en vez de esconderlo en una
 * cola. Y aplicar el mismo cambio dos veces no hace daño, así que reintentar
 * converge: el segundo intento deja las dos iguales.
 *
 * Por eso NO hay bandeja de salida ni reintentos automáticos. Un fallo se ve,
 * se vuelve a pulsar guardar, y listo.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * <p>Si no hay secreto de cruce o no está configurada la dirección del otro
 * backend, esto queda apagado y guardar un usuario funciona como siempre.
 */
@Service
public class ServicioEspejoDeCuenta {

  private static final Logger registro = LoggerFactory.getLogger(ServicioEspejoDeCuenta.class);

  /**
   * Lo que dura el sobre.
   *
   * Más que el pase —treinta segundos— porque este no lo lleva una persona
   * pulsando un botón: lo manda un servidor a otro y puede encontrarse una red
   * lenta o un contenedor despertando. Dos minutos alcanzan para eso y siguen
   * siendo una ventana corta si alguien lo interceptara.
   */
  private static final Duration VIDA = Duration.ofMinutes(2);

  /**
   * Lo que se espera al otro restaurante antes de darlo por perdido.
   *
   * Corto a propósito: al otro lado de esta llamada hay alguien esperando a que
   * termine de guardarse un usuario. Si el hermano no contesta en cinco
   * segundos, se le dice a la persona que no se pudo y que reintente, que es
   * mejor que dejarla mirando una rueda.
   */
  private static final Duration ESPERA = Duration.ofSeconds(5);

  private final LlaveDeCruce llave;
  private final String apiHermana;
  private final HttpClient cliente;

  public ServicioEspejoDeCuenta(
      LlaveDeCruce llave, @Value("${elpatio.cruce.api-hermana:}") String apiHermana) {
    this.llave = llave;
    // Sin barra final, para que al concatenar la ruta no queden dos seguidas.
    this.apiHermana = apiHermana == null ? "" : apiHermana.replaceAll("/+$", "");
    this.cliente = HttpClient.newBuilder().connectTimeout(ESPERA).build();
  }

  /** Si este despliegue puede mandarle cambios al otro restaurante. */
  public boolean puedeReplicar() {
    return llave.activo() && !apiHermana.isBlank();
  }

  /** Si este despliegue acepta cambios que le manden. */
  public boolean puedeRecibir() {
    return llave.activo();
  }

  /**
   * Manda al otro restaurante el cambio que se acaba de hacer aquí.
   *
   * No lanza: el cambio local ya se guardó y es válido, y que el hermano no
   * conteste no puede deshacerlo. Devuelve qué pasó para que la pantalla lo
   * diga.
   */
  public Resultado replicar(Cambio cambio) {
    if (!puedeReplicar()) return Resultado.APAGADO;

    try {
      String sobre = firmar(cambio);
      HttpRequest peticion =
          HttpRequest.newBuilder(URI.create(apiHermana + "/api/acceso/espejo"))
              .timeout(ESPERA)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString("{\"sobre\":\"" + sobre + "\"}"))
              .build();

      HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());

      if (respuesta.statusCode() == 200) {
        // El destino contesta si encontró la cuenta o no. No encontrarla no es
        // un fallo: significa que el dueño no tiene cuenta espejo allá, que es
        // lo normal para cualquier administrador que solo trabaje en un local.
        boolean aplicado = respuesta.body().contains("\"aplicado\":true");
        return aplicado ? Resultado.REPLICADO : Resultado.SIN_CUENTA_ALLA;
      }

      registro.warn(
          "El otro restaurante rechazó el espejo de «{}»: HTTP {}",
          cambio.usuario(),
          respuesta.statusCode());
      return Resultado.FALLO;

    } catch (InterruptedException error) {
      // Restaurar la marca es obligatorio: tragársela deja al hilo sin saber
      // que alguien le pidió terminar.
      Thread.currentThread().interrupt();
      return Resultado.FALLO;
    } catch (Exception error) {
      registro.warn("No se pudo replicar la cuenta «{}»: {}", cambio.usuario(), error.toString());
      return Resultado.FALLO;
    }
  }

  /**
   * Firma el sobre.
   *
   * Visible al paquete y no privado para que la prueba pueda producir un sobre
   * autentico sin levantar un servidor: sin esto, comprobar que uno firmado con
   * otro secreto no se abre exigiria un doble que responda lo que uno quiera
   * oir, que es justo lo que no prueba nada.
   */
  String firmar(Cambio cambio) {
    Instant ahora = Instant.now();
    return Jwts.builder()
        // El sujeto es el nombre de usuario ANTERIOR, que es con el que hay que
        // buscar la cuenta allá. Si el dueño se cambió el nombre de usuario y
        // se buscara por el nuevo, no se encontraría nada y las dos cuentas
        // quedarían separadas para siempre.
        .subject(cambio.usuarioAnterior())
        .claim("usuario", cambio.usuario())
        .claim("nombre", cambio.nombre())
        .claim("correo", cambio.correo())
        .claim("claveHash", cambio.claveHash())
        .claim("origen", llave.nombreDeEsteRestaurante())
        .issuedAt(Date.from(ahora))
        .expiration(Date.from(ahora.plus(VIDA)))
        .signWith(llave.llave())
        .compact();
  }

  /** Abre un sobre que llegó del otro restaurante. Vacío si no es de fiar. */
  public Optional<Cambio> abrir(String sobre) {
    if (!puedeRecibir() || sobre == null || sobre.isBlank()) return Optional.empty();
    try {
      Claims cuerpo = Jwts.parser().verifyWith(llave.llave()).build().parseSignedClaims(sobre).getPayload();
      return Optional.of(
          new Cambio(
              cuerpo.getSubject(),
              cuerpo.get("usuario", String.class),
              cuerpo.get("nombre", String.class),
              cuerpo.get("correo", String.class),
              cuerpo.get("claveHash", String.class),
              cuerpo.get("origen", String.class)));
    } catch (JwtException | IllegalArgumentException error) {
      return Optional.empty();
    }
  }

  /**
   * El cambio que viaja de un restaurante al otro.
   *
   * `claveHash` va nulo cuando la clave no cambió: así el destino sabe que no
   * tiene que tocar la suya, en vez de recibir el hash de siempre y no poder
   * distinguir «no cambió» de «cambió a lo mismo».
   */
  public record Cambio(
      String usuarioAnterior,
      String usuario,
      String nombre,
      String correo,
      String claveHash,
      String origen) {}

  /** Qué pasó al intentar replicar. */
  public enum Resultado {
    /** El cruce no está configurado en este despliegue. No hay nada que decir. */
    APAGADO,
    /** El otro restaurante aplicó el cambio. */
    REPLICADO,
    /** El otro restaurante contestó bien, pero allá no hay una cuenta con ese usuario. */
    SIN_CUENTA_ALLA,
    /** No se pudo. Hay que volver a guardar. */
    FALLO
  }
}
