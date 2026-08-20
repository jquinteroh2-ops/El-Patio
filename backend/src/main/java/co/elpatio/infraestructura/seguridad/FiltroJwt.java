package co.elpatio.infraestructura.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee el token de acceso de cada peticion y deja la credencial en el contexto.
 *
 * No decide si la peticion se permite: eso lo resuelven las reglas por endpoint.
 * Aqui solo se establece quien viene, y si el token falta o esta vencido la
 * peticion sigue como anonima para que la cadena responda 401 en un solo sitio.
 */
@Component
public class FiltroJwt extends OncePerRequestFilter {

  private static final String PREFIJO = "Bearer ";

  private final ServicioTokens tokens;

  public FiltroJwt(ServicioTokens tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest peticion,
      @NonNull HttpServletResponse respuesta,
      @NonNull FilterChain cadena)
      throws ServletException, IOException {

    String cabecera = peticion.getHeader("Authorization");
    if (cabecera != null && cabecera.startsWith(PREFIJO)) {
      String token = cabecera.substring(PREFIJO.length()).trim();
      tokens
          .verificarAcceso(token)
          .ifPresent(
              credencial -> {
                var autenticacion =
                    new UsernamePasswordAuthenticationToken(
                        credencial,
                        null,
                        List.of(new SimpleGrantedAuthority(credencial.rol().autoridad())));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
              });
    }

    cadena.doFilter(peticion, respuesta);
  }
}
