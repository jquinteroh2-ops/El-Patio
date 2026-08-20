package co.elpatio.dominio.cobro;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** El cobro de una cuenta. Una vez escrito no se toca: es el respaldo de la caja. */
public class Pago {
  private String id;
  private String ordenId;
  private long subtotal;
  private long inc;
  private long propina;
  private long cargosAdicionales;
  /** Lo cobrado por llevarlo. Se guarda con el pago y no se recalcula. */
  private long costoEnvio;
  private long total;
  private MetodoPago metodo;
  private List<DivisionPago> divisiones = new ArrayList<>();
  private String recibidoPor;
  private Instant fechaHora;

  public Pago() {}

  /**
   * Un pago mixto sin desglose dejaria el cierre de caja descuadrado: no habria
   * forma de saber cuanto efectivo tiene que haber en el cajon.
   */
  public static void exigirDesgloseCoherente(
      MetodoPago metodo, List<DivisionPago> divisiones, long total) {
    if (metodo != MetodoPago.MIXTO) return;
    if (divisiones == null || divisiones.isEmpty()) {
      throw new ReglaDeNegocioError("Un pago mixto necesita el desglose por medio de pago");
    }
    long suma = divisiones.stream().mapToLong(DivisionPago::valor).sum();
    if (suma != total) {
      throw new ReglaDeNegocioError("Las partes del pago no suman el total de la cuenta");
    }
  }

  /**
   * Reparte el pago por medio de cobro. Un pago mixto se abre en sus partes,
   * porque de lo contrario el efectivo declarado no cuadraria con la caja.
   */
  public long valorEn(MetodoPago buscado) {
    if (metodo != MetodoPago.MIXTO) return metodo == buscado ? total : 0;
    return divisiones.stream().filter(d -> d.metodo() == buscado).mapToLong(DivisionPago::valor).sum();
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrdenId() { return ordenId; }
  public void setOrdenId(String ordenId) { this.ordenId = ordenId; }
  public long getSubtotal() { return subtotal; }
  public void setSubtotal(long subtotal) { this.subtotal = subtotal; }
  public long getInc() { return inc; }
  public void setInc(long inc) { this.inc = inc; }
  public long getPropina() { return propina; }
  public void setPropina(long propina) { this.propina = propina; }
  public long getCargosAdicionales() { return cargosAdicionales; }
  public void setCargosAdicionales(long cargosAdicionales) { this.cargosAdicionales = cargosAdicionales; }
  public long getCostoEnvio() { return costoEnvio; }
  public void setCostoEnvio(long costoEnvio) { this.costoEnvio = costoEnvio; }
  public long getTotal() { return total; }
  public void setTotal(long total) { this.total = total; }
  public MetodoPago getMetodo() { return metodo; }
  public void setMetodo(MetodoPago metodo) { this.metodo = metodo; }
  public List<DivisionPago> getDivisiones() { return divisiones; }
  public void setDivisiones(List<DivisionPago> divisiones) {
    this.divisiones = divisiones != null ? divisiones : new ArrayList<>();
  }
  public String getRecibidoPor() { return recibidoPor; }
  public void setRecibidoPor(String recibidoPor) { this.recibidoPor = recibidoPor; }
  public Instant getFechaHora() { return fechaHora; }
  public void setFechaHora(Instant fechaHora) { this.fechaHora = fechaHora; }
}
