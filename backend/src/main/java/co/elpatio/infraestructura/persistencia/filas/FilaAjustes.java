package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.ajustes.Ajustes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Fila unica de la tabla `ajustes`. El id siempre es 1. */
@Entity
@Table(name = "ajustes")
public class FilaAjustes {

  /** Identificador de la unica fila. Lo fija el esquema con un CHECK. */
  public static final int UNICA = 1;

  @Id private Integer id;

  @Column(name = "porcentaje_inc")
  private int porcentajeInc;

  @Column(name = "simular_sin_conexion")
  private boolean simularSinConexion;

  @Column(name = "consecutivo_orden")
  private int consecutivoOrden;

  @Column(name = "fecha_consecutivo")
  private LocalDate fechaConsecutivo;

  public Ajustes aDominio() {
    Ajustes ajustes = new Ajustes();
    ajustes.setPorcentajeInc(porcentajeInc);
    ajustes.setSimularSinConexion(simularSinConexion);
    ajustes.setConsecutivoOrden(consecutivoOrden);
    ajustes.setFechaConsecutivo(fechaConsecutivo);
    return ajustes;
  }

  public void volcar(Ajustes ajustes) {
    this.id = UNICA;
    this.porcentajeInc = ajustes.getPorcentajeInc();
    this.simularSinConexion = ajustes.isSimularSinConexion();
    this.consecutivoOrden = ajustes.getConsecutivoOrden();
    this.fechaConsecutivo = ajustes.getFechaConsecutivo();
  }

  public int getConsecutivoOrden() { return consecutivoOrden; }
  public void setConsecutivoOrden(int valor) { this.consecutivoOrden = valor; }
  public LocalDate getFechaConsecutivo() { return fechaConsecutivo; }
  public void setFechaConsecutivo(LocalDate valor) { this.fechaConsecutivo = valor; }
}
