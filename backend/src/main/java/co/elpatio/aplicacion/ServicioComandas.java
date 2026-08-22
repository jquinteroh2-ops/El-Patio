package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.comanda.CargoAdicional;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.salon.EstadoMesa;
import co.elpatio.dominio.salon.Mesa;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todo lo que le pasa a una comanda entre que se abre la mesa y se pide la cuenta.
 *
 * El servicio orquesta y persiste; las reglas viven en el agregado `Orden`. Si
 * una regla nueva se cuela aqui, es senal de que le faltaba sitio en el dominio.
 */
@Service
public class ServicioComandas {

  private final Repositorios.DeOrdenes ordenes;
  private final Repositorios.DeMesas mesas;
  private final Repositorios.DeCarta carta;
  private final Repositorios.DeUsuarios usuarios;
  private final Repositorios.DeAjustes ajustes;
  private final GeneradorIds ids;
  private final Reloj reloj;
  private final PublicadorEventos eventos;

  public ServicioComandas(
      Repositorios.DeOrdenes ordenes,
      Repositorios.DeMesas mesas,
      Repositorios.DeCarta carta,
      Repositorios.DeUsuarios usuarios,
      Repositorios.DeAjustes ajustes,
      GeneradorIds ids,
      Reloj reloj,
      PublicadorEventos eventos) {
    this.ordenes = ordenes;
    this.mesas = mesas;
    this.carta = carta;
    this.usuarios = usuarios;
    this.ajustes = ajustes;
    this.ids = ids;
    this.reloj = reloj;
    this.eventos = eventos;
  }

  // ---------------------------------------------------------------------------
  // Lectura
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public Dtos.OrdenDetallada obtenerOrdenDeMesa(String mesaId) {
    Mesa mesa = exigirMesa(mesaId);
    if (mesa.getOrdenActivaId() == null) return null;

    Orden orden = ordenes.porId(mesa.getOrdenActivaId()).orElse(null);
    if (orden == null) return null;

    return new Dtos.OrdenDetallada(
        orden,
        Dtos.MesaDto.de(mesa),
        usuarios.porId(orden.getMeseroId()).map(u -> u.getNombre()).orElse("Sin asignar"),
        ajustes.leer().getPorcentajeInc());
  }

  @Transactional(readOnly = true)
  public Orden obtenerOrden(String ordenId) {
    return exigirOrden(ordenId);
  }

  // ---------------------------------------------------------------------------
  // Apertura
  // ---------------------------------------------------------------------------

  /**
   * Abre la cuenta de una mesa.
   *
   * El consecutivo se pide dentro de esta transaccion, con la fila de ajustes
   * bloqueada: si dos meseros abren mesa en el mismo segundo, uno espera al
   * otro y ninguno recibe un numero repetido.
   */
  @Transactional
  public Orden abrirMesa(String mesaId, String meseroId, int comensales) {
    Mesa mesa = exigirMesa(mesaId);
    if (mesa.getOrdenActivaId() != null) {
      throw new ReglaDeNegocioError("La mesa ya tiene una cuenta abierta");
    }
    if (usuarios.porId(meseroId).isEmpty()) {
      throw new NoEncontradoError("El mesero no existe");
    }

    Orden orden = new Orden();
    orden.setId(ids.nuevo("ord"));
    orden.setMesaId(mesaId);
    orden.setMeseroId(meseroId);
    orden.setNumero(ajustes.siguienteConsecutivo());
    orden.setComensales(Math.max(1, comensales));
    orden.setAbiertaEn(reloj.ahora());

    Orden guardada = ordenes.guardar(orden);
    mesa.ocupar(meseroId, guardada.getId());
    mesas.guardar(mesa);

    eventos.publicar(List.of("ordenes", "mesas"));
    return guardada;
  }

  // ---------------------------------------------------------------------------
  // Productos
  // ---------------------------------------------------------------------------

  @Transactional
  public void cambiarComensales(String ordenId, int comensales) {
    Orden orden = exigirOrden(ordenId);
    orden.exigirEditable();
    orden.setComensales(Math.max(1, comensales));
    ordenes.guardar(orden);
    eventos.publicar(List.of("ordenes"));
  }

