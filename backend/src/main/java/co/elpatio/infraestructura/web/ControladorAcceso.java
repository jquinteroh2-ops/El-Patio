package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioAcceso;
import co.elpatio.aplicacion.dto.Dtos;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingreso y administracion del personal.
 *
 * `/api/acceso/ingresar` y `/api/acceso/refrescar` son las dos unicas rutas del
 * sistema que no exigen sesion, porque son justamente las que la entregan.
 */
@RestController
@RequestMapping("/api")
public class ControladorAcceso {

  private final ServicioAcceso servicio;

  public ControladorAcceso(ServicioAcceso servicio) {
    this.servicio = servicio;
  }

  /** Equivale a `autenticar(usuario, clave)` de mockApi.ts. */
  @PostMapping("/acceso/ingresar")
  public Dtos.RespuestaAcceso ingresar(@RequestBody Dtos.PeticionIngreso peticion) {
    return servicio.ingresar(peticion.usuario(), peticion.clave());
  }

  @PostMapping("/acceso/refrescar")
  public Dtos.RespuestaAcceso refrescar(@RequestBody Dtos.PeticionRefresco peticion) {
    return servicio.refrescar(peticion.refresco());
  }

  @PostMapping("/acceso/salir")
  public ResponseEntity<Void> salir(@RequestBody Dtos.PeticionRefresco peticion) {
    servicio.salir(peticion.refresco());
    return ResponseEntity.noContent().build();
  }

  /** Equivale a `listarUsuarios()`. */
  @GetMapping("/usuarios")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public List<Dtos.UsuarioDto> listarUsuarios() {
    return servicio.listarUsuarios();
  }

  /** Equivale a `guardarUsuario(usuario)`. */
  @PutMapping("/usuarios")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Dtos.UsuarioDto guardarUsuario(@RequestBody Dtos.UsuarioDto usuario) {
    return servicio.guardarUsuario(usuario);
  }
}
