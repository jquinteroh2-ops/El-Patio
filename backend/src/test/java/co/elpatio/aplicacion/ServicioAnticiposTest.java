package co.elpatio.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.ajustes.Ajustes;
import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.carta.Destino;
import co.elpatio.dominio.comanda.EstadoItem;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.pago.EstadoPagoOnline;
import co.elpatio.dominio.pago.PagoOnline;
import co.elpatio.dominio.pedido.EstadoPedido;
import co.elpatio.dominio.pedido.TipoPedido;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PasarelaDePagos;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * `ServicioAnticipos` con todos sus puertos simulados: lo que importa aqui es
 * que el monto se calcule bien, que el pedido quede en el estado correcto y
 * que un evento repetido del webhook no dispare el flujo dos veces.
 */
class ServicioAnticiposTest {

  private Repositorios.DeOrdenes ordenes;
  private Repositorios.DePagosOnline pagosOnline;
  private Repositorios.DeAjustes ajustesRepo;
  private PasarelaDePagos pasarela;
  private GeneradorIds ids;
  private Reloj reloj;
  private PublicadorEventos eventos;
  private ServicioAnticipos servicio;

  private static final Instant AHORA = Instant.parse("2026-01-10T20:00:00Z");

  @BeforeEach
  void preparar() {
    ordenes = mock(Repositorios.DeOrdenes.class);
    pagosOnline = mock(Repositorios.DePagosOnline.class);
    ajustesRepo = mock(Repositorios.DeAjustes.class);
    pasarela = mock(PasarelaDePagos.class);
    ids = mock(GeneradorIds.class);
    reloj = mock(Reloj.class);
    eventos = mock(PublicadorEventos.class);
    servicio =
        new ServicioAnticipos(ordenes, pagosOnline, ajustesRepo, pasarela, ids, reloj, eventos);

    when(reloj.ahora()).thenReturn(AHORA);
    when(pagosOnline.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
    when(ordenes.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private static Orden ordenExterna(String id, long precioUnitario, EstadoPedido estadoPedido) {
    Orden orden = new Orden();
    orden.setId(id);
    orden.setNumero(1);
    orden.setTipo(TipoPedido.LLEVAR);
    orden.setCanal(Canal.WHATSAPP);
    orden.setEstadoPedido(estadoPedido);

    ItemOrden item = new ItemOrden();
    item.setId("io1");
    item.setItemCartaId("p1");
    item.setNombre("Plato");
    item.setPrecioUnitario(precioUnitario);
    item.setCantidad(1);
    item.setDestino(Destino.COCINA);
    item.setEstado(EstadoItem.PENDIENTE);
    orden.setItems(List.of(item));
    return orden;
  }

  private static Ajustes ajustes(int porcentajeInc, int porcentajeAnticipo) {
    Ajustes ajustes = new Ajustes();
    ajustes.setPorcentajeInc(porcentajeInc);
    ajustes.setPorcentajeAnticipo(porcentajeAnticipo);
    ajustes.setFechaConsecutivo(LocalDate.of(2026, 1, 10));
    return ajustes;
  }

  @Test
  void calculaElAnticipoSobreElTotalYCreaElLink() {
    Orden orden = ordenExterna("ord1", 100_000, EstadoPedido.BORRADOR);
    when(ordenes.porId("ord1")).thenReturn(Optional.of(orden));
    when(ajustesRepo.leer()).thenReturn(ajustes(8, 30));
    when(ids.nuevo("anticipo")).thenReturn("anticipo_1");
    when(ids.nuevo("pgo")).thenReturn("pgo_1");
    when(pasarela.crearLinkDePago(anyString(), anyLong(), anyString(), any()))
        .thenReturn("https://checkout.wompi.co/l/xyz");

    Dtos.AnticipoCreado resultado = servicio.crearAnticipo("ord1");

    // Total = 100000 + 8% inc = 108000. 30% de eso = 32400 pesos = 3240000 centavos.
    assertThat(resultado.montoCentavos()).isEqualTo(3_240_000);
    assertThat(resultado.urlPago()).isEqualTo("https://checkout.wompi.co/l/xyz");
    assertThat(orden.getEstadoPedido()).isEqualTo(EstadoPedido.ESPERANDO_ANTICIPO);
    verify(eventos).publicar(List.of("pedidos", "ordenes"));
  }

  @Test
  void unPedidoDeMesaNoPuedeTenerAnticipo() {
    Orden mesa = new Orden();
    mesa.setId("ord2");
    mesa.setTipo(TipoPedido.MESA);
    when(ordenes.porId("ord2")).thenReturn(Optional.of(mesa));

    assertThatThrownBy(() -> servicio.crearAnticipo("ord2")).isInstanceOf(ReglaDeNegocioError.class);
  }

  @Test
  void sinPorcentajeConfiguradoNoSeCreaAnticipo() {
    Orden orden = ordenExterna("ord3", 50_000, EstadoPedido.BORRADOR);
    when(ordenes.porId("ord3")).thenReturn(Optional.of(orden));
    when(ajustesRepo.leer()).thenReturn(ajustes(8, 0));

    assertThatThrownBy(() -> servicio.crearAnticipo("ord3")).isInstanceOf(ReglaDeNegocioError.class);
    verify(pasarela, never()).crearLinkDePago(any(), anyLong(), any(), any());
  }

  @Test
  void unWebhookAprobadoMueveElPedidoHastaNuevo() {
    Orden orden = ordenExterna("ord4", 50_000, EstadoPedido.ESPERANDO_ANTICIPO);
    PagoOnline pago = new PagoOnline();
    pago.setId("pgo_4");
    pago.setOrdenId("ord4");
    pago.setReferencia("anticipo_4");
    when(pagosOnline.porReferencia("anticipo_4")).thenReturn(Optional.of(pago));
    when(ordenes.porId("ord4")).thenReturn(Optional.of(orden));

    servicio.procesarEvento("anticipo_4", true, "txn_1");

    assertThat(pago.getEstado()).isEqualTo(EstadoPagoOnline.APROBADO);
    assertThat(orden.getEstadoPedido()).isEqualTo(EstadoPedido.NUEVO);
    verify(eventos).publicar(List.of("pedidos", "ordenes"));
  }

  @Test
  void unWebhookReenviadoNoVuelveAMoverElPedido() {
    Orden orden = ordenExterna("ord5", 50_000, EstadoPedido.NUEVO);
    PagoOnline pago = new PagoOnline();
    pago.setId("pgo_5");
    pago.setOrdenId("ord5");
    pago.setReferencia("anticipo_5");
    pago.aprobar("txn_1", AHORA.minusSeconds(60));
    when(pagosOnline.porReferencia("anticipo_5")).thenReturn(Optional.of(pago));

    servicio.procesarEvento("anticipo_5", true, "txn_1");

    verify(ordenes, never()).porId("ord5");
    verify(eventos, never()).publicar(any());
  }

  @Test
  void unWebhookRechazadoCancelaElPedido() {
    Orden orden = ordenExterna("ord6", 50_000, EstadoPedido.ESPERANDO_ANTICIPO);
    PagoOnline pago = new PagoOnline();
    pago.setId("pgo_6");
    pago.setOrdenId("ord6");
    pago.setReferencia("anticipo_6");
    when(pagosOnline.porReferencia("anticipo_6")).thenReturn(Optional.of(pago));
    when(ordenes.porId("ord6")).thenReturn(Optional.of(orden));

    servicio.procesarEvento("anticipo_6", false, "txn_2");

    assertThat(pago.getEstado()).isEqualTo(EstadoPagoOnline.RECHAZADO);
    assertThat(orden.getEstadoPedido()).isEqualTo(EstadoPedido.CANCELADO);
  }

  @Test
  void elJobExpiraSoloLosPendientesVencidosYCierraElPedido() {
    PagoOnline vencido = new PagoOnline();
    vencido.setId("pgo_7");
    vencido.setOrdenId("ord7");
    vencido.setReferencia("anticipo_7");
    vencido.setExpiraEn(AHORA.minusSeconds(1));

    Orden orden = ordenExterna("ord7", 50_000, EstadoPedido.ESPERANDO_ANTICIPO);
    when(pagosOnline.pendientesVencidosAntesDe(AHORA)).thenReturn(List.of(vencido));
    when(ordenes.porId("ord7")).thenReturn(Optional.of(orden));

    servicio.expirarVencidos();

    assertThat(vencido.getEstado()).isEqualTo(EstadoPagoOnline.EXPIRADO);
    assertThat(orden.getEstadoPedido()).isEqualTo(EstadoPedido.EXPIRADO);
    verify(eventos).publicar(List.of("pedidos", "ordenes"));
  }

  @Test
  void elJobNoPublicaEventosSiNoHayNadaQueExpirar() {
    when(pagosOnline.pendientesVencidosAntesDe(AHORA)).thenReturn(List.of());

    servicio.expirarVencidos();

    verify(eventos, never()).publicar(any());
  }
}
