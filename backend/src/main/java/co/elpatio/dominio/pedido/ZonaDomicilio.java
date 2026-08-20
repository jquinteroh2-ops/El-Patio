package co.elpatio.dominio.pedido;

/** Un barrio al que se lleva, con lo que cuesta y lo que tarda llegar. */
public class ZonaDomicilio {
  private String id;
  private String nombre;
  /** Lo que se le cobra al cliente por el envio. No causa INC ni propina. */
  private long tarifa;
  /** Por debajo de esto el envio se comeria el margen del pedido. */
  private long montoMinimo;
  private int minutosEstimados;
  private boolean activa;
  private int orden;

  public ZonaDomicilio() {}

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getNombre() { return nombre; }
  public void setNombre(String nombre) { this.nombre = nombre; }
  public long getTarifa() { return tarifa; }
  public void setTarifa(long tarifa) { this.tarifa = tarifa; }
  public long getMontoMinimo() { return montoMinimo; }
  public void setMontoMinimo(long montoMinimo) { this.montoMinimo = montoMinimo; }
  public int getMinutosEstimados() { return minutosEstimados; }
  public void setMinutosEstimados(int minutosEstimados) { this.minutosEstimados = minutosEstimados; }
  public boolean isActiva() { return activa; }
  public void setActiva(boolean activa) { this.activa = activa; }
  public int getOrden() { return orden; }
  public void setOrden(int orden) { this.orden = orden; }
}
