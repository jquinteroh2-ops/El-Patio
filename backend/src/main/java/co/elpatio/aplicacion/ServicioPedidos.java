package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.ajustes.Ajustes;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.cobro.CalculadoraCuenta;
import co.elpatio.dominio.comanda.EstadoOrden;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.pedido.ClienteExterno;
import co.elpatio.dominio.pedido.EstadoPedido;
import co.elpatio.dominio.pedido.TipoPedido;
import co.elpatio.dominio.pedido.UbicacionEntrega;
import co.elpatio.dominio.pedido.ZonaDomicilio;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domicilios y para llevar: lo que entra desde fuera del salon.
 *
 * El pedido no es una entidad aparte, es una `Orden` con otro canal. Por eso
 * cocina lo ve igual que el de una mesa, la caja lo suma sin logica propia y el
 * INC se calcula con la misma regla. Lo unico exclusivo de este canal es el
 * recorrido de recepcion y el costo del envio.
 */
@Service
public class ServicioPedidos {

  /** Un celular colombiano tiene diez digitos y empieza por 3. */
  private static final int DIGITOS_TELEFONO = 10;

  private final Repositorios.DeOrdenes ordenes;
  private final Repositorios.DeCarta carta;
  private final Repositorios.DeZonasDomicilio zonas;
  private final Repositorios.DeAjustes ajustes;
  private final GeneradorIds ids;
  private final Reloj reloj;
  private final PublicadorEventos eventos;

  /** Donde queda el local, para medir que tan lejos pidio el cliente. */
  private final double latitudLocal;
  private final double longitudLocal;

  public ServicioPedidos(
      Repositorios.DeOrdenes ordenes,
      Repositorios.DeCarta carta,
      Repositorios.DeZonasDomicilio zonas,
      Repositorios.DeAjustes ajustes,
      GeneradorIds ids,
      Reloj reloj,
      PublicadorEventos eventos,
      @Value("${elpatio.local.latitud}") double latitudLocal,
      @Value("${elpatio.local.longitud}") double longitudLocal) {
    this.latitudLocal = latitudLocal;
    this.longitudLocal = longitudLocal;
    this.ordenes = ordenes;
    this.carta = carta;
    this.zonas = zonas;
    this.ajustes = ajustes;
    this.ids = ids;
    this.reloj = reloj;
    this.eventos = eventos;
  }

  // ---------------------------------------------------------------------------
  // Estado del canal
  // ---------------------------------------------------------------------------

  /**
   * Lo que el sitio publico necesita saber antes de dejar pedir: si el canal
   * esta abierto, a que zonas se lleva y cuanto cuesta cada una.
   *
   * Se consulta sin sesion porque lo mira el cliente desde la calle.
   */
  @Transactional(readOnly = true)
  public Dtos.EstadoCanal estadoCanal() {
    Ajustes actuales = ajustes.leer();
    boolean abierto = actuales.recibiendoPedidos(reloj.horaDe(reloj.ahora()));
    return new Dtos.EstadoCanal(
        abierto,
        actuales.isDomiciliosPausados(),
        actuales.getDomiciliosDesde(),
        actuales.getDomiciliosHasta(),
        zonas.listar().stream().filter(ZonaDomicilio::isActiva).toList());
  }

  @Transactional(readOnly = true)
  public List<ZonaDomicilio> listarZonas() {
    return zonas.listar();
  }

  @Transactional
  public ZonaDomicilio guardarZona(ZonaDomicilio zona) {
    if (zona.getId() == null || zona.getId().isBlank()) zona.setId(ids.nuevo("zd"));
    if (zona.getNombre() == null || zona.getNombre().isBlank()) {
      throw new ReglaDeNegocioError("La zona necesita un nombre que el cliente reconozca");
    }
    ZonaDomicilio guardada = zonas.guardar(zona);
    eventos.publicar(List.of("zonas", "ajustes"));
    return guardada;
  }

