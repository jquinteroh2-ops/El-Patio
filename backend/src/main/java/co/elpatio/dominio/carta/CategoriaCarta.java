package co.elpatio.dominio.carta;

public class CategoriaCarta {
  private String id;
  private String nombre;
  private int orden;

  public CategoriaCarta() {}

  public CategoriaCarta(String id, String nombre, int orden) {
    this.id = id;
    this.nombre = nombre;
    this.orden = orden;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getNombre() { return nombre; }
  public void setNombre(String nombre) { this.nombre = nombre; }
  public int getOrden() { return orden; }
  public void setOrden(int orden) { this.orden = orden; }
}
