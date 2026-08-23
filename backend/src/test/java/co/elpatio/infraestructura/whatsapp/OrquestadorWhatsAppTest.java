package co.elpatio.infraestructura.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elpatio.aplicacion.ServicioAnticipos;
import co.elpatio.aplicacion.ServicioCarta;
import co.elpatio.aplicacion.ServicioConversaciones;
import co.elpatio.aplicacion.ServicioPedidos;
import co.elpatio.aplicacion.ServicioReservas;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.carta.Destino;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.conversacion.Conversacion;
import co.elpatio.dominio.conversacion.EstadoConversacion;
import co.elpatio.dominio.cobro.Cuenta;
import co.elpatio.dominio.interprete.ItemReconocido;
import co.elpatio.dominio.interprete.ResultadoInterpretacion;
import co.elpatio.dominio.pedido.TipoPedido;
import co.elpatio.dominio.puertos.InterpretePedidoTexto;
import co.elpatio.infraestructura.whatsapp.OrquestadorWhatsApp.MensajeEntrante;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El guion del bot, con todos los casos de uso simulados: lo que importa aqui
 * es que cada boton lleve al paso correcto y que confirmar un pedido termine
 * llamando a `ServicioPedidos` y `ServicioAnticipos` exactamente como lo haria
 * cualquier otro canal.
 */
class OrquestadorWhatsAppTest {

  private static final String TELEFONO = "573001234567";

  private ServicioConversaciones conversaciones;
  private ServicioPedidos pedidos;
  private ServicioReservas reservas;
  private ServicioAnticipos anticipos;
  private ServicioCarta carta;
  private ClienteGraphApi whatsapp;
  private InterpretePedidoTexto interprete;
  private OrquestadorWhatsApp orquestador;
  private Conversacion conversacion;

  @BeforeEach
  void preparar() {
    conversaciones = mock(ServicioConversaciones.class);
    pedidos = mock(ServicioPedidos.class);
    reservas = mock(ServicioReservas.class);
    anticipos = mock(ServicioAnticipos.class);
    carta = mock(ServicioCarta.class);
    whatsapp = mock(ClienteGraphApi.class);
    interprete = mock(InterpretePedidoTexto.class);
    orquestador =
        new OrquestadorWhatsApp(conversaciones, pedidos, reservas, anticipos, carta, whatsapp, interprete);

    conversacion = new Conversacion();
    conversacion.setId("conv1");
    conversacion.setCanal(Canal.WHATSAPP);
    conversacion.setIdentificadorExterno(TELEFONO);
    conversacion.setEstado(EstadoConversacion.INICIADA);
    when(conversaciones.obtenerOCrear(Canal.WHATSAPP, TELEFONO)).thenReturn(conversacion);
    // `guardar` se llama para persistir cada avance; la conversacion simulada
    // ya se muta en memoria, asi que basta con devolverla igual.
    when(conversaciones.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

    ItemCarta arepa = new ItemCarta();
    arepa.setId("p1");
    arepa.setNombre("Arepa e huevo");
    arepa.setPrecio(8000);
    arepa.setDisponible(true);
    arepa.setDestino(Destino.COCINA);
    when(carta.listarCarta()).thenReturn(List.of(arepa));
  }

  private void enviar(String idInteractivo, String texto) {
    orquestador.procesarMensaje(new MensajeEntrante(TELEFONO, idInteractivo, texto));
  }

  @Test
  void unMensajeInicialMandaElMenuPrincipal() {
    enviar(null, "hola");

    verify(whatsapp).enviarBotones(eq(TELEFONO), anyString(), any());
    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.EN_MENU_PRINCIPAL);
  }

