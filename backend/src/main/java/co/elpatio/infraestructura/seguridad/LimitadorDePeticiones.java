package co.elpatio.infraestructura.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Cuantas veces puede una misma IP tocar un formulario publico.
 *
 * Existe porque los formularios de postulacion y de PQR no piden sesion: son
 * publicos por diseño, y cualquier cosa publica en internet recibe antes o
 * despues un robot que la llena mil veces. Sin limite, una tarde de eso deja la
 * bandeja del administrador con basura y el volumen del disco lleno de PDF.
 *
 * <p><b>Es una ventana fija y vive en memoria.</b> Las dos cosas son decisiones
 * conscientes:
 *
 * <ul>
 *   <li><b>En memoria</b> significa que se reinicia con el servicio y que no se
 *       comparte entre instancias. Para un restaurante con una sola instancia
 *       es suficiente y no obliga a montar un Redis. Si algun dia hay varias
 *       instancias, esto deja de ser un limite real y hay que cambiarlo por
 *       algo compartido: queda dicho aqui para que no se descubra tarde.
 *   <li><b>Ventana fija</b>, no deslizante: en el peor caso alguien mete el
 *       doble del cupo a caballo entre dos ventanas. Da igual para lo que esto
 *       protege, y una ventana deslizante costaria guardar cada marca de tiempo.
 * </ul>
 *
 * Esto NO es proteccion contra un ataque decidido —basta cambiar de IP— sino
 * contra el ruido de fondo y el envio accidental repetido.
 */
@Component
public class LimitadorDePeticiones {

  /** Cada cuanto se vacia el contador. */
  private static final Duration VENTANA = Duration.ofMinutes(10);

  /**
   * Cuando se limpian las entradas viejas.
   *
   * Sin esto el mapa crece con cada IP que pase y no baja nunca: es una fuga de
   * memoria lenta, de las que se manifiestan a los meses.
   */
  private static final Duration LIMPIEZA = Duration.ofHours(1);

  private final Map<String, Contador> contadores = new ConcurrentHashMap<>();
  private volatile Instant ultimaLimpieza = Instant.now();

  private static final class Contador {
    final AtomicInteger veces = new AtomicInteger();
    volatile Instant desde;

    Contador(Instant desde) {
      this.desde = desde;
    }
  }

  /**
   * Cuenta un intento y dice si todavia cabe.
   *
   * @param clave que se esta limitando; lleva el nombre del formulario ademas
   *     de la IP, para que agotar el cupo de postulaciones no cierre tambien el
   *     de PQR, que son cosas distintas hechas por gente distinta.
   * @param maximo cuantas se permiten en la ventana.
   */
  public boolean permite(String clave, int maximo) {
    Instant ahora = Instant.now();
    limpiarDeVezEnCuando(ahora);

    Contador contador = contadores.computeIfAbsent(clave, k -> new Contador(ahora));

    // La ventana venció: se reinicia. Va sincronizado sobre el contador porque
    // dos peticiones simultáneas en el instante del vencimiento podrían
    // reiniciarlo dos veces y regalar el doble de cupo.
    synchronized (contador) {
      if (Duration.between(contador.desde, ahora).compareTo(VENTANA) > 0) {
        contador.desde = ahora;
        contador.veces.set(0);
      }
    }

    return contador.veces.incrementAndGet() <= maximo;
  }

  /** Cuanto falta para que se libere el cupo, para decírselo a quien lo agotó. */
  public long minutosParaReintentar(String clave) {
    Contador contador = contadores.get(clave);
    if (contador == null) return 0;
    long transcurridos = Duration.between(contador.desde, Instant.now()).toMinutes();
    return Math.max(0, VENTANA.toMinutes() - transcurridos);
  }

  private void limpiarDeVezEnCuando(Instant ahora) {
    if (Duration.between(ultimaLimpieza, ahora).compareTo(LIMPIEZA) < 0) return;
    ultimaLimpieza = ahora;
    contadores
        .entrySet()
        .removeIf(e -> Duration.between(e.getValue().desde, ahora).compareTo(LIMPIEZA) > 0);
  }
}
