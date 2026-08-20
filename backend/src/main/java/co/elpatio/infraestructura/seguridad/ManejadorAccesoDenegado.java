package co.elpatio.infraestructura.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Respuesta cuando la sesion es valida pero el rol no alcanza para esa area. */
public class ManejadorAccesoDenegado implements AccessDeniedHandler {

  @Override
  public void handle(
      HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException error)
      throws IOException {
    respuesta.setStatus(HttpServletResponse.SC_FORBIDDEN);
    respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
    respuesta.setCharacterEncoding("UTF-8");
    respuesta.getWriter().write("{\"mensaje\":\"Su usuario no tiene permiso para esta área\"}");
  }
}
