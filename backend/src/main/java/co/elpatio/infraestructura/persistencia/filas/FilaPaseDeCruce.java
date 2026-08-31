package co.elpatio.infraestructura.persistencia.filas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un pase de cruce que ya se canjeo.
 *
 * Existe para que ninguno sirva dos veces. Se guarda el identificador del token
 * y no el token: alcanza para reconocerlo y no sirve para nada si alguien se
 * lleva una copia de la base.
 *
 * <p>El usuario y el origen no hacen falta para la comprobacion; se guardan
 * para que el registro diga quien entro y desde donde el dia que haya que
 * mirarlo.
 */
@Entity
@Table(name = "pases_de_cruce_usados")
public class FilaPaseDeCruce {

  @Id private String jti;

  private String usuario;

  private String origen;

  @Column(name = "canjeado_en")
  private Instant canjeadoEn;

  @Column(name = "expira_en")
  private Instant expiraEn;

  public FilaPaseDeCruce() {}

  public FilaPaseDeCruce(String jti, String usuario, String origen, Instant canjeadoEn, Instant expiraEn) {
    this.jti = jti;
    this.usuario = usuario;
    this.origen = origen;
    this.canjeadoEn = canjeadoEn;
    this.expiraEn = expiraEn;
  }

  public String getJti() {
    return jti;
  }

  public String getUsuario() {
    return usuario;
  }

  public String getOrigen() {
    return origen;
  }

  public Instant getCanjeadoEn() {
    return canjeadoEn;
  }

  public Instant getExpiraEn() {
    return expiraEn;
  }
}
