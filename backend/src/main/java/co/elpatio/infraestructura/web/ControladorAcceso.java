package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioAcceso;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.infraestructura.config.ModoDemostracion;
import co.elpatio.infraestructura.seguridad.ServicioEspejoDeCuenta;
import co.elpatio.infraestructura.seguridad.ServicioTokens;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingreso y administracion del personal.
 *
 * `/api/acceso/ingresar`, `/api/acceso/refrescar` y `/api/acceso/cruce/canjear`
 * son las tres unicas rutas del sistema que entregan sesion sin exigirla. Las
 * dos primeras piden clave o token de refresco; la tercera pide un pase firmado
 * por el otro restaurante del dueno. `/api/acceso/demostracion` tambien queda
 * abierta, pero no entrega nada mientras el modo este apagado.
 */
@RestController
@RequestMapping("/api")
public class ControladorAcceso {

  private final ServicioAcceso servicio;
  private final ModoDemostracion demostracion;
  private final ServicioEspejoDeCuenta espejo;

  // El modo se lee aqui y no en ServicioAcceso porque es configuracion del
  // despliegue, no una regla del restaurante: la capa de aplicacion no conoce
  // el paquete de infraestructura, y este controlador si.
  public ControladorAcceso(
      ServicioAcceso servicio, ModoDemostracion demostracion, ServicioEspejoDeCuenta espejo) {
    this.servicio = servicio;
    this.demostracion = demostracion;
    this.espejo = espejo;
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

  /**
   * Emite el pase con que el dueno cruza al panel del otro restaurante.
   *
   * Exige sesion de administrador: sin `@PreAuthorize` esta seria una ruta para
   * fabricarse un pase a nombre de cualquiera. Lo que se pasa al servicio es el
   * identificador de quien pidio el pase, no un nombre que venga en el cuerpo,
   * por lo mismo.
   */
  @PostMapping("/acceso/cruce")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Dtos.RespuestaPaseDeCruce paseDeCruce(
      @AuthenticationPrincipal ServicioTokens.Credencial credencial) {
    return servicio.emitirPaseDeCruce(credencial.usuarioId());
  }

  /**
   * Canjea un pase del otro restaurante por una sesion de este.
   *
   * Abierta, como el ingreso: quien llega todavia no tiene sesion aqui, y lo
   * que trae en lugar de la clave es el pase. Va por POST y no por la URL para
   * que el pase no quede escrito en los registros del servidor ni en el
   * historial del navegador.
   */
  @PostMapping("/acceso/cruce/canjear")
  public Dtos.RespuestaAcceso canjearPaseDeCruce(@RequestBody Dtos.PeticionPaseDeCruce peticion) {
    return servicio.canjearPaseDeCruce(peticion.pase());
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

  /**
   * Equivale a `guardarUsuario(usuario)`.
   *
   * Si el usuario guardado es administrador, el cambio viaja ademas al otro
   * restaurante del dueno: alla tiene la misma cuenta y tiene que quedar igual.
   *
   * LA LLAMADA AL HERMANO SE HACE AQUI Y NO EN EL SERVICIO, a proposito. El
   * servicio guarda dentro de una transaccion, y meter una peticion HTTP de
   * hasta cinco segundos ahi dentro es tener una conexion de base bloqueada
   * esperando a otro servidor. Aqui la transaccion ya cerro.
   *
   * Que el hermano no conteste NO deshace lo que se guardo aqui: el cambio
   * local es valido por si mismo. Lo que se hace es contarlo en la respuesta,
   * para que la pantalla lo diga y la persona vuelva a guardar. Reintentar es
   * seguro: aplicar el mismo cambio dos veces deja lo mismo.
   */
  @PutMapping("/usuarios")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Dtos.RespuestaUsuarioGuardado guardarUsuario(@RequestBody Dtos.UsuarioDto usuario) {
    ServicioAcceso.UsuarioGuardado guardado = servicio.guardarUsuario(usuario);

    ServicioEspejoDeCuenta.Resultado resultado = ServicioEspejoDeCuenta.Resultado.APAGADO;
    if (guardado.esAdministrador()) {
      resultado =
          espejo.replicar(
              new ServicioEspejoDeCuenta.Cambio(
                  guardado.usuarioAnterior(),
                  guardado.usuario().usuario(),
                  guardado.usuario().nombre(),
                  guardado.usuario().correo(),
                  guardado.claveHash(),
                  null));
    }

    return new Dtos.RespuestaUsuarioGuardado(
        guardado.usuario(), resultado.name().toLowerCase(java.util.Locale.ROOT));
  }

  /**
   * Recibe del otro restaurante un cambio sobre la cuenta del dueno.
   *
   * Abierta, como el canje del pase: quien llama es el otro servidor, que no
   * tiene sesion aqui. Lo que lo autentica es la firma del sobre, hecha con el
   * secreto que solo conocen los dos restaurantes.
   */
  @PostMapping("/acceso/espejo")
  public Dtos.RespuestaEspejo recibirEspejo(@RequestBody Dtos.PeticionEspejo peticion) {
    return espejo
        .abrir(peticion.sobre())
        .map(cambio -> new Dtos.RespuestaEspejo(servicio.aplicarEspejo(cambio)))
        // Un sobre que no valida no dice cuanto falto para validar: se responde
        // lo mismo que si la cuenta no existiera.
        .orElseGet(() -> new Dtos.RespuestaEspejo(false));
  }
}
