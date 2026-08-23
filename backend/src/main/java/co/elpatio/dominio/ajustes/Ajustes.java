package co.elpatio.dominio.ajustes;

import java.time.LocalDate;
import java.time.LocalTime;

/** Los pocos parametros que un dueno distinto querria cambiar. */
public class Ajustes {
  /** Impuesto Nacional al Consumo, en porcentaje. Configurable por establecimiento. */
  private int porcentajeInc;
  /**
   * Consecutivo de comandas del dia. Se entrega bajo bloqueo de fila para que
   * dos meseros abriendo mesa a la vez no reciban el mismo numero.
   */
  private int consecutivoOrden;
  /** Fecha del consecutivo, para reiniciarlo cada dia. */
  private LocalDate fechaConsecutivo;

  /**
   * Cierra el canal de pedidos externos cuando la cocina esta saturada. Es lo
   * primero que busca un administrador un viernes a las nueve de la noche.
   */
  private boolean domiciliosPausados;

  /** Franja en que se reciben pedidos, en la zona del restaurante. */
  private LocalTime domiciliosDesde;
  private LocalTime domiciliosHasta;

  /**
   * Que porcentaje del total se cobra como anticipo en un canal automatizado
   * antes de mandar el pedido a cocina. Cero significa que ese canal no cobra
   * anticipo. Es del establecimiento, no una constante del codigo: cada
   * restaurante que use este sistema decide su propio riesgo.
   */
  private int porcentajeAnticipo;

  public Ajustes() {}

  public int getPorcentajeInc() { return porcentajeInc; }
  public void setPorcentajeInc(int porcentajeInc) { this.porcentajeInc = porcentajeInc; }
  public int getConsecutivoOrden() { return consecutivoOrden; }
  public void setConsecutivoOrden(int consecutivoOrden) { this.consecutivoOrden = consecutivoOrden; }
  public LocalDate getFechaConsecutivo() { return fechaConsecutivo; }
  public void setFechaConsecutivo(LocalDate fechaConsecutivo) { this.fechaConsecutivo = fechaConsecutivo; }
  public boolean isDomiciliosPausados() { return domiciliosPausados; }
  public void setDomiciliosPausados(boolean valor) { this.domiciliosPausados = valor; }
  public LocalTime getDomiciliosDesde() { return domiciliosDesde; }
  public void setDomiciliosDesde(LocalTime valor) { this.domiciliosDesde = valor; }
  public LocalTime getDomiciliosHasta() { return domiciliosHasta; }
  public void setDomiciliosHasta(LocalTime valor) { this.domiciliosHasta = valor; }
  public int getPorcentajeAnticipo() { return porcentajeAnticipo; }
  public void setPorcentajeAnticipo(int valor) { this.porcentajeAnticipo = valor; }

  /**
   * Si en este momento se pueden recibir pedidos.
   *
   * La franja se compara en la hora del restaurante, nunca en la del navegador
   * del cliente: alguien pidiendo desde otra ciudad no puede abrir la cocina.
   */
  public boolean recibiendoPedidos(LocalTime ahora) {
    if (domiciliosPausados) return false;
    if (domiciliosDesde == null || domiciliosHasta == null) return true;
    return !ahora.isBefore(domiciliosDesde) && !ahora.isAfter(domiciliosHasta);
  }
}
