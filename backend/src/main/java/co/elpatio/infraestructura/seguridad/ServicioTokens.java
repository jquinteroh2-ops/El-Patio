package co.elpatio.infraestructura.seguridad;

import co.elpatio.dominio.personal.Rol;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.Reloj;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emision y verificacion de tokens.
 *
 * El token de acceso es corto y no se guarda en ninguna parte: se verifica con
 * la firma. El de refresco es largo y si se guarda, pero solo su hash, para que
 * una copia de la base no sea una copia de las sesiones. Guardarlo es lo que
 * permite revocar: sin eso, cerrar sesion en una tablet perdida no serviria de
 * nada hasta que el token expirara solo.
 */
@Service
public class ServicioTokens {

  private final SecretKey llave;
  private final Duration vidaAcceso;
  private final Duration vidaRefresco;
  private final Reloj reloj;
  private final GeneradorIds ids;
  private final SecureRandom azar = new SecureRandom();

  public ServicioTokens(
      @Value("${elpatio.jwt.secreto}") String secreto,
      @Value("${elpatio.jwt.minutos-acceso:20}") long minutosAcceso,
      @Value("${elpatio.jwt.dias-refresco:30}") long diasRefresco,
      Reloj reloj,
      GeneradorIds ids) {
    // Una clave corta hace que la firma se pueda romper por fuerza bruta. Se
    // exige el minimo de HS256 aqui y no en la documentacion, para que un
    // despliegue mal configurado no arranque en vez de fallar en silencio.
    byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException(
          "ELPATIO_JWT_SECRETO debe tener al menos 32 caracteres: la firma actual es débil");
    }
    this.llave = Keys.hmacShaKeyFor(bytes);
    this.vidaAcceso = Duration.ofMinutes(minutosAcceso);
    this.vidaRefresco = Duration.ofDays(diasRefresco);
    this.reloj = reloj;
    this.ids = ids;
  }

  public Duration vidaAcceso() {
    return vidaAcceso;
  }

  public Duration vidaRefresco() {
    return vidaRefresco;
  }

  /** Token de acceso firmado. Lleva el rol para no consultar la base en cada peticion. */
  public String emitirAcceso(Usuario usuario) {
    Instant ahora = reloj.ahora();
    return Jwts.builder()
        .subject(usuario.getId())
        .claim("usuario", usuario.getUsuario())
        .claim("nombre", usuario.getNombre())
        .claim("rol", usuario.getRol().codigo())
        .issuedAt(Date.from(ahora))
        .expiration(Date.from(ahora.plus(vidaAcceso)))
        .signWith(llave)
        .compact();
  }

  public Optional<Credencial> verificarAcceso(String token) {
    try {
      Claims cuerpo = Jwts.parser().verifyWith(llave).build().parseSignedClaims(token).getPayload();
      return Optional.of(
          new Credencial(
              cuerpo.getSubject(),
              cuerpo.get("usuario", String.class),
              cuerpo.get("nombre", String.class),
              Rol.de(cuerpo.get("rol", String.class)),
              cuerpo.getExpiration().toInstant()));
    } catch (JwtException | IllegalArgumentException error) {
      // Un token vencido o alterado no es un fallo del sistema: es una peticion
      // sin credencial valida, y se responde 401 sin ruido en los registros.
      return Optional.empty();
    }
  }

  /**
   * Token de refresco: 256 bits de azar, no un JWT. No necesita llevar datos
   * adentro porque siempre se contrasta contra la fila que lo respalda.
   */
  public String nuevoRefresco() {
    byte[] bytes = new byte[32];
    azar.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String hashDe(String token) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      return Base64.getEncoder().encodeToString(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 no disponible en esta máquina virtual", error);
    }
  }

  public String nuevoIdSesion() {
    return ids.nuevo("ses");
  }

  /** Lo que el filtro deja disponible para el resto de la peticion. */
  public record Credencial(String usuarioId, String usuario, String nombre, Rol rol, Instant expiraEn) {}
}
