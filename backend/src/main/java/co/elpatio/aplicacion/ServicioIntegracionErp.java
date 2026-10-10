package co.elpatio.aplicacion;

import co.elpatio.dominio.cobro.DivisionPago;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.EstadoItem;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.erp.EnvioErp;
import co.elpatio.dominio.erp.EstadoEnvioErp;
import co.elpatio.dominio.erp.PoliticaReintentos;
import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.erp.VentaParaErp;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.puertos.FacturacionExterna;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El puente entre una venta cobrada y el ERP que la factura.
 *
 * El Patio no emite documentos fiscales: los emite Globalsoft. Este servicio es
 * lo que lleva la venta hasta alla y, sobre todo, lo que hace que el
 * restaurante pueda seguir cobrando cuando alla no contesta nadie.
 *
 * La regla que gobierna todo lo demas: <b>el cobro nunca espera al ERP</b>. La
 * venta se cierra, la mesa se libera y el envio queda en la cola. Un sabado a
 * las nueve de la noche, un ERP lento no puede ser la razon por la que una mesa
 * no se puede cobrar.
 */
@Service
public class ServicioIntegracionErp {

  private static final Logger registro = LoggerFactory.getLogger(ServicioIntegracionErp.class);

  /**
   * Cuantos envios se sacan por pasada.
   *
   * Tras una caida larga hay cientos esperando. Mandarlos todos de golpe contra
   * un servidor que acaba de levantarse lo vuelve a tumbar, y ademas deja la
   * transaccion abierta el tiempo que tarden todos. De a veinte, cada minuto,
   * el atraso se drena en minutos y nadie se entera.
   */
  private static final int POR_PASADA = 20;

  private final Repositorios.DeEnviosErp envios;
  private final Repositorios.DePagos pagos;
  private final Repositorios.DeOrdenes ordenes;
  private final FacturacionExterna erp;
  private final GeneradorIds ids;
  private final Reloj reloj;
  private final ObjectMapper json;
  private final PoliticaReintentos politica = PoliticaReintentos.porDefecto();

  public ServicioIntegracionErp(
      Repositorios.DeEnviosErp envios,
      Repositorios.DePagos pagos,
      Repositorios.DeOrdenes ordenes,
      FacturacionExterna erp,
      GeneradorIds ids,
      Reloj reloj,
      ObjectMapper json) {
    this.envios = envios;
    this.pagos = pagos;
    this.ordenes = ordenes;
    this.erp = erp;
    this.ids = ids;
    this.reloj = reloj;
    this.json = json;
  }

  // -------------------------------------------------------------------------
  // Encolar
  // -------------------------------------------------------------------------

  /**
   * Escribe la venta en la bandeja de salida.
   *
   * Se llama DENTRO de la transaccion que registra el pago, y no despues. Si
   * fuera despues, entre confirmar el pago y encolar el envio hay una ventana
   * —un reinicio, un corte, una excepcion— en la que la venta queda cobrada y
   * sin reportar. Esas son justamente las que nadie encuentra hasta el cierre
   * contable, cuando ya no hay a quien preguntarle.
   *
   * No lanza nunca. Una venta que ya se cobro no se deshace porque su envio no
   * se pudo encolar; queda el registro en la bitacora y la pantalla de
   * conciliacion la muestra como faltante.
   */
  public void encolar(Pago pago, Orden orden) {
    try {
      VentaParaErp venta = traducir(pago, orden);
      EnvioErp envio =
          EnvioErp.encolar(
              ids.nuevo("erp"),
              pago.getId(),
              venta.idempotencyKey(),
              json.writeValueAsString(venta),
              reloj.ahora());
      envios.guardar(envio);
    } catch (JsonProcessingException | RuntimeException e) {
      registro.error(
          "No se pudo encolar la venta {} para el ERP. El cobro SI quedo registrado.",
          pago.getId(),
          e);
    }
  }