  @Transactional
  public void eliminarZona(String zonaId) {
    zonas.eliminar(zonaId);
    eventos.publicar(List.of("zonas", "ajustes"));
  }

  // ---------------------------------------------------------------------------
  // Entrada del pedido
  // ---------------------------------------------------------------------------

  /**
   * Crea un pedido hecho desde el sitio publico.
   *
   * Todas las validaciones se repiten aqui aunque el formulario ya las haya
   * hecho: el formulario protege al cliente de equivocarse, esto protege al
   * restaurante de que le entren pedidos imposibles. Un `fetch` a mano se salta
   * la pantalla, no el servidor.
   *
   * El pedido NO sale a cocina al entrar: se queda en `nuevo` esperando que
   * recepcion lo acepte. Mandarlo directo a la plancha significaria cocinar
   * algo que nadie confirmo que se puede entregar.
   */
  @Transactional
  public Dtos.PedidoCreado crearPedidoExterno(Dtos.NuevoPedidoExterno datos) {
    Ajustes actuales = ajustes.leer();

    if (!actuales.recibiendoPedidos(reloj.horaDe(reloj.ahora()))) {
      throw new ReglaDeNegocioError(
          actuales.isDomiciliosPausados()
              ? "Por ahora no estamos recibiendo pedidos. Inténtelo en un rato."
              : "Estamos fuera del horario de pedidos. Lo esperamos en nuestro horario de atención.");
    }

    TipoPedido tipo = datos.tipo();
    if (tipo == null || !tipo.esExterno()) {
      throw new ReglaDeNegocioError("El pedido tiene que ser a domicilio o para llevar");
    }

    String telefono = soloDigitos(datos.telefono());
    if (telefono.length() != DIGITOS_TELEFONO) {
      throw new ReglaDeNegocioError("El teléfono debe tener 10 dígitos para poder confirmarle");
    }
    if (datos.nombre() == null || datos.nombre().isBlank()) {
      throw new ReglaDeNegocioError("Necesitamos su nombre para entregarle el pedido");
    }
    if (datos.items() == null || datos.items().isEmpty()) {
      throw new ReglaDeNegocioError("El pedido está vacío");
    }

    ZonaDomicilio zona = null;
    long costoEnvio = 0;
    if (tipo == TipoPedido.DOMICILIO) {
      if (datos.direccion() == null || datos.direccion().isBlank()) {
        throw new ReglaDeNegocioError("Escriba la dirección para poder llevarle el pedido");
      }
      zona =
          zonas
              .porId(datos.zonaDomicilioId() == null ? "" : datos.zonaDomicilioId())
              .filter(ZonaDomicilio::isActiva)
              .orElseThrow(() -> new ReglaDeNegocioError("Todavía no llevamos a ese barrio"));
      costoEnvio = zona.getTarifa();
    }

    Orden orden = new Orden();
    orden.setId(ids.nuevo("ord"));
    orden.setTipo(tipo);
    orden.setEstadoPedido(EstadoPedido.NUEVO);
    orden.setNumero(ajustes.siguienteConsecutivo());
    orden.setComensales(1);
    orden.setAbiertaEn(reloj.ahora());
    orden.setRecibidoEn(reloj.ahora());
    orden.setCliente(
        new ClienteExterno(
            datos.nombre().trim(),
            telefono,
            datos.direccion() == null ? null : datos.direccion().trim(),
            datos.barrio() == null ? null : datos.barrio().trim()));
    orden.setZonaDomicilioId(zona == null ? null : zona.getId());
    orden.setCostoEnvio(costoEnvio);
    orden.setMetodoPagoPrevisto(datos.metodoPagoPrevisto());
    orden.setNotas(datos.notas());

    // La ubicacion solo tiene sentido en un domicilio, y solo si el cliente la
    // compartio. Que falte no invalida nada: la direccion escrita es la que
    // manda y el pedido entra igual.
    if (tipo == TipoPedido.DOMICILIO && datos.latitud() != null && datos.longitud() != null) {
      orden.setUbicacion(
          new UbicacionEntrega(datos.latitud(), datos.longitud(), datos.precisionMetros()));
    }
    orden.setMinutosEstimados(zona != null ? zona.getMinutosEstimados() : null);

    for (Dtos.NuevoItem nuevo : datos.items()) {
      ItemCarta item =
          carta
              .porId(nuevo.itemCartaId())
              .orElseThrow(() -> new NoEncontradoError("Uno de los productos ya no está en la carta"));
      orden.agregarItem(
          item,
          nuevo.cantidad(),
          nuevo.modificadoresSeleccionados(),
          nuevo.notaCocina(),
          ids.nuevo("io"));
    }

    // El monto minimo se comprueba contra el subtotal de comida, sin el envio:
    // cobrarle el domicilio para alcanzar el minimo seria hacer trampa.
    long subtotal = CalculadoraCuenta.subtotal(orden.getItems());
    if (zona != null && subtotal < zona.getMontoMinimo()) {
      throw new ReglaDeNegocioError(
          "El pedido mínimo para " + zona.getNombre() + " es de $" + conPuntos(zona.getMontoMinimo()));
    }

    Orden guardada = ordenes.guardar(orden);

    // Recepcion se entera al instante, sin recargar: es el punto del canal.
    eventos.publicar(List.of("pedidos", "ordenes"));

    return new Dtos.PedidoCreado(
        guardada.getId(),
        guardada.getNumero(),
        guardada.getMinutosEstimados(),
        CalculadoraCuenta.calcular(guardada, ajustes.leer().getPorcentajeInc()));
  }

