package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.carta.Destino;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.carta.Modificador;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

  public ItemCarta aDominio() {
    ItemCarta item = new ItemCarta();
    item.setId(id);
    item.setCategoriaId(categoriaId);
    item.setNombre(nombre);
    item.setDescripcion(descripcion);
    item.setPrecio(precio);
    item.setDisponible(disponible);
    item.setTiempoPreparacionMin(tiempoPreparacionMin);
    item.setDestino(Destino.de(destino));
    item.setModificadores(modificadores == null ? List.of() : List.copyOf(modificadores));
    return item;
  }

  public static FilaItemCarta deDominio(ItemCarta item) {
    FilaItemCarta fila = new FilaItemCarta();
    fila.id = item.getId();
    fila.categoriaId = item.getCategoriaId();
    fila.nombre = item.getNombre();
    fila.descripcion = item.getDescripcion() == null ? "" : item.getDescripcion();
    fila.precio = item.getPrecio();
    fila.disponible = item.isDisponible();
    fila.tiempoPreparacionMin = item.getTiempoPreparacionMin();
    fila.destino = item.getDestino().codigo();
    fila.modificadores =
        item.getModificadores() == null ? new ArrayList<>() : new ArrayList<>(item.getModificadores());
    return fila;
  }

  public String getId() { return id; }

  public void setDisponible(boolean disponible) { this.disponible = disponible; }
}
