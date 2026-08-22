package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.caja.CierreCaja;
import co.elpatio.dominio.caja.Turno;
import co.elpatio.dominio.cobro.MetodoPago;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.EstadoOrden;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.ModificadorSeleccionado;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.pedido.TipoPedido;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.salon.Mesa;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores, cierre de caja, reportes y alertas.
 *
 * Todas las agregaciones se hacen en memoria sobre el rango consultado, igual
 * que en el prototipo. Es suficiente para un restaurante de dieciocho mesas: el
 * historico de un mes cabe holgado y evita repartir la logica de negocio entre
 * Java y consultas SQL, donde seria mas facil que las dos versiones se
 * desalinearan sin que nadie lo note.
 */
@Service
public class ServicioAdministracion {

  private static final DateTimeFormatter CLAVE_DIA = DateTimeFormatter.ISO_LOCAL_DATE;

  private final Repositorios.DeOrdenes ordenes;
  private final Repositorios.DePagos pagos;
  private final Repositorios.DeMesas mesas;
  private final Repositorios.DeUsuarios usuarios;
  private final Repositorios.DeCierres cierres;
  // La tarifa de INC del turno sale de aqui: es configuracion del
  // establecimiento, no un numero fijo del sistema.
  private final Repositorios.DeAjustes ajustes;
  private final GeneradorIds ids;
  private final Reloj reloj;
  private final PublicadorEventos eventos;

  public ServicioAdministracion(
      Repositorios.DeOrdenes ordenes,
      Repositorios.DePagos pagos,
      Repositorios.DeMesas mesas,
      Repositorios.DeUsuarios usuarios,
      Repositorios.DeCierres cierres,
      GeneradorIds ids,
      Reloj reloj,
      PublicadorEventos eventos,
      Repositorios.DeAjustes ajustes) {
    this.ordenes = ordenes;
    this.pagos = pagos;
    this.mesas = mesas;
    this.usuarios = usuarios;
    this.cierres = cierres;
    this.ajustes = ajustes;
    this.ids = ids;
    this.reloj = reloj;
    this.eventos = eventos;
  }

  // ---------------------------------------------------------------------------
  // Indicadores del dia
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public Dtos.IndicadoresDia indicadoresDia() {
    LocalDate hoy = reloj.hoy();
    List<Pago> pagosHoy = pagosDelDia(hoy);
    long ventaTotal = pagosHoy.stream().mapToLong(Pago::getTotal).sum();

    List<Long> tiempos = new ArrayList<>();
    List<Orden> delDia = ordenes.abiertasDesde(reloj.inicioDelDia(hoy));
    for (Orden orden : delDia) {
      for (ItemOrden item : orden.getItems()) {
        if (item.getEnviadoEn() == null || item.getListoEn() == null) continue;
        if (!reloj.diaDe(item.getListoEn()).equals(hoy)) continue;
        tiempos.add(Duration.between(item.getEnviadoEn(), item.getListoEn()).toMinutes());
      }
    }

    List<Mesa> todas = mesas.listar();

    return new Dtos.IndicadoresDia(
        ventaTotal,
        pagosHoy.size(),
        pagosHoy.isEmpty() ? 0 : Math.round((double) ventaTotal / pagosHoy.size()),
        (int) todas.stream().filter(m -> m.getEstado() != co.elpatio.dominio.salon.EstadoMesa.LIBRE).count(),
        todas.size(),
        pagosHoy.stream().mapToLong(Pago::getPropina).sum(),
        pagosHoy.stream().mapToLong(Pago::getInc).sum(),
        tiempos.isEmpty()
            ? 0
            : (int) Math.round(tiempos.stream().mapToLong(Long::longValue).average().orElse(0)),
        delDia.stream().mapToInt(Orden::getComensales).sum());
  }

