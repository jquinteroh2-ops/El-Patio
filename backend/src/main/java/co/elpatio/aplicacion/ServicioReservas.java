package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.reserva.EstadoReserva;
import co.elpatio.dominio.reserva.Reserva;
import co.elpatio.dominio.salon.EstadoMesa;
import co.elpatio.dominio.salon.Mesa;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reservas del salon y la mesa que se les separa. */
@Service
public class ServicioReservas {

  private final Repositorios.DeReservas reservas;
  private final Repositorios.DeMesas mesas;
  private final GeneradorIds ids;
  private final PublicadorEventos eventos;

  public ServicioReservas(
      Repositorios.DeReservas reservas,
      Repositorios.DeMesas mesas,
      GeneradorIds ids,
      PublicadorEventos eventos) {
    this.reservas = reservas;
    this.mesas = mesas;
    this.ids = ids;
    this.eventos = eventos;
  }

  @Transactional(readOnly = true)
  public List<Reserva> listarReservas() {
    return reservas.listar();
  }

  /** La crea el cliente desde el sitio publico: entra siempre como solicitada. */
  @Transactional
  public Reserva crearReserva(Dtos.NuevaReserva datos) {
    Reserva reserva = new Reserva();
    reserva.setId(ids.nuevo("r"));
    reserva.setNombreCliente(datos.nombreCliente().trim());
    reserva.setTelefono(datos.telefono().trim());
    reserva.setFechaHora(datos.fechaHora());
    reserva.setPersonas(datos.personas());
    reserva.setOcasion(datos.ocasion());
    reserva.setNotas(datos.notas());
    reserva.setEstado(EstadoReserva.SOLICITADA);
    reserva.setCanal(datos.canal() == null ? Canal.WEB : datos.canal());

    Reserva guardada = reservas.guardar(reserva);
    eventos.publicar(List.of("reservas"));
    return guardada;
  }

  /**
   * Cambia el estado y ajusta la mesa separada.
   *
   * Confirmar reserva una mesa libre; cancelar, cumplir o marcar inasistencia
   * la sueltan. Solo se toca la mesa si sigue en el estado que esta reserva le
   * puso: si mientras tanto alguien la ocupo, mandan los comensales que ya
   * estan sentados.
   */
  @Transactional
  public Reserva cambiarEstadoReserva(String reservaId, EstadoReserva estado, String mesaAsignadaId) {
    Reserva reserva =
        reservas.porId(reservaId).orElseThrow(() -> new NoEncontradoError("La reserva no existe"));

    reserva.setEstado(estado);
    if (mesaAsignadaId != null) {
      reserva.setMesaAsignadaId(mesaAsignadaId.isBlank() ? null : mesaAsignadaId);
    }

    if (reserva.getMesaAsignadaId() != null) {
      Mesa mesa = mesas.porId(reserva.getMesaAsignadaId()).orElse(null);
      if (mesa != null) {
        if (estado == EstadoReserva.CONFIRMADA && mesa.getEstado() == EstadoMesa.LIBRE) {
          mesa.setEstado(EstadoMesa.RESERVADA);
          mesas.guardar(mesa);
        } else if (estado.liberaMesa() && mesa.getEstado() == EstadoMesa.RESERVADA) {
          mesa.setEstado(EstadoMesa.LIBRE);
          mesas.guardar(mesa);
        }
      }
    }

    Reserva guardada = reservas.guardar(reserva);
    eventos.publicar(List.of("reservas", "mesas"));
    return guardada;
  }

  @Transactional
  public void reprogramarReserva(String reservaId, java.time.Instant fechaHora) {
    Reserva reserva =
        reservas.porId(reservaId).orElseThrow(() -> new NoEncontradoError("La reserva no existe"));
    reserva.setFechaHora(fechaHora);
    reservas.guardar(reserva);
    eventos.publicar(List.of("reservas"));
  }
}
