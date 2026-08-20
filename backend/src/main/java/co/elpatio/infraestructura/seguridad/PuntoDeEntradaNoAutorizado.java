package co.elpatio.infraestructura.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Respuesta cuando falta la credencial o esta vencida.
 *
 * El cuerpo tiene la misma forma que el resto de los errores del API para que
 * el frontend lo lea con un solo camino, y el mensaje esta en espanol porque
 * puede terminar en pantalla si la renovacion silenciosa no alcanza a correr.
 */
public class PuntoDeEntradaNoAutorizado implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException error)
      throws IOException {
    respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
    respuesta.setCharacterEncoding("UTF-8");
    respuesta.getWriter().write("{\"mensaje\":\"La sesión expiró: vuelva a ingresar\"}");
  }
}
