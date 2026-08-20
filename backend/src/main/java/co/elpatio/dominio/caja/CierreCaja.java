package co.elpatio.dominio.caja;

import java.time.Instant;
import java.time.LocalDate;

/** El corte de un turno. Es el documento con el que se entrega la caja. */
public class CierreCaja {
  private String id;
  private LocalDate fecha;
  private Turno turno;
  private long ventaTotal;
  private long totalEfectivo;
  private long totalTarjeta;
  private long totalTransferencia;
  private long propinasTotales;
  private long incTotal;
  private int ordenesAtendidas;
  private long ticketPromedio;
  /** Venta por canal. La suma de los tres es `ventaTotal`. */
  private long totalSalon;
  private long totalDomicilio;
  private long totalLlevar;
  /** Lo cobrado por envios, ya incluido dentro de `totalDomicilio`. */
  private long totalEnvios;
  private String cerradoPor;
  private Instant fechaHora;

  public CierreCaja() {}

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public LocalDate getFecha() { return fecha; }
  public void setFecha(LocalDate fecha) { this.fecha = fecha; }
  public Turno getTurno() { return turno; }
  public void setTurno(Turno turno) { this.turno = turno; }
  public long getVentaTotal() { return ventaTotal; }
  public void setVentaTotal(long ventaTotal) { this.ventaTotal = ventaTotal; }
  public long getTotalEfectivo() { return totalEfectivo; }
  public void setTotalEfectivo(long totalEfectivo) { this.totalEfectivo = totalEfectivo; }
  public long getTotalTarjeta() { return totalTarjeta; }
  public void setTotalTarjeta(long totalTarjeta) { this.totalTarjeta = totalTarjeta; }
  public long getTotalTransferencia() { return totalTransferencia; }
  public void setTotalTransferencia(long totalTransferencia) { this.totalTransferencia = totalTransferencia; }
  public long getPropinasTotales() { return propinasTotales; }
  public void setPropinasTotales(long propinasTotales) { this.propinasTotales = propinasTotales; }
  public long getIncTotal() { return incTotal; }
  public void setIncTotal(long incTotal) { this.incTotal = incTotal; }
  public int getOrdenesAtendidas() { return ordenesAtendidas; }
  public void setOrdenesAtendidas(int ordenesAtendidas) { this.ordenesAtendidas = ordenesAtendidas; }
  public long getTicketPromedio() { return ticketPromedio; }
  public void setTicketPromedio(long ticketPromedio) { this.ticketPromedio = ticketPromedio; }
  public long getTotalSalon() { return totalSalon; }
  public void setTotalSalon(long totalSalon) { this.totalSalon = totalSalon; }
  public long getTotalDomicilio() { return totalDomicilio; }
  public void setTotalDomicilio(long totalDomicilio) { this.totalDomicilio = totalDomicilio; }
  public long getTotalLlevar() { return totalLlevar; }
  public void setTotalLlevar(long totalLlevar) { this.totalLlevar = totalLlevar; }
  public long getTotalEnvios() { return totalEnvios; }
  public void setTotalEnvios(long totalEnvios) { this.totalEnvios = totalEnvios; }
  public String getCerradoPor() { return cerradoPor; }
  public void setCerradoPor(String cerradoPor) { this.cerradoPor = cerradoPor; }
  public Instant getFechaHora() { return fechaHora; }
  public void setFechaHora(Instant fechaHora) { this.fechaHora = fechaHora; }
}
