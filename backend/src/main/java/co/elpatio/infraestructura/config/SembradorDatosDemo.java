package co.elpatio.infraestructura.config;

import co.elpatio.dominio.caja.CierreCaja;
import co.elpatio.dominio.caja.Turno;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.cobro.CalculadoraCuenta;
import co.elpatio.dominio.cobro.Cuenta;
import co.elpatio.dominio.cobro.DivisionPago;
import co.elpatio.dominio.cobro.MetodoPago;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.EstadoItem;
import co.elpatio.dominio.comanda.EstadoOrden;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.pedido.ClienteExterno;
import co.elpatio.dominio.pedido.EstadoPedido;
import co.elpatio.dominio.pedido.TipoPedido;
import co.elpatio.dominio.pedido.ZonaDomicilio;
import co.elpatio.dominio.personal.Rol;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.reserva.EstadoReserva;
import co.elpatio.dominio.reserva.Ocasion;
import co.elpatio.dominio.reserva.Reserva;
import co.elpatio.dominio.salon.EstadoMesa;
import co.elpatio.dominio.salon.Mesa;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Llena el sistema de actividad para poder enseñarlo.
 *
 * Un sistema de sala vacio no se puede mostrar: el mapa esta todo libre, la
 * pantalla de cocina dice que no hay nada, y los reportes son una linea plana
 * en cero. Nada de eso demuestra que el sistema funciona; demuestra que nadie
 * lo ha usado todavia.
 *
 * Aqui se siembra casi un mes de servicio: ventas cerradas dia por dia, mesas
 * abiertas con platos en distintos momentos, pedidos esperando en recepcion,
 * reservas y cierres de caja.
 *
 * <h2>Como se quita</h2>
 *
 * Cada fila que se crea aqui lleva el identificador marcado con "demo_". Al
 * arrancar, lo primero que hace esta clase es borrar todo lo que lleve esa
 * marca; solo despues, y solo si el modo demostracion sigue encendido, vuelve
 * a sembrar. Asi:
 *
 * <ul>
 *   <li>Quitar ELPATIO_CLAVE_DEMO y redesplegar deja el sistema limpio, sin
 *       tener que entrar a la base a borrar nada a mano.
 *   <li>Lo que se haya creado durante la demostracion —una comanda de verdad,
 *       un pedido real— no lleva la marca y no se toca.
 *   <li>Volver a arrancar con el modo encendido reconstruye el mismo escenario,
 *       porque los identificadores y el azar son deterministas.
 * </ul>
 */
@Component
@Order(20)
public class SembradorDatosDemo implements ApplicationRunner {

  private static final Logger registro = LoggerFactory.getLogger(SembradorDatosDemo.class);

  /** La marca que hace reversible todo lo de aqui. */
  private static final String MARCA = "demo_";

  /** Un mes escaso: suficiente para que la grafica tenga forma. */
  private static final int DIAS_DE_HISTORIA = 27;

  /** Semilla fija: la misma demostracion en cada arranque, sin sorpresas. */
  private static final long SEMILLA = 20260821L;

  private static final List<String> CLIENTES =
      List.of(
          "Marcela Arrieta",
          "Yeison Padilla",
          "Yulieth Contreras",
          "Édgar Manuel Ariza",
          "Dayana Julio",
          "Wilfrido Baena",
          "Katia Salgado",
          "Jorge Eliécer Puello",
          "Nayibe Pérez",
          "Rafael Cantillo",
          "Sirley Guardo",
          "Osnaider Mendoza");

  private static final List<String> DIRECCIONES =
      List.of(
          "Calle 12 #4-33",
          "Carrera 15 #22-40, casa esquinera",
          "Calle 30 #18-11",
          "Transversal 7 #9-52, apto 201",
          "Carrera 21 #14-08",
          "Calle 26 #31-19",
          "Diagonal 5 #12-77",
          "Carrera 9 #27-63");

  private static final List<String> NOTAS_COCINA =
      List.of("Sin cebolla", "Sin picante", "Para compartir", "Sin sal", "Alergia: mariscos");

