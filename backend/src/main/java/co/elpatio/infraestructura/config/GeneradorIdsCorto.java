package co.elpatio.infraestructura.config;

import co.elpatio.dominio.puertos.GeneradorIds;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Identificadores con prefijo legible: ord_m3k1a2b4.
 *
 * Se conservo el formato del prototipo porque aparece en las URLs de la
 * comandera y en los registros: leer "ord_m3k1a2" ahorra una consulta cuando se
 * rastrea un problema en sala. La parte aleatoria sale de SecureRandom y no de
 * Math.random, que era lo unico disponible en el navegador.
 */
@Component
public class GeneradorIdsCorto implements GeneradorIds {

  private static final String ALFABETO = "0123456789abcdefghijklmnopqrstuvwxyz";
  private final SecureRandom azar = new SecureRandom();

  @Override
  public String nuevo(String prefijo) {
    StringBuilder sufijo = new StringBuilder(Long.toString(System.currentTimeMillis(), 36));
    for (int i = 0; i < 5; i++) sufijo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
    return prefijo + "_" + sufijo;
  }
}
