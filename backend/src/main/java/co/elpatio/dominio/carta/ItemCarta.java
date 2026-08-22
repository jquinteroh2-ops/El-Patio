package co.elpatio.dominio.carta;

import java.time.LocalDate;
import java.util.List;

/** Un producto de la carta. */
public class ItemCarta {
  private String id;
  private String categoriaId;
  private String nombre;
  private String descripcion;
  private long precio;
  /**
   * Precio de promocion, o nulo si el plato se vende al de lista.
   *
   * Es un PRECIO y no un descuento, y esa diferencia es la que mantiene simple
   * todo lo demas: la venta ocurre a este valor y el INC, la propina y el
   * documento electronico se calculan sobre el sin ninguna regla aparte.
   */
  private Long precioPromocional;
  /** Vigencia de la promocion. Nulo en los dos extremos: mientras este puesta. */
  private LocalDate promocionDesde;
  private LocalDate promocionHasta;
  /** Agotar un plato debe ser un solo clic desde /admin/carta. */
  private boolean disponible;
  private int tiempoPreparacionMin;
  private Destino destino;
  private List<Modificador> modificadores;

  public ItemCarta() {}

  /**
   * Si hoy el plato se esta vendiendo en promocion.
   *
   * Vive en el dominio porque la respuesta tiene que ser la misma para la carta
   * publica, para la comandera y para el cobro. Si cada pantalla lo decidiera
   * por su cuenta, el cliente podria ver un precio y pagar otro.
   */
  public boolean enPromocion(LocalDate dia) {
    if (precioPromocional == null) {
      return false;
    }
    if (promocionDesde != null && dia.isBefore(promocionDesde)) {
      return false;
    }
    return promocionHasta == null || !dia.isAfter(promocionHasta);
  }

  /** Lo que hay que cobrar hoy por este plato. */
  public long precioVigente(LocalDate dia) {
    return enPromocion(dia) ? precioPromocional : precio;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getCategoriaId() { return categoriaId; }
  public void setCategoriaId(String categoriaId) { this.categoriaId = categoriaId; }
  public String getNombre() { return nombre; }
  public void setNombre(String nombre) { this.nombre = nombre; }
  public String getDescripcion() { return descripcion; }
  public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
  public long getPrecio() { return precio; }
  public void setPrecio(long precio) { this.precio = precio; }
  public Long getPrecioPromocional() { return precioPromocional; }
  public void setPrecioPromocional(Long precioPromocional) { this.precioPromocional = precioPromocional; }
  public LocalDate getPromocionDesde() { return promocionDesde; }
  public void setPromocionDesde(LocalDate promocionDesde) { this.promocionDesde = promocionDesde; }
  public LocalDate getPromocionHasta() { return promocionHasta; }
  public void setPromocionHasta(LocalDate promocionHasta) { this.promocionHasta = promocionHasta; }
  public boolean isDisponible() { return disponible; }
  public void setDisponible(boolean disponible) { this.disponible = disponible; }
  public int getTiempoPreparacionMin() { return tiempoPreparacionMin; }
  public void setTiempoPreparacionMin(int tiempoPreparacionMin) { this.tiempoPreparacionMin = tiempoPreparacionMin; }
  public Destino getDestino() { return destino; }
  public void setDestino(Destino destino) { this.destino = destino; }
  public List<Modificador> getModificadores() { return modificadores; }
  public void setModificadores(List<Modificador> modificadores) { this.modificadores = modificadores; }
}
