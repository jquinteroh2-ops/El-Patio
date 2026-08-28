package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.ajustes.Ajustes;
import co.elpatio.dominio.cobro.CalculadoraCuenta;
import co.elpatio.dominio.cobro.Cuenta;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.pago.Centavos;
import co.elpatio.dominio.pago.PagoOnline;
import co.elpatio.dominio.pedido.EstadoPedido;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PasarelaDePagos;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El anticipo que un canal automatizado cobra antes de que un pedido entre a
 * cocina.
 *
 * Esto es lo unico que sabe que existe Wompi (a traves del puerto
 * `PasarelaDePagos`, sin nombrarlo): el resto del sistema solo ve que un
 * pedido pasa de `esperando_anticipo` a `anticipo_pagado` y sigue su camino de
 * siempre por recepcion. Si algun dia se cambia de pasarela, este es el unico
 * servicio que se entera.
 */
@Service
public class ServicioAnticipos {

  private static final Logger registro = LoggerFactory.getLogger(ServicioAnticipos.class);

  /** Wompi recomienda links cortos: uno largo deja al cliente pensando que ya nadie lo cobra. */
  private static final long MINUTOS_VIGENCIA_LINK = 20;

  private final Repositorios.DeOrdenes ordenes;
  private final Repositorios.DePagosOnline pagosOnline;
  private final Repositorios.DeAjustes ajustes;
  private final PasarelaDePagos pasarela;
  private final GeneradorIds ids;
  private final Reloj reloj;
  private final PublicadorEventos eventos;

  public ServicioAnticipos(
      Repositorios.DeOrdenes ordenes,
      Repositorios.DePagosOnline pagosOnline,
      Repositorios.DeAjustes ajustes,
      PasarelaDePagos pasarela,
      GeneradorIds ids,
      Reloj reloj,
      PublicadorEventos eventos) {
    this.ordenes = ordenes;
    this.pagosOnline = pagosOnline;
    this.ajustes = ajustes;
    this.pasarela = pasarela;
    this.ids = ids;
    this.reloj = reloj;
    this.eventos = eventos;
  }

  /**
   * Calcula el anticipo, crea el link de pago y deja el pedido esperandolo.
   *
   * El monto SIEMPRE lo calcula este metodo, nunca quien lo llama: el
   * porcentaje es configuracion del restaurante (`Ajustes.porcentajeAnticipo`),
   * no algo que un adaptador de canal (WhatsApp, telefono) pueda decidir por su
   * cuenta.
   */
  @Transactional
  public Dtos.AnticipoCreado crearAnticipo(String ordenId) {
    Orden orden =
        ordenes.porId(ordenId).orElseThrow(() -> new NoEncontradoError("Ese pedido no existe"));
    if (!orden.esExterno()) {
      throw new ReglaDeNegocioError("Un pedido de mesa no cobra anticipo");
    }

    Ajustes actuales = ajustes.leer();
    int porcentaje = actuales.getPorcentajeAnticipo();
    if (porcentaje <= 0) {
      throw new ReglaDeNegocioError("El restaurante no tiene configurado un anticipo");
    }

    Cuenta cuenta = CalculadoraCuenta.calcular(orden, actuales.getPorcentajeInc());
    long montoAnticipoPesos = Math.round(cuenta.total() * porcentaje / 100.0);
    long montoCentavos = Centavos.deCOP(montoAnticipoPesos);

    Instant ahora = reloj.ahora();
    Instant expira = ahora.plus(MINUTOS_VIGENCIA_LINK, ChronoUnit.MINUTES);
    String referencia = ids.nuevo("anticipo");

    String urlPago =
        pasarela.crearLinkDePago(
            referencia, montoCentavos, "Anticipo pedido " + orden.etiquetaCanal(), expira);

    PagoOnline pago = new PagoOnline();
    pago.setId(ids.nuevo("pgo"));
    pago.setOrdenId(orden.getId());
    pago.setReferencia(referencia);
    pago.setMontoCentavos(montoCentavos);
    pago.setUrlPago(urlPago);
    pago.setExpiraEn(expira);
    pago.setCreadaEn(ahora);
    pago.setActualizadaEn(ahora);
    pagosOnline.guardar(pago);

    orden.cambiarEstadoPedido(EstadoPedido.ESPERANDO_ANTICIPO);
    ordenes.guardar(orden);

    eventos.publicar(List.of("pedidos", "ordenes"));

    return new Dtos.AnticipoCreado(pago.getId(), urlPago, montoCentavos, expira);
  }

  /**
   * Procesa un evento de la pasarela.
   *
   * Idempotente por construccion: `PagoOnline.aprobar/rechazar` devuelven falso
   * en un reenvio del mismo evento, y esto no vuelve a tocar la orden cuando
   * eso pasa. Es lo que permite responder 200 sin miedo aunque Wompi reenvie
   * el mismo aviso varias veces, que es justo lo que hace cuando el primer 200
   * se demora.
   */
  @Transactional
  public void procesarEvento(String referencia, boolean aprobado, String transactionId) {
    PagoOnline pago =
        pagosOnline
            .porReferencia(referencia)
            .orElseThrow(() -> new NoEncontradoError("Ese anticipo no existe"));

    Instant ahora = reloj.ahora();
    boolean cambio = aprobado ? pago.aprobar(transactionId, ahora) : pago.rechazar(transactionId, ahora);
    pagosOnline.guardar(pago);

    if (!cambio) {
      registro.info("Evento de Wompi repetido para {}, se ignora", referencia);
      return;
    }

    Orden orden =
        ordenes
            .porId(pago.getOrdenId())
            .orElseThrow(() -> new NoEncontradoError("El pedido de ese anticipo ya no existe"));

    if (aprobado) {
      // Del tramo de anticipo entra directo al punto donde siempre empezo el
      // recorrido de recepcion: de ahi en adelante lo acepta y lo despacha una
      // persona, exactamente igual que un pedido del sitio publico.
      orden.cambiarEstadoPedido(EstadoPedido.ANTICIPO_PAGADO);
      orden.cambiarEstadoPedido(EstadoPedido.NUEVO);
      ordenes.guardar(orden);
    } else {
      orden.cancelar("El anticipo fue rechazado por la pasarela de pago");
      ordenes.guardar(orden);
    }

    // Aqui se le avisaba al cliente por WhatsApp. El aviso automatico se fue
    // con el bot: el unico canal que lo recibia era el suyo, y sin pedidos por
    // WhatsApp no queda a quien mandarselo. El pedido igual aparece en
    // recepcion, que es quien contesta al cliente a mano.

    eventos.publicar(List.of("pedidos", "ordenes"));
  }

  /**
   * El job periodico llama aqui: cierra los anticipos que nadie pago a tiempo.
   */
  @Transactional
  public void expirarVencidos() {
    Instant ahora = reloj.ahora();
    boolean huboAlguno = false;
    for (PagoOnline pago : pagosOnline.pendientesVencidosAntesDe(ahora)) {
      if (!pago.expirar(ahora)) continue;
      pagosOnline.guardar(pago);
      huboAlguno = true;

      ordenes
          .porId(pago.getOrdenId())
          .ifPresent(
              orden -> {
                if (orden.getEstadoPedido() == EstadoPedido.ESPERANDO_ANTICIPO) {
                  orden.cambiarEstadoPedido(EstadoPedido.EXPIRADO);
                  ordenes.guardar(orden);
                }
              });
    }
    if (huboAlguno) eventos.publicar(List.of("pedidos", "ordenes"));
  }
}