  private static final List<String> NOTAS_PEDIDO =
      List.of(
          "Portón verde, timbre dañado",
          "Llamar al llegar, el perro ladra",
          "Dejar en portería a nombre de la señora",
          "Segundo piso, escalera del lado derecho");

  private static final List<String> REPARTIDORES = List.of("Ronald", "Deiver", "Alexis");

  private final ModoDemostracion demostracion;
  private final Repositorios.DeUsuarios usuarios;
  private final Repositorios.DeMesas mesas;
  private final Repositorios.DeCarta carta;
  private final Repositorios.DeOrdenes ordenes;
  private final Repositorios.DePagos pagos;
  private final Repositorios.DeReservas reservas;
  private final Repositorios.DeCierres cierres;
  private final Repositorios.DeAjustes ajustes;
  private final Repositorios.DeZonasDomicilio zonas;
  private final Reloj reloj;
  private final JdbcTemplate jdbc;

  private Random azar = new Random(SEMILLA);

  public SembradorDatosDemo(
      ModoDemostracion demostracion,
      Repositorios.DeUsuarios usuarios,
      Repositorios.DeMesas mesas,
      Repositorios.DeCarta carta,
      Repositorios.DeOrdenes ordenes,
      Repositorios.DePagos pagos,
      Repositorios.DeReservas reservas,
      Repositorios.DeCierres cierres,
      Repositorios.DeAjustes ajustes,
      Repositorios.DeZonasDomicilio zonas,
      Reloj reloj,
      JdbcTemplate jdbc) {
    this.demostracion = demostracion;
    this.usuarios = usuarios;
    this.mesas = mesas;
    this.carta = carta;
    this.ordenes = ordenes;
    this.pagos = pagos;
    this.reservas = reservas;
    this.cierres = cierres;
    this.ajustes = ajustes;
    this.zonas = zonas;
    this.reloj = reloj;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments argumentos) {
    int retiradas = limpiar();

    if (!demostracion.activo()) {
      if (retiradas > 0) {
        registro.warn("Modo demostración apagado: se retiraron {} filas de prueba.", retiradas);
      }
      return;
    }

    azar = new Random(SEMILLA);
    try {
      sembrar();
    } catch (RuntimeException error) {
      // Los datos de demostracion son un adorno; el sistema no lo es. Si algo
      // falla al sembrar se anota y se arranca igual: un restaurante sin
      // pantallas por culpa de unos datos de prueba es un problema mayor que
      // una demostracion a medias.
      registro.error(
          "No se pudieron sembrar los datos de demostración; el sistema arranca igual: {}",
          error.toString(),
          error);
    }
  }

  // ---------------------------------------------------------------------------
  // Retirada
  // ---------------------------------------------------------------------------

  /**
   * Borra todo lo marcado y devuelve cuantas filas se fueron.
   *
   * Se usa starts_with y no LIKE porque en LIKE el guion bajo es un comodin de
   * un caracter: el patron ingenuo tambien borraria una fila llamada
   * "demoXalgo". El orden importa por las llaves foraneas: los pagos antes que
   * las comandas, y las mesas sueltas antes de que desaparezca la comanda a la
   * que apuntan.
   */
  private int limpiar() {
    int total = 0;

    // Tambien los pagos de verdad que se hayan cobrado sobre una comanda de
    // demostracion durante la demostracion: sin esto la comanda no se puede
    // borrar, y el arranque siguiente falla.
    total +=
        jdbc.update(
            "delete from pagos where starts_with(id, ?) or starts_with(orden_id, ?)", MARCA, MARCA);

    total +=
        jdbc.update(
            "update mesas set estado = 'libre', mesero_id = null, orden_activa_id = null"
                + " where starts_with(orden_activa_id, ?)",
            MARCA);

    // items_orden y cargos_adicionales se van solos: cuelgan de la comanda con
    // ON DELETE CASCADE.
    total += jdbc.update("delete from ordenes where starts_with(id, ?)", MARCA);
    total += jdbc.update("delete from reservas where starts_with(id, ?)", MARCA);
    total += jdbc.update("delete from cierres_caja where starts_with(id, ?)", MARCA);

    return total;
  }

