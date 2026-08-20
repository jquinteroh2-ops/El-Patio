package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.pedido.ZonaDomicilio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla `zonas_domicilio`. */
@Entity
@Table(name = "zonas_domicilio")
public class FilaZonaDomicilio {

  @Id private String id;

  private String nombre;
  private long tarifa;

  @Column(name = "monto_minimo")
  private long montoMinimo;

  @Column(name = "minutos_estimados")
  private int minutosEstimados;

  private boolean activa;

  /** `orden` es palabra reservada en varios motores; se cita para no depender de eso. */
  @Column(name = "\"orden\"")
  private int orden;

  public ZonaDomicilio aDominio() {
    ZonaDomicilio zona = new ZonaDomicilio();
    zona.setId(id);
    zona.setNombre(nombre);
    zona.setTarifa(tarifa);
    zona.setMontoMinimo(montoMinimo);
    zona.setMinutosEstimados(minutosEstimados);
    zona.setActiva(activa);
    zona.setOrden(orden);
    return zona;
  }

  public static FilaZonaDomicilio deDominio(ZonaDomicilio zona) {
    FilaZonaDomicilio fila = new FilaZonaDomicilio();
    fila.id = zona.getId();
    fila.nombre = zona.getNombre();
    fila.tarifa = zona.getTarifa();
    fila.montoMinimo = zona.getMontoMinimo();
    fila.minutosEstimados = zona.getMinutosEstimados();
    fila.activa = zona.isActiva();
    fila.orden = zona.getOrden();
    return fila;
  }
}