  private static String soloDigitos(String texto) {
    return texto == null ? "" : texto.replaceAll("\\D", "");
  }

  /** Miles con punto, como se escribe el peso en Colombia: 45.000 */
  private static String conPuntos(long valor) {
    return String.format("%,d", valor).replace(',', '.');
  }

  // ---------------------------------------------------------------------------
  // Recepcion
  // ---------------------------------------------------------------------------

  /** Los pedidos que recepcion tiene entre manos, del mas antiguo al mas nuevo. */
  @Transactional(readOnly = true)
  public List<Dtos.PedidoEnRecepcion> listarPedidos(boolean incluirCerrados) {
    int porcentajeInc = ajustes.leer().getPorcentajeInc();
    List<ZonaDomicilio> todasLasZonas = zonas.listar();

    List<Orden> candidatas =
        incluirCerrados
            ? ordenes.abiertasDesde(reloj.inicioDelDia(reloj.hoy()))
            : ordenes.activas();

    List<Dtos.PedidoEnRecepcion> lista = new ArrayList<>();
    for (Orden orden : candidatas) {
      if (!orden.esExterno()) continue;
      if (!incluirCerrados && orden.getEstadoPedido().esFinal()) continue;

      String nombreZona =
          todasLasZonas.stream()
              .filter(z -> z.getId().equals(orden.getZonaDomicilioId()))
              .map(ZonaDomicilio::getNombre)
              .findFirst()
              .orElse(null);

      lista.add(
          new Dtos.PedidoEnRecepcion(
              orden,
              orden.etiquetaCanal(),
              nombreZona,
              CalculadoraCuenta.calcular(orden, porcentajeInc),
              orden.getUbicacion() == null
                  ? null
                  : orden.getUbicacion().metrosHasta(latitudLocal, longitudLocal)));
    }

    lista.sort(Comparator.comparing(p -> p.orden().inicioDeEspera()));
    return lista;
  }

