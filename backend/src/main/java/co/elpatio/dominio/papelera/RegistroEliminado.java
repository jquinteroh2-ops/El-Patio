package co.elpatio.dominio.papelera;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;

/**
 * Algo que el administrador elimino: que fue, por que, quien y cuando, y la
 * copia con que se puede restaurar.
 *
 * El registro nunca se borra. Restaurar no lo quita de la lista: lo marca como
 * restaurado, para que la historia completa —se elimino el martes, se restauro
 * el miercoles— quede a la vista de quien audite.
 */
public class RegistroEliminado {

  public static final String VENTA = "venta";

  private String id;
  private String tipo;
  private String referencia;
  private String resumen;
  private String motivo;
  /** La copia exacta de lo borrado. No sale por la API: es solo para restaurar. */
  private String datos;
  private String eliminadoPor;
  private Instant eliminadoEn;
  private String restauradoPor;
  private Instant restauradoEn;

  public RegistroEliminado() {}

  public static RegistroEliminado deVenta(
      String id, String pagoId, String resumen, String motivo, String datos, String quien, Instant ahora) {
    if (motivo == null || motivo.isBlank()) {
      throw new ReglaDeNegocioError("Escriba por qué se elimina: sin motivo no se puede auditar");
    }
    RegistroEliminado r = new RegistroEliminado();
    r.id = id;
    r.tipo = VENTA;
    r.referencia = pagoId;
    r.resumen = resumen;
    r.motivo = motivo.trim();
    r.datos = datos;
    r.eliminadoPor = quien;
    r.eliminadoEn = ahora;
    return r;
  }

  public boolean estaRestaurado() {
    return restauradoEn != null;
  }

  public void marcarRestaurado(String quien, Instant ahora) {
    if (estaRestaurado()) {
      throw new ReglaDeNegocioError("Esto ya se restauró el " + restauradoEn);
    }
    this.restauradoPor = quien;
    this.restauradoEn = ahora;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getTipo() { return tipo; }
  public void setTipo(String tipo) { this.tipo = tipo; }
  public String getReferencia() { return referencia; }
  public void setReferencia(String referencia) { this.referencia = referencia; }
  public String getResumen() { return resumen; }
  public void setResumen(String resumen) { this.resumen = resumen; }
  public String getMotivo() { return motivo; }
  public void setMotivo(String motivo) { this.motivo = motivo; }
  public String getDatos() { return datos; }
  public void setDatos(String datos) { this.datos = datos; }
  public String getEliminadoPor() { return eliminadoPor; }
  public void setEliminadoPor(String eliminadoPor) { this.eliminadoPor = eliminadoPor; }
  public Instant getEliminadoEn() { return eliminadoEn; }
  public void setEliminadoEn(Instant eliminadoEn) { this.eliminadoEn = eliminadoEn; }
  public String getRestauradoPor() { return restauradoPor; }
  public void setRestauradoPor(String restauradoPor) { this.restauradoPor = restauradoPor; }
  public Instant getRestauradoEn() { return restauradoEn; }
  public void setRestauradoEn(Instant restauradoEn) { this.restauradoEn = restauradoEn; }
}
