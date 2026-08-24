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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

  /** Los envios de un periodo, para cruzarlos contra la contabilidad. */
  @Transactional(readOnly = true)
  public List<EnvioErp> conciliacion(Instant desde, Instant hasta) {
    return envios.entre(desde, hasta);
  }

  /** El pago de un envio, para poder mostrar de que venta se trata. */
  @Transactional(readOnly = true)
  public Pago pagoDe(EnvioErp envio) {
    return pagos.porId(envio.getPagoId()).orElse(null);
  }

  /** La comanda de un envio, para el numero que el restaurante reconoce. */
  @Transactional(readOnly = true)
  public Orden ordenDe(Pago pago) {
    return pago == null ? null : ordenes.porId(pago.getOrdenId()).orElse(null);
  }
}
