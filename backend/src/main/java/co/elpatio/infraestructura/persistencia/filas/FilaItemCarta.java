package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.carta.Destino;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.carta.Modificador;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Type;

/** Fila de la tabla `items_carta`. */
@Entity
@Table(name = "items_carta")
public class FilaItemCarta {

  @Id private String id;

  @Column(name = "categoria_id")
  private String categoriaId;

  private String nombre;
  private String descripcion;
  private long precio;

  /** Nulo cuando el plato no esta en promocion, que es el caso normal. */
  @Column(name = "precio_promocional")
  private Long precioPromocional;

  @Column(name = "promocion_desde")
  private LocalDate promocionDesde;

  @Column(name = "promocion_hasta")
  private LocalDate promocionHasta;
  private boolean disponible;

  @Column(name = "tiempo_preparacion_min")
  private int tiempoPreparacionMin;

  private String destino;

  /**
   * Los modificadores viajan como JSONB. Solo se leen completos junto con su
   * producto y nunca se filtra por ellos, asi que normalizarlos en tres tablas
   * mas costaria mantenimiento sin que ninguna consulta lo aproveche.
   */
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private List<Modificador> modificadores = new ArrayList<>();

  /** Nombre del archivo en el almacen de imagenes, o nulo si el plato no tiene foto. */
  private String imagen;

  /** Las demas fotos, como JSONB por lo mismo que los modificadores. */
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private List<String> galeria = new ArrayList<>();

  public ItemCarta aDominio() {
    ItemCarta item = new ItemCarta();
    item.setId(id);
    item.setCategoriaId(categoriaId);
    item.setNombre(nombre);
    item.setDescripcion(descripcion);
    item.setPrecio(precio);
    item.setPrecioPromocional(precioPromocional);
    item.setPromocionDesde(promocionDesde);
    item.setPromocionHasta(promocionHasta);
    item.setDisponible(disponible);
    item.setTiempoPreparacionMin(tiempoPreparacionMin);
    item.setDestino(Destino.de(destino));
    item.setModificadores(modificadores == null ? List.of() : List.copyOf(modificadores));
    item.setImagen(imagen);
    item.setGaleria(galeria == null ? List.of() : List.copyOf(galeria));
    return item;
  }

  public static FilaItemCarta deDominio(ItemCarta item) {
    FilaItemCarta fila = new FilaItemCarta();
    fila.id = item.getId();
    fila.categoriaId = item.getCategoriaId();
    fila.nombre = item.getNombre();
    fila.descripcion = item.getDescripcion() == null ? "" : item.getDescripcion();
    fila.precio = item.getPrecio();
    fila.precioPromocional = item.getPrecioPromocional();
    fila.promocionDesde = item.getPromocionDesde();
    fila.promocionHasta = item.getPromocionHasta();
    fila.disponible = item.isDisponible();
    fila.tiempoPreparacionMin = item.getTiempoPreparacionMin();
    fila.destino = item.getDestino().codigo();
    fila.modificadores =
        item.getModificadores() == null ? new ArrayList<>() : new ArrayList<>(item.getModificadores());
    fila.imagen = item.getImagen();
    fila.galeria =
        item.getGaleria() == null ? new ArrayList<>() : new ArrayList<>(item.getGaleria());
    return fila;
  }

  public String getId() { return id; }

  public void setDisponible(boolean disponible) { this.disponible = disponible; }
}
