package co.elpatio.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.reserva.EstadoReserva;
import co.elpatio.dominio.reserva.Ocasion;
import co.elpatio.dominio.reserva.Reserva;
import co.elpatio.dominio.salon.EstadoMesa;
import co.elpatio.dominio.salon.Mesa;
import co.elpatio.dominio.salon.Zona;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * La reserva que anota el personal.
 *
 * Lo que se comprueba es lo que la separa de la del sitio publico: que pueda
 * nacer en firme, que solo entonces se le quite un puesto al salon, y que las
 * validaciones que antes reventaban con un NullPointerException ahora salgan
 * como un mensaje que se puede leer.
 */
class ServicioReservasMostradorTest {

  private Repositorios.DeReservas reservas;
  private Repositorios.DeMesas mesas;
  private GeneradorIds ids;
  private PublicadorEventos eventos;
  private ServicioReservas servicio;

  private static final Instant CUANDO = Instant.parse("2026-01-10T20:00:00Z");

  @BeforeEach
  void preparar() {
    reservas = mock(Repositorios.DeReservas.class);
    mesas = mock(Repositorios.DeMesas.class);
    ids = mock(GeneradorIds.class);
    eventos = mock(PublicadorEventos.class);
    servicio = new ServicioReservas(reservas, mesas, ids, eventos);

    when(ids.nuevo(anyString())).thenReturn("r1");
    when(reservas.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private static Mesa mesaLibre() {
    Mesa mesa = new Mesa();
    mesa.setId("m4");
    mesa.setNumero(4);
    mesa.setZona(Zona.TERRAZA);
    mesa.setCapacidad(4);
    mesa.setEstado(EstadoMesa.LIBRE);
    return mesa;
  }

  private static Dtos.NuevaReservaMostrador datos(boolean confirmada, String mesaId) {
    return new Dtos.NuevaReservaMostrador(
        "Carolina Mendoza", "3001234567", CUANDO, 4, Ocasion.CUMPLEANOS, null,
        Canal.WHATSAPP, confirmada, mesaId);
  }

  @Test
  void la_que_ya_quedo_acordada_nace_confirmada_y_separa_la_mesa() {
    Mesa mesa = mesaLibre();
    when(mesas.porId("m4")).thenReturn(Optional.of(mesa));

    Reserva reserva = servicio.crearReservaDeMostrador(datos(true, "m4"));

    assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    assertThat(reserva.getCanal()).isEqualTo(Canal.WHATSAPP);
    assertThat(reserva.getMesaAsignadaId()).isEqualTo("m4");
    assertThat(mesa.getEstado()).isEqualTo(EstadoMesa.RESERVADA);
  }

  @Test
  void la_que_solo_se_apunta_cae_en_por_responder_y_no_toca_el_salon() {
    Reserva reserva = servicio.crearReservaDeMostrador(datos(false, "m4"));

    assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SOLICITADA);
    assertThat(reserva.getMesaAsignadaId()).isNull();
    // Apartar una mesa por algo que todavia se puede caer cuesta un puesto
    // toda la noche.
    verify(mesas, never()).guardar(any());
  }

  @Test
  void una_mesa_ocupada_no_se_le_quita_a_quien_ya_esta_sentado() {
    Mesa ocupada = mesaLibre();
    ocupada.setEstado(EstadoMesa.OCUPADA);
    when(mesas.porId("m4")).thenReturn(Optional.of(ocupada));

    Reserva reserva = servicio.crearReservaDeMostrador(datos(true, "m4"));

    assertThat(reserva.getMesaAsignadaId()).isEqualTo("m4");
    assertThat(ocupada.getEstado()).isEqualTo(EstadoMesa.OCUPADA);
    verify(mesas, never()).guardar(any());
  }

  @Test
  void sin_nombre_no_hay_reserva() {
    Dtos.NuevaReservaMostrador sinNombre =
        new Dtos.NuevaReservaMostrador(
            "  ", "3001234567", CUANDO, 2, null, null, Canal.TELEFONO, true, null);

    assertThatThrownBy(() -> servicio.crearReservaDeMostrador(sinNombre))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("nombre");
  }

  @Test
  void la_del_sitio_publico_sigue_entrando_como_solicitada_y_por_el_canal_web() {
    Reserva reserva =
        servicio.crearReserva(
            new Dtos.NuevaReserva(
                "Carolina Mendoza", "3001234567", CUANDO, 2, Ocasion.NINGUNA, null, null));

    assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SOLICITADA);
    assertThat(reserva.getCanal()).isEqualTo(Canal.WEB);
  }
}
