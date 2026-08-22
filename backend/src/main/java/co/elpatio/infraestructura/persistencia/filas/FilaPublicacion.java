package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.publicacion.Publicacion;
import co.elpatio.dominio.publicacion.TipoPublicacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
   * Se guarda como texto y no como numero.
   *
   * Un ordinal ata el significado al orden en que estan escritas las constantes
   * de Java: alguien reordena el enum y todas las promociones de la base pasan
   * a ser eventos, sin error y sin aviso.
   */
  @Enumerated(EnumType.STRING)
  private TipoPublicacion tipo;

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
    p.setTipo(tipo);
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
    fila.tipo = p.getTipo();
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
