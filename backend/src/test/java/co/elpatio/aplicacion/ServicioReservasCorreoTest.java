package co.elpatio.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.puertos.CorreoDeReservas;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.NotificadorPorCorreo;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.reserva.Ocasion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El aviso al buzón de reservas cuando alguien reserva por el sitio. */
class ServicioReservasCorreoTest {

  /** Sábado 17 de octubre de 2026, 8:00 p. m. en Turbaco. */
  private static final Instant CUANDO = Instant.parse("2026-10-18T01:00:00Z");

  private final List<String> asuntos = new ArrayList<>();
  private final List<String> htmls = new ArrayList<>();
  private final List<String> destinos = new ArrayList<>();
  private ServicioReservas servicio;

  @BeforeEach
  void preparar() {
    Repositorios.DeReservas reservas = mock(Repositorios.DeReservas.class);
    GeneradorIds ids = mock(GeneradorIds.class);
    when(ids.nuevo(anyString())).thenReturn("r1");
    when(reservas.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

    NotificadorPorCorreo correo =
        new NotificadorPorCorreo() {
          @Override
          public void enviar(String destinatario, String asunto, String cuerpo) {}

          @Override
          public void enviarConAdjunto(
              String destinatario, String asunto, String cuerpo, String cuerpoHtml, String n, byte[] c) {
            destinos.add(destinatario);
            asuntos.add(asunto);
            htmls.add(cuerpoHtml);
          }

          @Override
          public boolean estaActivo() {
            return true;
          }
        };
    servicio =
        new ServicioReservas(
            reservas,
            mock(Repositorios.DeMesas.class),
            ids,
            mock(PublicadorEventos.class),
            new CorreoDeReservas(correo),
            "reservas@elpatio.co",
            "https://www.restauranteelpatio.com");
  }

  @Test
  void la_reserva_del_sitio_le_llega_al_buzon_de_reservas_con_todo_para_confirmarla() {
    servicio.crearReserva(
        new Dtos.NuevaReserva("Carolina Mendoza", "300 123 4567", CUANDO, 4, Ocasion.CUMPLEANOS, "Mesa en la terraza", null));

    assertThat(destinos).containsExactly("reservas@elpatio.co");
    assertThat(asuntos.get(0)).contains("Sábado 17 de octubre de 2026").contains("4 personas").contains("Carolina Mendoza");
    assertThat(htmls.get(0))
        .contains("Nueva solicitud de reserva")
        .contains("Cumpleaños")
        .contains("Mesa en la terraza")
        .contains("https://wa.me/573001234567?text=")
        .contains("/admin/reservas");
  }

  @Test
  void la_que_anota_el_personal_no_manda_correo() {
    servicio.crearReservaDeMostrador(
        new Dtos.NuevaReservaMostrador(
            "Carolina Mendoza", "3001234567", CUANDO, 4, Ocasion.NINGUNA, null, Canal.WHATSAPP, false, null));

    assertThat(destinos).isEmpty();
  }

  /** El formulario es público: lo que escriba el cliente no puede volverse HTML. */
  @Test
  void lo_que_escribe_el_cliente_no_se_vuelve_html() {
    servicio.crearReserva(
        new Dtos.NuevaReserva("<b>Ana</b>", "3001234567", CUANDO, 2, Ocasion.NINGUNA, "<script>x</script>", null));

    assertThat(htmls.get(0)).doesNotContain("<script>").contains("&lt;script&gt;").contains("&lt;b&gt;Ana");
  }
}
