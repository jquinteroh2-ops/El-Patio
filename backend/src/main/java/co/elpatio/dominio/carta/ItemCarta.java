package co.elpatio.dominio.carta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
  /** Nombre del archivo en el almacen de imagenes, o nulo si el plato no tiene foto. */
  private String imagen;
  /**
   * Las demas fotos del plato, en el orden en que se muestran.
   *
   * NO incluye la portada. Separarlas evita que algo tenga que decidir cual de
   * un arreglo es la principal, y deja que quitar una foto de la ficha no
   * cambie cual identifica al plato en el listado.
   */
  private List<String> galeria = List.of();

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
  public String getImagen() { return imagen; }
  public void setImagen(String imagen) { this.imagen = imagen; }
  public List<String> getGaleria() { return galeria; }
  public void setGaleria(List<String> galeria) {
    // Copia, no la lista que le pasaron: quedandose con la de fuera, quien la
    // modifique despues le estaria cambiando las fotos al plato sin saberlo.
    // Se copia con `ArrayList` y no con `List.copyOf` porque esa ultima
    // revienta si dentro viene un nulo, y un nulo es justo lo que puede traer
    // el JSON del panel; filtrarlo es cosa de `fotos()`, no motivo para tumbar
    // la peticion con un 500.
    this.galeria =
        galeria == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(galeria));
  }

  /**
   * Todos los archivos a los que apunta el plato: la portada primero.
   *
   * Es lo que hay que recorrer para saber que imagenes le pertenecen, y por eso
   * vive aqui: si viviera en el servicio, quien anadiera un tercer sitio para
   * fotos tendria que acordarse de este recorrido.
   */
  public List<String> fotos() {
    List<String> todas = new ArrayList<>();
    if (imagen != null && !imagen.isBlank()) {
      todas.add(imagen);
    }
    for (String foto : galeria) {
      if (foto != null && !foto.isBlank()) {
        todas.add(foto);
      }
    }
    return List.copyOf(todas);
  }
}
