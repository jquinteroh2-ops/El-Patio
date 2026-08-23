package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.pago.EstadoPagoOnline;
import co.elpatio.dominio.pago.PagoOnline;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Fila de la tabla `pagos_online`: el anticipo cobrado por Wompi u otra pasarela. */
@Entity
@Table(name = "pagos_online")
public class FilaPagoOnline {

  @Id private String id;

  @Column(name = "orden_id")
  private String ordenId;

  private String referencia;

  @Column(name = "monto_centavos")
  private long montoCentavos;

  private String estado;

  @Column(name = "url_pago")
  private String urlPago;

  @Column(name = "transaction_id")
  private String transactionId;

  @Column(name = "expira_en")
  private Instant expiraEn;

  @Column(name = "creada_en")
  private Instant creadaEn;

  @Column(name = "actualizada_en")
  private Instant actualizadaEn;

  public PagoOnline aDominio() {
    PagoOnline pago = new PagoOnline();
    pago.setId(id);
    pago.setOrdenId(ordenId);
    pago.setReferencia(referencia);
    pago.setMontoCentavos(montoCentavos);
    pago.setEstado(EstadoPagoOnline.de(estado));
    pago.setUrlPago(urlPago);
    pago.setTransactionId(transactionId);
    pago.setExpiraEn(expiraEn);
    pago.setCreadaEn(creadaEn);
    pago.setActualizadaEn(actualizadaEn);
    return pago;
  }

  public static FilaPagoOnline deDominio(PagoOnline pago) {
    FilaPagoOnline fila = new FilaPagoOnline();
    fila.id = pago.getId();
    fila.ordenId = pago.getOrdenId();
    fila.referencia = pago.getReferencia();
    fila.montoCentavos = pago.getMontoCentavos();
    fila.estado = pago.getEstado().codigo();
    fila.urlPago = pago.getUrlPago();
    fila.transactionId = pago.getTransactionId();
    fila.expiraEn = pago.getExpiraEn();
    fila.creadaEn = pago.getCreadaEn();
    fila.actualizadaEn = pago.getActualizadaEn();
    return fila;
  }
}
