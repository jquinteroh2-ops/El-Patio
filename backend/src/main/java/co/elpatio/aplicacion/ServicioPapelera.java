package co.elpatio.aplicacion;

import co.elpatio.dominio.caja.Turno;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.erp.EstadoEnvioErp;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.papelera.RegistroEliminado;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.salon.Mesa;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eliminar una venta errada, y deshacerlo.
 *
 * Solo el administrador. La venta se borra de verdad —pago, comanda, platos,
 * cargos y su envio al ERP— para que ningun reporte tenga que filtrarla, y en
 * la misma transaccion queda en la papelera la copia exacta, con quien, cuando
 * y por que. Desde la papelera se restaura tal como estaba.
 *
 * <p>Dos cosas no se permiten, y las dos son por la contabilidad:
 *
 * <ul>
 *   <li>Eliminar (o restaurar) una venta de un turno que ya tiene cierre de
 *       caja: el cierre guarda sus cifras y quedaria diciendo una plata que ya
 *       no cuadra con las ventas.
 *   <li>Eliminar una venta que el ERP ya facturo: eso se corrige alla, con una
 *       nota credito.
 * </ul>
 */
@Service
public class ServicioPapelera {

  private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-CO"));

  private final Repositorios.DePapelera papelera;
  private final Repositorios.DePagos pagos;
  private final Repositorios.DeOrdenes ordenes;
  private final Repositorios.DeMesas mesas;
  private final Repositorios.DeCierres cierres;
  private final Repositorios.DeEnviosErp enviosErp;
  private final GeneradorIds ids;
  private final Reloj reloj;
  private final PublicadorEventos eventos;

  public ServicioPapelera(
      Repositorios.DePapelera papelera,
      Repositorios.DePagos pagos,
      Repositorios.DeOrdenes ordenes,
      Repositorios.DeMesas mesas,
      Repositorios.DeCierres cierres,
      Repositorios.DeEnviosErp enviosErp,
      GeneradorIds ids,
      Reloj reloj,
      PublicadorEventos eventos) {
    this.papelera = papelera;
    this.pagos = pagos;
    this.ordenes = ordenes;
    this.mesas = mesas;
    this.cierres = cierres;
    this.enviosErp = enviosErp;
    this.ids = ids;
    this.reloj = reloj;
    this.eventos = eventos;
  }

  @Transactional
  public RegistroEliminado eliminarVenta(String pagoId, String motivo, String quien) {
    Pago pago = pagos.porId(pagoId).orElseThrow(() -> new NoEncontradoError("Esa venta no existe"));
    Orden orden =
        ordenes.porId(pago.getOrdenId()).orElseThrow(() -> new NoEncontradoError("La comanda de esa venta no existe"));

    exigirTurnoAbierto(pago, "eliminar");
    enviosErp
        .porPago(pagoId)
        .filter(e -> e.getEstado() == EstadoEnvioErp.FACTURADA_ERP)
        .ifPresent(
            e -> {
              throw new ReglaDeNegocioError(
                  "Esa venta ya tiene el documento " + e.getDocumentoExterno()
                      + " en el ERP. Se corrige allá con una nota crédito, no eliminándola aquí.");
            });

    // El registro se arma antes de borrar: si falta el motivo, no se toca nada.
    RegistroEliminado registro =
        RegistroEliminado.deVenta(
            ids.nuevo("pap"),
            pagoId,
            resumen(pago, orden),
            motivo,
            papelera.copiaDeVenta(pagoId, orden.getId()),
            quien,
            reloj.ahora());

    papelera.borrarVenta(pagoId, orden.getId());
    papelera.guardar(registro);
    eventos.publicar(List.of("pagos", "ordenes", "mesas", "papelera"));
    return registro;
  }

  /**
   * Devuelve la venta tal como estaba. Si entre tanto se cerro su turno, no se
   * restaura: la transaccion se revierte y el cierre sigue cuadrando.
   */
  @Transactional
  public RegistroEliminado restaurar(String registroId, String quien) {
    RegistroEliminado registro =
        papelera.porId(registroId).orElseThrow(() -> new NoEncontradoError("Ese registro no existe"));
    if (!RegistroEliminado.VENTA.equals(registro.getTipo())) {
      throw new ReglaDeNegocioError("Este tipo de registro no se puede restaurar");
    }
    if (registro.estaRestaurado()) {
      throw new ReglaDeNegocioError("Esta venta ya se restauró");
    }
    if (pagos.porId(registro.getReferencia()).isPresent()) {
      throw new ReglaDeNegocioError("Esa venta ya existe en el sistema");
    }

    papelera.restaurarVenta(registro.getDatos());
    Pago restaurado =
        pagos.porId(registro.getReferencia())
            .orElseThrow(() -> new IllegalStateException("La copia no traía el pago"));
    exigirTurnoAbierto(restaurado, "restaurar");

    registro.marcarRestaurado(quien, reloj.ahora());
    papelera.guardar(registro);
    eventos.publicar(List.of("pagos", "ordenes", "mesas", "papelera"));
    return registro;
  }

  @Transactional(readOnly = true)
  public List<RegistroEliminado> listar() {
    return papelera.listar();
  }

  private void exigirTurnoAbierto(Pago pago, String accion) {
    LocalDate dia = reloj.diaDe(pago.getFechaHora());
    Turno turno = Turno.enHora(reloj.horaDe(pago.getFechaHora()));
    boolean cerrado =
        cierres.listar().stream().anyMatch(c -> dia.equals(c.getFecha()) && turno == c.getTurno());
    if (cerrado) {
      throw new ReglaDeNegocioError(
          "Esa venta es del turno de " + turno.codigo() + " del " + FECHA.format(dia)
              + ", que ya tiene cierre de caja. No se puede " + accion
              + " sin descuadrar el cierre.");
    }
  }

  private String resumen(Pago pago, Orden orden) {
    String lugar =
        orden.esExterno()
            ? orden.etiquetaCanal()
            : orden.getMesaId() == null
                ? "Sin mesa"
                : mesas.porId(orden.getMesaId()).map(Mesa::etiqueta).orElse("Mesa retirada");
    String total = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")).format(pago.getTotal());
    return "Comanda #" + orden.getNumero() + " · " + lugar + " · $" + total + " · "
        + FECHA.format(reloj.diaDe(pago.getFechaHora())) + " " + HORA.format(reloj.horaDe(pago.getFechaHora()));
  }
}
