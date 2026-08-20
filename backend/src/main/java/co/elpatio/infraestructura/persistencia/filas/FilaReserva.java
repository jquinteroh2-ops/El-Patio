package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.reserva.EstadoReserva;
import co.elpatio.dominio.reserva.Ocasion;
import co.elpatio.dominio.reserva.Reserva;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Fila de la tabla `reservas`. */
@Entity
@Table(name = "reservas")
public class FilaReserva {

  @Id private String id;

  @Column(name = "nombre_cliente")
  private String nombreCliente;

  private String telefono;

  @Column(name = "fecha_hora")
  private Instant fechaHora;

  private int personas;
  private String ocasion;
  private String estado;
  private String notas;

  @Column(name = "mesa_asignada_id")
  private String mesaAsignadaId;

  public Reserva aDominio() {
    Reserva reserva = new Reserva();
    reserva.setId(id);
    reserva.setNombreCliente(nombreCliente);
    reserva.setTelefono(telefono);
    reserva.setFechaHora(fechaHora);
    reserva.setPersonas(personas);
    reserva.setOcasion(ocasion == null ? null : Ocasion.de(ocasion));
    reserva.setEstado(EstadoReserva.de(estado));
    reserva.setNotas(notas);
    reserva.setMesaAsignadaId(mesaAsignadaId);
    return reserva;
  }

  public static FilaReserva deDominio(Reserva reserva) {
    FilaReserva fila = new FilaReserva();
    fila.id = reserva.getId();
    fila.nombreCliente = reserva.getNombreCliente();
    fila.telefono = reserva.getTelefono();
    fila.fechaHora = reserva.getFechaHora();
    fila.personas = reserva.getPersonas();
    fila.ocasion = reserva.getOcasion() == null ? null : reserva.getOcasion().codigo();
    fila.estado = reserva.getEstado().codigo();
    fila.notas = reserva.getNotas();
    fila.mesaAsignadaId = reserva.getMesaAsignadaId();
    return fila;
  }
}
