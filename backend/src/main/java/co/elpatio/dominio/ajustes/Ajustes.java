package co.elpatio.dominio.ajustes;

import java.time.LocalDate;

/** Los pocos parametros que un dueno distinto querria cambiar. */
public class Ajustes {
  /** Impuesto Nacional al Consumo, en porcentaje. Configurable por establecimiento. */
  private int porcentajeInc;
  /** Interruptor de demostracion: simula perdida de WiFi en la comandera. */
  private boolean simularSinConexion;
  /**
   * Consecutivo de comandas del dia. Se entrega bajo bloqueo de fila para que
   * dos meseros abriendo mesa a la vez no reciban el mismo numero.
   */
  private int consecutivoOrden;
  /** Fecha del consecutivo, para reiniciarlo cada dia. */
  private LocalDate fechaConsecutivo;

  public Ajustes() {}

  public int getPorcentajeInc() { return porcentajeInc; }
  public void setPorcentajeInc(int porcentajeInc) { this.porcentajeInc = porcentajeInc; }
  public boolean isSimularSinConexion() { return simularSinConexion; }
  public void setSimularSinConexion(boolean simularSinConexion) { this.simularSinConexion = simularSinConexion; }
  public int getConsecutivoOrden() { return consecutivoOrden; }
  public void setConsecutivoOrden(int consecutivoOrden) { this.consecutivoOrden = consecutivoOrden; }
  public LocalDate getFechaConsecutivo() { return fechaConsecutivo; }
  public void setFechaConsecutivo(LocalDate fechaConsecutivo) { this.fechaConsecutivo = fechaConsecutivo; }
}
