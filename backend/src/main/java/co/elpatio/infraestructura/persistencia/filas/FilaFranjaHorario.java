package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.sitio.FranjaHorario;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla `franja_horario`: una linea del horario de atencion. */
@Entity
@Table(name = "franja_horario")
public class FilaFranjaHorario {

  @Id private String id;

  private String dias;
  private String horas;
  private int orden;

  public FilaFranjaHorario() {}

  public FilaFranjaHorario(String id, FranjaHorario franja, int orden) {
    this.id = id;
    this.dias = franja.dias();
    this.horas = franja.horas();
    this.orden = orden;
  }

  public FranjaHorario aDominio() {
    return new FranjaHorario(dias, horas);
  }
}
