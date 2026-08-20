package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.comanda.CargoAdicional;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Fila de la tabla `cargos_adicionales`. */
@Entity
@Table(name = "cargos_adicionales")
public class FilaCargoAdicional {

  @Id private String id;

  private String nombre;
  private long valor;

  @Column(name = "agregado_por")
  private String agregadoPor;

  @Column(name = "agregado_en")
  private Instant agregadoEn;

  public CargoAdicional aDominio() {
    return new CargoAdicional(id, nombre, valor, agregadoPor, agregadoEn);
  }

  public static FilaCargoAdicional deDominio(CargoAdicional cargo) {
    FilaCargoAdicional fila = new FilaCargoAdicional();
    fila.id = cargo.getId();
    fila.nombre = cargo.getNombre();
    fila.valor = cargo.getValor();
    fila.agregadoPor = cargo.getAgregadoPor();
    fila.agregadoEn = cargo.getAgregadoEn();
    return fila;
  }
}
