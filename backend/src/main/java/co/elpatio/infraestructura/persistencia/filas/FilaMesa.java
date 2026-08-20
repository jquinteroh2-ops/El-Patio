package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.salon.EstadoMesa;
import co.elpatio.dominio.salon.Mesa;
import co.elpatio.dominio.salon.Zona;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla `mesas`. */
@Entity
@Table(name = "mesas")
public class FilaMesa {

  @Id private String id;

  private int numero;
  private String nombre;
  private String zona;
  private int capacidad;
  private String estado;

  @Column(name = "mesero_id")
  private String meseroId;

  @Column(name = "orden_activa_id")
  private String ordenActivaId;

  public Mesa aDominio() {
    Mesa mesa = new Mesa();
    mesa.setId(id);
    mesa.setNumero(numero);
    mesa.setNombre(nombre);
    mesa.setZona(Zona.de(zona));
    mesa.setCapacidad(capacidad);
    mesa.setEstado(EstadoMesa.de(estado));
    mesa.setMeseroId(meseroId);
    mesa.setOrdenActivaId(ordenActivaId);
    return mesa;
  }

  public static FilaMesa deDominio(Mesa mesa) {
    FilaMesa fila = new FilaMesa();
    fila.id = mesa.getId();
    fila.numero = mesa.getNumero();
    fila.nombre = mesa.getNombre();
    fila.zona = mesa.getZona().codigo();
    fila.capacidad = mesa.getCapacidad();
    fila.estado = mesa.getEstado().codigo();
    fila.meseroId = mesa.getMeseroId();
    fila.ordenActivaId = mesa.getOrdenActivaId();
    return fila;
  }

  public String getId() { return id; }
}
