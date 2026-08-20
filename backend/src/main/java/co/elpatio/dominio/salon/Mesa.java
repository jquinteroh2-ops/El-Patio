package co.elpatio.dominio.salon;

import co.elpatio.dominio.error.ReglaDeNegocioError;

/** Una mesa del salon, la terraza o los privados. */
public class Mesa {
  private String id;
  private int numero;
  /** Nombre de sala si lo tiene: "Terraza 3", "Privado 2". */
  private String nombre;
  private Zona zona;
  private int capacidad;
  private EstadoMesa estado;
  private String meseroId;
  private String ordenActivaId;

  public Mesa() {}

  /** Como se nombra la mesa en cocina y en el comprobante. */
  public String etiqueta() {
    return nombre != null && !nombre.isBlank() ? nombre : "Mesa " + numero;
  }

  public void ocupar(String meseroId, String ordenId) {
    if (ordenActivaId != null) throw new ReglaDeNegocioError("La mesa ya tiene una cuenta abierta");
    this.estado = EstadoMesa.OCUPADA;
    this.meseroId = meseroId;
    this.ordenActivaId = ordenId;
  }

  /** Deja la mesa lista para el siguiente comensal. */
  public void liberar() {
    this.estado = EstadoMesa.LIBRE;
    this.meseroId = null;
    this.ordenActivaId = null;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public int getNumero() { return numero; }
  public void setNumero(int numero) { this.numero = numero; }
  public String getNombre() { return nombre; }
  public void setNombre(String nombre) { this.nombre = nombre; }
  public Zona getZona() { return zona; }
  public void setZona(Zona zona) { this.zona = zona; }
  public int getCapacidad() { return capacidad; }
  public void setCapacidad(int capacidad) { this.capacidad = capacidad; }
  public EstadoMesa getEstado() { return estado; }
  public void setEstado(EstadoMesa estado) { this.estado = estado; }
  public String getMeseroId() { return meseroId; }
  public void setMeseroId(String meseroId) { this.meseroId = meseroId; }
  public String getOrdenActivaId() { return ordenActivaId; }
  public void setOrdenActivaId(String ordenActivaId) { this.ordenActivaId = ordenActivaId; }
}
