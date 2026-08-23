package co.elpatio.dominio.pago;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;

/**
 * El anticipo de un pedido automatizado, cobrado por una pasarela externa
 * antes de que la comanda pueda entrar a cocina.
 *
 * Es distinto de `Pago`: aquel es el cobro completo de una cuenta, hecho en
 * caja, ya sucedido cuando alguien lo registra. Este nace en un estado
 * incierto -- el cliente puede pagar, demorarse o no volver -- y solo el
 * webhook de la pasarela lo puede mover de sitio. Un redirect del navegador
 * del cliente NUNCA lo aprueba: podria mentir, o simplemente cerrarse antes de
 * que el banco confirme.
 */
public class PagoOnline {
  private String id;
  private String ordenId;

  /** Lo que cruza con la pasarela: aparece en cada evento del webhook. */
  private String referencia;

  private long montoCentavos;
  private EstadoPagoOnline estado = EstadoPagoOnline.PENDIENTE;
  private String urlPago;

  /** Solo lo llena el webhook, nunca el redirect del navegador. */
  private String transactionId;

  private Instant expiraEn;
  private Instant creadaEn;
  private Instant actualizadaEn;

  public PagoOnline() {}

  public boolean estaVencido(Instant ahora) {
    return estado == EstadoPagoOnline.PENDIENTE && expiraEn != null && ahora.isAfter(expiraEn);
  }

  /**
   * El webhook confirma el pago.
   *
   * Devuelve si este aviso produjo un cambio real: Wompi reenvia eventos, y un
   * segundo aviso del mismo pago aprobado no puede volver a disparar el resto
   * del flujo (mandar la comanda a cocina, avisarle al cliente) una segunda
   * vez. Por eso esto es una consulta ademas de una accion, y quien la llama
   * decide que hacer solo cuando la respuesta es verdadera.
   */
  public boolean aprobar(String transactionId, Instant ahora) {
    if (estado == EstadoPagoOnline.APROBADO) return false;
    if (estado != EstadoPagoOnline.PENDIENTE) {
      throw new ReglaDeNegocioError("Este anticipo ya no está pendiente de pago");
    }
    estado = EstadoPagoOnline.APROBADO;
    this.transactionId = transactionId;
    actualizadaEn = ahora;
    return true;
  }

  /** Igual de idempotente que `aprobar`, para el mismo reenvio de eventos. */
  public boolean rechazar(String transactionId, Instant ahora) {
    if (estado != EstadoPagoOnline.PENDIENTE) return false;
    estado = EstadoPagoOnline.RECHAZADO;
    this.transactionId = transactionId;
    actualizadaEn = ahora;
    return true;
  }

  /** El job de expiracion lo mueve aqui cuando nadie pago a tiempo. */
  public boolean expirar(Instant ahora) {
    if (estado != EstadoPagoOnline.PENDIENTE) return false;
    estado = EstadoPagoOnline.EXPIRADO;
    actualizadaEn = ahora;
    return true;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrdenId() { return ordenId; }
  public void setOrdenId(String ordenId) { this.ordenId = ordenId; }
  public String getReferencia() { return referencia; }
  public void setReferencia(String referencia) { this.referencia = referencia; }
  public long getMontoCentavos() { return montoCentavos; }
  public void setMontoCentavos(long montoCentavos) { this.montoCentavos = montoCentavos; }
  public EstadoPagoOnline getEstado() { return estado; }
  public void setEstado(EstadoPagoOnline estado) { this.estado = estado; }
  public String getUrlPago() { return urlPago; }
  public void setUrlPago(String urlPago) { this.urlPago = urlPago; }
  public String getTransactionId() { return transactionId; }
  public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
  public Instant getExpiraEn() { return expiraEn; }
  public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }
  public Instant getCreadaEn() { return creadaEn; }
  public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
  public Instant getActualizadaEn() { return actualizadaEn; }
  public void setActualizadaEn(Instant actualizadaEn) { this.actualizadaEn = actualizadaEn; }
}
