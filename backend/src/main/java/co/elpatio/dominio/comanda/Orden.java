package co.elpatio.dominio.comanda;

import co.elpatio.dominio.carta.Destino;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.cobro.MetodoPago;
import co.elpatio.dominio.pedido.ClienteExterno;
import co.elpatio.dominio.pedido.EstadoPedido;
import co.elpatio.dominio.pedido.TipoPedido;
import co.elpatio.dominio.pedido.UbicacionEntrega;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * La comanda de una mesa, con sus productos y sus cargos.
 *
 * Aqui vive el comportamiento que en el prototipo estaba repartido entre
 * mockApi.ts y calculos.ts. Se concentro en el agregado porque son reglas de
 * sala, no de transporte: un controlador no deberia saber que un plato ya
 * enviado no se puede borrar, solo que la operacion fue rechazada.
 */
public class Orden {
  private String id;
  private String mesaId;
  private String meseroId;
  /** Consecutivo del dia. */
  private int numero;
  private EstadoOrden estado = EstadoOrden.ABIERTA;
  private List<ItemOrden> items = new ArrayList<>();
  private List<CargoAdicional> cargosAdicionales = new ArrayList<>();
  private int comensales;
  private Instant abiertaEn;
  private Instant cerradaEn;
  private String notas;
  /**
   * Motivo por el que se anulo. Una comanda ya cobrada no se edita: se anula
   * dejando el motivo escrito y se abre una nueva, para que la anulacion sea
   * auditable y el cierre de caja siga cuadrando.
   */
  private String motivoAnulacion;
  /** Comanda que reemplaza a esta cuando se anulo una ya cobrada. */
  private String ordenReemplazoId;

  // ---------------------------------------------------------------------------
  // Canal
  // ---------------------------------------------------------------------------

  /**
   * Por donde entro la venta. Las comandas de mesa, que son todas las que
   * existian antes del canal externo, se quedan en MESA sin tocar nada.
   */
  private TipoPedido tipo = TipoPedido.MESA;

  /** Solo lo llevan los pedidos externos: es el recorrido que ve recepcion. */
  private EstadoPedido estadoPedido;

  private ClienteExterno cliente;

  /**
   * Donde hay que llevarlo, en coordenadas. Opcional: el cliente puede negar el
   * permiso y el pedido entra igual. La direccion escrita es la que manda.
   */
  private UbicacionEntrega ubicacion;

  private String zonaDomicilioId;

  /**
   * Lo que cuesta llevarlo. Va como linea aparte despues del impuesto: el
   * envio no es consumo, asi que no causa INC ni entra en la base de la propina.
   */
  private long costoEnvio;

  /** Lo que el cliente dijo que pensaba pagar. El cobro real vive en `Pago`. */
  private MetodoPago metodoPagoPrevisto;

  private Integer minutosEstimados;
  private String repartidor;
  private String motivoRechazo;

  /**
   * Cuando entro el pedido. El cronometro de recepcion cuenta desde aqui y no
   * desde `abiertaEn`: para el cliente el reloj arranco cuando pidio.
   */
  private Instant recibidoEn;

  public Orden() {}

  // -------------------------------------------------------------------------
  // Guardas
  // -------------------------------------------------------------------------

  /**
   * Toda modificacion pasa por aqui. Una comanda cobrada o anulada es un
   * documento cerrado: si el cajero se equivoco, se anula con motivo y se
   * genera otra, nunca se reescribe la que el cliente ya pago.
   */
  public void exigirEditable() {
    if (estado == EstadoOrden.PAGADA) {
      throw new ReglaDeNegocioError(
          "Esta cuenta ya fue cobrada: anúlela con motivo y genere una nueva");
    }
    if (estado == EstadoOrden.ANULADA) {
      throw new ReglaDeNegocioError("Esta comanda está anulada");
    }
  }

  // -------------------------------------------------------------------------
  // Productos
  // -------------------------------------------------------------------------