  @Transactional
  public Orden agregarItems(String ordenId, List<Dtos.NuevoItem> nuevos) {
    Orden orden = exigirOrden(ordenId);

    for (Dtos.NuevoItem nuevo : nuevos) {
      ItemCarta item =
          carta
              .porId(nuevo.itemCartaId())
              .orElseThrow(() -> new NoEncontradoError("El producto no existe en la carta"));
      orden.agregarItem(
          item,
          nuevo.cantidad(),
          nuevo.modificadoresSeleccionados(),
          nuevo.notaCocina(),
          ids.nuevo("io"),
          reloj.hoy());
    }

    Orden guardada = ordenes.guardar(orden);
    eventos.publicar(List.of("ordenes", "mesas"));
    return guardada;
  }

  @Transactional
  public void cambiarCantidad(String ordenId, String itemId, int cantidad) {
    Orden orden = exigirOrden(ordenId);
    orden.cambiarCantidad(itemId, cantidad);
    ordenes.guardar(orden);
    eventos.publicar(List.of("ordenes", "mesas"));
  }

  @Transactional
  public void anularItem(String ordenId, String itemId, String motivo) {
    if (motivo == null || motivo.isBlank()) {
      throw new ReglaDeNegocioError("Una anulación sin motivo no se puede auditar");
    }
    Orden orden = exigirOrden(ordenId);
    orden.anularItem(itemId, motivo.trim());
    ordenes.guardar(orden);
    eventos.publicar(List.of("ordenes", "mesas", "cocina"));
  }

  // ---------------------------------------------------------------------------
  // Envio a produccion
  // ---------------------------------------------------------------------------

  /**
   * Manda a cocina y barra lo que todavia no se ha enviado.
   *
   * La cola por falta de senal vive en el dispositivo, que es donde se pierde
   * el WiFi: si esta llamada respondio, es porque el servidor recibio, y
   * `encolado` sale siempre en false.
   *
   * `itemIds` y `turno` son opcionales y solo los manda la comandera cuando
   * esta vaciando su cola: sirven para reponer un envio exactamente como se
   * dicto, sin arrastrar productos que el mesero agrego despues de que se
   * cayera la senal. Sin ellos se envia todo lo pendiente, que es el caso
   * normal.
   *
   * El conflicto lo resuelve el servidor, no el dispositivo: `aplicarEnvio`
   * ignora los productos que ya salieron, asi que reponer un envio dos veces no
   * duplica nada, y si la comanda se cerro entre tanto la guarda del agregado
   * rechaza la operacion.
   */
  @Transactional
  public Dtos.ResultadoEnvio enviarACocina(String ordenId, List<String> itemIds, Integer turnoPedido) {
    Orden orden = exigirOrden(ordenId);

    List<ItemOrden> pendientes = orden.itemsSinEnviar();
    if (itemIds != null && !itemIds.isEmpty()) {
      pendientes = pendientes.stream().filter(i -> itemIds.contains(i.getId())).toList();
    }

    if (pendientes.isEmpty()) {
      throw new ReglaDeNegocioError("No hay productos nuevos para enviar");
    }

    // El turno que pide el dispositivo nunca puede pisar uno ya usado en la
    // mesa: si mientras estuvo sin senal alguien mando otro turno, este entra
    // detras y no encima.
    int turno = Math.max(orden.proximoTurno(), turnoPedido != null ? turnoPedido : 0);
    int enviados =
        orden.aplicarEnvio(pendientes.stream().map(ItemOrden::getId).toList(), turno, reloj.ahora());
    ordenes.guardar(orden);

    eventos.publicar(List.of("ordenes", "mesas", "cocina"));
    return new Dtos.ResultadoEnvio(false, turno, enviados);
  }

  // ---------------------------------------------------------------------------
  // Cargos y notas
  // ---------------------------------------------------------------------------

  @Transactional
  public CargoAdicional agregarCargo(String ordenId, String nombre, long valor, String agregadoPor) {
    Orden orden = exigirOrden(ordenId);
    CargoAdicional cargo =
        orden.agregarCargo(ids.nuevo("cg"), nombre, Math.round((double) valor), agregadoPor, reloj.ahora());
    ordenes.guardar(orden);
    eventos.publicar(List.of("ordenes", "mesas"));
    return cargo;
  }

  @Transactional
  public void quitarCargo(String ordenId, String cargoId) {
    Orden orden = exigirOrden(ordenId);
    orden.quitarCargo(cargoId);
    ordenes.guardar(orden);
    eventos.publicar(List.of("ordenes", "mesas"));
  }

