package co.elpatio.dominio.reclutamiento;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;

/**
 * Alguien que dejo su hoja de vida.
 *
 * Es un agregado propio y de un contexto propio —reclutamiento—, sin relacion
 * con `Usuario` ni con nada de ventas. La separacion importa: un aspirante NO
 * es personal del restaurante, y confundir las dos cosas terminaria dandole una
 * cuenta del sistema a quien solo mando un PDF.
 *
 * <p><b>Aqui hay datos personales de verdad.</b> Nombre, cedula, telefono,
 * correo y una hoja de vida completa. La Ley 1581 de 2012 obliga a pedir
 * autorizacion antes de tratarlos y a poder borrarlos cuando el titular lo
 * pida, y por eso la autorizacion no es un booleano suelto sino tres campos
 * —si autorizo, cuando y desde que IP—: sin la evidencia, la autorizacion no
 * sirve de nada el dia que alguien la reclame.
 */
public class Postulacion {

  private String id;
  private String nombreCompleto;
  private TipoDocumento tipoDocumento;
  private String numeroDocumento;
  private String email;
  private String telefono;
  private CargoDeInteres cargoInteres;
  /** Presentacion breve. Opcional: no todo el mundo sabe que escribir. */
  private String mensaje;

  /** La referencia con que el almacen recupera el PDF. Nunca la ruta. */
  private String hojaDeVidaRef;
  /**
   * El nombre con que lo subieron.
   *
   * Se guarda solo para mostrarselo a quien lo descarga —«hoja de vida Ana
   * Perez.pdf» dice mas que un UUID— y jamas se usa para construir una ruta.
   */
  private String hojaDeVidaNombreOriginal;

  private EstadoPostulacion estado = EstadoPostulacion.RECIBIDA;
  private Instant fechaPostulacion;

  private boolean autorizacionDatos;
  private Instant autorizacionFecha;
  private String autorizacionIp;

  /** Lo que anota quien revisa. No lo ve el aspirante. */
  private String notasInternas;
  private Instant actualizadoEn;

  public Postulacion() {}

  /**
   * Una postulacion recien llegada.
   *
   * La autorizacion se exige aqui y no en el controlador a proposito: es una
   * regla de negocio —y de ley—, no una validacion de formulario. Ponerla en el
   * borde dejaria la puerta abierta a que otro camino de entrada la salte.
   */
  public static Postulacion recibir(
      String id,
      String nombreCompleto,
      TipoDocumento tipoDocumento,
      String numeroDocumento,
      String email,
      String telefono,
      CargoDeInteres cargoInteres,
      String mensaje,
      String hojaDeVidaRef,
      String hojaDeVidaNombreOriginal,
      boolean autorizacionDatos,
      String autorizacionIp,
      Instant ahora) {

    if (!autorizacionDatos) {
      throw new ReglaDeNegocioError(
          "Sin autorización para el tratamiento de datos no se puede recibir la hoja de vida");
    }

    Postulacion postulacion = new Postulacion();
    postulacion.id = id;
    postulacion.nombreCompleto = exigirTexto(nombreCompleto, "el nombre completo");
    postulacion.tipoDocumento = tipoDocumento;
    postulacion.numeroDocumento = exigirTexto(numeroDocumento, "el número de identificación");
    postulacion.email = exigirCorreo(email);
    postulacion.telefono = exigirTexto(telefono, "el teléfono");
    postulacion.cargoInteres = cargoInteres;
    postulacion.mensaje = recortar(mensaje, 500);
    postulacion.hojaDeVidaRef = hojaDeVidaRef;
    postulacion.hojaDeVidaNombreOriginal = hojaDeVidaNombreOriginal;
    postulacion.estado = EstadoPostulacion.RECIBIDA;
    postulacion.fechaPostulacion = ahora;
    postulacion.autorizacionDatos = true;
    postulacion.autorizacionFecha = ahora;
    postulacion.autorizacionIp = autorizacionIp;
    postulacion.actualizadoEn = ahora;
    return postulacion;
  }

