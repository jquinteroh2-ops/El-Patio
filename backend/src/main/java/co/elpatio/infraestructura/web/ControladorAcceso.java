package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioAcceso;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.infraestructura.config.ModoDemostracion;
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
 * sistema que entregan sesion sin exigirla. `/api/acceso/demostracion` tambien
 * queda abierta, pero no entrega nada mientras el modo este apagado.
 */
@RestController
@RequestMapping("/api")
public class ControladorAcceso {

  private final ServicioAcceso servicio;
  private final ModoDemostracion demostracion;

  // El modo se lee aqui y no en ServicioAcceso porque es configuracion del
  // despliegue, no una regla del restaurante: la capa de aplicacion no conoce
  // el paquete de infraestructura, y este controlador si.
  public ControladorAcceso(ServicioAcceso servicio, ModoDemostracion demostracion) {
    this.servicio = servicio;
    this.demostracion = demostracion;
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

  /**
   * Que cuentas ensenar en la pantalla de acceso.
   *
   * Abierta a proposito: la consulta la pantalla antes de que exista sesion.
   * Con el modo apagado responde una lista vacia, que es lo unico que puede
   * filtrarse de un despliegue de produccion.
   */
  @GetMapping("/acceso/demostracion")
  public Dtos.CuentasDemostracion cuentasDeDemostracion() {
    if (!demostracion.activo()) {
      return new Dtos.CuentasDemostracion(false, "", List.of());
    }
    return new Dtos.CuentasDemostracion(
        true,
        demostracion.clave(),
        demostracion.cuentas().stream()
            .map(
                cuenta ->
                    new Dtos.CuentaDemostracion(
                        cuenta.usuario(), cuenta.nombre(), cuenta.rol(), cuenta.destino()))
            .toList());
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
