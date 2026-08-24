package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.institucional.ContenidoInstitucional;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Fila de la tabla `contenido_institucional`. */
@Entity
@Table(name = "contenido_institucional")
public class FilaContenidoInstitucional {

  @Id private String clave;

  private String titulo;
  private String cuerpo;
  private int orden;
  private boolean visible;

  @Column(name = "actualizado_en")
  private Instant actualizadoEn;

  public ContenidoInstitucional aDominio() {
    ContenidoInstitucional c = new ContenidoInstitucional();
    c.setClave(clave);
    c.setTitulo(titulo);
    c.setCuerpo(cuerpo);
    c.setOrden(orden);
    c.setVisible(visible);
    c.setActualizadoEn(actualizadoEn);
    return c;
  }

  public void volcar(ContenidoInstitucional c) {
    this.clave = c.getClave();
    this.titulo = c.getTitulo();
    this.cuerpo = c.getCuerpo();
    this.orden = c.getOrden();
    this.visible = c.isVisible();
    this.actualizadoEn = c.getActualizadoEn();
  }
}
