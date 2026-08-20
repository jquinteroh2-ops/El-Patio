package co.elpatio.infraestructura.tiemporeal;

/**
 * Topicos por area operativa.
 *
 * Estan escritos una sola vez aqui porque los tiene que conocer tambien el
 * frontend: si cambia el nombre de uno, la pantalla que lo escuchaba se queda
 * muda sin que nada falle de forma visible.
 */
public final class Topicos {

  private Topicos() {}

  /** Comandas: lo que mira cocina y barra. */
  public static final String COMANDAS = "/topic/comandas";

  /** Estado del salon: lo que mira la comandera. */
  public static final String MESAS = "/topic/mesas";

  /** Domicilios y para llevar: lo que mira recepcion. Se llena en la fase 3. */
  public static final String PEDIDOS = "/topic/pedidos";

  /** Todo lo demas: carta, ajustes, reservas, pagos, cierres. */
  public static final String GENERAL = "/topic/general";
}