  /**
   * Agrega un producto de la carta. Un mismo plato sin modificadores ni nota se
   * acumula en una sola linea mientras no se haya enviado, para que la comanda
   * no se llene de renglones repetidos.
   */
  public ItemOrden agregarItem(
      ItemCarta carta,
      int cantidad,
      List<ModificadorSeleccionado> modificadores,
      String notaCocina,
      String nuevoId) {
    exigirEditable();
    if (!carta.isDisponible()) throw new ReglaDeNegocioError(carta.getNombre() + " está agotado");

    List<ModificadorSeleccionado> seleccion = modificadores != null ? modificadores : List.of();

    for (ItemOrden existente : items) {
      boolean acumulable =
          existente.getItemCartaId().equals(carta.getId())
              && existente.getTurnoEnvio() == 0
              && existente.estaVigente()
              && Objects.equals(textoNota(existente.getNotaCocina()), textoNota(notaCocina))
              && existente.getModificadoresSeleccionados().equals(seleccion);
      if (acumulable) {
        existente.setCantidad(existente.getCantidad() + cantidad);
        return existente;
      }
    }

    ItemOrden item = new ItemOrden();
    item.setId(nuevoId);
    item.setItemCartaId(carta.getId());
    item.setNombre(carta.getNombre());
    item.setPrecioUnitario(carta.getPrecio());
    item.setCantidad(cantidad);
    item.setModificadoresSeleccionados(new ArrayList<>(seleccion));
    item.setNotaCocina(notaCocina);
    item.setEstado(EstadoItem.PENDIENTE);
    item.setDestino(carta.getDestino());
    item.setTurnoEnvio(0);
    items.add(item);
    return item;
  }

  private static String textoNota(String nota) {
    return nota == null ? "" : nota;
  }

  public ItemOrden buscarItem(String itemId) {
    return items.stream()
        .filter(i -> i.getId().equals(itemId))
        .findFirst()
        .orElseThrow(() -> new ReglaDeNegocioError("El producto ya no está en la comanda"));
  }

  /**
   * Cambiar la cantidad solo tiene sentido antes del envio: despues el plato ya
   * esta en la plancha y lo unico honesto es anularlo dejando el motivo.
   */
  public void cambiarCantidad(String itemId, int cantidad) {
    exigirEditable();
    ItemOrden item = buscarItem(itemId);
    if (item.fueEnviado()) {
      throw new ReglaDeNegocioError("Ese producto ya se envió: pídele anulación al administrador");
    }
    if (cantidad <= 0) items.removeIf(i -> i.getId().equals(itemId));
    else item.setCantidad(cantidad);
    sincronizarEstado();
  }

  /** Anula un producto ya enviado. Queda registrado, nunca desaparece de la comanda. */
  public void anularItem(String itemId, String motivo) {
    exigirEditable();
    ItemOrden item = buscarItem(itemId);
    item.setEstado(EstadoItem.ANULADO);
    String anotacion = "Anulado: " + motivo;
    item.setNotaCocina(
        item.getNotaCocina() == null || item.getNotaCocina().isBlank()
            ? anotacion
            : item.getNotaCocina() + " · " + anotacion);
    sincronizarEstado();
  }

  // -------------------------------------------------------------------------
  // Turnos de envio
  // -------------------------------------------------------------------------

  /** Productos que el mesero todavia no ha mandado a cocina o barra. */
  public List<ItemOrden> itemsSinEnviar() {
    return items.stream().filter(i -> i.getTurnoEnvio() == 0 && i.estaVigente()).toList();
  }

  /** Numero del proximo turno de envio de la mesa. */
  public int proximoTurno() {
    return items.stream().mapToInt(ItemOrden::getTurnoEnvio).max().orElse(0) + 1;
  }

  /** Marca como enviados los productos indicados y devuelve cuantos salieron. */
  public int aplicarEnvio(List<String> itemIds, int turno, Instant ahora) {
    exigirEditable();
    int enviados = 0;
    for (ItemOrden item : items) {
      if (!itemIds.contains(item.getId()) || item.fueEnviado() || !item.estaVigente()) continue;
      item.setTurnoEnvio(turno);
      item.setEnviadoEn(ahora);
      item.setEstado(EstadoItem.PENDIENTE);
      enviados++;
    }
    sincronizarEstado();
    return enviados;
  }

  /** Un turno de envio con sus productos. */
  public record GrupoTurno(int turno, List<ItemOrden> items) {}