  // ---------------------------------------------------------------------------
  // Siembra
  // ---------------------------------------------------------------------------

  private void sembrar() {
    List<ItemCarta> platos = carta.listarItems().stream().filter(ItemCarta::isDisponible).toList();
    List<Mesa> salon = mesas.listar();
    List<Usuario> meseros = porRol(Rol.MESERO);
    Usuario cajero = unoDeRol(Rol.CAJERO);

    if (platos.isEmpty() || salon.isEmpty() || meseros.isEmpty() || cajero == null) {
      registro.warn("No hay carta, salón o personal: no se siembran datos de demostración.");
      return;
    }

    int inc = ajustes.leer().getPorcentajeInc();
    List<ZonaDomicilio> activas = zonas.listar().stream().filter(ZonaDomicilio::isActiva).toList();
    LocalDate hoy = reloj.hoy();

    int ventas = 0;
    for (int atras = DIAS_DE_HISTORIA; atras >= 1; atras--) {
      LocalDate dia = hoy.minusDays(atras);
      // El restaurante cierra los lunes. Un lunes en cero en la gráfica no es
      // un hueco en los datos: es el negocio.
      if (dia.getDayOfWeek() == DayOfWeek.MONDAY) continue;
      List<Venta> delDia = venderDia(dia, platos, salon, meseros, cajero, activas, inc, false);
      ventas += delDia.size();
      cerrarCaja(dia, delDia, cajero);
    }

    // Hoy se siembra siempre, sea el dia que sea: una demostracion sobre una
    // pantalla vacia no demuestra nada.
    ventas += venderDia(hoy, platos, salon, meseros, cajero, activas, inc, true).size();

    int enMesa = abrirMesas(platos, salon, meseros);
    int enRecepcion = ponerPedidosEnRecepcion(platos, activas);
    int agendadas = agendarReservas(salon);

    registro.warn(
        "Datos de demostración sembrados: {} ventas, {} mesas abiertas, {} pedidos en recepción,"
            + " {} reservas. Se retiran solos al quitar ELPATIO_CLAVE_DEMO.",
        ventas,
        enMesa,
        enRecepcion,
        agendadas);
  }

  /** Una comanda cobrada con su pago, que es lo que alimenta caja y reportes. */
  private record Venta(Orden orden, Pago pago) {}

  // ---------------------------------------------------------------------------
  // Ventas cerradas
  // ---------------------------------------------------------------------------

  private List<Venta> venderDia(
      LocalDate dia,
      List<ItemCarta> platos,
      List<Mesa> salon,
      List<Usuario> meseros,
      Usuario cajero,
      List<ZonaDomicilio> activas,
      int inc,
      boolean esHoy) {

    List<Venta> ventas = new ArrayList<>();
    LocalTime limite = esHoy ? reloj.horaDe(reloj.ahora()).minusMinutes(20) : LocalTime.of(22, 30);
    if (esHoy && limite.isBefore(LocalTime.of(12, 30))) return ventas;

    int cuantas = ventasEsperadas(dia);
    // Los numeros de un dia pasado arrancan despues de los que ya haya: si
    // alguien uso el sistema de verdad ese dia, su comanda ya ocupo el 1.
    int base = esHoy ? 0 : primerNumeroLibre(dia);
    for (int i = 0; i < cuantas; i++) {
      LocalTime hora = horaDeServicio(limite);
      Instant cobro = reloj.inicioDelDia(dia).plusSeconds(hora.toSecondOfDay());
      Instant apertura = cobro.minusSeconds(60L * (25 + azar.nextInt(50)));

      String id = MARCA + "ord_" + dia.toString().replace("-", "") + "_" + i;
      TipoPedido tipo = tipoDeVenta();

      Orden orden = new Orden();
      orden.setId(id);
      orden.setTipo(tipo);
      orden.setAbiertaEn(apertura);
      orden.setNumero(consecutivo(dia, base, i));

      if (tipo == TipoPedido.MESA) {
        Mesa mesa = uno(salon);
        orden.setMesaId(mesa.getId());
        orden.setMeseroId(uno(meseros).getId());
        orden.setComensales(comensales(mesa));
      } else {
        orden.setComensales(1);
        orden.setRecibidoEn(apertura);
        vestirDePedido(orden, tipo, activas);
        orden.setEstadoPedido(EstadoPedido.ENTREGADO);
        if (tipo == TipoPedido.DOMICILIO) orden.setRepartidor(uno(REPARTIDORES));
      }

      ponerPlatos(orden, platos, 2 + azar.nextInt(4));
      servirTodo(orden, apertura);
      orden.marcarPagada(cobro);
      ordenes.guardar(orden);

      ventas.add(new Venta(orden, cobrar(orden, id, inc, cobro, cajero)));
    }
    return ventas;
  }

