package co.elpatio.dominio.comanda;

import java.time.Instant;

/** Decoracion, descorche, servicios especiales. No causan INC. */
public class CargoAdicional {
  private String id;
  private String nombre;
  private long valor;
  /** Nombre del usuario que lo agrego: nada entra a la cuenta sin responsable. */
  private String agregadoPor;
  private Instant agregadoEn;

  public CargoAdicional() {}

  public CargoAdicional(String id, String nombre, long valor, String agregadoPor, Instant agregadoEn) {
    this.id = id;
    this.nombre = nombre;
    this.valor = valor;
    this.agregadoPor = agregadoPor;
    this.agregadoEn = agregadoEn;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getNombre() { return nombre; }
  public void setNombre(String nombre) { this.nombre = nombre; }
  public long getValor() { return valor; }
  public void setValor(long valor) { this.valor = valor; }
  public String getAgregadoPor() { return agregadoPor; }
  public void setAgregadoPor(String agregadoPor) { this.agregadoPor = agregadoPor; }
  public Instant getAgregadoEn() { return agregadoEn; }
  public void setAgregadoEn(Instant agregadoEn) { this.agregadoEn = agregadoEn; }
}
