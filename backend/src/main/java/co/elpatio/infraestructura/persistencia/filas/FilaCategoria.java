package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.carta.CategoriaCarta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla `categorias_carta`. */
@Entity
@Table(name = "categorias_carta")
public class FilaCategoria {

  @Id private String id;

  private String nombre;

  /** `orden` es palabra reservada en varios motores; se cita para no depender de eso. */
  @Column(name = "\"orden\"")
  private int orden;

  public CategoriaCarta aDominio() {
    return new CategoriaCarta(id, nombre, orden);
  }

  public static FilaCategoria deDominio(CategoriaCarta categoria) {
    FilaCategoria fila = new FilaCategoria();
    fila.id = categoria.getId();
    fila.nombre = categoria.getNombre();
    fila.orden = categoria.getOrden();
    return fila;
  }

  public String getId() { return id; }
}
