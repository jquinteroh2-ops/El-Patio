package co.elpatio.dominio.reserva;

import co.elpatio.dominio.canal.Canal;
import java.time.Instant;

public class Reserva {
  private String id;
  private String nombreCliente;
  private String telefono;
  private Instant fechaHora;
  private int personas;
  private Ocasion ocasion;
  private EstadoReserva estado = EstadoReserva.SOLICITADA;
  private String notas;
  private String mesaAsignadaId;

  /** Por donde se pidio. Igual que en `Orden`: solo dice quien atendio, no cambia el flujo. */
  private Canal canal = Canal.PRESENCIAL;

  public Reserva() {}

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getNombreCliente() { return nombreCliente; }
  public void setNombreCliente(String nombreCliente) { this.nombreCliente = nombreCliente; }
  public String getTelefono() { return telefono; }
  public void setTelefono(String telefono) { this.telefono = telefono; }
  public Instant getFechaHora() { return fechaHora; }
  public void setFechaHora(Instant fechaHora) { this.fechaHora = fechaHora; }
  public int getPersonas() { return personas; }
  public void setPersonas(int personas) { this.personas = personas; }
  public Ocasion getOcasion() { return ocasion; }
  public void setOcasion(Ocasion ocasion) { this.ocasion = ocasion; }
  public EstadoReserva getEstado() { return estado; }
  public void setEstado(EstadoReserva estado) { this.estado = estado; }
  public String getNotas() { return notas; }
  public void setNotas(String notas) { this.notas = notas; }
  public String getMesaAsignadaId() { return mesaAsignadaId; }
  public void setMesaAsignadaId(String mesaAsignadaId) { this.mesaAsignadaId = mesaAsignadaId; }
  public Canal getCanal() { return canal; }
  public void setCanal(Canal canal) { this.canal = canal != null ? canal : Canal.PRESENCIAL; }
}