  /**
   * Que numero le toca a una comanda sembrada.
   *
   * El de hoy sale del contador de verdad y no de un numero inventado: si el
   * mesero abre una mesa en plena demostracion, su comanda no puede chocar
   * contra una sembrada que ya ocupo ese numero del dia.
   *
   * Los de dias pasados arrancan despues del ultimo que ya exista en la base.
   * Antes empezaban en 1 siempre, y eso funcionaba solo mientras nadie hubiera
   * usado el sistema de verdad: en cuanto un dia tenia una comanda propia con
   * el numero 1, la llave (dia, numero) rechazaba la sembrada y el arranque
   * entero se caia. Un dato de prueba no puede dejar el restaurante sin sistema.
   */
  private int consecutivo(LocalDate dia, int base, int indice) {
    return dia.equals(reloj.hoy()) ? ajustes.siguienteConsecutivo() : base + indice;
  }

  /** El primer numero que nadie ha usado ese dia. */
  private int primerNumeroLibre(LocalDate dia) {
    Integer maximo =
        jdbc.queryForObject(
            "select coalesce(max(numero), 0) from ordenes where dia_operativo = ?",
            Integer.class,
            dia);
    return (maximo == null ? 0 : maximo) + 1;
  }

  /** Viernes y sabado llenos, martes flojo: la grafica tiene que tener relieve. */
  private int ventasEsperadas(LocalDate dia) {
    return switch (dia.getDayOfWeek()) {
      case FRIDAY, SATURDAY -> 15 + azar.nextInt(6);
      case SUNDAY -> 11 + azar.nextInt(5);
      case THURSDAY -> 9 + azar.nextInt(4);
      default -> 7 + azar.nextInt(4);
    };
  }

  /** Dos crestas, almuerzo y cena, como en un salon de verdad. */
  private LocalTime horaDeServicio(LocalTime limite) {
    LocalTime hora =
        azar.nextBoolean()
            ? LocalTime.of(12, 30).plusMinutes(azar.nextInt(150))
            : LocalTime.of(18, 30).plusMinutes(azar.nextInt(210));
    return hora.isAfter(limite) ? limite.minusMinutes(azar.nextInt(90)) : hora;
  }

  private TipoPedido tipoDeVenta() {
    int suerte = azar.nextInt(100);
    if (suerte < 66) return TipoPedido.MESA;
    if (suerte < 89) return TipoPedido.DOMICILIO;
    return TipoPedido.LLEVAR;
  }

