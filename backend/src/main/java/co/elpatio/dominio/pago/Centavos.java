package co.elpatio.dominio.pago;

import java.math.BigDecimal;

/**
 * La unica conversion entre pesos (como los maneja el resto del dominio,
 * enteros sin decimales) y centavos (como los exige Wompi en
 * `amount_in_cents`).
 *
 * Que exista una sola funcion no es un capricho: un peso que alguien
 * multiplica por cien a mano en dos sitios distintos es un cero de mas
 * esperando a que lo escriban. Se usa `BigDecimal` y no aritmetica de `long`
 * suelta para que la conversion quede escrita una sola vez y de forma exacta.
 */
public final class Centavos {

  private static final BigDecimal CIEN = BigDecimal.valueOf(100);

  private Centavos() {}

  public static long deCOP(long pesos) {
    return BigDecimal.valueOf(pesos).multiply(CIEN).longValueExact();
  }

  public static long aCOP(long centavos) {
    return BigDecimal.valueOf(centavos).divide(CIEN).longValueExact();
  }
}
