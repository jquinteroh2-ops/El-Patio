package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.ajustes.Ajustes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;

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

  @Column(name = "domicilios_pausados")
  private boolean domiciliosPausados;

  @Column(name = "domicilios_desde")
  private LocalTime domiciliosDesde;

  @Column(name = "domicilios_hasta")
  private LocalTime domiciliosHasta;

  public Ajustes aDominio() {
    Ajustes ajustes = new Ajustes();
    ajustes.setPorcentajeInc(porcentajeInc);
    ajustes.setSimularSinConexion(simularSinConexion);
    ajustes.setConsecutivoOrden(consecutivoOrden);
    ajustes.setFechaConsecutivo(fechaConsecutivo);
    ajustes.setDomiciliosPausados(domiciliosPausados);
    ajustes.setDomiciliosDesde(domiciliosDesde);
    ajustes.setDomiciliosHasta(domiciliosHasta);
    return ajustes;
  }

  public void volcar(Ajustes ajustes) {
    this.id = UNICA;
    this.porcentajeInc = ajustes.getPorcentajeInc();
    this.simularSinConexion = ajustes.isSimularSinConexion();
    this.consecutivoOrden = ajustes.getConsecutivoOrden();
    this.fechaConsecutivo = ajustes.getFechaConsecutivo();
    this.domiciliosPausados = ajustes.isDomiciliosPausados();
    this.domiciliosDesde = ajustes.getDomiciliosDesde();
    this.domiciliosHasta = ajustes.getDomiciliosHasta();
  }

  public int getConsecutivoOrden() { return consecutivoOrden; }
  public void setConsecutivoOrden(int valor) { this.consecutivoOrden = valor; }
  public LocalDate getFechaConsecutivo() { return fechaConsecutivo; }
  public void setFechaConsecutivo(LocalDate valor) { this.fechaConsecutivo = valor; }
}