  @Transactional
  public void agregarNota(String ordenId, String notas) {
    Orden orden = exigirOrden(ordenId);
    orden.exigirEditable();
    orden.setNotas(notas);
    ordenes.guardar(orden);
    eventos.publicar(List.of("ordenes", "cocina"));
  }

  // ---------------------------------------------------------------------------
  // Movimientos de mesa
  // ---------------------------------------------------------------------------

  @Transactional
  public void pedirCuenta(String ordenId) {
    Orden orden = exigirOrden(ordenId);
    orden.marcarCuentaPedida();
    ordenes.guardar(orden);

    Mesa mesa = exigirMesa(orden.getMesaId());
    mesa.setEstado(EstadoMesa.CUENTA_PEDIDA);
    mesas.guardar(mesa);

    eventos.publicar(List.of("ordenes", "mesas"));
  }

  @Transactional
  public void trasladarMesa(String ordenId, String mesaDestinoId) {
    Orden orden = exigirOrden(ordenId);
    orden.exigirEditable();

    Mesa destino = exigirMesa(mesaDestinoId);
    if (destino.getOrdenActivaId() != null) {
      throw new ReglaDeNegocioError("La mesa de destino ya está ocupada");
    }
    Mesa origen = exigirMesa(orden.getMesaId());

    destino.setEstado(origen.getEstado());
    destino.setMeseroId(origen.getMeseroId());
    destino.setOrdenActivaId(orden.getId());
    origen.liberar();

    orden.setMesaId(mesaDestinoId);

    // La comanda se guarda antes que las mesas para que la clave foranea de
    // `orden_activa_id` en el destino apunte a una fila que ya tiene la mesa
    // nueva escrita.
    ordenes.guardar(orden);
    mesas.guardar(origen);
    mesas.guardar(destino);

    eventos.publicar(List.of("ordenes", "mesas", "cocina"));
  }

  // ---------------------------------------------------------------------------
  // Anulacion
  // ---------------------------------------------------------------------------

  /**
   * Anula una comanda y, si ya estaba cobrada, abre su reemplazo.
   *
   * Es la unica forma de corregir una cuenta cerrada. El pago original no se
   * borra: queda apuntando a una comanda anulada con su motivo, y el cierre de
   * caja lo sigue viendo. Corregir borrando dejaria la caja cuadrando contra
   * una historia que ya nadie puede reconstruir.
   */
  @Transactional
  public Orden anularOrden(String ordenId, String motivo, String meseroId) {
    Orden original = exigirOrden(ordenId);
    boolean estabaCobrada = original.getEstado() == co.elpatio.dominio.comanda.EstadoOrden.PAGADA;

    original.anular(motivo, reloj.ahora());

    Orden reemplazo = null;
    if (estabaCobrada) {
      reemplazo = new Orden();
      reemplazo.setId(ids.nuevo("ord"));
      reemplazo.setMesaId(original.getMesaId());
      reemplazo.setMeseroId(meseroId != null ? meseroId : original.getMeseroId());
      reemplazo.setNumero(ajustes.siguienteConsecutivo());
      reemplazo.setComensales(original.getComensales());
      reemplazo.setAbiertaEn(reloj.ahora());
      reemplazo.setNotas("Reemplaza la comanda " + original.getNumero() + ": " + motivo.trim());
      reemplazo = ordenes.guardar(reemplazo);
      original.setOrdenReemplazoId(reemplazo.getId());
    }

    ordenes.guardar(original);

    Mesa mesa = exigirMesa(original.getMesaId());
    if (reemplazo != null) {
      mesa.setEstado(EstadoMesa.OCUPADA);
      mesa.setMeseroId(reemplazo.getMeseroId());
      mesa.setOrdenActivaId(reemplazo.getId());
    } else if (original.getId().equals(mesa.getOrdenActivaId())) {
      mesa.liberar();
    }
    mesas.guardar(mesa);

    eventos.publicar(List.of("ordenes", "mesas", "cocina"));
    return reemplazo != null ? reemplazo : original;
  }

  // ---------------------------------------------------------------------------

  private Orden exigirOrden(String ordenId) {
    return ordenes.porId(ordenId).orElseThrow(() -> new NoEncontradoError("La comanda ya no existe"));
  }

  private Mesa exigirMesa(String mesaId) {
    return mesas.porId(mesaId).orElseThrow(() -> new NoEncontradoError("La mesa no existe"));
  }
}