  private Pago cobrar(Orden orden, String id, int inc, Instant cuando, Usuario cajero) {
    int porcentajePropina = orden.getTipo() == TipoPedido.MESA ? propinaSugerida() : 0;
    Cuenta cuenta = CalculadoraCuenta.calcular(orden, inc, porcentajePropina, null);
    MetodoPago metodo = metodoDePago();

    Pago pago = new Pago();
    pago.setId(id.replace("ord_", "pg_"));
    pago.setOrdenId(orden.getId());
    pago.setSubtotal(cuenta.subtotal());
    pago.setInc(cuenta.inc());
    pago.setPropina(cuenta.propina());
    pago.setCargosAdicionales(cuenta.cargosAdicionales());
    pago.setCostoEnvio(cuenta.costoEnvio());
    pago.setTotal(cuenta.total());
    pago.setMetodo(metodo);
    pago.setRecibidoPor(cajero.getNombre());
    pago.setFechaHora(cuando);

    if (metodo == MetodoPago.MIXTO) {
      long enEfectivo = redondearAMil(cuenta.total() / 2);
      pago.setDivisiones(
          List.of(
              new DivisionPago("Efectivo", enEfectivo, MetodoPago.EFECTIVO),
              new DivisionPago("Tarjeta", cuenta.total() - enEfectivo, MetodoPago.TARJETA)));
    }

    return pagos.guardar(pago);
  }

  private int propinaSugerida() {
    int suerte = azar.nextInt(100);
    if (suerte < 22) return 0;
    if (suerte < 55) return 5;
    return 10;
  }

  private MetodoPago metodoDePago() {
    int suerte = azar.nextInt(100);
    if (suerte < 41) return MetodoPago.EFECTIVO;
    if (suerte < 74) return MetodoPago.TARJETA;
    if (suerte < 92) return MetodoPago.TRANSFERENCIA;
    return MetodoPago.MIXTO;
  }

  private static long redondearAMil(long valor) {
    return Math.max(1000, Math.round(valor / 1000.0) * 1000);
  }

  // ---------------------------------------------------------------------------
  // Cierres de caja
  // ---------------------------------------------------------------------------

  /** Un cierre por turno, como los firma el cajero al terminar. */
  private void cerrarCaja(LocalDate dia, List<Venta> delDia, Usuario cajero) {
    for (Turno turno : Turno.values()) {
      List<Venta> delTurno =
          delDia.stream()
              .filter(v -> Turno.enHora(reloj.horaDe(v.pago().getFechaHora())) == turno)
              .toList();
      if (delTurno.isEmpty()) continue;

      long total = suma(delTurno, Pago::getTotal);
      LocalTime firma = turno == Turno.ALMUERZO ? LocalTime.of(17, 0) : LocalTime.of(23, 15);

      CierreCaja cierre = new CierreCaja();
      cierre.setId(MARCA + "cc_" + dia.toString().replace("-", "") + "_" + turno.codigo());
      cierre.setFecha(dia);
      cierre.setTurno(turno);
      cierre.setVentaTotal(total);
      cierre.setTotalEfectivo(sumaEn(delTurno, MetodoPago.EFECTIVO));
      cierre.setTotalTarjeta(sumaEn(delTurno, MetodoPago.TARJETA));
      cierre.setTotalTransferencia(sumaEn(delTurno, MetodoPago.TRANSFERENCIA));
      cierre.setPropinasTotales(suma(delTurno, Pago::getPropina));
      cierre.setIncTotal(suma(delTurno, Pago::getInc));
      cierre.setOrdenesAtendidas(delTurno.size());
      cierre.setTicketPromedio(Math.round((double) total / delTurno.size()));
      cierre.setTotalSalon(sumaDeCanal(delTurno, TipoPedido.MESA));
      cierre.setTotalDomicilio(sumaDeCanal(delTurno, TipoPedido.DOMICILIO));
      cierre.setTotalLlevar(sumaDeCanal(delTurno, TipoPedido.LLEVAR));
      cierre.setTotalEnvios(suma(delTurno, Pago::getCostoEnvio));
      cierre.setCerradoPor(cajero.getNombre());
      cierre.setFechaHora(reloj.inicioDelDia(dia).plusSeconds(firma.toSecondOfDay()));
      cierres.guardar(cierre);
    }
  }

  private long suma(List<Venta> ventas, ToLongFunction<Pago> campo) {
    return ventas.stream().map(Venta::pago).mapToLong(campo).sum();
  }

