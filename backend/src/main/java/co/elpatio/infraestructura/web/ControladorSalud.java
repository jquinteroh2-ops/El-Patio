package co.elpatio.infraestructura.web;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pulso del sistema, en /salud.
 *
 * Railway lo consulta para decidir si el despliegue quedo sano. No basta con
 * responder que el proceso vive: si la base no contesta, la comandera tampoco
 * sirve, asi que la consulta forma parte de la respuesta.
 */
@RestController
public class ControladorSalud {

  private final JdbcTemplate jdbc;

  public ControladorSalud(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/salud")
  public ResponseEntity<Map<String, Object>> salud() {
    try {
      jdbc.queryForObject("select 1", Integer.class);
      return ResponseEntity.ok(Map.of("estado", "sano", "base", "conectada"));
    } catch (Exception error) {
      return ResponseEntity.status(503)
          .body(Map.of("estado", "degradado", "base", "sin conexión"));
    }
  }
}
