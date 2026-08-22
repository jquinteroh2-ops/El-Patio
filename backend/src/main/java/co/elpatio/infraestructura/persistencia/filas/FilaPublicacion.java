package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.publicacion.Publicacion;
import co.elpatio.dominio.publicacion.TipoPublicacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** Fila de la tabla `publicaciones`. */
@Entity
@Table(name = "publicaciones")
public class FilaPublicacion {

  @Id private String id;

  /**
   * El tipo, en minuscula y como texto.
   *
   * Se convierte a mano en vez de anotarlo con `@Enumerated`, igual que hace
   * `FilaItemCarta` con el destino. `@Enumerated(STRING)` escribiria
   * «PROMOCION» en mayuscula y la restriccion de la tabla, que espera
   * minusculas, rechazaria la fila; `@Enumerated(ORDINAL)` seria peor todavia,
   * porque ata el significado al orden en que estan escritas las constantes.
   */
  private String tipo;

  private String titulo;

  private String cuerpo;

  private String imagen;

  private LocalDate desde;

  private LocalDate hasta;

  private boolean publicada;

  /** `orden` es palabra reservada en varios motores; se cita para no depender de eso. */
  @Column(name = "\"orden\"")
  private int orden;

  @Column(name = "creada_en")
  private Instant creadaEn;

  public Publicacion aDominio() {
    Publicacion p = new Publicacion();
    p.setId(id);
    p.setTipo(TipoPublicacion.de(tipo));
    p.setTitulo(titulo);
    p.setCuerpo(cuerpo);
    p.setImagen(imagen);
    p.setDesde(desde);
    p.setHasta(hasta);
    p.setPublicada(publicada);
    p.setOrden(orden);
    p.setCreadaEn(creadaEn);
    return p;
  }

  public static FilaPublicacion deDominio(Publicacion p) {
    FilaPublicacion fila = new FilaPublicacion();
    fila.id = p.getId();
    fila.tipo = p.getTipo().codigo();
    fila.titulo = p.getTitulo();
    fila.cuerpo = p.getCuerpo() == null ? "" : p.getCuerpo();
    fila.imagen = p.getImagen();
    fila.desde = p.getDesde();
    fila.hasta = p.getHasta();
    fila.publicada = p.isPublicada();
    fila.orden = p.getOrden();
    fila.creadaEn = p.getCreadaEn();
    return fila;
  }

  public String getId() { return id; }
}