  private long sumaEn(List<Venta> ventas, MetodoPago metodo) {
    return ventas.stream().map(Venta::pago).mapToLong(p -> p.valorEn(metodo)).sum();
  }

  private long sumaDeCanal(List<Venta> ventas, TipoPedido tipo) {
    return ventas.stream()
        .filter(v -> v.orden().getTipo() == tipo)
        .mapToLong(v -> v.pago().getTotal())
        .sum();
  }

  // ---------------------------------------------------------------------------
  // Salon en curso
  // ---------------------------------------------------------------------------

  /**
   * Mesas abiertas ahora mismo, cada una en un momento distinto del servicio.
   *
   * Es lo que hace que el mapa de la comandera y la pantalla de cocina tengan
   * algo que enseñar: una recien sentada, una con la comida en la plancha, una
   * ya servida y una pidiendo la cuenta.
   */
  private int abrirMesas(List<ItemCarta> platos, List<Mesa> salon, List<Usuario> meseros) {
    List<Mesa> libres =
        new ArrayList<>(salon.stream().filter(m -> m.getEstado() == EstadoMesa.LIBRE).toList());
    if (libres.isEmpty()) return 0;

    EstadoItem[] momentos = {
      EstadoItem.PENDIENTE,
      EstadoItem.EN_PREPARACION,
      EstadoItem.LISTO,
      EstadoItem.SERVIDO,
      EstadoItem.SERVIDO
    };

    Instant ahora = reloj.ahora();
    int abiertas = 0;

    for (int i = 0; i < momentos.length && i < libres.size(); i++) {
      Mesa mesa = libres.get(i);
      Instant apertura = ahora.minusSeconds(60L * (8 + i * 14 + azar.nextInt(10)));

      Orden orden = new Orden();
      orden.setId(MARCA + "ord_sala_" + i);
      orden.setMesaId(mesa.getId());
      orden.setMeseroId(uno(meseros).getId());
      orden.setNumero(ajustes.siguienteConsecutivo());
      orden.setComensales(comensales(mesa));
      orden.setAbiertaEn(apertura);

      ponerPlatos(orden, platos, 2 + azar.nextInt(3));
      List<String> ids = orden.getItems().stream().map(ItemOrden::getId).toList();
      orden.aplicarEnvio(ids, orden.proximoTurno(), apertura.plusSeconds(180));

      for (ItemOrden item : orden.getItems()) {
        item.setEstado(momentos[i]);
        if (momentos[i] == EstadoItem.LISTO || momentos[i] == EstadoItem.SERVIDO) {
          item.setListoEn(apertura.plusSeconds(900));
        }
      }
      orden.sincronizarEstado();

      // La ultima ya pidio la cuenta: es la que el cajero puede cobrar en vivo
      // durante la demostracion.
      if (i == momentos.length - 1) orden.marcarCuentaPedida();

      ordenes.guardar(orden);

      mesa.setEstado(
          orden.getEstado() == EstadoOrden.CUENTA_PEDIDA
              ? EstadoMesa.CUENTA_PEDIDA
              : EstadoMesa.OCUPADA);
      mesa.setMeseroId(orden.getMeseroId());
      mesa.setOrdenActivaId(orden.getId());
      mesas.guardar(mesa);
      abiertas++;
    }
    return abiertas;
  }

  // ---------------------------------------------------------------------------
  // Recepcion
  // ---------------------------------------------------------------------------

