package co.elpatio.infraestructura.seguridad;

import co.elpatio.dominio.personal.Rol;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.Reloj;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * El pase que permite pasar del panel de un restaurante al del otro sin volver
 * a escribir la clave.
 *
 * <p>El dueño tiene dos locales con este mismo sistema, y son dos despliegues
 * separados: dos bases, dos juegos de usuarios, dos secretos de sesión. Nada
 * de eso cambia aquí. Lo único que se comparte es la {@link LlaveDeCruce}, que
 * no firma ninguna sesión.
 *
 * <p><b>Por qué el pase dura 30 segundos y sirve una vez.</b> Viaja en la barra
 * de direcciones, que es el único sitio por donde puede cruzar de un dominio a
 * otro. El navegador no manda a ningún servidor lo que va después del `#`, así
 * que no queda en registros ni en la cabecera `Referer`, pero sí queda un
 * instante en el historial. Treinta segundos y un solo uso hacen que lo que
 * quede ahí ya no sirva.
 *
 * <p><b>Lo que el pase NO decide.</b> No dice que alguien pueda entrar: dice
 * quién es. Quien decide es el destino, que exige tener su propia cuenta de
 * administrador activa con ese mismo nombre de usuario. Si el dueño no tiene
 * cuenta en el otro restaurante, o está desactivada, el pase no abre nada.
 *
 * <p>Si el secreto no está configurado, la función queda APAGADA en los dos
 * sentidos: ni se emite ni se canjea. Es el estado por defecto y el correcto
 * para un despliegue suelto, donde no hay ningún restaurante hermano.
 */
@Service
public class ServicioPaseDeCruce {

  /**
   * Lo que dura un pase.
   *
   * Es el tiempo entre pulsar el botón y que cargue la otra página: segundos.
   * Se dan treinta para que aguante una red lenta, y ni uno más, porque cada
   * segundo de más es tiempo en que un pase visto de reojo todavía sirve.
   */
  private static final Duration VIDA = Duration.ofSeconds(30);

  private final LlaveDeCruce llave;
  private final Reloj reloj;
  private final SecureRandom azar = new SecureRandom();

  public ServicioPaseDeCruce(LlaveDeCruce llave, Reloj reloj) {
    this.llave = llave;
    this.reloj = reloj;
  }

  /** Si el cruce entre restaurantes está configurado en este despliegue. */
  public boolean activo() {
    return llave.activo();
  }

  /**
   * Firma un pase para que este usuario entre al otro restaurante.
   *
   * El sujeto es el NOMBRE DE USUARIO y no el identificador: los identificadores
   * se generan en cada base por separado y el de aquí no significa nada allá.
   * El nombre de usuario es lo único que las dos bases pueden compartir, y por
   * eso el dueño tiene que llamarse igual en los dos sistemas.
   */
  public String emitir(Usuario usuario) {
    if (!activo()) {
      throw new IllegalStateException("El cruce entre restaurantes no está configurado");
    }

    Instant ahora = reloj.ahora();
    return Jwts.builder()
        .id(nuevoJti())
        .subject(usuario.getUsuario())
        .claim("nombre", usuario.getNombre())
        .claim("rol", usuario.getRol().codigo())
        // De dónde viene, para que el destino lo escriba en su registro y para
        // poder decirle a la persona de dónde acaba de llegar.
        .claim("origen", llave.nombreDeEsteRestaurante())
        .issuedAt(Date.from(ahora))
        .expiration(Date.from(ahora.plus(VIDA)))
        .signWith(llave.llave())
        .compact();
  }

  /**
   * Comprueba la firma y la vigencia de un pase.
   *
   * No comprueba si ya se usó: eso exige mirar la base y es tarea de quien lo
   * canjea. Aquí solo se responde si el papel es auténtico y está en fecha.
   */
  public Optional<Pase> verificar(String pase) {
    if (!activo() || pase == null || pase.isBlank()) return Optional.empty();
    try {
      Claims cuerpo =
          Jwts.parser()
              .verifyWith(llave.llave())
              // El vencimiento se mide contra el reloj del proyecto y no contra
              // el de la maquina virtual, que es el que la libreria usa por su
              // cuenta. Es el mismo reloj con que se emitio el pase, y el
              // unico que una prueba puede mover: sin esto, comprobar que un
              // pase de hace un minuto ya no sirve exigiria esperar un minuto.
              .clock(() -> Date.from(reloj.ahora()))
              .build()
              .parseSignedClaims(pase)
              .getPayload();
      return Optional.of(
          new Pase(
              cuerpo.getId(),
              cuerpo.getSubject(),
              cuerpo.get("nombre", String.class),
              Rol.de(cuerpo.get("rol", String.class)),
              cuerpo.get("origen", String.class),
              cuerpo.getExpiration().toInstant()));
    } catch (JwtException | IllegalArgumentException error) {
      // Un pase vencido o alterado no es un fallo del sistema: es alguien
      // llegando sin credencial válida, y se responde como tal sin ruido.
      return Optional.empty();
    }
  }

  /** 128 bits de azar. Es lo que identifica al pase para que no sirva dos veces. */
  private String nuevoJti() {
    byte[] bytes = new byte[16];
    azar.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Lo que venía escrito en un pase auténtico. */
  public record Pase(String jti, String usuario, String nombre, Rol rol, String origen, Instant expiraEn) {}
}
