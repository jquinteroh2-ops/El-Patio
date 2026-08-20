package co.elpatio.dominio.error;

/** Lo que se pidio ya no existe. Se traduce a 404. */
public class NoEncontradoError extends RuntimeException {
  public NoEncontradoError(String mensaje) {
    super(mensaje);
  }
}
