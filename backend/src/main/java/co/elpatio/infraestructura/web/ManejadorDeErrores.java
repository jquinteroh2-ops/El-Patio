package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores a respuestas.
 *
 * Todas salen con la misma forma, `{ "mensaje": "..." }`, porque el frontend
 * las lee con un solo camino: lo que llegue en `mensaje` es lo que ve el mesero
 * en pantalla. Por eso los mensajes de negocio estan en espanol y en tono de
 * sala, y los tecnicos jamas se reenvian tal cual.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

  private static final Logger registro = LoggerFactory.getLogger(ManejadorDeErrores.class);

  /** Una regla del negocio dijo que no. Es 409 y no 400: la peticion estaba bien formada. */
  @ExceptionHandler(ReglaDeNegocioError.class)
  public ResponseEntity<Dtos.ErrorApi> reglaDeNegocio(ReglaDeNegocioError error) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new Dtos.ErrorApi(error.getMessage()));
  }

  @ExceptionHandler(NoEncontradoError.class)
  public ResponseEntity<Dtos.ErrorApi> noEncontrado(NoEncontradoError error) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Dtos.ErrorApi(error.getMessage()));
  }

  /**
   * Rol insuficiente detectado por @PreAuthorize.
   *
   * Hay que declararlo aqui aunque la cadena de seguridad ya tenga su propio
   * manejador: cuando el rechazo lo lanza la anotacion del metodo, la excepcion
   * nace dentro del controlador y este @RestControllerAdvice la atrapa primero.
   * Sin esta regla, un permiso denegado salia como 500.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Dtos.ErrorApi> accesoDenegado(AccessDeniedException error) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new Dtos.ErrorApi("Su usuario no tiene permiso para esta área"));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Dtos.ErrorApi> sinCredencial(AuthenticationException error) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new Dtos.ErrorApi("La sesión expiró: vuelva a ingresar"));
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
  public ResponseEntity<Dtos.ErrorApi> peticionMalFormada(Exception error) {
    registro.warn("Petición mal formada: {}", error.getMessage());
    return ResponseEntity.badRequest().body(new Dtos.ErrorApi("La petición llegó incompleta o mal formada"));
  }

  /**
   * Choque contra una restriccion de la base. Casi siempre es una carrera entre
   * dos dispositivos: dos meseros abriendo la misma mesa, dos cobros del mismo
   * ticket. Se responde 409 para que el frontend reintente o recargue.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Dtos.ErrorApi> conflictoDeDatos(DataIntegrityViolationException error) {
    registro.warn("Conflicto de integridad", error);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new Dtos.ErrorApi("Alguien más cambió esto al mismo tiempo: vuelva a intentarlo"));
  }

  /**
   * Lo que no se previo. El detalle va al registro, nunca a la respuesta: un
   * mensaje de excepcion puede revelar nombres de tablas o rutas del servidor.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Dtos.ErrorApi> inesperado(Exception error) {
    registro.error("Error no controlado", error);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new Dtos.ErrorApi("Ocurrió un problema en el servidor. Avise al administrador."));
  }
}