  /**
   * De comanda cobrada a venta que un ERP pueda leer.
   *
   * Los items anulados se quedan fuera. Un plato que se devolvio a cocina no se
   * consumio, no se cobro y no puede aparecer en la contabilidad; el historico
   * de la anulacion vive en la comanda, que es donde se audita.
   */
  private VentaParaErp traducir(Pago pago, Orden orden) {
    List<VentaParaErp.LineaVenta> lineas =
        orden.getItems().stream()
            .filter(i -> i.getEstado() != EstadoItem.ANULADO)
            .map(
                (ItemOrden i) ->
                    new VentaParaErp.LineaVenta(
                        i.getItemCartaId(), i.getNombre(), i.getCantidad(), i.getPrecioUnitario(),
                        i.precio()))
            .toList();

    List<VentaParaErp.ParteDelPago> divisiones =
        pago.getDivisiones() == null
            ? List.of()
            : pago.getDivisiones().stream()
                .map((DivisionPago d) -> new VentaParaErp.ParteDelPago(d.metodo().name(), d.valor()))
                .toList();

    return new VentaParaErp(
        // Un UUID y no el id del pago: el id del pago ya identifica la fila, y
        // si algun dia hubiera que reenviar una venta a proposito —una nota
        // credito, una correccion— conviene poder darle una llave nueva sin
        // tocar su identidad.
        UUID.randomUUID().toString(),
        pago.getId(),
        orden.getId(),
        orden.getNumero(),
        pago.getFechaHora(),
        orden.getTipo().name().toLowerCase(),
        orden.getCanal().name().toLowerCase(),
        lineas,
        pago.getSubtotal(),
        pago.getInc(),
        // El porcentaje no se guarda con el pago; se deduce de lo cobrado. Con
        // subtotal cero —una cortesia completa— no hay division posible y se
        // reporta cero, que es lo que efectivamente se cobro de impuesto.
        pago.getSubtotal() == 0 ? 0 : (int) Math.round(pago.getInc() * 100.0 / pago.getSubtotal()),
        pago.getCargosAdicionales(),
        pago.getCostoEnvio(),
        pago.getPropina(),
        pago.getTotal(),
        pago.getMetodo().name().toLowerCase(),
        divisiones,
        pago.getRecibidoPor());
  }

  // -------------------------------------------------------------------------
  // Drenar la cola
  // -------------------------------------------------------------------------

  /**
   * Los identificadores de la siguiente tanda.
   *
   * Devuelve ids y no objetos, y el bucle vive en la tarea y no aqui, por una
   * razon de Spring que no se ve a simple vista: una llamada de este objeto a
   * su propio {@link #procesar} no pasa por el proxy, y {@code @Transactional}
   * se perderia en silencio. Cada envio necesita su propia transaccion —que uno
   * falle no puede arrastrar a los demas—, asi que la llamada tiene que entrar
   * desde fuera.
   */
  @Transactional(readOnly = true)
  public List<String> pendientes() {
    return envios.pendientesListos(reloj.ahora(), POR_PASADA).stream().map(EnvioErp::getId).toList();
  }

  /**
   * Manda un envio al ERP y anota como quedo.
   *
   * En su propia transaccion: que una venta mal formada falle no puede detener
   * la cola entera, que es como un atraso de una noche se descubre tres dias
   * despues.
   */
  @Transactional
  public boolean procesar(String envioId) {
    EnvioErp envio = envios.porId(envioId).orElse(null);
    if (envio == null || envio.getEstado().esFinal()) return false;

    Instant ahora = reloj.ahora();
    envio.marcarEnviado(erp.nombre(), ahora);
    envios.guardar(envio);

    VentaParaErp venta;
    try {
      venta = json.readValue(envio.getPayload(), VentaParaErp.class);
    } catch (JsonProcessingException e) {
      // El cuerpo guardado no se puede leer. Reintentar da lo mismo, asi que se
      // manda directo a revision humana en vez de gastar los ocho intentos.
      envio.fallar("El cuerpo guardado no se puede leer: " + e.getMessage(), null, sinReintentos(), ahora);
      envios.guardar(envio);
      return false;
    }

    ResultadoFacturacion resultado;
    try {
      resultado = erp.emitirDocumento(venta);
    } catch (RuntimeException e) {
      // El puerto dice que no se lanza por fallos del ERP. Si llega una
      // excepcion, el adaptador esta roto; se trata como fallo reintentable
      // pero se registra como lo que es.
      registro.error("El adaptador {} lanzo una excepcion", erp.nombre(), e);
      envio.fallar("Fallo del adaptador: " + e.getMessage(), null, politica, ahora);
      envios.guardar(envio);
      return false;
    }

    return aplicarResultado(envio, resultado, ahora);
  }