  // ---------------------------------------------------------------------------
  // Historico de ventas
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<Dtos.VentaHistorica> historicoVentas(
      LocalDate desde, LocalDate hasta, String meseroId, MetodoPago metodo) {

    Map<String, Orden> porId =
        ordenes.listar().stream().collect(Collectors.toMap(Orden::getId, Function.identity()));
    Map<String, Mesa> porMesa =
        mesas.listar().stream().collect(Collectors.toMap(Mesa::getId, Function.identity()));
    Map<String, String> nombres =
        usuarios.listar().stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));

    List<Dtos.VentaHistorica> resultado = new ArrayList<>();

    for (Pago pago : pagos.listar()) {
      LocalDate dia = reloj.diaDe(pago.getFechaHora());
      if (desde != null && dia.isBefore(desde)) continue;
      if (hasta != null && dia.isAfter(hasta)) continue;
      if (metodo != null && pago.getMetodo() != metodo) continue;

      Orden orden = porId.get(pago.getOrdenId());
      if (orden == null) continue;
      if (meseroId != null && !meseroId.isBlank() && !meseroId.equals(orden.getMeseroId())) continue;

      Mesa mesa = orden.getMesaId() == null ? null : porMesa.get(orden.getMesaId());
      resultado.add(
          new Dtos.VentaHistorica(
              orden,
              pago,
              orden.esExterno()
                  ? orden.etiquetaCanal()
                  : (mesa != null ? mesa.etiqueta() : "Mesa retirada"),
              nombres.getOrDefault(orden.getMeseroId(), "")));
    }

    resultado.sort(Comparator.comparing((Dtos.VentaHistorica v) -> v.pago().getFechaHora()).reversed());
    return resultado;
  }

  // ---------------------------------------------------------------------------
  // Cierre de caja
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public Dtos.ResumenTurno resumenTurnoActual() {
    LocalDate hoy = reloj.hoy();
    Turno turno = Turno.enHora(reloj.horaDe(reloj.ahora()));

    List<Pago> delTurno = pagosDelTurno(hoy, turno);
    List<Pago> deAyer = pagosDelTurno(hoy.minusDays(1), turno);
    long ventaTotal = delTurno.stream().mapToLong(Pago::getTotal).sum();

    // El canal de cada pago sale de su comanda: el pago no lo guarda porque ya
    // esta en la orden, y duplicarlo abriria la puerta a que se contradigan.
    List<Orden> todas = ordenes.listar();
    Map<String, TipoPedido> canalPorOrden =
        todas.stream().collect(Collectors.toMap(Orden::getId, Orden::getTipo, (a, b) -> a));

    // Las comandas que corresponden a los pagos del turno. Todo lo que se
    // reporta de la comanda -comensales, descuentos, anulaciones- se mira
    // sobre estas y no sobre las del dia entero: un cierre es de un turno.
    Set<String> ordenesDelTurno =
        delTurno.stream().map(Pago::getOrdenId).collect(Collectors.toSet());
    List<Orden> comandasDelTurno =
        todas.stream().filter(o -> ordenesDelTurno.contains(o.getId())).toList();

    // La base gravable es el subtotal de alimentos y bebidas: lo unico que
    // causa impuesto al consumo. Los cargos y el domicilio van aparte porque no
    // lo causan, y sumarlos aqui inflaria la base que se declara.
    long baseGravable = delTurno.stream().mapToLong(Pago::getSubtotal).sum();
    long totalCargos = delTurno.stream().mapToLong(Pago::getCargosAdicionales).sum();
    long totalEnvios = delTurno.stream().mapToLong(Pago::getCostoEnvio).sum();

    long descuentos =
        comandasDelTurno.stream()
            .flatMap(o -> o.getItems().stream())
            .filter(ItemOrden::estaVigente)
            .mapToLong(ItemOrden::descuento)
            .sum();

    List<ItemOrden> anulados =
        comandasDelTurno.stream()
            .flatMap(o -> o.getItems().stream())
            .filter(i -> !i.estaVigente())
            .toList();

    return new Dtos.ResumenTurno(
        hoy,
        turno,
        ventaTotal,
        totalEn(delTurno, MetodoPago.EFECTIVO),
        totalEn(delTurno, MetodoPago.TARJETA),
        totalEn(delTurno, MetodoPago.TRANSFERENCIA),
        delTurno.stream().mapToLong(Pago::getPropina).sum(),
        delTurno.stream().mapToLong(Pago::getInc).sum(),
        delTurno.size(),
        delTurno.isEmpty() ? 0 : Math.round((double) ventaTotal / delTurno.size()),
        totalDeCanal(delTurno, canalPorOrden, TipoPedido.MESA),
        totalDeCanal(delTurno, canalPorOrden, TipoPedido.DOMICILIO),
        totalDeCanal(delTurno, canalPorOrden, TipoPedido.LLEVAR),
        totalEnvios,
        deAyer.stream().mapToLong(Pago::getTotal).sum(),
        deAyer.size(),
        baseGravable,
        totalCargos + totalEnvios,
        totalCargos,
        ajustes.leer().getPorcentajeInc(),
        descuentos,
        comandasDelTurno.stream().mapToInt(Orden::getComensales).sum(),
        anulados.size(),
        anulados.stream().mapToLong(ItemOrden::precio).sum());
  }

  @Transactional(readOnly = true)
  public List<CierreCaja> listarCierres() {
    return cierres.listar();
  }

  @Transactional
  public CierreCaja cerrarTurno(String cerradoPor) {
    Dtos.ResumenTurno resumen = resumenTurnoActual();

    CierreCaja cierre = new CierreCaja();
    cierre.setId(ids.nuevo("cc"));
    cierre.setFecha(resumen.fecha());
    cierre.setTurno(resumen.turno());
    cierre.setVentaTotal(resumen.ventaTotal());
    cierre.setTotalEfectivo(resumen.totalEfectivo());
    cierre.setTotalTarjeta(resumen.totalTarjeta());
    cierre.setTotalTransferencia(resumen.totalTransferencia());
    cierre.setPropinasTotales(resumen.propinasTotales());
    cierre.setIncTotal(resumen.incTotal());
    cierre.setOrdenesAtendidas(resumen.ordenesAtendidas());
    cierre.setTicketPromedio(resumen.ticketPromedio());
    cierre.setTotalSalon(resumen.totalSalon());
    cierre.setTotalDomicilio(resumen.totalDomicilio());
    cierre.setTotalLlevar(resumen.totalLlevar());
    cierre.setTotalEnvios(resumen.totalEnvios());
    cierre.setBaseGravable(resumen.baseGravable());
    cierre.setBaseNoGravada(resumen.baseNoGravada());
    cierre.setTotalCargos(resumen.totalCargos());
    cierre.setPorcentajeInc(resumen.porcentajeInc());
    cierre.setDescuentos(resumen.descuentos());
    cierre.setComensales(resumen.comensales());
    cierre.setLineasAnuladas(resumen.lineasAnuladas());
    cierre.setValorAnulado(resumen.valorAnulado());
    cierre.setCerradoPor(cerradoPor);
    cierre.setFechaHora(reloj.ahora());

    CierreCaja guardado = cierres.guardar(cierre);
    eventos.publicar(List.of("cierres"));
    return guardado;
  }

  // ---------------------------------------------------------------------------
  // Reportes
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public Dtos.Reportes reportes(int dias) {
    Instant desde = reloj.ahora().minus(Duration.ofDays(dias));
    List<Pago> enRango =
        pagos.listar().stream().filter(p -> !p.getFechaHora().isBefore(desde)).toList();
    Map<String, Orden> porId =
        ordenes.listar().stream().collect(Collectors.toMap(Orden::getId, Function.identity()));

    Map<TipoPedido, long[]> canales = new LinkedHashMap<>(); // [ordenes, ventas]
    Map<String, long[]> productos = new LinkedHashMap<>(); // [unidades, ingreso]
    Map<Integer, long[]> franjas = new LinkedHashMap<>(); // [ventas, ordenes]
    Map<String, long[]> meseros = new LinkedHashMap<>(); // [ordenes, ventas, propinas]
    Map<String, Long> porDia = new LinkedHashMap<>();

    for (Pago pago : enRango) {
      Orden orden = porId.get(pago.getOrdenId());
      if (orden == null) continue;

      for (ItemOrden item : orden.itemsVigentes()) {
        long adicionales =
            item.getModificadoresSeleccionados().stream()
                .mapToLong(ModificadorSeleccionado::precioAdicional)
                .sum();
        long[] actual = productos.computeIfAbsent(item.getNombre(), n -> new long[2]);
        actual[0] += item.getCantidad();
        actual[1] += (item.getPrecioUnitario() + adicionales) * item.getCantidad();
      }

      int hora = reloj.horaDe(pago.getFechaHora()).getHour();
      long[] franja = franjas.computeIfAbsent(hora, h -> new long[2]);
      franja[0] += pago.getTotal();
      franja[1] += 1;

      long[] mesero = meseros.computeIfAbsent(orden.getMeseroId(), m -> new long[3]);
      mesero[0] += 1;
      mesero[1] += pago.getTotal();
      mesero[2] += pago.getPropina();

      long[] canal = canales.computeIfAbsent(orden.getTipo(), c -> new long[2]);
      canal[0] += 1;
      canal[1] += pago.getTotal();

      String dia = reloj.diaDe(pago.getFechaHora()).format(CLAVE_DIA);
      porDia.merge(dia, pago.getTotal(), Long::sum);
    }

    // Tiempo real de preparacion por producto, desde el envio hasta que sale
    // listo. Se descartan los extremos: un turno que quedo abierto toda la
    // noche no dice nada sobre cuanto tarda ese plato.
    Map<String, long[]> tiempos = new LinkedHashMap<>();
    for (Orden orden : ordenes.abiertasDesde(desde)) {
      for (ItemOrden item : orden.getItems()) {
        if (item.getEnviadoEn() == null || item.getListoEn() == null) continue;
        long minutos = Duration.between(item.getEnviadoEn(), item.getListoEn()).toMinutes();
        if (minutos <= 0 || minutos > 180) continue;
        long[] actual = tiempos.computeIfAbsent(item.getNombre(), n -> new long[2]);
        actual[0] += minutos;
        actual[1] += 1;
      }
    }

    Map<String, String> nombres =
        usuarios.listar().stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));

    return new Dtos.Reportes(
        productos.entrySet().stream()
            .map(e -> new Dtos.ProductoVendido(e.getKey(), (int) e.getValue()[0], e.getValue()[1]))
            .sorted(Comparator.comparingInt(Dtos.ProductoVendido::unidades).reversed())
            .limit(12)
            .toList(),
        franjas.entrySet().stream()
            .map(
                e ->
                    new Dtos.VentaPorFranja(
                        franjaHoraria(e.getKey()), e.getKey(), e.getValue()[0], (int) e.getValue()[1]))
            .sorted(Comparator.comparingInt(Dtos.VentaPorFranja::hora))
            .toList(),
        meseros.entrySet().stream()
            .map(
                e ->
                    new Dtos.VentaPorMesero(
                        nombres.getOrDefault(e.getKey(), "Sin asignar"),
                        (int) e.getValue()[0],
                        e.getValue()[1],
                        e.getValue()[0] == 0 ? 0 : Math.round((double) e.getValue()[1] / e.getValue()[0]),
                        e.getValue()[2]))
            .sorted(Comparator.comparingLong(Dtos.VentaPorMesero::ventas).reversed())
            .toList(),
        tiempos.entrySet().stream()
            .map(
                e ->
                    new Dtos.TiempoProducto(
                        e.getKey(),
                        (int) Math.round((double) e.getValue()[0] / e.getValue()[1]),
                        (int) e.getValue()[1]))
            .sorted(Comparator.comparingInt(Dtos.TiempoProducto::minutos).reversed())
            .limit(12)
            .toList(),
        porDia.entrySet().stream()
            .map(e -> new Dtos.VentaPorDia(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(Dtos.VentaPorDia::dia))
            .toList(),
        canales.entrySet().stream()
            .map(
                e ->
                    new Dtos.VentaPorCanal(
                        e.getKey(),
                        (int) e.getValue()[0],
                        e.getValue()[1],
                        e.getValue()[0] == 0
                            ? 0
                            : Math.round((double) e.getValue()[1] / e.getValue()[0])))
            .sorted(Comparator.comparingLong(Dtos.VentaPorCanal::ventas).reversed())
            .toList());
  }

  /** Franja horaria para los reportes: "7 p. m." */
  private String franjaHoraria(int hora) {
    int doce = hora % 12 == 0 ? 12 : hora % 12;
    return doce + (hora < 12 ? " a. m." : " p. m.");
  }

  // ---------------------------------------------------------------------------
  // Alertas
  // ---------------------------------------------------------------------------

  /** Mesas esperando comida hace rato y cuentas pedidas sin cobrar. */
  @Transactional(readOnly = true)
  public List<Dtos.Alerta> alertas(int umbralMinutos) {
    Map<String, Mesa> porMesa =
        mesas.listar().stream().collect(Collectors.toMap(Mesa::getId, Function.identity()));
    Instant ahora = reloj.ahora();
    List<Dtos.Alerta> lista = new ArrayList<>();

    for (Orden orden : ordenes.activas()) {
      // Un pedido externo tambien puede quedarse esperando en la plancha, y el
      // administrador tiene el mismo derecho a enterarse que con una mesa.
      Mesa mesa = orden.getMesaId() == null ? null : porMesa.get(orden.getMesaId());
      if (mesa == null && !orden.esExterno()) continue;
      String etiqueta = mesa != null ? mesa.etiqueta() : orden.etiquetaCanal();

      Instant masAntiguo = orden.esperaMasAntigua();
      if (masAntiguo != null) {
        int minutos = (int) Duration.between(masAntiguo, ahora).toMinutes();
        if (minutos >= umbralMinutos) {
          lista.add(
              new Dtos.Alerta(
                  "al_" + orden.getId() + "_demora",
                  "demora",
                  etiqueta + " lleva " + minutos + " min esperando comida",
                  minutos,
                  mesa == null ? null : mesa.getId()));
        }
      }

      if (orden.getEstado() == EstadoOrden.CUENTA_PEDIDA) {
        int minutos = (int) Duration.between(orden.getAbiertaEn(), ahora).toMinutes();
        lista.add(
            new Dtos.Alerta(
                "al_" + orden.getId() + "_cobro",
                "cobro",
                etiqueta + " pidió la cuenta y sigue sin cobrar",
                minutos,
                mesa == null ? null : mesa.getId()));
      }
    }

    lista.sort(Comparator.comparingInt(Dtos.Alerta::minutos).reversed());
    return lista;
  }

  // ---------------------------------------------------------------------------

  private List<Pago> pagosDelDia(LocalDate dia) {
    return pagos.listar().stream().filter(p -> reloj.diaDe(p.getFechaHora()).equals(dia)).toList();
  }

  private List<Pago> pagosDelTurno(LocalDate dia, Turno turno) {
    return pagos.listar().stream()
        .filter(p -> reloj.diaDe(p.getFechaHora()).equals(dia))
        .filter(p -> Turno.enHora(reloj.horaDe(p.getFechaHora())) == turno)
        .toList();
  }

  private long totalEn(List<Pago> lista, MetodoPago metodo) {
    return lista.stream().mapToLong(p -> p.valorEn(metodo)).sum();
  }

  private long totalDeCanal(
      List<Pago> lista, Map<String, TipoPedido> canalPorOrden, TipoPedido canal) {
    return lista.stream()
        .filter(p -> canalPorOrden.getOrDefault(p.getOrdenId(), TipoPedido.MESA) == canal)
        .mapToLong(Pago::getTotal)
        .sum();
  }
}