  /** Un pedido en cada casilla del tablero de recepcion. */
  private int ponerPedidosEnRecepcion(List<ItemCarta> platos, List<ZonaDomicilio> activas) {
    EstadoPedido[] casillas = {
      EstadoPedido.NUEVO,
      EstadoPedido.NUEVO,
      EstadoPedido.ACEPTADO,
      EstadoPedido.EN_PREPARACION,
      EstadoPedido.LISTO,
      EstadoPedido.DESPACHADO
    };

    Instant ahora = reloj.ahora();
    int puestos = 0;

    for (int i = 0; i < casillas.length; i++) {
      EstadoPedido casilla = casillas[i];
      // El despachado es siempre un domicilio: un para llevar no sale del local
      // en una moto, se entrega en el mostrador. De paso es el que le da algo
      // que enseñar a la pantalla del repartidor.
      TipoPedido tipo =
          casilla == EstadoPedido.DESPACHADO
              ? TipoPedido.DOMICILIO
              : i % 3 == 2 ? TipoPedido.LLEVAR : TipoPedido.DOMICILIO;
      Instant entrada = ahora.minusSeconds(60L * (3 + i * 7 + azar.nextInt(6)));

      Orden orden = new Orden();
      orden.setId(MARCA + "ord_recepcion_" + i);
      orden.setTipo(tipo);
      orden.setComensales(1);
      orden.setAbiertaEn(entrada);
      orden.setRecibidoEn(entrada);
      orden.setNumero(ajustes.siguienteConsecutivo());
      vestirDePedido(orden, tipo, activas);
      orden.setNotas(uno(NOTAS_PEDIDO));

      ponerPlatos(orden, platos, 1 + azar.nextInt(3));

      // Un pedido nuevo todavia no ha salido a cocina: se queda esperando que
      // recepcion lo acepte, que es justo la decision que hay que enseñar.
      if (casilla != EstadoPedido.NUEVO) {
        orden.setMinutosEstimados(25 + azar.nextInt(20));
        List<String> ids = orden.getItems().stream().map(ItemOrden::getId).toList();
        orden.aplicarEnvio(ids, orden.proximoTurno(), entrada.plusSeconds(120));
        for (ItemOrden item : orden.getItems()) item.setEstado(itemSegunPedido(casilla));
        orden.sincronizarEstado();
      }
      // El despachado se le asigna al repartidor de demostracion cuando existe:
      // asi su pantalla tiene algo que enseñar, que es de lo que se trata todo
      // esto. Si no hay cuenta de reparto, queda un nombre suelto como antes.
      if (casilla == EstadoPedido.DESPACHADO && tipo == TipoPedido.DOMICILIO) {
        Usuario quienLoLleva = unoDeRol(Rol.REPARTIDOR);
        if (quienLoLleva == null) {
          orden.setRepartidor(uno(REPARTIDORES));
        } else {
          orden.setRepartidor(quienLoLleva.getNombre());
          orden.setRepartidorId(quienLoLleva.getId());
        }
      }

      orden.setEstadoPedido(casilla);
      ordenes.guardar(orden);
      puestos++;
    }
    return puestos;
  }

  private EstadoItem itemSegunPedido(EstadoPedido estado) {
    return switch (estado) {
      case ACEPTADO -> EstadoItem.PENDIENTE;
      case EN_PREPARACION -> EstadoItem.EN_PREPARACION;
      default -> EstadoItem.LISTO;
    };
  }

  private void vestirDePedido(Orden orden, TipoPedido tipo, List<ZonaDomicilio> activas) {
    String nombre = uno(CLIENTES);
    String telefono = "3" + (10 + azar.nextInt(90)) + (1000000 + azar.nextInt(8999999));

    if (tipo == TipoPedido.DOMICILIO && !activas.isEmpty()) {
      ZonaDomicilio zona = uno(activas);
      orden.setCliente(new ClienteExterno(nombre, telefono, uno(DIRECCIONES), zona.getNombre()));
      orden.setZonaDomicilioId(zona.getId());
      orden.setCostoEnvio(zona.getTarifa());
      orden.setMinutosEstimados(zona.getMinutosEstimados());
    } else {
      orden.setCliente(new ClienteExterno(nombre, telefono, null, null));
    }
    orden.setMetodoPagoPrevisto(azar.nextBoolean() ? MetodoPago.EFECTIVO : MetodoPago.TRANSFERENCIA);
  }

  // ---------------------------------------------------------------------------
  // Reservas
  // ---------------------------------------------------------------------------

