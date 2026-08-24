package co.elpatio.dominio.pqr;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.util.Locale;

/**
 * El numero con que el cliente vuelve a preguntar por su solicitud.
 *
 * <p><b>No es cosmetico.</b> Es la evidencia de que la solicitud entro y la
 * unica referencia con que alguien que no tiene cuenta puede consultarla
 * despues. Un radicado con saltos o repetido destruye las dos cosas: si hay
 * huecos, el restaurante no puede demostrar cuantas recibio; si hay repetidos,
 * dos personas distintas ven la misma solicitud.
 *
 * <p>El formato es {@code PQR-AAAA-NNNNN}: legible por telefono —«pe-cu-erre,
 * dos mil veintiseis, cero cero cero cuarenta y siete»— y ordenable por año,
 * que es como se archiva.
 *
 * <p><b>El consecutivo se reinicia cada año</b>, como cualquier consecutivo
 * documental. Por eso el año va en el numero: sin el, el consecutivo del 2027
 * chocaria con el del 2026.
 */
public record Radicado(int ano, int consecutivo) {

  /** Cinco digitos dan cien mil solicitudes por año. De sobra para un restaurante. */
  private static final int DIGITOS = 5;

  public Radicado {
    if (ano < 2000 || ano > 2999) throw new ReglaDeNegocioError("Año de radicado fuera de rango");
    if (consecutivo <= 0) throw new ReglaDeNegocioError("El consecutivo empieza en 1");
  }

  @Override
  public String toString() {
    return String.format(Locale.ROOT, "PQR-%d-%0" + DIGITOS + "d", ano, consecutivo);
  }

  /**
   * Lee un radicado escrito.
   *
   * Acepta minusculas y espacios sobrantes porque el cliente lo va a copiar de
   * un correo o a escribirlo de memoria, y rechazarle «pqr-2026-00047» por la
   * caja de las letras seria negarle la consulta por nada.
   */
  public static Radicado de(String texto) {
    if (texto == null) throw new ReglaDeNegocioError("Falta el número de radicado");
    String limpio = texto.trim().toUpperCase(Locale.ROOT);
    String[] partes = limpio.split("-");
    if (partes.length != 3 || !partes[0].equals("PQR")) {
      throw new ReglaDeNegocioError("El número de radicado no tiene el formato PQR-AAAA-NNNNN");
    }
    try {
      return new Radicado(Integer.parseInt(partes[1]), Integer.parseInt(partes[2]));
    } catch (NumberFormatException e) {
      throw new ReglaDeNegocioError("El número de radicado no tiene el formato PQR-AAAA-NNNNN");
    }
  }
}
