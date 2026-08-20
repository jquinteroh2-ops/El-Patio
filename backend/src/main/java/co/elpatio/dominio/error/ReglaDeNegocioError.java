package co.elpatio.dominio.error;

/**
 * Violacion de una regla del negocio: la mesa ya tiene cuenta, el plato esta
 * agotado, la cuenta ya se cobro.
 *
 * Es distinta de un fallo tecnico porque el mensaje se le muestra tal cual al
 * mesero en la tablet. Por eso se escribe en espanol y en tono de sala, no de
 * sistema. El manejador de errores la traduce a 409.
 */
public class ReglaDeNegocioError extends RuntimeException {
  public ReglaDeNegocioError(String mensaje) {
    super(mensaje);
  }
}
