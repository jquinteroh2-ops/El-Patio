package co.elpatio.dominio.sitio;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A que horas abrimos y como nos encuentran.
 *
 * Vive en la base y no en el codigo por lo mismo que el texto institucional: es
 * el dato que mas se mueve del sitio y el que peor envejece. Un horario de
 * temporada, un numero nuevo de WhatsApp o una direccion corregida no pueden
 * costar un despliegue.
 *
 * <p>Todo lo de aqui es texto plano y se pinta como texto. No se acepta HTML:
 * lo escribe alguien desde un formulario del panel, y aceptarlo seria dejar que
 * quien entre al panel inyecte un script en la pagina publica.
 */
public class FichaSitio {

  private String direccion;
  private String ciudad;
  private String telefono;
  /** El mismo numero en el formato de wa.me: solo digitos, con indicativo. */
  private String whatsapp;
  /** Sin arroba y sin url: la pantalla arma el enlace. */
  private String instagram;
  private List<FranjaHorario> horario = new ArrayList<>();
  private Instant actualizadoEn;

  public FichaSitio() {}

  /**
   * Reescribe la ficha entera.
   *
   * No deja guardar una franja a medias -un dia sin horas, o unas horas sin
   * dias- porque en la pagina eso se ve como un error del sitio y no como una
   * decision. Para quitar una franja se manda la lista sin ella.
   *
   * <p>El horario si puede quedar vacio: es un restaurante que todavia no
   * publica horas, y la seccion simplemente no se pinta.
   */
  public void editar(
      String direccion,
      String ciudad,
      String telefono,
      String whatsapp,
      String instagram,
      List<FranjaHorario> horario,
      Instant ahora) {

    this.direccion = exigir(direccion, "la dirección");
    this.ciudad = exigir(ciudad, "la ciudad");
    this.telefono = exigir(telefono, "el teléfono");

    // Solo digitos: lo que aqui se guarde se pega detras de wa.me/ y un espacio
    // o un signo de mas rompen el enlace sin dar ningun aviso.
    String soloDigitos = whatsapp == null ? "" : whatsapp.replaceAll("\\D", "");
    if (soloDigitos.length() < 10) {
      throw new ReglaDeNegocioError(
          "El WhatsApp va con indicativo y sin espacios, por ejemplo 573001234567");
    }
    this.whatsapp = soloDigitos;

    // La arroba se cae aqui y no en la pantalla: se escribe con ella la mitad
    // de las veces, y el enlace queda con dos si no se limpia.
    this.instagram = instagram == null ? "" : instagram.trim().replaceFirst("^@", "");

    List<FranjaHorario> limpias = new ArrayList<>();
    for (FranjaHorario franja : horario == null ? List.<FranjaHorario>of() : horario) {
      String dias = franja.dias() == null ? "" : franja.dias().trim();
      String horas = franja.horas() == null ? "" : franja.horas().trim();
      if (dias.isEmpty() && horas.isEmpty()) continue;
      if (dias.isEmpty() || horas.isEmpty()) {
        throw new ReglaDeNegocioError(
            "Cada franja del horario necesita los días y las horas. Para quitarla, bórrela entera.");
      }
      limpias.add(new FranjaHorario(dias, horas));
    }
    this.horario = limpias;
    this.actualizadoEn = ahora;
  }

  private static String exigir(String valor, String queEs) {
    if (valor == null || valor.isBlank()) throw new ReglaDeNegocioError("Falta " + queEs);
    return valor.trim();
  }

  public String getDireccion() { return direccion; }
  public void setDireccion(String valor) { this.direccion = valor; }
  public String getCiudad() { return ciudad; }
  public void setCiudad(String valor) { this.ciudad = valor; }
  public String getTelefono() { return telefono; }
  public void setTelefono(String valor) { this.telefono = valor; }
  public String getWhatsapp() { return whatsapp; }
  public void setWhatsapp(String valor) { this.whatsapp = valor; }
  public String getInstagram() { return instagram; }
  public void setInstagram(String valor) { this.instagram = valor; }
  public List<FranjaHorario> getHorario() { return horario; }
  public void setHorario(List<FranjaHorario> valor) {
    this.horario = valor == null ? new ArrayList<>() : new ArrayList<>(valor);
  }
  public Instant getActualizadoEn() { return actualizadoEn; }
  public void setActualizadoEn(Instant valor) { this.actualizadoEn = valor; }
}
