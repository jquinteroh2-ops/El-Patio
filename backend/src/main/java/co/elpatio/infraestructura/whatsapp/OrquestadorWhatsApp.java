package co.elpatio.infraestructura.whatsapp;

import co.elpatio.aplicacion.ServicioAnticipos;
import co.elpatio.aplicacion.ServicioCarta;
import co.elpatio.aplicacion.ServicioConversaciones;
import co.elpatio.aplicacion.ServicioPedidos;
import co.elpatio.aplicacion.ServicioReservas;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.conversacion.Conversacion;
import co.elpatio.dominio.conversacion.EstadoConversacion;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.interprete.ItemDelMenu;
import co.elpatio.dominio.interprete.ItemReconocido;
import co.elpatio.dominio.interprete.ResultadoInterpretacion;
import co.elpatio.dominio.pago.Centavos;
import co.elpatio.dominio.pedido.TipoPedido;
import co.elpatio.dominio.puertos.InterpretePedidoTexto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * El guion del bot: botones e interactivos, sin una linea de IA.
 *
 * Cada rama termina llamando a un caso de uso agnostico de canal
 * (`ServicioPedidos`, `ServicioReservas`, `ServicioAnticipos`) exactamente
 * igual que lo haria el sitio publico o, algun dia, el agente de voz. Lo unico
 * que hay aqui es "que boton mandar despues de cada respuesta", que es
 * conducta de WhatsApp y de ningun otro canal.
 *
 * El progreso de una charla que todavia no termina se guarda en
 * `Conversacion.datosContexto` como JSON: es la unica forma de que un
 * pod que se reinicia entre un mensaje y el siguiente no pierda el pedido que
 * el cliente ya venia armando.
 */
@Component
public class OrquestadorWhatsApp {

  private static final Logger registro = LoggerFactory.getLogger(OrquestadorWhatsApp.class);

  /** Meta permite hasta 10 filas por lista interactiva. */
  private static final int MAXIMO_ITEMS_EN_LISTA = 10;

  private static final DateTimeFormatter FORMATO_FECHA_HORA =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private static final ZoneId ZONA_RESTAURANTE = ZoneId.of("America/Bogota");

  private final ServicioConversaciones conversaciones;
  private final ServicioPedidos pedidos;
  private final ServicioReservas reservas;
  private final ServicioAnticipos anticipos;
  private final ServicioCarta carta;
  private final ClienteGraphApi whatsapp;
  private final InterpretePedidoTexto interprete;
  private final ObjectMapper json =
      new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public OrquestadorWhatsApp(
      ServicioConversaciones conversaciones,
      ServicioPedidos pedidos,
      ServicioReservas reservas,
      ServicioAnticipos anticipos,
      ServicioCarta carta,
      ClienteGraphApi whatsapp,
      InterpretePedidoTexto interprete) {
    this.conversaciones = conversaciones;
    this.pedidos = pedidos;
    this.reservas = reservas;
    this.anticipos = anticipos;
    this.carta = carta;
    this.whatsapp = whatsapp;
    this.interprete = interprete;
  }

  /** Lo que se saca de un mensaje entrante de WhatsApp, ya reducido a lo que importa aqui. */
  public record MensajeEntrante(String telefono, String idInteractivo, String textoLibre) {}

  /** El pedido que el cliente va armando, mientras no se confirma. Vive solo en `datosContexto`. */
  private record ItemBorrador(String itemCartaId, String nombre, long precioUnitario, int cantidad) {}

  /**
   * `paso` recorre: eligiendo_items -> cantidad -> eligiendo_items (repite) ->
   * nombre -> confirmar. Los campos `pendiente*` solo tienen valor durante el
   * paso `cantidad`, mientras se espera cuantas unidades del item elegido.
   */
  private record BorradorPedido(
      String paso,
      List<ItemBorrador> items,
      String nombre,
      String pendienteItemCartaId,
      String pendienteNombre,
      long pendientePrecioUnitario) {
    static BorradorPedido inicial() {
      return new BorradorPedido("eligiendo_items", new ArrayList<>(), null, null, null, 0);
    }
  }

