package co.elpatio.dominio.publicacion;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Algo que el restaurante le cuenta al cliente: una promocion, un evento o una
 * foto del local.
 *
 * La vigencia y el estado de publicada son dos cosas distintas a proposito. Una
 * promocion puede estar escrita y guardada sin publicar —el dueno la prepara el
 * martes para el viernes— y una publicada puede estar fuera de vigencia sin que
 * nadie la borre. Solo se le muestra al cliente lo que cumple las dos.
 */
public class Publicacion {
  private String id;
  private TipoPublicacion tipo;
  private String titulo;
  private String cuerpo;
  /** Nombre del archivo en el almacen de imagenes. Puede no haber foto. */
  private String imagen;
  private LocalDate desde;
  private LocalDate hasta;
  private boolean publicada;
  private int orden;
  private Instant creadaEn;

  public Publicacion() {}

  /**
   * Si hoy le corresponde estar a la vista del cliente.
   *
   * Vive en el dominio y no en la consulta porque la respuesta tiene que ser la
   * misma la mire quien la mire: el sitio publico, la pantalla del dueno o el
   * dia que alguien escriba un reporte.
   */
  public boolean visibleEn(LocalDate dia) {
    if (!publicada) {
      return false;
    }
    if (desde != null && dia.isBefore(desde)) {
      return false;
    }
    return hasta == null || !dia.isAfter(hasta);
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public TipoPublicacion getTipo() { return tipo; }
  public void setTipo(TipoPublicacion tipo) { this.tipo = tipo; }
  public String getTitulo() { return titulo; }
  public void setTitulo(String titulo) { this.titulo = titulo; }
  public String getCuerpo() { return cuerpo; }
  public void setCuerpo(String cuerpo) { this.cuerpo = cuerpo; }
  public String getImagen() { return imagen; }
  public void setImagen(String imagen) { this.imagen = imagen; }
  public LocalDate getDesde() { return desde; }
  public void setDesde(LocalDate desde) { this.desde = desde; }
  public LocalDate getHasta() { return hasta; }
  public void setHasta(LocalDate hasta) { this.hasta = hasta; }
  public boolean isPublicada() { return publicada; }
  public void setPublicada(boolean publicada) { this.publicada = publicada; }
  public int getOrden() { return orden; }
  public void setOrden(int orden) { this.orden = orden; }
  public Instant getCreadaEn() { return creadaEn; }
  public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
}
