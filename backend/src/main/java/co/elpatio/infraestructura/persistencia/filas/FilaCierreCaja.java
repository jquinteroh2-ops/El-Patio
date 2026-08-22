package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.caja.CierreCaja;
import co.elpatio.dominio.caja.Turno;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** Fila de la tabla `cierres_caja`. */
@Entity
@Table(name = "cierres_caja")
public class FilaCierreCaja {

  @Id private String id;

  private LocalDate fecha;
  private String turno;

  @Column(name = "venta_total")
  private long ventaTotal;

  @Column(name = "total_efectivo")
  private long totalEfectivo;

  @Column(name = "total_tarjeta")
  private long totalTarjeta;

  @Column(name = "total_transferencia")
  private long totalTransferencia;

  @Column(name = "propinas_totales")
  private long propinasTotales;

  @Column(name = "inc_total")
  private long incTotal;

  @Column(name = "ordenes_atendidas")
  private int ordenesAtendidas;

  @Column(name = "ticket_promedio")
  private long ticketPromedio;

  @Column(name = "total_salon")
  private long totalSalon;

  @Column(name = "total_domicilio")
  private long totalDomicilio;

  @Column(name = "total_llevar")
  private long totalLlevar;

  @Column(name = "total_envios")
  private long totalEnvios;

  @Column(name = "base_gravable")
  private long baseGravable;

  @Column(name = "base_no_gravada")
  private long baseNoGravada;

  @Column(name = "total_cargos")
  private long totalCargos;

  @Column(name = "porcentaje_inc")
  private int porcentajeInc;

  @Column(name = "descuentos")
  private long descuentos;

  @Column(name = "comensales")
  private int comensales;

  @Column(name = "lineas_anuladas")
  private int lineasAnuladas;

  @Column(name = "valor_anulado")
  private long valorAnulado;

  @Column(name = "cerrado_por")
  private String cerradoPor;

  @Column(name = "fecha_hora")
  private Instant fechaHora;

  public CierreCaja aDominio() {
    CierreCaja cierre = new CierreCaja();
    cierre.setId(id);
    cierre.setFecha(fecha);
    cierre.setTurno(Turno.de(turno));
    cierre.setVentaTotal(ventaTotal);
    cierre.setTotalEfectivo(totalEfectivo);
    cierre.setTotalTarjeta(totalTarjeta);
    cierre.setTotalTransferencia(totalTransferencia);
    cierre.setPropinasTotales(propinasTotales);
    cierre.setIncTotal(incTotal);
    cierre.setOrdenesAtendidas(ordenesAtendidas);
    cierre.setTicketPromedio(ticketPromedio);
    cierre.setTotalSalon(totalSalon);
    cierre.setTotalDomicilio(totalDomicilio);
    cierre.setTotalLlevar(totalLlevar);
    cierre.setTotalEnvios(totalEnvios);
    cierre.setBaseGravable(baseGravable);
    cierre.setBaseNoGravada(baseNoGravada);
    cierre.setTotalCargos(totalCargos);
    cierre.setPorcentajeInc(porcentajeInc);
    cierre.setDescuentos(descuentos);
    cierre.setComensales(comensales);
    cierre.setLineasAnuladas(lineasAnuladas);
    cierre.setValorAnulado(valorAnulado);
    cierre.setCerradoPor(cerradoPor);
    cierre.setFechaHora(fechaHora);
    return cierre;
  }

  public static FilaCierreCaja deDominio(CierreCaja cierre) {
    FilaCierreCaja fila = new FilaCierreCaja();
    fila.id = cierre.getId();
    fila.fecha = cierre.getFecha();
    fila.turno = cierre.getTurno().codigo();
    fila.ventaTotal = cierre.getVentaTotal();
    fila.totalEfectivo = cierre.getTotalEfectivo();
    fila.totalTarjeta = cierre.getTotalTarjeta();
    fila.totalTransferencia = cierre.getTotalTransferencia();
    fila.propinasTotales = cierre.getPropinasTotales();
    fila.incTotal = cierre.getIncTotal();
    fila.ordenesAtendidas = cierre.getOrdenesAtendidas();
    fila.ticketPromedio = cierre.getTicketPromedio();
    fila.totalSalon = cierre.getTotalSalon();
    fila.totalDomicilio = cierre.getTotalDomicilio();
    fila.totalLlevar = cierre.getTotalLlevar();
    fila.totalEnvios = cierre.getTotalEnvios();
    fila.baseGravable = cierre.getBaseGravable();
    fila.baseNoGravada = cierre.getBaseNoGravada();
    fila.totalCargos = cierre.getTotalCargos();
    fila.porcentajeInc = cierre.getPorcentajeInc();
    fila.descuentos = cierre.getDescuentos();
    fila.comensales = cierre.getComensales();
    fila.lineasAnuladas = cierre.getLineasAnuladas();
    fila.valorAnulado = cierre.getValorAnulado();
    fila.cerradoPor = cierre.getCerradoPor();
    fila.fechaHora = cierre.getFechaHora();
    return fila;
  }
}