  private record BorradorReserva(String paso, Instant fechaHora, Integer personas, String nombre) {
    static BorradorReserva inicial() {
      return new BorradorReserva("fecha_hora", null, null, null);
    }
  }

  /**
   * Se llama desde el controlador del webhook, que ya respondio 200 a Meta:
   * lo que pase aqui adentro (una pasarela lenta, un problema de red) no
   * puede demorar esa respuesta ni arriesgarse a que Meta reintente el mismo
   * mensaje por creer que no llego.
   */
  @Async
  public void procesarMensaje(MensajeEntrante mensaje) {
    try {
      Conversacion conversacion = conversaciones.obtenerOCrear(Canal.WHATSAPP, mensaje.telefono());
      atender(conversacion, mensaje);
    } catch (RuntimeException e) {
      registro.error("Fallo atendiendo un mensaje de WhatsApp", e);
      whatsapp.enviarTexto(
          mensaje.telefono(), "Disculpa, tuvimos un problema procesando tu mensaje. Intenta de nuevo.");
    }
  }

  private void atender(Conversacion conversacion, MensajeEntrante mensaje) {
    switch (conversacion.getEstado()) {
      case INICIADA -> enviarMenuPrincipal(conversacion);
      case EN_MENU_PRINCIPAL -> atenderMenuPrincipal(conversacion, mensaje);
      case ARMANDO_PEDIDO -> atenderArmandoPedido(conversacion, mensaje);
      case ARMANDO_RESERVA -> atenderArmandoReserva(conversacion, mensaje);
      case DERIVADA_A_HUMANO ->
          whatsapp.enviarTexto(
              mensaje.telefono(), "Ya avisamos a alguien del restaurante, en un momento te escribe.");
      case FINALIZADA, EXPIRADA -> {
        // No deberia llegar aqui: `abiertaPara` filtra las conversaciones
        // finales, asi que un mensaje nuevo abre una conversacion distinta.
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Menu principal
  // ---------------------------------------------------------------------------

  private void enviarMenuPrincipal(Conversacion conversacion) {
    whatsapp.enviarBotones(
        conversacion.getIdentificadorExterno(),
        "¡Hola! Soy el asistente de El Patio. ¿Qué deseas hacer?",
        List.of(
            new ClienteGraphApi.Boton("menu:pedido", "Hacer pedido"),
            new ClienteGraphApi.Boton("menu:reserva", "Reservar mesa"),
            new ClienteGraphApi.Boton("menu:humano", "Hablar con alguien")));
    avanzar(conversacion, EstadoConversacion.EN_MENU_PRINCIPAL, null);
  }

  private void atenderMenuPrincipal(Conversacion conversacion, MensajeEntrante mensaje) {
    String opcion = mensaje.idInteractivo();
    if (opcion == null) {
      enviarMenuPrincipal(conversacion);
      return;
    }
    switch (opcion) {
      case "menu:pedido" -> iniciarPedido(conversacion);
      case "menu:reserva" -> iniciarReserva(conversacion);
      case "menu:humano" -> {
        whatsapp.enviarTexto(
            mensaje.telefono(), "Listo, en un momento te atiende alguien del restaurante.");
        avanzar(conversacion, EstadoConversacion.DERIVADA_A_HUMANO, null);
      }
      default -> enviarMenuPrincipal(conversacion);
    }
  }

  // ---------------------------------------------------------------------------
  // Armar pedido
  //
  // Version minima a proposito: solo "para llevar", sin domicilio todavia.
  // Domicilio necesita elegir zona y capturar direccion con un WhatsApp Flow
  // real, que queda para una fase aparte una vez este el resto probado.
  // ---------------------------------------------------------------------------

  private void iniciarPedido(Conversacion conversacion) {
    enviarListaDeMenu(conversacion, "Elige un producto del menú:");
    avanzar(conversacion, EstadoConversacion.ARMANDO_PEDIDO, escribir(BorradorPedido.inicial()));
  }

  private void enviarListaDeMenu(Conversacion conversacion, String texto) {
    List<ItemCarta> disponibles = carta.listarCarta().stream().filter(ItemCarta::isDisponible).toList();
    List<ClienteGraphApi.FilaLista> filas =
        disponibles.stream()
            .limit(MAXIMO_ITEMS_EN_LISTA)
            .map(
                item ->
                    new ClienteGraphApi.FilaLista(
                        "item:" + item.getId(), item.getNombre(), formatoPesos(item.getPrecio())))
            .toList();
    if (filas.isEmpty()) {
      whatsapp.enviarTexto(conversacion.getIdentificadorExterno(), "Por ahora no hay productos disponibles.");
      return;
    }
    whatsapp.enviarLista(
        conversacion.getIdentificadorExterno(),
        texto,
        "Ver menú",
        List.of(new ClienteGraphApi.SeccionLista("Menú", filas)));
  }

  private void atenderArmandoPedido(Conversacion conversacion, MensajeEntrante mensaje) {
    BorradorPedido borrador = leer(conversacion, BorradorPedido.class, BorradorPedido::inicial);

    switch (borrador.paso()) {
      case "eligiendo_items" -> {
        if (mensaje.idInteractivo() != null && mensaje.idInteractivo().startsWith("item:")) {
          String itemCartaId = mensaje.idInteractivo().substring("item:".length());
          ItemCarta item =
              carta.listarCarta().stream().filter(i -> i.getId().equals(itemCartaId)).findFirst().orElse(null);
          if (item == null) {
            whatsapp.enviarTexto(mensaje.telefono(), "Ese producto ya no está disponible.");
            enviarListaDeMenu(conversacion, "Elige otro producto:");
            return;
          }
          whatsapp.enviarTexto(mensaje.telefono(), "¿Cuántas unidades de " + item.getNombre() + " deseas?");
          avanzar(
              conversacion,
              EstadoConversacion.ARMANDO_PEDIDO,
              escribir(
                  new BorradorPedido(
                      "cantidad", borrador.items(), null, item.getId(), item.getNombre(), item.getPrecio())));
          return;
        }
        String texto = normalizar(mensaje.textoLibre());
        if ("listo".equals(texto) || "terminar".equals(texto)) {
          if (borrador.items().isEmpty()) {
            whatsapp.enviarTexto(
                mensaje.telefono(), "Todavía no agregaste nada. Elige un producto o descríbeme qué quieres pedir.");
            enviarListaDeMenu(conversacion, "Elige un producto:");
            return;
          }
          whatsapp.enviarTexto(mensaje.telefono(), "Perfecto, ¿a nombre de quién dejamos el pedido?");
          avanzar(
              conversacion,
              EstadoConversacion.ARMANDO_PEDIDO,
              escribir(new BorradorPedido("nombre", borrador.items(), null, null, null, 0)));
          return;
        }
        // Texto libre que no es un comando: se interpreta contra el menu real
        // en vez de solo reenviar la lista. El interprete solo puede devolver
        // ids que esten en el menu; lo demas se le pregunta al cliente.
        if (mensaje.textoLibre() != null && !mensaje.textoLibre().isBlank()) {
          interpretarTextoLibre(conversacion, borrador, mensaje.textoLibre(), mensaje.telefono());
          return;
        }
        enviarListaDeMenu(conversacion, "Elige un producto de la lista, o descríbeme qué quieres pedir:");
      }

      case "cantidad" -> {
        Integer cantidad = enteroPositivo(mensaje.textoLibre());
        if (cantidad == null) {
          whatsapp.enviarTexto(mensaje.telefono(), "Escribe solo el número de unidades, por ejemplo: 2");
          return;
        }
        List<ItemBorrador> items = new ArrayList<>(borrador.items());
        items.add(
            new ItemBorrador(
                borrador.pendienteItemCartaId(), borrador.pendienteNombre(), borrador.pendientePrecioUnitario(), cantidad));
        BorradorPedido siguiente = new BorradorPedido("eligiendo_items", items, null, null, null, 0);
        whatsapp.enviarTexto(mensaje.telefono(), "Agregado: " + cantidad + " x " + borrador.pendienteNombre());
        enviarListaDeMenu(conversacion, "¿Algo más? Elige otro producto, o escribe \"listo\" para continuar:");
        avanzar(conversacion, EstadoConversacion.ARMANDO_PEDIDO, escribir(siguiente));
      }

      case "nombre" -> {
        String nombre = normalizar(mensaje.textoLibre());
        if (nombre == null || nombre.isBlank()) {
          whatsapp.enviarTexto(mensaje.telefono(), "Necesito un nombre para el pedido.");
          return;
        }
        BorradorPedido conNombre =
            new BorradorPedido("confirmar", borrador.items(), mensaje.textoLibre().trim(), null, null, 0);
        whatsapp.enviarTexto(mensaje.telefono(), resumenPedido(conNombre));
        whatsapp.enviarBotones(
            mensaje.telefono(),
            "¿Confirmas el pedido?",
            List.of(
                new ClienteGraphApi.Boton("pedido:confirmar", "Confirmar"),
                new ClienteGraphApi.Boton("pedido:cancelar", "Cancelar")));
        avanzar(conversacion, EstadoConversacion.ARMANDO_PEDIDO, escribir(conNombre));
      }

      case "confirmar" -> {
        if ("pedido:cancelar".equals(mensaje.idInteractivo())) {
          whatsapp.enviarTexto(mensaje.telefono(), "Pedido cancelado.");
          enviarMenuPrincipal(conversacion);
          return;
        }
        if ("pedido:confirmar".equals(mensaje.idInteractivo())) {
          confirmarPedido(conversacion, borrador, mensaje.telefono());
          return;
        }
        whatsapp.enviarTexto(mensaje.telefono(), "Usa los botones para confirmar o cancelar el pedido.");
      }

      default -> enviarListaDeMenu(conversacion, "Elige un producto del menú:");
    }
  }

  /**
   * Interpreta un pedido escrito en texto libre contra el menu real.
   *
   * Al interprete solo se le pasa id y nombre de cada producto disponible,
   * nunca precios. Lo que devuelva se vuelve a validar aqui contra ese mismo
   * menu antes de tocar el borrador: un id que el modelo se haya inventado no
   * llega ni a la lista de "agregado", queda descartado en silencio y su texto
   * cae en `no_reconocidos` para que el cliente aclare. El precio real y el
   * nombre real los busca este metodo por el id, nunca los que diga el modelo.
   */
  private void interpretarTextoLibre(Conversacion conversacion, BorradorPedido borrador, String textoLibre, String telefono) {
    List<ItemCarta> disponibles = carta.listarCarta().stream().filter(ItemCarta::isDisponible).toList();
    List<ItemDelMenu> menu = disponibles.stream().map(i -> new ItemDelMenu(i.getId(), i.getNombre())).toList();

    ResultadoInterpretacion resultado;
    try {
      resultado = interprete.interpretar(textoLibre, menu);
    } catch (RuntimeException e) {
      registro.error("Fallo el interprete de pedidos", e);
      whatsapp.enviarTexto(telefono, "No logré entender eso. Elige un producto de la lista:");
      enviarListaDeMenu(conversacion, "Elige un producto:");
      return;
    }

    List<ItemBorrador> nuevos = new ArrayList<>(borrador.items());
    for (ItemReconocido reconocido : resultado.reconocidos()) {
      ItemCarta item =
          disponibles.stream().filter(i -> i.getId().equals(reconocido.itemCartaId())).findFirst().orElse(null);
      // El interprete solo deberia devolver ids que estaban en el menu que se
      // le paso, pero se revalida igual: nunca se confia un id a ciegas.
      if (item == null) continue;
      nuevos.add(new ItemBorrador(item.getId(), item.getNombre(), item.getPrecio(), reconocido.cantidad()));
    }

    BorradorPedido siguiente = new BorradorPedido("eligiendo_items", nuevos, null, null, null, 0);
    avanzar(conversacion, EstadoConversacion.ARMANDO_PEDIDO, escribir(siguiente));

    if (!resultado.reconocidos().isEmpty()) {
      String agregados =
          resultado.reconocidos().stream()
              .map(r -> disponibles.stream().filter(i -> i.getId().equals(r.itemCartaId())).findFirst())
              .flatMap(java.util.Optional::stream)
              .map(ItemCarta::getNombre)
              .distinct()
              .collect(java.util.stream.Collectors.joining(", "));
      if (!agregados.isBlank()) whatsapp.enviarTexto(telefono, "Agregado: " + agregados);
    }

    if (!resultado.noReconocidos().isEmpty()) {
      whatsapp.enviarTexto(
          telefono,
          "No identifiqué esto en el menú: \""
              + String.join("\", \"", resultado.noReconocidos())
              + "\". ¿Puedes elegirlo de la lista?");
      enviarListaDeMenu(conversacion, "Elige el producto:");
      return;
    }

    whatsapp.enviarTexto(telefono, "¿Algo más? Descríbelo, elige de la lista, o escribe \"listo\" para continuar.");
  }

  private void confirmarPedido(Conversacion conversacion, BorradorPedido borrador, String telefono) {
    List<Dtos.NuevoItem> items =
        borrador.items().stream()
            .map(i -> new Dtos.NuevoItem(i.itemCartaId(), i.cantidad(), List.of(), null))
            .toList();

    Dtos.PedidoCreado creado;
    try {
      creado =
          pedidos.crearPedidoExterno(
              new Dtos.NuevoPedidoExterno(
                  TipoPedido.LLEVAR,
                  borrador.nombre(),
                  telefono,
                  null,
                  null,
                  null,
                  null,
                  "Pedido por WhatsApp",
                  null,
                  null,
                  null,
                  items,
                  Canal.WHATSAPP));
    } catch (ReglaDeNegocioError e) {
      whatsapp.enviarTexto(telefono, "No se pudo crear el pedido: " + e.getMessage());
      enviarMenuPrincipal(conversacion);
      return;
    }

    Dtos.AnticipoCreado anticipo = anticipos.crearAnticipo(creado.id());
    whatsapp.enviarTexto(
        telefono,
        "Tu pedido #"
            + creado.numero()
            + " quedó registrado. Para confirmarlo, paga el anticipo de "
            + formatoPesos(Centavos.aCOP(anticipo.montoCentavos()))
            + " aquí: "
            + anticipo.urlPago()
            + "\n\nEl link vence en pocos minutos.");

    conversacion.asociarPedido(creado.id(), Instant.now());
    avanzar(conversacion, EstadoConversacion.FINALIZADA, null);
  }

  private String resumenPedido(BorradorPedido borrador) {
    StringBuilder texto = new StringBuilder("Resumen de tu pedido:\n");
    for (ItemBorrador item : borrador.items()) {
      texto.append("- ").append(item.cantidad()).append(" x ").append(item.nombre()).append('\n');
    }
    texto.append("\nA nombre de: ").append(borrador.nombre());
    return texto.toString();
  }

  // ---------------------------------------------------------------------------
  // Armar reserva
  //
  // Version minima: fecha y hora por texto libre, sin validar disponibilidad
  // de mesas todavia (el dominio de Reserva tampoco lo hace hoy para ningun
  // canal). Queda para cuando se agregue esa regla en general.
  // ---------------------------------------------------------------------------

  private void iniciarReserva(Conversacion conversacion) {
    whatsapp.enviarTexto(
        conversacion.getIdentificadorExterno(),
        "¿Para qué fecha y hora? Escríbela así: 2026-08-30 19:30");
    avanzar(conversacion, EstadoConversacion.ARMANDO_RESERVA, escribir(BorradorReserva.inicial()));
  }

  private void atenderArmandoReserva(Conversacion conversacion, MensajeEntrante mensaje) {
    BorradorReserva borrador = leer(conversacion, BorradorReserva.class, BorradorReserva::inicial);

    switch (borrador.paso()) {
      case "fecha_hora" -> {
        Instant fechaHora = parsearFechaHora(mensaje.textoLibre());
        if (fechaHora == null) {
          whatsapp.enviarTexto(
              mensaje.telefono(), "No entendí la fecha. Escríbela así: 2026-08-30 19:30");
          return;
        }
        whatsapp.enviarTexto(mensaje.telefono(), "¿Para cuántas personas?");
        avanzar(
            conversacion,
            EstadoConversacion.ARMANDO_RESERVA,
            escribir(new BorradorReserva("personas", fechaHora, null, null)));
      }

      case "personas" -> {
        Integer personas = enteroPositivo(mensaje.textoLibre());
        if (personas == null) {
          whatsapp.enviarTexto(mensaje.telefono(), "Escribe solo el número de personas, por ejemplo: 4");
          return;
        }
        whatsapp.enviarTexto(mensaje.telefono(), "¿A nombre de quién hacemos la reserva?");
        avanzar(
            conversacion,
            EstadoConversacion.ARMANDO_RESERVA,
            escribir(new BorradorReserva("nombre", borrador.fechaHora(), personas, null)));
      }

      case "nombre" -> {
        String nombre = mensaje.textoLibre();
        if (nombre == null || nombre.isBlank()) {
          whatsapp.enviarTexto(mensaje.telefono(), "Necesito un nombre para la reserva.");
          return;
        }
        reservas.crearReserva(
            new Dtos.NuevaReserva(
                nombre.trim(), mensaje.telefono(), borrador.fechaHora(), borrador.personas(), null, null, Canal.WHATSAPP));
        whatsapp.enviarTexto(
            mensaje.telefono(),
            "¡Listo! Tu reserva para " + borrador.personas() + " personas quedó solicitada. Te confirmamos pronto.");
        avanzar(conversacion, EstadoConversacion.FINALIZADA, null);
      }

      default -> iniciarReserva(conversacion);
    }
  }

  private Instant parsearFechaHora(String texto) {
    if (texto == null) return null;
    try {
      LocalDateTime local = LocalDateTime.parse(texto.trim(), FORMATO_FECHA_HORA);
      return local.atZone(ZONA_RESTAURANTE).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private void avanzar(Conversacion conversacion, EstadoConversacion estado, String contexto) {
    conversacion.cambiarEstado(estado, Instant.now());
    conversacion.setDatosContexto(contexto);
    conversaciones.guardar(conversacion);
  }

  private String escribir(Object valor) {
    try {
      return json.writeValueAsString(valor);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo guardar el contexto de la conversación", e);
    }
  }

  private <T> T leer(Conversacion conversacion, Class<T> tipo, java.util.function.Supplier<T> pordefecto) {
    String contexto = conversacion.getDatosContexto();
    if (contexto == null || contexto.isBlank()) {
      if (pordefecto == null) {
        throw new IllegalStateException("Se esperaba contexto de conversación y no había");
      }
      return pordefecto.get();
    }
    try {
      return json.readValue(contexto, tipo);
    } catch (Exception e) {
      if (pordefecto != null) return pordefecto.get();
      throw new IllegalStateException("No se pudo leer el contexto de la conversación", e);
    }
  }

  private static String normalizar(String texto) {
    return texto == null ? null : texto.trim().toLowerCase(Locale.ROOT);
  }

  private static Integer enteroPositivo(String texto) {
    if (texto == null) return null;
    try {
      int valor = Integer.parseInt(texto.trim());
      return valor > 0 ? valor : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String formatoPesos(long pesos) {
    return "$" + String.format(Locale.of("es", "CO"), "%,d", pesos).replace(',', '.');
  }
}