  /**
   * Recepcion acepta el pedido y con eso entra a cocina.
   *
   * Los productos se mandan por el mismo camino que los de una mesa, asi que
   * cada uno cae en su destino segun lo que diga la carta: la cocina no tiene
   * una pantalla aparte para domicilios ni tiene que saber de donde vino.
   */
  @Transactional
  public Orden aceptar(String ordenId, int minutosEstimados, String recibidoPor) {
    Orden orden = exigirPedido(ordenId);
    orden.aceptar(minutosEstimados);
    orden.setMeseroId(recibidoPor);

    List<ItemOrden> pendientes = orden.itemsSinEnviar();
    if (!pendientes.isEmpty()) {
      orden.aplicarEnvio(
          pendientes.stream().map(ItemOrden::getId).toList(), orden.proximoTurno(), reloj.ahora());
    }

    Orden guardada = ordenes.guardar(orden);
    eventos.publicar(List.of("pedidos", "ordenes", "cocina"));
    return guardada;
  }

  /**
   * Corrige el tiempo prometido de un pedido que ya esta en cocina.
   *
   * No mueve el pedido de casilla ni vuelve a mandar nada a la plancha: lo unico
   * que cambia es la promesa. El aviso al cliente lo manda quien esta en
   * recepcion por WhatsApp, con el texto a la vista, igual que todo lo demas que
   * sale a nombre de la casa.
   */
  @Transactional
  public Orden cambiarTiempo(String ordenId, int minutosEstimados) {
    Orden orden = exigirPedido(ordenId);
    orden.cambiarTiempoEstimado(minutosEstimados);
    Orden guardada = ordenes.guardar(orden);
    eventos.publicar(List.of("pedidos", "ordenes"));
    return guardada;
  }

  @Transactional
  public Orden rechazar(String ordenId, String motivo) {
    Orden orden = exigirPedido(ordenId);
    orden.rechazar(motivo);
    Orden guardada = ordenes.guardar(orden);
    eventos.publicar(List.of("pedidos", "ordenes", "cocina"));
    return guardada;
  }

  @Transactional
  public Orden cancelar(String ordenId, String motivo) {
    Orden orden = exigirPedido(ordenId);
    orden.cancelar(motivo);
    Orden guardada = ordenes.guardar(orden);
    eventos.publicar(List.of("pedidos", "ordenes", "cocina"));
    return guardada;
  }

  @Transactional
  public Orden cambiarEstado(String ordenId, EstadoPedido siguiente) {
    Orden orden = exigirPedido(ordenId);
    orden.cambiarEstadoPedido(siguiente);
    Orden guardada = ordenes.guardar(orden);
    eventos.publicar(List.of("pedidos", "ordenes"));
    return guardada;
  }

  @Transactional
  public Orden despachar(String ordenId, String repartidor) {
    Orden orden = exigirPedido(ordenId);
    orden.despachar(repartidor);
    Orden guardada = ordenes.guardar(orden);
    eventos.publicar(List.of("pedidos", "ordenes"));
    return guardada;
  }

  /**
   * Cierra el pedido como entregado.
   *
   * Si nadie lo cobro por caja, se marca la comanda como servida y no como
   * pagada: el dinero de un domicilio entra cuando el repartidor lo liquida, y
   * darlo por cobrado aqui descuadraria la caja del turno.
   */
  @Transactional
  public Orden entregar(String ordenId) {
    Orden orden = exigirPedido(ordenId);
    orden.cambiarEstadoPedido(EstadoPedido.ENTREGADO);
    if (orden.getEstado() != EstadoOrden.PAGADA) orden.setEstado(EstadoOrden.SERVIDA);
    Orden guardada = ordenes.guardar(orden);
    eventos.publicar(List.of("pedidos", "ordenes"));
    return guardada;
  }

  private Orden exigirPedido(String ordenId) {
    Orden orden =
        ordenes.porId(ordenId).orElseThrow(() -> new NoEncontradoError("El pedido ya no existe"));
    if (!orden.esExterno()) throw new ReglaDeNegocioError("Esa comanda es de mesa, no de recepción");
    return orden;
  }
}
