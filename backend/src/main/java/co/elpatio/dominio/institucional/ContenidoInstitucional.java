package co.elpatio.dominio.institucional;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;

/**
 * Un bloque de texto institucional del sitio: quienes somos, mision, vision.
 *
 * Vive en la base y no en el codigo porque el dueño del restaurante tiene que
 * poder corregir su propia mision sin pedirle a nadie un despliegue. Un texto
 * de estos se reescribe tres veces el primer mes.
 *
 * <p><b>El cuerpo es texto plano, nunca HTML.</b> Lo edita una persona desde un
 * formulario y se pinta en la pagina publica; aceptar HTML seria aceptar que
 * cualquiera con acceso al panel pueda inyectar un script en el sitio. Los
 * saltos de linea se respetan al pintar, que es todo el formato que hace falta.
 */
public class ContenidoInstitucional {

  /** Estable. El sitio busca la seccion por aqui, no por el titulo. */
  private String clave;
  private String titulo;
  private String cuerpo;
  private int orden;
  /** Apagado, no se pinta. Sirve para la vision mientras no exista. */
  private boolean visible;
  private Instant actualizadoEn;

  public ContenidoInstitucional() {}

  /**
   * Cambia el texto.
   *
   * No deja guardar un bloque visible con el cuerpo vacio: publicaria un titulo
   * seguido de nada, que en la pagina se ve como un error del sitio y no como
   * una decision. Para dejarlo en blanco, se apaga.
   */
  public void editar(String titulo, String cuerpo, boolean visible, Instant ahora) {
    String tituloLimpio = exigir(titulo, "el título");
    String cuerpoLimpio = cuerpo == null ? "" : cuerpo.trim();
    if (visible && cuerpoLimpio.isEmpty()) {
      throw new ReglaDeNegocioError(
          "Una sección visible necesita texto. Si todavía no lo tiene, déjela oculta.");
    }
    this.titulo = tituloLimpio;
    this.cuerpo = cuerpoLimpio;
    this.visible = visible;
    this.actualizadoEn = ahora;
  }

  private static String exigir(String valor, String queEs) {
    if (valor == null || valor.isBlank()) throw new ReglaDeNegocioError("Falta " + queEs);
    return valor.trim();
  }

  public String getClave() { return clave; }
  public void setClave(String clave) { this.clave = clave; }
  public String getTitulo() { return titulo; }
  public void setTitulo(String titulo) { this.titulo = titulo; }
  public String getCuerpo() { return cuerpo; }
  public void setCuerpo(String cuerpo) { this.cuerpo = cuerpo; }
  public int getOrden() { return orden; }
  public void setOrden(int orden) { this.orden = orden; }
  public boolean isVisible() { return visible; }
  public void setVisible(boolean visible) { this.visible = visible; }
  public Instant getActualizadoEn() { return actualizadoEn; }
  public void setActualizadoEn(Instant valor) { this.actualizadoEn = valor; }
}