  public void cambiarEstado(EstadoPostulacion nuevo, Instant ahora) {
    if (nuevo == null) throw new ReglaDeNegocioError("Falta el estado");
    this.estado = nuevo;
    this.actualizadoEn = ahora;
  }

  public void anotar(String notas, Instant ahora) {
    this.notasInternas = recortar(notas, 2000);
    this.actualizadoEn = ahora;
  }

  // -------------------------------------------------------------------------

  private static String exigirTexto(String valor, String queEs) {
    if (valor == null || valor.isBlank()) {
      throw new ReglaDeNegocioError("Falta " + queEs);
    }
    return valor.trim();
  }

  /**
   * Comprobacion minima del correo.
   *
   * Deliberadamente floja: es el canal por el que se le va a responder, asi que
   * un error evidente conviene atajarlo, pero una expresion regular estricta
   * rechaza correos validos y deja fuera a alguien que si queria postularse.
   * La validacion de verdad es que llegue el correo.
   */
  private static String exigirCorreo(String valor) {
    String correo = exigirTexto(valor, "el correo electrónico");
    int arroba = correo.indexOf('@');
    if (arroba <= 0 || correo.indexOf('.', arroba) < 0 || correo.endsWith(".")) {
      throw new ReglaDeNegocioError("El correo electrónico no parece válido");
    }
    return correo.toLowerCase();
  }

  /**
   * Corta en vez de rechazar.
   *
   * El limite lo impone la base y el frontend ya lo enseña; que el servidor
   * rechace un mensaje de 501 caracteres significa que alguien pierde lo que
   * escribio. Cortar es mas amable y no pierde nada relevante.
   */
  private static String recortar(String texto, int maximo) {
    if (texto == null || texto.isBlank()) return null;
    String limpio = texto.trim();
    return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getNombreCompleto() { return nombreCompleto; }
  public void setNombreCompleto(String valor) { this.nombreCompleto = valor; }
  public TipoDocumento getTipoDocumento() { return tipoDocumento; }
  public void setTipoDocumento(TipoDocumento valor) { this.tipoDocumento = valor; }
  public String getNumeroDocumento() { return numeroDocumento; }
  public void setNumeroDocumento(String valor) { this.numeroDocumento = valor; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getTelefono() { return telefono; }
  public void setTelefono(String telefono) { this.telefono = telefono; }
  public CargoDeInteres getCargoInteres() { return cargoInteres; }
  public void setCargoInteres(CargoDeInteres valor) { this.cargoInteres = valor; }
  public String getMensaje() { return mensaje; }
  public void setMensaje(String mensaje) { this.mensaje = mensaje; }
  public String getHojaDeVidaRef() { return hojaDeVidaRef; }
  public void setHojaDeVidaRef(String valor) { this.hojaDeVidaRef = valor; }
  public String getHojaDeVidaNombreOriginal() { return hojaDeVidaNombreOriginal; }
  public void setHojaDeVidaNombreOriginal(String valor) { this.hojaDeVidaNombreOriginal = valor; }
  public EstadoPostulacion getEstado() { return estado; }
  public void setEstado(EstadoPostulacion estado) { this.estado = estado; }
  public Instant getFechaPostulacion() { return fechaPostulacion; }
  public void setFechaPostulacion(Instant valor) { this.fechaPostulacion = valor; }
  public boolean isAutorizacionDatos() { return autorizacionDatos; }
  public void setAutorizacionDatos(boolean valor) { this.autorizacionDatos = valor; }
  public Instant getAutorizacionFecha() { return autorizacionFecha; }
  public void setAutorizacionFecha(Instant valor) { this.autorizacionFecha = valor; }
  public String getAutorizacionIp() { return autorizacionIp; }
  public void setAutorizacionIp(String valor) { this.autorizacionIp = valor; }
  public String getNotasInternas() { return notasInternas; }
  public void setNotasInternas(String valor) { this.notasInternas = valor; }
  public Instant getActualizadoEn() { return actualizadoEn; }
  public void setActualizadoEn(Instant valor) { this.actualizadoEn = valor; }
}
