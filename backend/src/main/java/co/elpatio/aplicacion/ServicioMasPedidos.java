package co.elpatio.aplicacion;

import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los platos más pedidos, para la carta pública.
 *
 * Sale de las ventas pagadas de los últimos días, contadas por unidades. Al
 * sitio solo le llegan los ids de los platos en orden: ni unidades ni dinero,
 * que son cifras del negocio y no del cliente.
 *
 * <p>Se guarda unos minutos en memoria porque lo pide cada visita a la carta y
 * para calcularlo hay que recorrer las ventas del periodo; un ranking que se
 * actualiza cada diez minutos no le cambia nada a quien lo lee.
 */
@Service
public class ServicioMasPedidos {

  /** Ventana de ventas que cuenta para el ranking. */
  private static final Duration VENTANA = Duration.ofDays(30);
  private static final Duration VIGENCIA = Duration.ofMinutes(10);
  private static final int CUANTOS = 6;
  /**
   * Con menos platos distintos vendidos no es un ranking sino casualidad: la
   * sección no se muestra hasta que haya ventas suficientes.
   */
  private static final int MINIMO = 3;

  private final Repositorios.DePagos pagos;
  private final Repositorios.DeOrdenes ordenes;
  private final Repositorios.DeCarta carta;
  private final Reloj reloj;

  private List<String> enMemoria = List.of();
  private Instant calculadoEn = Instant.EPOCH;

  public ServicioMasPedidos(
      Repositorios.DePagos pagos,
      Repositorios.DeOrdenes ordenes,
      Repositorios.DeCarta carta,
      Reloj reloj) {
    this.pagos = pagos;
    this.ordenes = ordenes;
    this.carta = carta;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  public synchronized List<String> masPedidos() {
    Instant ahora = reloj.ahora();
    if (Duration.between(calculadoEn, ahora).compareTo(VIGENCIA) < 0) return enMemoria;

    Map<String, Integer> unidades = new HashMap<>();
    for (Pago pago : pagos.entre(ahora.minus(VENTANA), ahora)) {
      ordenes
          .porId(pago.getOrdenId())
          .ifPresent(
              orden -> {
                for (ItemOrden item : orden.itemsVigentes()) {
                  unidades.merge(item.getItemCartaId(), item.getCantidad(), Integer::sum);
                }
              });
    }

    // Solo lo que sigue en la carta y se puede pedir: recomendar un plato
    // agotado o retirado es mandar al cliente a buscar algo que no hay.
    List<String> vigentes =
        carta.listarItems().stream().filter(i -> i.isDisponible()).map(i -> i.getId()).toList();

    List<String> ranking =
        unidades.entrySet().stream()
            .filter(e -> vigentes.contains(e.getKey()))
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(CUANTOS)
            .map(Map.Entry::getKey)
            .toList();

    enMemoria = ranking.size() < MINIMO ? List.of() : ranking;
    calculadoEn = ahora;
    return enMemoria;
  }
}