  /**
   * Anota como le fue a un envio, venga la respuesta del adaptador o del
   * agente del restaurante. Es un solo lugar a proposito: las dos rutas tienen
   * que dejar la venta en el mismo estado ante la misma respuesta.
   */
  private boolean aplicarResultado(EnvioErp envio, ResultadoFacturacion resultado, Instant ahora) {
    switch (resultado.desenlace()) {
      case CONFIRMADO -> {
        envio.confirmar(resultado, ahora);
        envios.guardar(envio);
        return true;
      }
      case EN_ESPERA -> {
        // No es un error: el adaptador hizo su parte y el documento depende de
        // alguien mas. Se deja en ENVIADA_ERP, visible en la conciliacion, y no
        // se reintenta sola: reintentar depositaria el mismo archivo otra vez.
        envio.setEstado(EstadoEnvioErp.ENVIADA_ERP);
        envio.setError(null);
        envio.setRespuestaCruda(resultado.motivo());
        envio.setProximoIntento(null);
        envio.setActualizadoEn(ahora);
        envios.guardar(envio);
        return false;
      }
      default -> {
        envio.fallar(resultado.motivo(), resultado.respuestaCruda(), politica, ahora);
        envios.guardar(envio);
        return false;
      }
    }
  }

  // -------------------------------------------------------------------------
  // Agente del restaurante
  // -------------------------------------------------------------------------

  /**
   * Cuanto tiene el agente para contestar antes de que la venta vuelva sola a
   * la cola. Diez minutos sobran para entregar una tanda a un ERP que esta en
   * la misma red; si se pasa, lo mas probable es que se haya apagado el
   * computador, y para entonces la venta debe estar otra vez disponible.
   */
  private static final Duration PLAZO_AGENTE = Duration.ofMinutes(10);

  /** Si la tarea de cada minuto debe mandar ventas o le toca al agente venir por ellas. */
  public boolean laNubeEntrega() {
    return erp.entregaDesdeLaNube();
  }

  /** Con que adaptador esta configurado el sistema. Lo pregunta el agente al arrancar. */
  public String nombreAdaptador() {
    return erp.nombre();
  }

  /** Una venta que el agente se lleva, con el cuerpo tal cual se guardo. */
  public record EnvioReclamado(String envioId, int intento, VentaParaErp venta) {}

  /**
   * Le entrega al agente la siguiente tanda.
   *
   * Cada venta queda reservada {@link #PLAZO_AGENTE}; si el agente no contesta
   * en ese tiempo vuelve a la cola. Una venta cuyo cuerpo no se puede leer no
   * se le manda: se aparta para revision humana, igual que en {@link #procesar}.
   */
  @Transactional
  public List<EnvioReclamado> reclamarParaAgente(int limite) {
    Instant ahora = reloj.ahora();
    int tanda = Math.max(1, Math.min(limite, POR_PASADA));
    List<EnvioReclamado> resultado = new ArrayList<>();
    for (EnvioErp envio : envios.pendientesListos(ahora, tanda)) {
      VentaParaErp venta;
      try {
        venta = json.readValue(envio.getPayload(), VentaParaErp.class);
      } catch (JsonProcessingException e) {
        envio.marcarEnviado("agente", ahora);
        envio.fallar(
            "El cuerpo guardado no se puede leer: " + e.getMessage(), null, sinReintentos(), ahora);
        envios.guardar(envio);
        continue;
      }
      envio.reservarParaAgente(ahora, PLAZO_AGENTE);
      envios.guardar(envio);
      resultado.add(new EnvioReclamado(envio.getId(), envio.getIntentos(), venta));
    }
    return resultado;
  }

  /**
   * Anota lo que el agente dice que paso con una venta.
   *
   * Si la venta ya estaba facturada, repetir la misma confirmacion no hace
   * nada: es el agente reintentando porque no le llego la respuesta. Lo que no
   * se acepta es otro numero de documento ni un fallo encima de una factura,
   * porque eso significaria dos documentos para una sola comida.
   */
  @Transactional
  public void registrarResultadoDelAgente(String envioId, ResultadoFacturacion resultado) {
    EnvioErp envio =
        envios.porId(envioId).orElseThrow(() -> new NoEncontradoError("Ese envio no existe"));

    if (envio.getEstado() == EstadoEnvioErp.FACTURADA_ERP) {
      if (resultado.confirmo()
          && resultado.numeroDocumento().equals(envio.getDocumentoExterno())) {
        return;
      }
      throw new co.elpatio.dominio.error.ReglaDeNegocioError(
          "Esa venta ya tiene el documento "
              + envio.getDocumentoExterno()
              + " en el ERP. No se le puede anotar otro resultado.");
    }

    aplicarResultado(envio, resultado, reloj.ahora());
  }

