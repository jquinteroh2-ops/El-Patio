package co.elpatio.dominio.comanda;

import co.elpatio.dominio.carta.Destino;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Una linea de la comanda. */
public class ItemOrden {
  private String id;
  private String itemCartaId;
  private String nombre;
  private long precioUnitario;
  private int cantidad;
  private List<ModificadorSeleccionado> modificadoresSeleccionados = new ArrayList<>();
  /** "sin sal", "para compartir", "termino tres cuartos". */
  private String notaCocina;
  private EstadoItem estado = EstadoItem.PENDIENTE;
  private Destino destino;
  private Instant enviadoEn;
  private Instant listoEn;
  /** Entradas van en turno 1, fuertes en turno 2. 0 = todavia sin enviar. */
  private int turnoEnvio;

  public ItemOrden() {}

  /** Precio de la linea completa, con modificadores y cantidad. */
  public long precio() {
    long adicionales = modificadoresSeleccionados.stream()
        .mapToLong(ModificadorSeleccionado::precioAdicional)
        .sum();
    return (precioUnitario + adicionales) * cantidad;
  }

  public boolean estaVigente() {
    return estado != EstadoItem.ANULADO;
  }

  public boolean fueEnviado() {
    return turnoEnvio > 0;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getItemCartaId() { return itemCartaId; }
  public void setItemCartaId(String itemCartaId) { this.itemCartaId = itemCartaId; }
  public String getNombre() { return nombre; }
  public void setNombre(String nombre) { this.nombre = nombre; }
  public long getPrecioUnitario() { return precioUnitario; }
  public void setPrecioUnitario(long precioUnitario) { this.precioUnitario = precioUnitario; }
  public int getCantidad() { return cantidad; }
  public void setCantidad(int cantidad) { this.cantidad = cantidad; }
  public List<ModificadorSeleccionado> getModificadoresSeleccionados() { return modificadoresSeleccionados; }
  public void setModificadoresSeleccionados(List<ModificadorSeleccionado> valor) {
    this.modificadoresSeleccionados = valor != null ? valor : new ArrayList<>();
  }
  public String getNotaCocina() { return notaCocina; }
  public void setNotaCocina(String notaCocina) { this.notaCocina = notaCocina; }
  public EstadoItem getEstado() { return estado; }
  public void setEstado(EstadoItem estado) { this.estado = estado; }
  public Destino getDestino() { return destino; }
  public void setDestino(Destino destino) { this.destino = destino; }
  public Instant getEnviadoEn() { return enviadoEn; }
  public void setEnviadoEn(Instant enviadoEn) { this.enviadoEn = enviadoEn; }
  public Instant getListoEn() { return listoEn; }
  public void setListoEn(Instant listoEn) { this.listoEn = listoEn; }
  public int getTurnoEnvio() { return turnoEnvio; }
  public void setTurnoEnvio(int turnoEnvio) { this.turnoEnvio = turnoEnvio; }
}
