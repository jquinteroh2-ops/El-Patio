package co.elpatio.infraestructura.persistencia.filas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * El contador del radicado, una fila por año.
 *
 * Se entrega bajo bloqueo de esta fila dentro de la transaccion que inserta la
 * solicitud. Una secuencia de PostgreSQL seria mas simple y dejaria huecos al
 * revertirse una transaccion, y un radicado con huecos no sirve para lo unico
 * que tiene que servir: demostrar cuantas solicitudes entraron.
 */
@Entity
@Table(name = "pqr_consecutivos")
public class FilaConsecutivoPqr {

  @Id private int ano;

  @Column(name = "ultimo")
  private int ultimo;

  public FilaConsecutivoPqr() {}

  public FilaConsecutivoPqr(int ano, int ultimo) {
    this.ano = ano;
    this.ultimo = ultimo;
  }

  public int getAno() { return ano; }
  public int getUltimo() { return ultimo; }
  public void setUltimo(int ultimo) { this.ultimo = ultimo; }
}
