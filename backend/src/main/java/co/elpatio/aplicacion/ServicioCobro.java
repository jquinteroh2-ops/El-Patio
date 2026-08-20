package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.cobro.CalculadoraCuenta;
import co.elpatio.dominio.cobro.Cuenta;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.salon.Mesa;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** El cobro de una cuenta y la reimpresion de su comprobante. */
@Service
public class ServicioCobro {

  private final Repositorios.DeOrdenes ordenes;
  private final Repositorios.DePagos pagos;
  private final Repositorios.DeMesas mesas;
  private final Repositorios.DeUsuarios usuarios;
  private final Repositorios.DeAjustes ajustes;
  private final GeneradorIds ids;
  private final Reloj reloj;
  private final PublicadorEventos eventos;

  public ServicioCobro(
      Repositorios.DeOrdenes ordenes,
      Repositorios.DePagos pagos,
      Repositorios.DeMesas mesas,
      Repositorios.DeUsuarios usuarios,
      Repositorios.DeAjustes ajustes,
      GeneradorIds ids,
      Reloj reloj,
      PublicadorEventos eventos) {
    this.ordenes = ordenes;
    this.pagos = pagos;
    this.mesas = mesas;
    this.usuarios = usuarios;
    this.ajustes = ajustes;
    this.ids = ids;
    this.reloj = reloj;
    this.eventos = eventos;
  }

  /**
   * Registra el cobro y libera la mesa.
   *
   * El total se recalcula aqui contra los productos que hay en la base, no se
   * toma el que mando la tablet: si un plato se anulo mientras el cajero tenia
   * la pantalla de pago abierta, el cliente no puede terminar pagandolo.
   */
  @Transactional
  public Pago registrarPago(Dtos.DatosPago datos) {
    Orden orden =
        ordenes
            .porId(datos.ordenId())
            .orElseThrow(() -> new NoEncontradoError("La comanda ya no existe"));

    Cuenta cuenta =
        CalculadoraCuenta.calcular(
            orden, ajustes.leer().getPorcentajeInc(), datos.porcentajePropina(), datos.propina());

    Pago.exigirDesgloseCoherente(datos.metodo(), datos.divisiones(), cuenta.total());

    Pago pago = new Pago();
    pago.setId(ids.nuevo("pg"));
    pago.setOrdenId(orden.getId());
    pago.setSubtotal(cuenta.subtotal());
    pago.setInc(cuenta.inc());
    pago.setPropina(cuenta.propina());
    pago.setCargosAdicionales(cuenta.cargosAdicionales());
    pago.setTotal(cuenta.total());
    pago.setMetodo(datos.metodo());
    pago.setDivisiones(datos.divisiones());
    pago.setRecibidoPor(datos.recibidoPor());
    pago.setFechaHora(reloj.ahora());

    // Marcar la comanda antes de escribir el pago hace que un segundo cobro
    // simultaneo choque contra la guarda del agregado y no contra la clave
    // unica de la tabla, que daria un error tecnico en vez de uno de sala.
    orden.marcarPagada(pago.getFechaHora());
    ordenes.guardar(orden);
    Pago guardado = pagos.guardar(pago);

    Mesa mesa = mesas.porId(orden.getMesaId()).orElse(null);
    if (mesa != null && orden.getId().equals(mesa.getOrdenActivaId())) {
      mesa.liberar();
      mesas.guardar(mesa);
    }

    eventos.publicar(List.of("ordenes", "mesas", "pagos"));
    return guardado;
  }

  /** Todo lo que hace falta para reimprimir un comprobante desde el historico. */
  @Transactional(readOnly = true)
  public Dtos.ComprobanteDetallado obtenerComprobante(String pagoId) {
    Pago pago = pagos.porId(pagoId).orElse(null);
    if (pago == null) return null;

    Orden orden = ordenes.porId(pago.getOrdenId()).orElse(null);
    if (orden == null) return null;

    return new Dtos.ComprobanteDetallado(
        pago,
        orden,
        mesas.porId(orden.getMesaId()).map(Mesa::etiqueta).orElse("Mesa retirada"),
        usuarios.porId(orden.getMeseroId()).map(u -> u.getNombre()).orElse(""));
  }
}
