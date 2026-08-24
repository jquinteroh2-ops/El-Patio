package co.elpatio.dominio.erp;

import java.time.Duration;
import java.time.Instant;

/**
 * Cuando se vuelve a intentar un envio que fallo, y hasta cuando.
 *
 * El retroceso es exponencial por una razon practica: si Globalsoft esta caido,
 * reintentar cada minuto durante una noche entera son cientos de intentos que
 * no van a servir y que ademas llenan la bitacora de ruido hasta esconder el
 * fallo real. Duplicar la espera llega al mismo sitio con una decena.
 *
 * El tope existe por otra razon: una cola que reintenta para siempre no es una
 * cola, es una fuga. Agotado el tope, el envio queda en ERROR_ERP y aparece en
 * la pantalla de conciliacion, que es donde una persona puede mirarlo. Perder
 * el reintento automatico es el precio de que alguien se entere.
 */
public record PoliticaReintentos(Duration esperaInicial, int maximoIntentos, Duration esperaMaxima) {

  /**
   * Lo razonable para un ERP local: empieza en un minuto y no pasa de una hora.
   *
   * Con estos valores, un envio agota sus ocho intentos en poco mas de dos
   * horas. Es tiempo de sobra para un reinicio del servidor del restaurante y
   * lo bastante corto para que una caida larga se vea el mismo dia, y no en el
   * cierre contable de fin de mes.
   */
  public static PoliticaReintentos porDefecto() {
    return new PoliticaReintentos(Duration.ofMinutes(1), 8, Duration.ofHours(1));
  }

  /**
   * Cuando toca el siguiente intento, dados los fallos que ya lleva.
   *
   * El primer fallo espera {@code esperaInicial}; a partir de ahi la espera se
   * duplica y se corta en {@code esperaMaxima}. El parametro son fallos
   * ACUMULADOS y no intentos pendientes: contarlo al reves adelanta todo un
   * escalon y hace que el primer reintento espere el doble de lo configurado,
   * que es justo el tipo de desfase que nadie nota hasta que importa.
   *
   * El calculo va en segundos y no multiplicando Duration: duplicar sin techo
   * es exactamente como se desborda un contador, y aqui el techo corta antes de
   * que el problema exista.
   */
  public Instant proximoIntento(Instant ahora, int fallosAcumulados) {
    long segundos = esperaInicial.getSeconds();
    for (int i = 1; i < fallosAcumulados && segundos < esperaMaxima.getSeconds(); i++) {
      segundos *= 2;
    }
    return ahora.plusSeconds(Math.min(segundos, esperaMaxima.getSeconds()));
  }

  /** Si todavia queda margen para volver a intentarlo. */
  public boolean quedanIntentos(int fallosAcumulados) {
    return fallosAcumulados < maximoIntentos;
  }
}
