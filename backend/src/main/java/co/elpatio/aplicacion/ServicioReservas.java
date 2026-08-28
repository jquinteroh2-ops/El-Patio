package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
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
    Reserva reserva =
        armar(
            datos.nombreCliente(),
            datos.telefono(),
            datos.fechaHora(),
            datos.personas(),
            datos.ocasion(),
            datos.notas(),
            datos.canal() == null ? Canal.WEB : datos.canal());
    reserva.setEstado(EstadoReserva.SOLICITADA);

    Reserva guardada = reservas.guardar(reserva);
    eventos.publicar(List.of("reservas"));
    return guardada;
  }

  /**
   * La anota alguien del restaurante: por WhatsApp, por telefono o en la puerta.
   *
   * Nace confirmada cuando quien la toma ya le dijo que si al cliente, que es
   * el caso normal de una llamada, y con eso puede separar la mesa en el mismo
   * gesto. Queda solicitada cuando solo se esta apuntando lo que pidieron y la
   * disponibilidad se mira despues: entonces cae en la bandeja de «por
   * responder» como la del sitio publico y sigue el mismo camino.
   */
  @Transactional
  public Reserva crearReservaDeMostrador(Dtos.NuevaReservaMostrador datos) {
    Reserva reserva =
        armar(
            datos.nombreCliente(),
            datos.telefono(),
            datos.fechaHora(),
            datos.personas(),
            datos.ocasion(),
            datos.notas(),
            datos.canal() == null ? Canal.PRESENCIAL : datos.canal());
    reserva.setEstado(
        datos.confirmada() ? EstadoReserva.CONFIRMADA : EstadoReserva.SOLICITADA);

    // La mesa solo se separa si la reserva queda en firme: apartarla por algo
    // que todavia se puede caer le quita un puesto al salon toda la noche.
    String mesaId = datos.mesaAsignadaId();
    if (datos.confirmada() && mesaId != null && !mesaId.isBlank()) {
      reserva.setMesaAsignadaId(mesaId);
      separar(mesaId);
    }

    Reserva guardada = reservas.guardar(reserva);
    eventos.publicar(List.of("reservas", "mesas"));
    return guardada;
  }

  /**
   * Lo comun a las dos puertas de entrada.
   *
   * Las validaciones estan aqui y no en cada llamador porque son las mismas:
   * una reserva sin nombre o sin telefono no se puede confirmar, y sin fecha no
   * se puede sentar a nadie.
   */
  private Reserva armar(
      String nombreCliente,
      String telefono,
      java.time.Instant fechaHora,
      int personas,
      co.elpatio.dominio.reserva.Ocasion ocasion,
      String notas,
      Canal canal) {

    if (nombreCliente == null || nombreCliente.isBlank()) {
      throw new ReglaDeNegocioError("Necesitamos el nombre de quien reserva");
    }
    if (telefono == null || telefono.isBlank()) {
      throw new ReglaDeNegocioError("Necesitamos un teléfono para poder confirmar");
    }
    if (fechaHora == null) {
      throw new ReglaDeNegocioError("Falta el día y la hora de la reserva");
    }
    if (personas < 1) {
      throw new ReglaDeNegocioError("Una reserva es para una persona o más");
    }

    Reserva reserva = new Reserva();
    reserva.setId(ids.nuevo("r"));
    reserva.setNombreCliente(nombreCliente.trim());
    reserva.setTelefono(telefono.trim());
    reserva.setFechaHora(fechaHora);
    reserva.setPersonas(personas);
    reserva.setOcasion(ocasion);
    reserva.setNotas(notas);
    reserva.setCanal(canal);
    return reserva;
  }

  /** Marca la mesa como reservada, y solo si estaba libre. */
  private void separar(String mesaId) {
    Mesa mesa = mesas.porId(mesaId).orElse(null);
    if (mesa != null && mesa.getEstado() == EstadoMesa.LIBRE) {
      mesa.setEstado(EstadoMesa.RESERVADA);
      mesas.guardar(mesa);
    }
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
