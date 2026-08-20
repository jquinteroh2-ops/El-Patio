package co.elpatio.dominio.carta;

import java.util.List;

/** Un producto de la carta. */
public class ItemCarta {
  private String id;
  private String categoriaId;
  private String nombre;
  private String descripcion;
  private long precio;
  /** Agotar un plato debe ser un solo clic desde /admin/carta. */
  private boolean disponible;
  private int tiempoPreparacionMin;
  private Destino destino;
  private List<Modificador> modificadores;

  public ItemCarta() {}

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
  public boolean isDisponible() { return disponible; }
  public void setDisponible(boolean disponible) { this.disponible = disponible; }
  public int getTiempoPreparacionMin() { return tiempoPreparacionMin; }
  public void setTiempoPreparacionMin(int tiempoPreparacionMin) { this.tiempoPreparacionMin = tiempoPreparacionMin; }
  public Destino getDestino() { return destino; }
  public void setDestino(Destino destino) { this.destino = destino; }
  public List<Modificador> getModificadores() { return modificadores; }
  public void setModificadores(List<Modificador> modificadores) { this.modificadores = modificadores; }
}