  /** Politica para lo que no tiene sentido reintentar: cero intentos de margen. */
  private PoliticaReintentos sinReintentos() {
    return new PoliticaReintentos(politica.esperaInicial(), 0, politica.esperaMaxima());
  }

  // -------------------------------------------------------------------------
  // Conciliacion
  // -------------------------------------------------------------------------

  /** Devuelve un envio a la cola por orden de un administrador. */
  @Transactional
  public void reintentar(String envioId) {
    EnvioErp envio =
        envios.porId(envioId).orElseThrow(() -> new NoEncontradoError("Ese envio no existe"));
    if (envio.getEstado() == EstadoEnvioErp.FACTURADA_ERP) {
      // Reintentar algo ya facturado es como se emite un segundo documento por
      // la misma comida. No se hace ni por orden manual.
      throw new co.elpatio.dominio.error.ReglaDeNegocioError(
          "Esa venta ya tiene documento en el ERP. Reintentarla lo duplicaria.");
    }
    envio.reencolar(reloj.ahora());
    envios.guardar(envio);
  }

  /** Los envios de un periodo, sin resolver. */
  @Transactional(readOnly = true)
  public List<EnvioErp> conciliacion(Instant desde, Instant hasta) {
    return envios.entre(desde, hasta);
  }

  /**
   * Los envios del periodo con su venta y su comanda ya resueltas.
   *
   * Lo consumen la pantalla de conciliacion y el reporte descargable, y por eso
   * vive aqui una sola vez: el estado de una venta no puede leerse de una forma
   * en la pantalla y de otra en el archivo que se manda al contador.
   *
   * Los pagos y las comandas se cargan de una sola vez y se cruzan en memoria.
   * Preguntar por cada uno dentro del bucle son dos consultas por venta, y un
   * mes de operacion son miles: eso no se nota probando con el dia de hoy y se
   * nota mucho el dia que alguien pide el cierre del mes.
   */
  @Transactional(readOnly = true)
  public List<VentaConciliada> conciliacionDetallada(Instant desde, Instant hasta) {
    List<EnvioErp> lista = envios.entre(desde, hasta);

    Map<String, Pago> porPago =
        pagos.entre(desde, hasta).stream()
            .collect(Collectors.toMap(Pago::getId, p -> p, (a, b) -> a));
    Map<String, Orden> porOrden =
        ordenes.listar().stream().collect(Collectors.toMap(Orden::getId, o -> o, (a, b) -> a));

    List<VentaConciliada> resultado = new ArrayList<>(lista.size());
    for (EnvioErp envio : lista) {
      // El pago puede faltar en el mapa si su fecha cae fuera del rango aunque
      // el envio no: se busca uno a uno solo en ese caso, que es el raro.
      Pago pago =
          porPago.containsKey(envio.getPagoId())
              ? porPago.get(envio.getPagoId())
              : pagos.porId(envio.getPagoId()).orElse(null);
      Orden orden = pago == null ? null : porOrden.get(pago.getOrdenId());
      resultado.add(new VentaConciliada(envio, pago, orden));
    }
    return resultado;
  }

  /** Un envio con la venta y la comanda a las que corresponde. */
  public record VentaConciliada(EnvioErp envio, Pago pago, Orden orden) {

    public int numeroComanda() {
      return orden == null ? 0 : orden.getNumero();
    }

    public long total() {
      return pago == null ? 0 : pago.getTotal();
    }

    public Instant fechaVenta() {
      return pago == null ? envio.getCreadoEn() : pago.getFechaHora();
    }

    /** El estado dicho para quien concilia, no para quien programa. */
    public String estadoLegible() {
      return switch (envio.getEstado()) {
        case FACTURADA_ERP -> "Facturada";
        case ERROR_ERP -> "Con error";
        case ENVIADA_ERP -> "Sin documento";
        case PENDIENTE_ENVIO_ERP -> "Sin enviar";
      };
    }
  }
}