  @Test
  void elBotonDePedidoMandaLaListaDeMenu() {
    conversacion.setEstado(EstadoConversacion.EN_MENU_PRINCIPAL);

    enviar("menu:pedido", null);

    verify(whatsapp).enviarLista(eq(TELEFONO), anyString(), anyString(), any());
    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.ARMANDO_PEDIDO);
  }

  @Test
  void elBotonDeHumanoDerivaLaConversacion() {
    conversacion.setEstado(EstadoConversacion.EN_MENU_PRINCIPAL);

    enviar("menu:humano", null);

    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.DERIVADA_A_HUMANO);
  }

  @Test
  void armarYConfirmarUnPedidoCreaElPedidoYElAnticipo() {
    when(pedidos.crearPedidoExterno(any()))
        .thenReturn(new Dtos.PedidoCreado("ord1", 7, 20, cuentaVacia()));
    when(anticipos.crearAnticipo("ord1"))
        .thenReturn(new Dtos.AnticipoCreado("pgo1", "https://checkout.wompi.co/l/xyz", 500_000, Instant.now()));

    conversacion.setEstado(EstadoConversacion.EN_MENU_PRINCIPAL);
    enviar("menu:pedido", null); // -> ARMANDO_PEDIDO, eligiendo_items
    enviar("item:p1", null); // -> pregunta cantidad
    enviar(null, "2"); // -> agrega 2 arepas, vuelve a eligiendo_items
    enviar(null, "listo"); // -> pide nombre
    enviar(null, "Juan Perez"); // -> resumen + botones confirmar/cancelar
    enviar("pedido:confirmar", null); // -> crea el pedido y el anticipo

    verify(pedidos).crearPedidoExterno(argThat(datos ->
        datos.canal() == Canal.WHATSAPP
            && datos.tipo() == TipoPedido.LLEVAR
            && datos.telefono().equals(TELEFONO)
            && datos.items().size() == 1
            && datos.items().get(0).cantidad() == 2));
    verify(anticipos).crearAnticipo("ord1");
    verify(whatsapp).enviarTexto(eq(TELEFONO), org.mockito.ArgumentMatchers.contains("checkout.wompi.co"));
    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.FINALIZADA);
    assertThat(conversacion.getPedidoId()).isEqualTo("ord1");
  }

  @Test
  void cancelarElPedidoVuelveAlMenuPrincipalSinCrearNada() {
    conversacion.setEstado(EstadoConversacion.EN_MENU_PRINCIPAL);
    enviar("menu:pedido", null);
    enviar("item:p1", null);
    enviar(null, "1");
    enviar(null, "listo");
    enviar(null, "Juan");

    enviar("pedido:cancelar", null);

    verify(pedidos, never()).crearPedidoExterno(any());
    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.EN_MENU_PRINCIPAL);
  }

  @Test
  void armarUnaReservaLaCreaConElCanalWhatsapp() {
    conversacion.setEstado(EstadoConversacion.EN_MENU_PRINCIPAL);
    enviar("menu:reserva", null);
    enviar(null, "2026-09-15 20:00");
    enviar(null, "4");
    enviar(null, "Maria Lopez");

    verify(reservas)
        .crearReserva(
            argThat(
                datos ->
                    datos.canal() == Canal.WHATSAPP
                        && datos.telefono().equals(TELEFONO)
                        && datos.personas() == 4
                        && datos.nombreCliente().equals("Maria Lopez")));
    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.FINALIZADA);
  }

  @Test
  void unTextoQueNoEsUnaFechaValidaVuelveAPreguntar() {
    conversacion.setEstado(EstadoConversacion.EN_MENU_PRINCIPAL);
    enviar("menu:reserva", null);

    enviar(null, "mañana en la tarde");

    verify(reservas, never()).crearReserva(any());
    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.ARMANDO_RESERVA);
  }

  @Test
  void unTextoLibreReconocidoAgregaElItemConElPrecioRealDelMenu() {
    conversacion.setEstado(EstadoConversacion.EN_MENU_PRINCIPAL);
    enviar("menu:pedido", null);
    when(interprete.interpretar(org.mockito.ArgumentMatchers.eq("2 arepas e huevo"), any()))
        .thenReturn(new ResultadoInterpretacion(List.of(new ItemReconocido("p1", 2)), List.of()));

    enviar(null, "2 arepas e huevo");

    verify(whatsapp).enviarTexto(eq(TELEFONO), org.mockito.ArgumentMatchers.contains("Arepa e huevo"));
    // El precio y el nombre los pone este backend a partir del id, nunca el interprete.
    when(pedidos.crearPedidoExterno(any()))
        .thenReturn(new Dtos.PedidoCreado("ord9", 1, 10, cuentaVacia()));
    when(anticipos.crearAnticipo("ord9"))
        .thenReturn(new Dtos.AnticipoCreado("pgo9", "https://checkout.wompi.co/l/abc", 100_000, Instant.now()));
    enviar(null, "listo");
    enviar(null, "Carlos");
    enviar("pedido:confirmar", null);

    verify(pedidos)
        .crearPedidoExterno(
            argThat(
                datos ->
                    datos.items().size() == 1
                        && datos.items().get(0).itemCartaId().equals("p1")
                        && datos.items().get(0).cantidad() == 2));
  }

  @Test
  void unTextoLibreConAlgoQueNoEstaEnElMenuLoPreguntaEnVezDeAdivinar() {
    conversacion.setEstado(EstadoConversacion.EN_MENU_PRINCIPAL);
    enviar("menu:pedido", null);
    when(interprete.interpretar(anyString(), any()))
        .thenReturn(new ResultadoInterpretacion(List.of(), List.of("una pizza hawaiana")));

    enviar(null, "quiero una pizza hawaiana");

    verify(whatsapp)
        .enviarTexto(eq(TELEFONO), org.mockito.ArgumentMatchers.contains("pizza hawaiana"));
    verify(pedidos, never()).crearPedidoExterno(any());
    assertThat(conversacion.getEstado()).isEqualTo(EstadoConversacion.ARMANDO_PEDIDO);
  }

  @Test
  void unIdInventadoPorElInterpreteSeDescartaSinTocarElBorrador() {
    conversacion.setEstado(EstadoConversacion.EN_MENU_PRINCIPAL);
    enviar("menu:pedido", null);
    when(interprete.interpretar(anyString(), any()))
        .thenReturn(new ResultadoInterpretacion(List.of(new ItemReconocido("no-existe", 3)), List.of()));

    enviar(null, "3 hamburguesas");

    // Nada que agregar y nada no reconocido: solo la invitacion a seguir, sin pedido a medias.
    verify(pedidos, never()).crearPedidoExterno(any());
  }

  private static Cuenta cuentaVacia() {
    return new Cuenta(0, 0, 0, 0, 0, 0, 0, 0);
  }

  // Pequeños alias para no arrastrar los imports estaticos de Mockito completos.
  private static String eq(String valor) {
    return org.mockito.ArgumentMatchers.eq(valor);
  }

  private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
    return org.mockito.ArgumentMatchers.argThat(matcher);
  }
}
