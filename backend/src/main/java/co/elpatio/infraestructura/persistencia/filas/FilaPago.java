package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.cobro.DivisionPago;
import co.elpatio.dominio.cobro.MetodoPago;
import co.elpatio.dominio.cobro.Pago;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Type;

/** Fila de la tabla `pagos`. */
@Entity
@Table(name = "pagos")
public class FilaPago {

  @Id private String id;

  @Column(name = "orden_id")
  private String ordenId;

  private long subtotal;
  private long inc;
  private long propina;

  @Column(name = "cargos_adicionales")
  private long cargosAdicionales;

  @Column(name = "costo_envio")
  private long costoEnvio;

  private long total;
  private String metodo;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private List<DivisionPago> divisiones = new ArrayList<>();

  @Column(name = "recibido_por")
  private String recibidoPor;

  @Column(name = "fecha_hora")
  private Instant fechaHora;

  public Pago aDominio() {
    Pago pago = new Pago();
    pago.setId(id);
    pago.setOrdenId(ordenId);
    pago.setSubtotal(subtotal);
    pago.setInc(inc);
    pago.setPropina(propina);
    pago.setCargosAdicionales(cargosAdicionales);
    pago.setCostoEnvio(costoEnvio);
    pago.setTotal(total);
    pago.setMetodo(MetodoPago.de(metodo));
    pago.setDivisiones(divisiones == null ? new ArrayList<>() : new ArrayList<>(divisiones));
    pago.setRecibidoPor(recibidoPor);
    pago.setFechaHora(fechaHora);
    return pago;
  }

  public static FilaPago deDominio(Pago pago) {
    FilaPago fila = new FilaPago();
    fila.id = pago.getId();
    fila.ordenId = pago.getOrdenId();
    fila.subtotal = pago.getSubtotal();
    fila.inc = pago.getInc();
    fila.propina = pago.getPropina();
    fila.cargosAdicionales = pago.getCargosAdicionales();
    fila.costoEnvio = pago.getCostoEnvio();
    fila.total = pago.getTotal();
    fila.metodo = pago.getMetodo().codigo();
    fila.divisiones = new ArrayList<>(pago.getDivisiones());
    fila.recibidoPor = pago.getRecibidoPor();
    fila.fechaHora = pago.getFechaHora();
    return fila;
  }
}