  private int agendarReservas(List<Mesa> salon) {
    Ocasion[] ocasiones = {
      Ocasion.CUMPLEANOS,
      Ocasion.NINGUNA,
      Ocasion.ANIVERSARIO,
      Ocasion.NEGOCIOS,
      Ocasion.NINGUNA,
      Ocasion.CUMPLEANOS,
      Ocasion.NINGUNA
    };
    EstadoReserva[] estados = {
      EstadoReserva.CONFIRMADA,
      EstadoReserva.SOLICITADA,
      EstadoReserva.CONFIRMADA,
      EstadoReserva.CONFIRMADA,
      EstadoReserva.SOLICITADA,
      EstadoReserva.CONFIRMADA,
      EstadoReserva.CANCELADA
    };

    LocalDate hoy = reloj.hoy();
    for (int i = 0; i < ocasiones.length; i++) {
      LocalDate dia = hoy.plusDays(i / 3);
      LocalTime hora = LocalTime.of(19, 0).plusMinutes(30L * (i % 4));

      Reserva reserva = new Reserva();
      reserva.setId(MARCA + "res_" + i);
      reserva.setNombreCliente(CLIENTES.get(i % CLIENTES.size()));
      reserva.setTelefono("3" + (10 + azar.nextInt(90)) + (1000000 + azar.nextInt(8999999)));
      reserva.setFechaHora(reloj.inicioDelDia(dia).plusSeconds(hora.toSecondOfDay()));
      reserva.setPersonas(2 + azar.nextInt(9));
      reserva.setOcasion(ocasiones[i]);
      reserva.setEstado(estados[i]);
      if (ocasiones[i] == Ocasion.CUMPLEANOS) reserva.setNotas("Quieren torta al final");
      if (estados[i] == EstadoReserva.CONFIRMADA && !salon.isEmpty()) {
        reserva.setMesaAsignadaId(salon.get((i * 3) % salon.size()).getId());
      }
      reservas.guardar(reserva);
    }
    return ocasiones.length;
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private void ponerPlatos(Orden orden, List<ItemCarta> platos, int cuantos) {
    for (int i = 0; i < cuantos; i++) {
      ItemCarta plato = uno(platos);
      String nota = azar.nextInt(100) < 18 ? uno(NOTAS_COCINA) : null;
      orden.agregarItem(
          plato,
          1 + (azar.nextInt(100) < 25 ? 1 : 0),
          List.of(),
          nota,
          orden.getId().replace("ord_", "io_") + "_" + i);
    }
  }

  /** Deja la comanda como una mesa que ya comio: todo enviado, todo servido. */
  private void servirTodo(Orden orden, Instant apertura) {
    List<String> ids = orden.getItems().stream().map(ItemOrden::getId).toList();
    orden.aplicarEnvio(ids, orden.proximoTurno(), apertura.plusSeconds(300));
    for (ItemOrden item : orden.getItems()) {
      item.setEstado(EstadoItem.SERVIDO);
      item.setListoEn(apertura.plusSeconds(1200));
    }
    orden.sincronizarEstado();
  }

  private List<Usuario> porRol(Rol rol) {
    return usuarios.listar().stream().filter(u -> u.getRol() == rol && u.isActivo()).toList();
  }

  private Usuario unoDeRol(Rol rol) {
    List<Usuario> encontrados = porRol(rol);
    return encontrados.isEmpty() ? null : encontrados.get(0);
  }

  /**
   * Dos, tres o cuatro personas, nunca mas de lo que cabe en la mesa.
   *
   * Repartir al azar sobre la capacidad llenaba de mesas de ocho y dejaba un
   * promedio de comensales que ningun restaurante tiene: la mesa grande existe,
   * pero es la excepcion y no la media.
   */
  private int comensales(Mesa mesa) {
    return Math.min(Math.max(1, mesa.getCapacidad()), 2 + azar.nextInt(3));
  }

  private <T> T uno(List<T> lista) {
    return lista.get(azar.nextInt(lista.size()));
  }
}
