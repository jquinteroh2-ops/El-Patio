package co.elpatio.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.ajustes.Ajustes;
import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.carta.Destino;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.pedido.EstadoPedido;
import co.elpatio.dominio.pedido.TipoPedido;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El pedido que anota una persona del restaurante, con los puertos simulados.
 *
 * Lo que se comprueba aqui es la diferencia entre esa puerta y la del sitio
 * publico, que es donde estaba el riesgo: si el pedido tomado por WhatsApp
 * naciera en `borrador` -como nacia el del bot que se retiro- se quedaria
 * invisible para siempre, porque ya no hay nadie esperando un anticipo detras.
 */
class ServicioPedidosMostradorTest {

  private Repositorios.DeOrdenes ordenes;
  private Repositorios.DeCarta carta;
  private Repositorios.DeZonasDomicilio zonas;
  private Repositorios.DeAjustes ajustesRepo;
  private Repositorios.DeUsuarios usuarios;
  private GeneradorIds ids;
  private Reloj reloj;
  private PublicadorEventos eventos;
  private ServicioPedidos servicio;

  /** Fuera de la franja de domicilios, que es lo que frena al sitio publico. */
  private static final Instant AHORA = Instant.parse("2026-01-10T08:00:00Z");

  private final AtomicReference<Orden> guardada = new AtomicReference<>();

  @BeforeEach
  void preparar() {
    ordenes = mock(Repositorios.DeOrdenes.class);
    carta = mock(Repositorios.DeCarta.class);
    zonas = mock(Repositorios.DeZonasDomicilio.class);
    ajustesRepo = mock(Repositorios.DeAjustes.class);
    usuarios = mock(Repositorios.DeUsuarios.class);
    ids = mock(GeneradorIds.class);
    reloj = mock(Reloj.class);
    eventos = mock(PublicadorEventos.class);

    servicio =
        new ServicioPedidos(
            ordenes, carta, zonas, ajustesRepo, usuarios, ids, reloj, eventos,
            10.3390034, -75.4225372);

    Ajustes ajustes = new Ajustes();
    ajustes.setPorcentajeInc(8);
    ajustes.setDomiciliosDesde(LocalTime.of(11, 30));
    ajustes.setDomiciliosHasta(LocalTime.of(21, 30));

    when(ajustesRepo.leer()).thenReturn(ajustes);
    when(ajustesRepo.siguienteConsecutivo()).thenReturn(7);
    when(reloj.ahora()).thenReturn(AHORA);
    when(reloj.hoy()).thenReturn(LocalDate.of(2026, 1, 10));
    when(reloj.horaDe(any())).thenReturn(LocalTime.of(8, 0));
    when(ids.nuevo(anyString())).thenReturn("id-1");
    when(carta.porId("p1")).thenReturn(Optional.of(plato()));
    when(ordenes.guardar(any()))
        .thenAnswer(
            invocacion -> {
              guardada.set(invocacion.getArgument(0));
              return invocacion.getArgument(0);
            });
  }

  private static ItemCarta plato() {
    ItemCarta item = new ItemCarta();
    item.setId("p1");
    item.setNombre("Róbalo al bijao");
    item.setPrecio(48000);
    item.setDisponible(true);
    item.setDestino(Destino.COCINA);
    return item;
  }

  private static Dtos.NuevoPedidoExterno paraLlevar(Canal canal) {
    return new Dtos.NuevoPedidoExterno(
        TipoPedido.LLEVAR,
        "Carolina Mendoza",
        "3001234567",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(new Dtos.NuevoItem("p1", 1, List.of(), null)),
        canal);
  }

  @Test
  void el_pedido_tomado_por_whatsapp_entra_al_tablero_y_no_a_borrador() {
    Dtos.PedidoCreado creado = servicio.crearPedidoDeMostrador(paraLlevar(Canal.WHATSAPP));

    assertThat(creado.numero()).isEqualTo(7);
    assertThat(guardada.get().getCanal()).isEqualTo(Canal.WHATSAPP);
    // Es lo que hace que recepcion lo vea. En `borrador` se perderia.
    assertThat(guardada.get().getEstadoPedido()).isEqualTo(EstadoPedido.NUEVO);
  }

  @Test
  void el_mostrador_puede_anotar_fuera_del_horario_de_domicilios() {
    // Mismo canal, misma hora: por la puerta publica no entra.
    assertThatThrownBy(() -> servicio.crearPedidoExterno(paraLlevar(Canal.WEB)))
        .isInstanceOf(ReglaDeNegocioError.class);

    // Por la del mostrador si: quien esta ahi ya tiene ese juicio delante.
    assertThat(servicio.crearPedidoDeMostrador(paraLlevar(Canal.TELEFONO)).numero()).isEqualTo(7);
  }

  @Test
  void el_mostrador_puede_anotar_con_el_canal_pausado() {
    Ajustes pausados = new Ajustes();
    pausados.setPorcentajeInc(8);
    pausados.setDomiciliosPausados(true);
    pausados.setDomiciliosDesde(LocalTime.of(0, 0));
    pausados.setDomiciliosHasta(LocalTime.of(23, 59));
    when(ajustesRepo.leer()).thenReturn(pausados);

    assertThatThrownBy(() -> servicio.crearPedidoExterno(paraLlevar(Canal.WEB)))
        .isInstanceOf(ReglaDeNegocioError.class);

    assertThat(servicio.crearPedidoDeMostrador(paraLlevar(Canal.WHATSAPP)).numero()).isEqualTo(7);
  }

  @Test
  void las_validaciones_del_cliente_siguen_valiendo_en_el_mostrador() {
    Dtos.NuevoPedidoExterno sinTelefono =
        new Dtos.NuevoPedidoExterno(
            TipoPedido.LLEVAR, "Carolina", "300", null, null, null, null, null,
            null, null, null, List.of(new Dtos.NuevoItem("p1", 1, List.of(), null)),
            Canal.WHATSAPP);

    assertThatThrownBy(() -> servicio.crearPedidoDeMostrador(sinTelefono))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("10 dígitos");
  }
}