  /** Agrupa los productos ya enviados por turno, en orden de envio. */
  public List<GrupoTurno> agruparPorTurno(List<ItemOrden> deInteres) {
    Map<Integer, List<ItemOrden>> mapa = new LinkedHashMap<>();
    for (ItemOrden item : deInteres) {
      if (item.getTurnoEnvio() == 0) continue;
      mapa.computeIfAbsent(item.getTurnoEnvio(), t -> new ArrayList<>()).add(item);
    }
    return mapa.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> new GrupoTurno(e.getKey(), e.getValue()))
        .toList();
  }

  /** Productos vigentes de un destino que ya salieron a produccion. */
  public List<ItemOrden> itemsEnviadosDe(Destino destino) {
    return items.stream()
        .filter(i -> i.getDestino() == destino && i.fueEnviado() && i.estaVigente())
        .toList();
  }

  public void cambiarEstadoTurno(int turno, Destino destino, EstadoItem estado, Instant ahora) {
    exigirEditable();
    for (ItemOrden item : items) {
      if (item.getTurnoEnvio() != turno || item.getDestino() != destino || !item.estaVigente()) continue;
      item.setEstado(estado);
      if (estado == EstadoItem.LISTO) item.setListoEn(ahora);
    }
    sincronizarEstado();
  }

  public void cambiarEstadoItem(String itemId, EstadoItem estado, Instant ahora) {
    exigirEditable();
    ItemOrden item = buscarItem(itemId);
    item.setEstado(estado);
    if (estado == EstadoItem.LISTO) item.setListoEn(ahora);
    sincronizarEstado();
  }

  /**
   * Recalcula el estado de la comanda a partir del estado de sus productos.
   * `cuenta_pedida`, `pagada` y `anulada` los fija una accion explicita del
   * cajero y no pueden retroceder porque cocina toque un plato.
   */
  public void sincronizarEstado() {
    if (estado == EstadoOrden.PAGADA
        || estado == EstadoOrden.ANULADA
        || estado == EstadoOrden.CUENTA_PEDIDA) return;

    List<ItemOrden> enviados = items.stream().filter(i -> i.fueEnviado() && i.estaVigente()).toList();
    if (enviados.isEmpty()) {
      estado = EstadoOrden.ABIERTA;
      return;
    }
    if (enviados.stream().allMatch(i -> i.getEstado() == EstadoItem.SERVIDO)) {
      estado = EstadoOrden.SERVIDA;
    } else if (enviados.stream()
        .anyMatch(i -> i.getEstado() == EstadoItem.EN_PREPARACION || i.getEstado() == EstadoItem.LISTO)) {
      estado = EstadoOrden.EN_PREPARACION;
    } else {
      estado = EstadoOrden.ENVIADA;
    }
  }

  // -------------------------------------------------------------------------
  // Cargos
  // -------------------------------------------------------------------------

  public CargoAdicional agregarCargo(
      String id, String nombre, long valor, String agregadoPor, Instant ahora) {
    exigirEditable();
    if (nombre == null || nombre.isBlank()) {
      throw new ReglaDeNegocioError("El cargo necesita un nombre visible para el cliente");
    }
    CargoAdicional cargo = new CargoAdicional(id, nombre.trim(), valor, agregadoPor, ahora);
    cargosAdicionales.add(cargo);
    return cargo;
  }

  public void quitarCargo(String cargoId) {
    exigirEditable();
    cargosAdicionales.removeIf(c -> c.getId().equals(cargoId));
  }

  // -------------------------------------------------------------------------
  // Cierre
  // -------------------------------------------------------------------------

  public void marcarCuentaPedida() {
    exigirEditable();
    estado = EstadoOrden.CUENTA_PEDIDA;
  }

  public void marcarPagada(Instant cuando) {
    if (estado == EstadoOrden.PAGADA) throw new ReglaDeNegocioError("Esta cuenta ya fue cobrada");
    if (estado == EstadoOrden.ANULADA) throw new ReglaDeNegocioError("Esta comanda está anulada");
    estado = EstadoOrden.PAGADA;
    cerradaEn = cuando;
    for (ItemOrden item : items) if (item.estaVigente()) item.setEstado(EstadoItem.SERVIDO);
  }

  /** Anula la comanda dejando escrito el porque. Es irreversible a proposito. */
  public void anular(String motivo, Instant cuando) {
    if (motivo == null || motivo.isBlank()) {
      throw new ReglaDeNegocioError("Una anulación sin motivo no se puede auditar");
    }
    if (estado == EstadoOrden.ANULADA) throw new ReglaDeNegocioError("Esta comanda ya está anulada");
    estado = EstadoOrden.ANULADA;
    motivoAnulacion = motivo.trim();
    cerradaEn = cuando;
  }

  public List<ItemOrden> itemsVigentes() {
    return items.stream().filter(ItemOrden::estaVigente).toList();
  }

  /** El envio mas antiguo todavia sin servir, para el cronometro de cocina. */
  public Instant esperaMasAntigua() {
    return items.stream()
        .filter(
            i ->
                i.fueEnviado()
                    && (i.getEstado() == EstadoItem.PENDIENTE
                        || i.getEstado() == EstadoItem.EN_PREPARACION))
        .map(i -> i.getEnviadoEn() != null ? i.getEnviadoEn() : abiertaEn)
        .min(Comparator.naturalOrder())
        .orElse(null);
  }

  // -------------------------------------------------------------------------
  // Recorrido del pedido externo
  // -------------------------------------------------------------------------

  public boolean esExterno() {
    return tipo != null && tipo.esExterno();
  }

  private void exigirExterno() {
    if (!esExterno()) throw new ReglaDeNegocioError("Esta comanda es de mesa, no de recepción");
  }

  /** Como se nombra el pedido en cocina y en el comprobante. */
  public String etiquetaCanal() {
    return switch (tipo) {
      case DOMICILIO -> "Domicilio #" + numero;
      case LLEVAR -> "Para llevar #" + numero;
      case MESA -> "Mesa";
    };
  }

  /**
   * Mueve el pedido al siguiente estado.
   *
   * El recorrido no se puede saltar ni devolver. Sin esa guarda, un pedido
   * podria quedar entregado sin haber pasado por despachado, y entonces nadie
   * sabria quien lo llevo cuando el cliente llame a reclamar.
   */
  public void cambiarEstadoPedido(EstadoPedido siguiente) {
    exigirExterno();
    if (!estadoPedido.puedePasarA(siguiente)) {
      throw new ReglaDeNegocioError(
          "Un pedido " + estadoPedido.codigo() + " no puede pasar a " + siguiente.codigo());
    }
    estadoPedido = siguiente;
  }

  /**
   * Recepcion acepta el pedido y fija el tiempo que le promete al cliente.
   *
   * El tiempo estimado se guarda al aceptar y no al recibir, porque quien
   * conoce la carga real de la cocina en ese momento es la persona que esta
   * mirando la pantalla, no una tabla de tiempos por zona.
   */
  public void aceptar(int minutos) {
    exigirExterno();
    if (minutos <= 0) throw new ReglaDeNegocioError("El tiempo estimado tiene que ser mayor que cero");
    cambiarEstadoPedido(EstadoPedido.ACEPTADO);
    minutosEstimados = minutos;
  }

  /** Rechaza el pedido dejando escrito el porque: el cliente merece una razon. */
  public void rechazar(String motivo) {
    exigirExterno();
    if (motivo == null || motivo.isBlank()) {
      throw new ReglaDeNegocioError("Dígale al cliente por qué no se le puede atender");
    }
    cambiarEstadoPedido(EstadoPedido.RECHAZADO);
    motivoRechazo = motivo.trim();
    // Un pedido rechazado no puede seguir contando como venta abierta.
    estado = EstadoOrden.ANULADA;
    motivoAnulacion = "Pedido rechazado: " + motivoRechazo;
  }

  public void cancelar(String motivo) {
    exigirExterno();
    cambiarEstadoPedido(EstadoPedido.CANCELADO);
    motivoRechazo = motivo == null || motivo.isBlank() ? "Cancelado" : motivo.trim();
    estado = EstadoOrden.ANULADA;
    motivoAnulacion = "Pedido cancelado: " + motivoRechazo;
  }

  /**
   * Sale del local. En domicilio se exige quien lo lleva: si el cliente llama
   * porque no le ha llegado, alguien tiene que poder decir con quien salio.
   */
  public void despachar(String quienLoLleva) {
    exigirExterno();
    if (tipo == TipoPedido.DOMICILIO && (quienLoLleva == null || quienLoLleva.isBlank())) {
      throw new ReglaDeNegocioError("Anote quién lleva el domicilio antes de despacharlo");
    }
    cambiarEstadoPedido(EstadoPedido.DESPACHADO);
    repartidor = quienLoLleva == null || quienLoLleva.isBlank() ? null : quienLoLleva.trim();
  }

  /** Minutos que lleva esperando el cliente desde que hizo el pedido. */
  public Instant inicioDeEspera() {
    return recibidoEn != null ? recibidoEn : abiertaEn;
  }

  // -------------------------------------------------------------------------

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getMesaId() { return mesaId; }
  public void setMesaId(String mesaId) { this.mesaId = mesaId; }
  public String getMeseroId() { return meseroId; }
  public void setMeseroId(String meseroId) { this.meseroId = meseroId; }
  public int getNumero() { return numero; }
  public void setNumero(int numero) { this.numero = numero; }
  public EstadoOrden getEstado() { return estado; }
  public void setEstado(EstadoOrden estado) { this.estado = estado; }
  public List<ItemOrden> getItems() { return items; }
  public void setItems(List<ItemOrden> items) { this.items = items != null ? items : new ArrayList<>(); }
  public List<CargoAdicional> getCargosAdicionales() { return cargosAdicionales; }
  public void setCargosAdicionales(List<CargoAdicional> valor) {
    this.cargosAdicionales = valor != null ? valor : new ArrayList<>();
  }
  public int getComensales() { return comensales; }
  public void setComensales(int comensales) { this.comensales = comensales; }
  public Instant getAbiertaEn() { return abiertaEn; }
  public void setAbiertaEn(Instant abiertaEn) { this.abiertaEn = abiertaEn; }
  public Instant getCerradaEn() { return cerradaEn; }
  public void setCerradaEn(Instant cerradaEn) { this.cerradaEn = cerradaEn; }
  public String getNotas() { return notas; }
  public void setNotas(String notas) { this.notas = notas; }
  public String getMotivoAnulacion() { return motivoAnulacion; }
  public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }
  public String getOrdenReemplazoId() { return ordenReemplazoId; }
  public void setOrdenReemplazoId(String ordenReemplazoId) { this.ordenReemplazoId = ordenReemplazoId; }

  public TipoPedido getTipo() { return tipo; }
  public void setTipo(TipoPedido tipo) { this.tipo = tipo != null ? tipo : TipoPedido.MESA; }
  public EstadoPedido getEstadoPedido() { return estadoPedido; }
  public void setEstadoPedido(EstadoPedido estadoPedido) { this.estadoPedido = estadoPedido; }
  public ClienteExterno getCliente() { return cliente; }
  public void setCliente(ClienteExterno cliente) { this.cliente = cliente; }
  public UbicacionEntrega getUbicacion() { return ubicacion; }
  public void setUbicacion(UbicacionEntrega ubicacion) { this.ubicacion = ubicacion; }
  public String getZonaDomicilioId() { return zonaDomicilioId; }
  public void setZonaDomicilioId(String zonaDomicilioId) { this.zonaDomicilioId = zonaDomicilioId; }
  public long getCostoEnvio() { return costoEnvio; }
  public void setCostoEnvio(long costoEnvio) { this.costoEnvio = costoEnvio; }
  public MetodoPago getMetodoPagoPrevisto() { return metodoPagoPrevisto; }
  public void setMetodoPagoPrevisto(MetodoPago valor) { this.metodoPagoPrevisto = valor; }
  public Integer getMinutosEstimados() { return minutosEstimados; }
  public void setMinutosEstimados(Integer minutosEstimados) { this.minutosEstimados = minutosEstimados; }
  public String getRepartidor() { return repartidor; }
  public void setRepartidor(String repartidor) { this.repartidor = repartidor; }
  public String getMotivoRechazo() { return motivoRechazo; }
  public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }
  public Instant getRecibidoEn() { return recibidoEn; }
  public void setRecibidoEn(Instant recibidoEn) { this.recibidoEn = recibidoEn; }
}
